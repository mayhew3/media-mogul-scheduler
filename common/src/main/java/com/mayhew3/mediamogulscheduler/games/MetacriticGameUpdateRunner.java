package com.mayhew3.mediamogulscheduler.games;

import com.google.common.collect.Lists;
import com.mayhew3.mediamogulscheduler.model.games.Game;
import com.mayhew3.mediamogulscheduler.scheduler.UpdateRunner;
import com.mayhew3.mediamogulscheduler.tv.helper.UpdateMode;
import com.mayhew3.postgresobject.ArgumentChecker;
import com.mayhew3.postgresobject.db.PostgresConnectionFactory;
import com.mayhew3.postgresobject.db.SQLConnection;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetacriticGameUpdateRunner implements UpdateRunner {

  private UpdateMode updateMode;

  private final Map<UpdateMode, Runnable> methodMap;

  private SQLConnection connection;

  public MetacriticGameUpdateRunner(SQLConnection connection, UpdateMode updateMode) {
    methodMap = new HashMap<>();
    methodMap.put(UpdateMode.FULL, this::updateAllGames);
    methodMap.put(UpdateMode.UNMATCHED, this::updateUnmatchedGames);
    methodMap.put(UpdateMode.SINGLE, this::updateSingleGame);

    this.connection = connection;

    if (!methodMap.keySet().contains(updateMode)) {
      throw new IllegalArgumentException("Update mode '" + updateMode + "' is not applicable for this updater.");
    }

    this.updateMode = updateMode;
  }

  public static void main(String[] args) throws FileNotFoundException, SQLException, URISyntaxException {
    List<String> argList = Lists.newArrayList(args);
    Boolean logToFile = argList.contains("LogToFile");
    ArgumentChecker argumentChecker = new ArgumentChecker(args);
    UpdateMode updateMode = UpdateMode.getUpdateModeOrDefault(argumentChecker, UpdateMode.UNMATCHED);

    if (logToFile) {
      String mediaMogulLogs = System.getenv("MediaMogulLogs");

      File file = new File(mediaMogulLogs + "\\MetacriticGameUpdater.log");
      FileOutputStream fos = new FileOutputStream(file, true);
      PrintStream ps = new PrintStream(fos);
      System.setErr(ps);
      System.setOut(ps);
    }

    SQLConnection connection = PostgresConnectionFactory.createConnection(argumentChecker);

    MetacriticGameUpdateRunner updateRunner = new MetacriticGameUpdateRunner(connection, updateMode);
    updateRunner.runUpdate();
  }

  @Override
  public String getRunnerName() {
    return "Metacritic Game Updater";
  }

  @Override
  public @Nullable UpdateMode getUpdateMode() {
    return updateMode;
  }

  public void runUpdate() {
    methodMap.get(updateMode).run();
  }

  private void updateSingleGame() {
    String nameOfSingleGame = "DOOM";

    String sql = "SELECT * FROM game"
        + " WHERE title = ?";

    try {
      ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql, nameOfSingleGame);

      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void updateAllGames() {
    String sql = "SELECT * FROM game";

    try {
      ResultSet resultSet = connection.executeQuery(sql);
      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void updateUnmatchedGames() {
    String sql = "SELECT * FROM game"
     + " WHERE metacritic_matched IS NULL";

    try {
      ResultSet resultSet = connection.executeQuery(sql);

      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void runUpdateOnResultSet(ResultSet resultSet) throws SQLException {
    int i = 1;
    int failures = 0;

    while (resultSet.next()) {
      Game game = new Game();
      try {
        game.initializeFromDBObject(resultSet);

        debug("Updating game: " + game.title.getValue());

        MetacriticGameUpdater metacriticGameUpdater = new MetacriticGameUpdater(game, connection);
        metacriticGameUpdater.runUpdater();
      } catch (GameFailedException e) {
        e.printStackTrace();
        debug("Show failed: " + game.title.getValue());
        failures++;
      } catch (SQLException e) {
        e.printStackTrace();
        debug("Failed to load game from database.");
        failures++;
      }

      debug(i + " processed.");
      i++;
    }

    if (i > 1) {
      debug("Operation completed! Failed on " + failures + "/" + (i - 1) + " games (" + (100 * failures / (i - 1)) + "%)");
    }
  }


  protected static void debug(Object object) {
    System.out.println(new Date() + ": " + object);
  }
}

