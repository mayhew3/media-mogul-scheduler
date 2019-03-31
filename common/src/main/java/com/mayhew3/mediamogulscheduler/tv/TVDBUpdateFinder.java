package com.mayhew3.mediamogulscheduler.tv;

import com.mashape.unirest.http.exceptions.UnirestException;
import com.mayhew3.mediamogulscheduler.model.tv.Series;
import com.mayhew3.mediamogulscheduler.model.tv.TVDBWorkItem;
import com.mayhew3.mediamogulscheduler.scheduler.UpdateRunner;
import com.mayhew3.mediamogulscheduler.tv.helper.UpdateMode;
import com.mayhew3.mediamogulscheduler.tv.provider.TVDBJWTProvider;
import com.mayhew3.mediamogulscheduler.xml.JSONReader;
import com.mayhew3.postgresobject.db.SQLConnection;
import org.apache.http.auth.AuthenticationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class TVDBUpdateFinder implements UpdateRunner {

  private JSONReader jsonReader;
  private TVDBJWTProvider tvdbjwtProvider;

  private SQLConnection connection;

  private Timestamp lastUpdated;

  public TVDBUpdateFinder(SQLConnection connection, TVDBJWTProvider tvdbjwtProvider, JSONReader jsonReader) {
    this.jsonReader = jsonReader;
    this.tvdbjwtProvider = tvdbjwtProvider;
    this.connection = connection;
  }

  @Override
  public String getRunnerName() {
    return "TVDB Update Finder";
  }

  @Override
  public @Nullable UpdateMode getUpdateMode() {
    return null;
  }

  public void runUpdate() throws SQLException, UnirestException, AuthenticationException {
    Timestamp lastUpdateTime = getLastUpdateTime();
    Timestamp startTime = createStartTimeWithBuffer(lastUpdateTime);
    runPeriodicUpdate(startTime);
  }

  @NotNull
  private Timestamp createStartTimeWithBuffer(@NotNull Timestamp lastUpdateTime) {
    DateTime startTimeWithBuffer = new DateTime(lastUpdateTime).minusSeconds(120);
    return new Timestamp(startTimeWithBuffer.toDate().getTime());
  }

  private void runPeriodicUpdate(@NotNull Timestamp startTime) throws UnirestException, SQLException, AuthenticationException {
    Timestamp now = now();

    Seconds secondsDiff = Seconds.secondsBetween(new DateTime(startTime), new DateTime(now));
    debug("Finding updates between " + startTime + " and " + now + ", diff of " + secondsDiff.getSeconds() + " seconds.");

    JSONObject updatedSeries = tvdbjwtProvider.getUpdatedSeries(startTime);

    if (updatedSeries.isNull("data")) {
      debug("Empty list of TVDB updated.");
      lastUpdated = now;
    }

    processTVDBPayload(now, updatedSeries);

    lastUpdated = now;
  }

  private void processTVDBPayload(Timestamp now, JSONObject updatedSeries) throws SQLException {
    @NotNull JSONArray seriesArray = jsonReader.getArrayWithKey(updatedSeries, "data");

    debug("Total series found: " + seriesArray.length());

    for (int i = 0; i < seriesArray.length(); i++) {
      JSONObject seriesRow = seriesArray.getJSONObject(i);
      @NotNull Integer tvdbSeriesExtId = jsonReader.getIntegerWithKey(seriesRow, "id");
      @NotNull Integer lastUpdatedEpoch = jsonReader.getIntegerWithKey(seriesRow, "lastUpdated");
      Timestamp tvdbLastUpdated = getFromEpochTime(lastUpdatedEpoch);

      maybeUpdateSeries(now, tvdbSeriesExtId, tvdbLastUpdated);
    }
  }

  private void maybeUpdateSeries(Timestamp now, Integer tvdbSeriesExtId, Timestamp tvdbLastUpdated) throws SQLException {
    String sql = "select * " +
        "from series " +
        "where tvdb_series_ext_id = ? " +
        "and tvdb_match_status = ? " +
        "and retired = ? ";

    @NotNull ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql, tvdbSeriesExtId, TVDBMatchStatus.MATCH_COMPLETED, 0);
    if (resultSet.next()) {
      if (hasWorkItemAlready(tvdbSeriesExtId, tvdbLastUpdated)) {
        debug("Work item already exists for TVDB ID " + tvdbSeriesExtId + " and update time of " + tvdbLastUpdated);
      } else {
        updateSingleSeries(now, tvdbSeriesExtId, tvdbLastUpdated, resultSet);
      }
    } else {
      debug("Recently updated series not found: ID " + tvdbSeriesExtId + ", updated " + tvdbLastUpdated);
    }
  }

  private Boolean hasWorkItemAlready(Integer tvdbSeriesExtId, Timestamp tvdbLastUpdated) throws SQLException {
    String sql = "select * " +
        "from tvdb_work_item " +
        "where tvdb_series_ext_id = ? " +
        "and last_updated <= ?";
    ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql, tvdbSeriesExtId, tvdbLastUpdated);
    return resultSet.next();
  }

  private void updateSingleSeries(Timestamp now, Integer tvdbSeriesExtId, Timestamp tvdbLastUpdated, ResultSet resultSet) throws SQLException {
    Series series = new Series();
    series.initializeFromDBObject(resultSet);

    debug("Found update for series '" + series.seriesTitle.getValue() + "', updated " + tvdbLastUpdated);

    Integer seriesId = series.id.getValue();

    TVDBWorkItem workItem = new TVDBWorkItem();
    workItem.initializeForInsert();

    workItem.tvdbSeriesExtId.changeValue(tvdbSeriesExtId);
    workItem.seriesId.changeValue(seriesId);
    workItem.lastUpdated.changeValue(tvdbLastUpdated);
    workItem.foundTime.changeValue(now);

    workItem.commit(connection);
  }

  protected static void debug(Object message) {
    System.out.println(message);
  }

  @NotNull
  private Timestamp getLastUpdateTime() throws SQLException {
    return lastUpdated == null ? getLastUpdateTimeFromDB() : lastUpdated;
  }

  @NotNull
  private Timestamp getLastUpdateTimeFromDB() throws SQLException {
    Timestamp mostRecentSuccessfulUpdate = getMostRecentSuccessfulUpdate();
    Timestamp mostRecentPeriodicCheck = getMostRecentPeriodicCheck();

    // todo: after series match is found, check that we haven't updated it more recently.
    if (mostRecentPeriodicCheck != null) {
      return mostRecentPeriodicCheck;
    } else if (mostRecentSuccessfulUpdate != null) {
      return mostRecentSuccessfulUpdate;
    } else {
      throw new IllegalStateException("No update time found in tvdb_connection_log or tvdb_work_item.");
    }
  }

  private Timestamp getMostRecentSuccessfulUpdate() throws SQLException {
    String sql = "select max(start_time) as max_start_time\n" +
        "from tvdb_connection_log\n" +
        "where update_type in (?, ?, ?)\n" +
        "and finish_time is not null";
    @NotNull ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql,
        UpdateMode.FULL.getTypekey(),
        UpdateMode.SMART.getTypekey(),
        UpdateMode.RECENT.getTypekey()
    );
    if (resultSet.next()) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
      Timestamp maxStartTime = resultSet.getTimestamp("max_start_time");
      if (maxStartTime == null) {
        throw new IllegalStateException("Max start time should never be null.");
      } else {
        return maxStartTime;
      }
    } else {
      throw new IllegalStateException("Max start time should never be an empty set.");
    }
  }

  @Nullable
  private Timestamp getMostRecentPeriodicCheck() throws SQLException {
    String sql = "select max(found_time) as max_update_time " +
        "from tvdb_work_item ";
    ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql);
    if (resultSet.next()) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
      return resultSet.getTimestamp("max_update_time");
    }
    return null;
  }

  private Timestamp getFromEpochTime(long epochTime) {
    return new Timestamp(epochTime * 1000L);
  }

  @NotNull
  private Timestamp now() {
    return new Timestamp(new Date().getTime());
  }

}
