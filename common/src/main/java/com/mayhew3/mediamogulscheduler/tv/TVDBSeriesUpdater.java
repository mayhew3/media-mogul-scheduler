package com.mayhew3.mediamogulscheduler.tv;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mayhew3.mediamogulscheduler.model.tv.*;
import com.mayhew3.mediamogulscheduler.tv.exception.ShowFailedException;
import com.mayhew3.mediamogulscheduler.tv.helper.SeriesMerger;
import com.mayhew3.mediamogulscheduler.tv.provider.TVDBJWTProvider;
import com.mayhew3.mediamogulscheduler.xml.JSONReader;
import com.mayhew3.mediamogulscheduler.xml.JSONReaderImpl;
import com.mayhew3.postgresobject.dataobject.FieldValue;
import com.mayhew3.postgresobject.db.SQLConnection;
import org.apache.http.auth.AuthenticationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class TVDBSeriesUpdater {

  private Series series;

  private SQLConnection connection;
  private TVDBJWTProvider tvdbDataProvider;
  private JSONReader jsonReader;

  private Set<Integer> foundEpisodeIds;
  private Set<Integer> erroredEpisodeIds;

  private Integer episodesAdded = 0;
  private Integer episodesUpdated = 0;
  private Integer episodesFailed = 0;

  public TVDBSeriesUpdater(SQLConnection connection,
                           @NotNull Series series,
                           TVDBJWTProvider tvdbWebProvider,
                           JSONReader jsonReader) {
    this.series = series;
    this.connection = connection;
    this.tvdbDataProvider = tvdbWebProvider;
    this.jsonReader = jsonReader;
    this.foundEpisodeIds = new HashSet<>();
    this.erroredEpisodeIds = new HashSet<>();
  }


  void updateSeries() throws SQLException, ShowFailedException, UnirestException, AuthenticationException {
    String seriesTitle = series.seriesTitle.getValue();

    debug(seriesTitle + ": ID found, getting show data.");

    Boolean duplicateSeriesMatched = duplicateSeriesMatched();

    if (!duplicateSeriesMatched) {
      updateShowData();

      if (series.tivoSeriesV2ExtId.getValue() != null) {
        tryToMatchUnmatchedEpisodes();
      }
    }
  }

  private Boolean duplicateSeriesMatched() throws SQLException, ShowFailedException {
    Integer matchId = series.tvdbMatchId.getValue();
    if (matchId != null && TVDBMatchStatus.MATCH_CONFIRMED.equals(series.tvdbMatchStatus.getValue())) {
      Optional<Series> existingSeries = Series.findSeriesFromTVDBExtID(matchId, connection);

      if (existingSeries.isPresent()) {
        SeriesMerger seriesMerger = new SeriesMerger(series, existingSeries.get(), connection);
        seriesMerger.executeMerge();
      }
      return existingSeries.isPresent();
    }
    return false;
  }

  private void tryToMatchUnmatchedEpisodes() throws SQLException {
    List<TiVoEpisode> unmatchedEpisodes = findUnmatchedEpisodes();

    debug(unmatchedEpisodes.size() + " unmatched episodes found.");

    List<String> newlyMatched = new ArrayList<>();

    for (TiVoEpisode tivoEpisode : unmatchedEpisodes) {
      TVDBEpisodeMatcher matcher = new TVDBEpisodeMatcher(connection, tivoEpisode, series.id.getValue());
      Optional<TVDBEpisode> tvdbEpisodeOptional = matcher.matchAndLinkEpisode();

      if (tvdbEpisodeOptional.isPresent()) {
        TVDBEpisode tvdbEpisode = tvdbEpisodeOptional.get();
        newlyMatched.add(tvdbEpisode.seasonNumber.getValue() + "x" + tvdbEpisode.episodeNumber.getValue());
      }
    }

    if (!newlyMatched.isEmpty()) {
      String join = Joiner.on(", ").join(newlyMatched);
      debug(newlyMatched.size() + " episodes matched: " + join);
    }
  }

  private List<TiVoEpisode> findUnmatchedEpisodes() throws SQLException {
    ResultSet resultSet = connection.prepareAndExecuteStatementFetch(
        "SELECT * " +
            "FROM tivo_episode " +
            "WHERE tivo_series_v2_ext_id = ? " +
            "AND tvdb_match_status = ? " +
            "AND retired = ? " +
            "ORDER BY episode_number, showing_start_time",
        series.tivoSeriesV2ExtId.getValue(),
        TVDBMatchStatus.MATCH_FIRST_PASS,
        0
    );
    List<TiVoEpisode> tiVoEpisodes = new ArrayList<>();
    while (resultSet.next()) {
      TiVoEpisode tiVoEpisode = new TiVoEpisode();
      tiVoEpisode.initializeFromDBObject(resultSet);
      tiVoEpisodes.add(tiVoEpisode);
    }
    return tiVoEpisodes;
  }

  private void updateShowData() throws SQLException, UnirestException, AuthenticationException, ShowFailedException {
    debug("UpdateShowData...");
    if (series.tvdbMatchId.getValue() != null && TVDBMatchStatus.MATCH_CONFIRMED.equals(series.tvdbMatchStatus.getValue())) {
      series.tvdbSeriesExtId.changeValue(series.tvdbMatchId.getValue());
    }

    Integer tvdbSeriesExtId = series.tvdbSeriesExtId.getValue();

    if (tvdbSeriesExtId == null) {
      throw new ShowFailedException("Updater trying to process series with null TVDB ID: " + series);
    }

    String seriesTitle = series.seriesTitle.getValue();

    JSONObject seriesRoot = tvdbDataProvider.getSeriesData(tvdbSeriesExtId);

    debug(seriesTitle + ": Data found, updating.");

    JSONObject seriesJson = seriesRoot.getJSONObject("data");

    TVDBSeries tvdbSeries = getTVDBSeries(tvdbSeriesExtId);

    updateTVDBSeries(tvdbSeriesExtId, seriesJson, tvdbSeries);

    // If we are finalizing the series match for the first time, add it to the collection of the person who made the add request.
    if (series.addedByUser.getValue() != null &&
        !TVDBMatchStatus.MATCH_COMPLETED.equalsIgnoreCase(series.tvdbMatchStatus.getValue()) &&
        !isSeriesAlreadyInPersonCollection(series)) {
      addPersonSeriesForRequestOwner(series);
    }

    Integer tvdbSeriesId = tvdbSeries.id.getValue();
    series.tvdbSeriesId.changeValue(tvdbSeriesId);
    series.lastTVDBUpdate.changeValue(new Date());
    series.tvdbManualQueue.changeValue(false);

    series.tvdbMatchStatus.changeValue(TVDBMatchStatus.MATCH_COMPLETED);

    series.commit(connection);

    debug("Finished series update.");

    updateAllEpisodes(tvdbSeriesExtId);
    retireOrphanedEpisodes(tvdbSeriesId);
    updateOnlyAbsoluteNumbers();

    series.tvdbNew.changeValue(false);
    series.commit(connection);

    // Change API version if no episodes failed.
    if (episodesFailed == 0) {
      tvdbSeries.apiVersion.changeValue(2);
      tvdbSeries.commit(connection);
    }

    debug(seriesTitle + ": Update complete! Added: " + episodesAdded + "; Updated: " + episodesUpdated);

  }

  private Boolean isSeriesAlreadyInPersonCollection(Series series) throws SQLException {
    Preconditions.checkArgument(series.addedByUser.getValue() != null, "Cannot check for existing PersonSeries for series with null person_id.");

    Integer personId = series.addedByUser.getValue();
    Integer seriesId = series.id.getValue();

    String sql = "SELECT 1 " +
        "FROM person_series " +
        "WHERE person_id = ? " +
        "AND series_id = ? " +
        "AND retired = ? ";

    ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql, personId, seriesId, 0);
    return resultSet.next();
  }

  private void addPersonSeriesForRequestOwner(Series series) throws SQLException {
    Preconditions.checkArgument(series.addedByUser.getValue() != null, "Cannot add PersonSeries for series with null person_id.");

    Integer personId = series.addedByUser.getValue();
    Integer seriesId = series.id.getValue();

    PersonSeries personSeries = new PersonSeries();
    personSeries.initializeForInsert();
    personSeries.personId.changeValue(personId);
    personSeries.seriesId.changeValue(seriesId);
    personSeries.tier.changeValue(1);
    personSeries.unwatchedEpisodes.changeValue(0);
    personSeries.unwatchedStreaming.changeValue(0);

    personSeries.commit(connection);
  }

  private void updateOnlyAbsoluteNumbers() throws SQLException {
    ResultSet resultSet = connection.prepareAndExecuteStatementFetch(
        "SELECT e.* " +
            "FROM episode e " +
            "WHERE e.series_id = ? " +
            "AND e.retired = ? " +
            "AND e.air_time IS NOT NULL " +
            "ORDER BY e.season, e.episode_number ", series.id.getValue(), 0);

    int i = 1;
    while (resultSet.next()) {
      Episode episode = new Episode();
      episode.initializeFromDBObject(resultSet);

      episode.absoluteNumber.changeValue(i);
      episode.commit(connection);

      i++;
    }
  }


  private void retireOrphanedEpisodes(Integer tvdbSeriesId) throws SQLException {
    String sql = "SELECT te.* " +
        "FROM tvdb_episode te " +
        "INNER JOIN episode e " +
        " ON e.tvdb_episode_id = te.id " +
        "LEFT OUTER JOIN edge_tivo_episode ete " +
        " ON ete.episode_id = e.id " +
        "LEFT OUTER JOIN episode_rating er " +
        " ON er.episode_id = e.id " +
        "WHERE ete.id IS NULL " +
        "AND er.id IS NULL " +
        "AND te.tvdb_series_id = ? " +
        "AND te.retired = ? ";

    ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql, tvdbSeriesId, 0);
    while (resultSet.next()) {
      TVDBEpisode tvdbEpisode = new TVDBEpisode();
      tvdbEpisode.initializeFromDBObject(resultSet);

      Integer tvdbEpisodeExtId = tvdbEpisode.tvdbEpisodeExtId.getValue();
      if (!foundEpisodeIds.contains(tvdbEpisodeExtId) &&
          !erroredEpisodeIds.contains(tvdbEpisodeExtId)) {
        Episode episode = tvdbEpisode.getEpisode(connection);
        episode.retire();
        episode.commit(connection);

        tvdbEpisode.retire();
        tvdbEpisode.commit(connection);
      }
    }
  }

  private <T> void updateLinkedFieldsIfNotOverridden(FieldValue<T> slaveField, FieldValue<T> masterField, @Nullable T newValue) {
    if (slaveField.getValue() == null ||
        slaveField.getValue().equals(masterField.getValue())) {
      slaveField.changeValue(newValue);
    }
    masterField.changeValue(newValue);
  }

  private void updateAllEpisodes(Integer tvdbID) throws UnirestException, AuthenticationException, SQLException {
    Integer pageNumber = 1;
    Integer lastPage;

    do {

      JSONObject episodeData = tvdbDataProvider.getEpisodeSummaries(tvdbID, pageNumber);

      JSONObject links = episodeData.getJSONObject("links");
      lastPage = jsonReader.getIntegerWithKey(links, "last");
      debug("Page " + pageNumber + " of " + lastPage + "...");

      JSONArray episodeArray = episodeData.getJSONArray("data");

      for (int i = 0; i < episodeArray.length(); i++) {
        JSONObject episode = episodeArray.getJSONObject(i);
        updateEpisode(episode);
      }

      pageNumber++;

    } while (pageNumber <= lastPage);
    debug("end updateAllEpisodes.");
  }

  @NotNull
  private TVDBSeries getTVDBSeries(Integer tvdbID) throws SQLException, ShowFailedException {
    ResultSet resultSet = connection.prepareAndExecuteStatementFetch(
        "SELECT * " +
            "FROM tvdb_series " +
            "WHERE tvdb_series_ext_id = ? " +
            "and retired = ? ",
        tvdbID, 0
    );

    TVDBSeries tvdbSeries = new TVDBSeries();

    if (resultSet.next()) {
      tvdbSeries.initializeFromDBObject(resultSet);
    } else {
      if (TVDBMatchStatus.MATCH_COMPLETED.equals(series.tvdbMatchStatus.getValue())) {
        throw new ShowFailedException("All 'Match Completed' shows should have a corresponding tvdb_series object.");
      } else {
        tvdbSeries.initializeForInsert();
      }
    }

    return tvdbSeries;
  }

  private void updateEpisode(JSONObject episode) throws SQLException {
    Integer episodeRemoteId = episode.getInt("id");
    debug("updateEpisode " + episodeRemoteId);

    try {
      TVDBEpisodeUpdater tvdbEpisodeUpdater = new TVDBEpisodeUpdater(series, connection, tvdbDataProvider, episodeRemoteId, new JSONReaderImpl(), false);
      TVDBEpisodeUpdater.EPISODE_RESULT episodeResult = tvdbEpisodeUpdater.updateSingleEpisode();

      if (episodeResult == TVDBEpisodeUpdater.EPISODE_RESULT.ADDED) {
        episodesAdded++;
      } else if (episodeResult == TVDBEpisodeUpdater.EPISODE_RESULT.UPDATED) {
        episodesUpdated++;
      }

      if (episodeResult != TVDBEpisodeUpdater.EPISODE_RESULT.RETIRED) {
        foundEpisodeIds.add(episodeRemoteId);
      }
    } catch (Exception e) {
      debug("TVDB update of episode failed: ");
      e.printStackTrace();
      episodesFailed++;
      erroredEpisodeIds.add(episodeRemoteId);
      updateEpisodeLastError(episodeRemoteId);
      addUpdateError(episodeRemoteId, e);
      addMigrationError(episodeRemoteId, e);
    }
  }

  private void updateTVDBSeries(Integer tvdbID, JSONObject seriesJson, TVDBSeries tvdbSeries) throws UnirestException, AuthenticationException, SQLException {
    String tvdbSeriesName = jsonReader.getStringWithKey(seriesJson, "seriesName");

    Integer id = jsonReader.getIntegerWithKey(seriesJson, "id");

    tvdbSeries.tvdbSeriesExtId.changeValue(id);
    tvdbSeries.name.changeValue(tvdbSeriesName);
    tvdbSeries.airsDayOfWeek.changeValue(jsonReader.getNullableStringWithKey(seriesJson, "airsDayOfWeek"));

    updateLinkedFieldsIfNotOverridden(series.airTime, tvdbSeries.airsTime, jsonReader.getNullableStringWithKey(seriesJson, "airsTime"));

    tvdbSeries.firstAired.changeValueFromString(jsonReader.getNullableStringWithKey(seriesJson, "firstAired"));
    tvdbSeries.network.changeValue(jsonReader.getNullableStringWithKey(seriesJson, "network"));
    tvdbSeries.overview.changeValue(jsonReader.getNullableStringWithKey(seriesJson, "overview"));
    tvdbSeries.rating.changeValue(jsonReader.getNullableDoubleWithKey(seriesJson, "siteRating"));
    tvdbSeries.ratingCount.changeValue(jsonReader.getNullableIntegerWithKey(seriesJson, "siteRatingCount"));
    tvdbSeries.runtime.changeValueFromString(jsonReader.getNullableStringWithKey(seriesJson, "runtime"));
    tvdbSeries.status.changeValue(jsonReader.getNullableStringWithKey(seriesJson, "status"));

    tvdbSeries.banner.changeValueFromString(jsonReader.getNullableStringWithKey(seriesJson, "banner"));

    // todo: change to integer in data model
    tvdbSeries.lastUpdated.changeValueFromString(((Integer)seriesJson.getInt("lastUpdated")).toString());
    tvdbSeries.imdbId.changeValueFromString(jsonReader.getNullableStringWithKey(seriesJson, "imdbId"));
    tvdbSeries.zap2it_id.changeValueFromString(jsonReader.getNullableStringWithKey(seriesJson, "zap2itId"));

    // todo: 'added' field
    // todo: 'networkid' field

    // todo: add api_version column to tvdb_series and tvdb_episode, and change it when this finishes processing.
    // todo: create api_change_log table and add a row for each change to series or episode
    // todo: create tvdb_error_log table and log any json format issues where non-nullable are null, or values are wrong type.

    Boolean isForInsert = tvdbSeries.isForInsert();

    // if we are inserting, need to commit before adding posters, which will reference tvdb_series.id
    if (isForInsert) {
      tvdbSeries.commit(connection);
    }

    Optional<String> primaryPoster = updatePosters(tvdbID, tvdbSeries);
    primaryPoster.ifPresent(poster -> updateLinkedFieldsIfNotOverridden(series.poster, tvdbSeries.lastPoster, poster));

    // only add change log if an existing series is changing, not for a new one.
    if (!isForInsert && tvdbSeries.hasChanged()) {
      addChangeLogs(tvdbSeries);
    }

    tvdbSeries.commit(connection);
  }

  private Optional<String> updatePosters(Integer tvdbID, TVDBSeries tvdbSeries) throws UnirestException, AuthenticationException, SQLException {

    JSONObject imageData = tvdbDataProvider.getPosterData(tvdbID);
    @NotNull JSONArray images = jsonReader.getArrayWithKey(imageData, "data");

    if (images.length() == 0) {
      return Optional.empty();
    }

    JSONObject mostRecentImageObj = images.getJSONObject(images.length()-1);
    @NotNull String mostRecentImage = jsonReader.getStringWithKey(mostRecentImageObj, "fileName");

    for (int i = 0; i < images.length(); i++) {
      JSONObject image = images.getJSONObject(i);
      @NotNull String filename = jsonReader.getStringWithKey(image, "fileName");
      tvdbSeries.addPoster(filename, null, connection);
    }

    return Optional.of(mostRecentImage);
  }

  private void updateEpisodeLastError(Integer tvdbEpisodeExtId) {
    String sql = "SELECT e.* " +
        "FROM episode e " +
        "INNER JOIN tvdb_episode te " +
        " ON te.id = e.tvdb_episode_id " +
        "WHERE te.tvdb_episode_ext_id = ? " +
        "AND te.retired = ?";
    try {
      @NotNull ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql, tvdbEpisodeExtId, 0);
      if (resultSet.next()) {
        Episode episode = new Episode();
        episode.initializeFromDBObject(resultSet);
        episode.lastTVDBError.changeValue(new Date());
        episode.commit(connection);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private void addMigrationError(Integer tvdbEpisodeExtId, Exception e) throws SQLException {
    TVDBMigrationError migrationError = new TVDBMigrationError();
    migrationError.initializeForInsert();

    migrationError.seriesId.changeValue(series.id.getValue());
    migrationError.tvdbEpisodeExtId.changeValue(tvdbEpisodeExtId);
    migrationError.exceptionType.changeValue(e.getClass().toString());
    migrationError.exceptionMsg.changeValue(e.getMessage());

    migrationError.commit(connection);
  }

  private void addUpdateError(Integer tvdbEpisodeExtId, Exception e) throws SQLException {
    TVDBUpdateError tvdbUpdateError = new TVDBUpdateError();
    tvdbUpdateError.initializeForInsert();

    tvdbUpdateError.context.changeValue("TVDBSeriesUpdater");
    tvdbUpdateError.exceptionClass.changeValue(e.getClass().toString());
    tvdbUpdateError.exceptionMsg.changeValue(e.getMessage());
    tvdbUpdateError.seriesId.changeValue(series.id.getValue());
    tvdbUpdateError.tvdbEpisodeExtId.changeValue(tvdbEpisodeExtId);

    tvdbUpdateError.commit(connection);
  }



  private void addChangeLogs(TVDBSeries tvdbSeries) throws SQLException {
    for (FieldValue fieldValue : tvdbSeries.getChangedFields()) {
      TVDBMigrationLog tvdbMigrationLog = new TVDBMigrationLog();
      tvdbMigrationLog.initializeForInsert();

      tvdbMigrationLog.tvdbSeriesId.changeValue(tvdbSeries.id.getValue());

      tvdbMigrationLog.tvdbFieldName.changeValue(fieldValue.getFieldName());
      tvdbMigrationLog.oldValue.changeValue(fieldValue.getOriginalValue() == null ?
          null :
          fieldValue.getOriginalValue().toString());
      tvdbMigrationLog.newValue.changeValue(fieldValue.getChangedValue() == null ?
          null :
          fieldValue.getChangedValue().toString());

      tvdbMigrationLog.commit(connection);
    }
  }


  protected void debug(Object object) {
    System.out.println(object);
  }


  Integer getEpisodesAdded() {
    return episodesAdded;
  }

  Integer getEpisodesUpdated() {
    return episodesUpdated;
  }

}
