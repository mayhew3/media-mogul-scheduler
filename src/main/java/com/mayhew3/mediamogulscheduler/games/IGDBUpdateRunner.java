package com.mayhew3.mediamogulscheduler.games;

import com.mayhew3.mediamogulscheduler.games.provider.IGDBProvider;
import com.mayhew3.mediamogulscheduler.games.provider.IGDBProviderImpl;
import com.mayhew3.mediamogulscheduler.model.games.Game;
import com.mayhew3.mediamogulscheduler.scheduler.UpdateRunner;
import com.mayhew3.mediamogulscheduler.tv.helper.UpdateMode;
import com.mayhew3.mediamogulscheduler.xml.JSONReader;
import com.mayhew3.mediamogulscheduler.xml.JSONReaderImpl;
import com.mayhew3.postgresobject.ArgumentChecker;
import com.mayhew3.postgresobject.db.PostgresConnectionFactory;
import com.mayhew3.postgresobject.db.SQLConnection;
import org.jetbrains.annotations.Nullable;

import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class IGDBUpdateRunner implements UpdateRunner {

  private SQLConnection connection;
  private IGDBProvider igdbProvider;
  private JSONReader jsonReader;
  private UpdateMode updateMode;

  private final Map<UpdateMode, Runnable> methodMap;

  public IGDBUpdateRunner(SQLConnection connection, IGDBProvider igdbProvider, JSONReader jsonReader, UpdateMode updateMode) {
    methodMap = new HashMap<>();
    methodMap.put(UpdateMode.SMART, this::runUpdateSmart);
    methodMap.put(UpdateMode.SINGLE, this::runUpdateSingle);
    methodMap.put(UpdateMode.SANITY, this::runUpdateSanity);

    this.connection = connection;
    this.igdbProvider = igdbProvider;
    this.jsonReader = jsonReader;

    if (!methodMap.keySet().contains(updateMode)) {
      throw new IllegalArgumentException("Update type '" + updateMode + "' is not applicable for this updater.");
    }

    this.updateMode = updateMode;
  }

  public static void main(String[] args) throws SQLException, URISyntaxException {
    ArgumentChecker argumentChecker = new ArgumentChecker(args);

    UpdateMode updateMode = UpdateMode.getUpdateModeOrDefault(argumentChecker, UpdateMode.FULL);

    SQLConnection connection = PostgresConnectionFactory.createConnection(argumentChecker);

    IGDBUpdateRunner igdbUpdateRunner = new IGDBUpdateRunner(connection, new IGDBProviderImpl(), new JSONReaderImpl(), updateMode);
    igdbUpdateRunner.runUpdate();
  }

  protected static void debug(Object object) {
    System.out.println(object);
  }


  @Override
  public String getRunnerName() {
    return "IGDB Updater";
  }

  @Override
  public @Nullable UpdateMode getUpdateMode() {
    return updateMode;
  }

  @Override
  public String getUniqueIdentifier() {
    return "IGDB Updater";
  }

  @Override
  public void runUpdate() {

    try {
      methodMap.get(updateMode).run();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  private void runUpdateSmart() {
    String sql = "SELECT * " +
        "FROM game " +
        "WHERE igdb_success IS NULL " +
        "AND igdb_failed IS NULL " +
        "AND igdb_ignored IS NULL ";

    try {
      ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql);
      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void runUpdateSingle() {
    String gameTitle = "Forza Horizon 4";
    String sql = "select * " +
        "from game " +
        "where title = ? ";
    try {
      ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql, gameTitle);

      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void runUpdateSanity() {
    String sql = "SELECT * " +
        "FROM game " +
        "WHERE igdb_next_update < now() " +
        "AND igdb_ignored IS NULL ";

    try {
      ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql);

      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }


  private void runUpdateOnResultSet(ResultSet resultSet) throws SQLException {
    debug("Starting update.");

    int i = 0;

    while (resultSet.next()) {
      i++;
      Game game = new Game();

      try {
        processSingleGame(resultSet, game);
      } catch (Exception e) {
        debug("Show failed on initialization from DB.");
      }
    }

    debug("Update complete for result set: " + i + " processed.");
  }

  private void processSingleGame(ResultSet resultSet, Game game) throws SQLException {
    game.initializeFromDBObject(resultSet);

    try {
      updateIGDB(game);
    } catch (Exception e) {
      e.printStackTrace();
      debug("Game failed IGDB: " + game.title.getValue() + " (ID " + game.id.getValue() + ")");
    }
  }

  private void updateIGDB(Game game) throws SQLException {
    IGDBUpdater updater = new IGDBUpdater(game, connection, igdbProvider, jsonReader);
    updater.updateGame();
  }

}
