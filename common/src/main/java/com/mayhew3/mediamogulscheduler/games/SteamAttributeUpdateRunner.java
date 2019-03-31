package com.mayhew3.mediamogulscheduler.games;

import com.google.common.collect.Lists;
import com.mayhew3.mediamogulscheduler.model.games.Game;
import com.mayhew3.mediamogulscheduler.scheduler.UpdateRunner;
import com.mayhew3.mediamogulscheduler.tv.helper.UpdateMode;
import com.mayhew3.postgresobject.ArgumentChecker;
import com.mayhew3.postgresobject.db.PostgresConnectionFactory;
import com.mayhew3.postgresobject.db.SQLConnection;
import org.jetbrains.annotations.Nullable;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SteamAttributeUpdateRunner implements UpdateRunner {

  private SQLConnection connection;
  private UpdateMode updateMode;

  private final Map<UpdateMode, Runnable> methodMap;

  public SteamAttributeUpdateRunner(SQLConnection connection, UpdateMode updateMode) {
    this.updateMode = updateMode;
    methodMap = new HashMap<>();
    methodMap.put(UpdateMode.FULL, this::runUpdateFull);
    methodMap.put(UpdateMode.SINGLE, this::runUpdateOnSingle);

    this.connection = connection;

    if (!methodMap.keySet().contains(updateMode)) {
      throw new IllegalArgumentException("Update type '" + updateMode + "' is not applicable for this updater.");
    }

    this.updateMode = updateMode;
  }

  public static void main(String[] args) throws FileNotFoundException, SQLException, URISyntaxException {
    List<String> argList = Lists.newArrayList(args);
    Boolean logToFile = argList.contains("LogToFile");
    ArgumentChecker argumentChecker = new ArgumentChecker(args);

    if (logToFile) {
      String mediaMogulLogs = System.getenv("MediaMogulLogs");

      File file = new File(mediaMogulLogs + "\\SteamAttributeUpdater.log");
      FileOutputStream fos = new FileOutputStream(file, true);
      PrintStream ps = new PrintStream(fos);
      System.setErr(ps);
      System.setOut(ps);
    }

    UpdateMode updateMode = UpdateMode.getUpdateModeOrDefault(argumentChecker, UpdateMode.FULL);
    SQLConnection connection = PostgresConnectionFactory.createConnection(argumentChecker);

    setDriverPath();

    SteamAttributeUpdateRunner updateRunner = new SteamAttributeUpdateRunner(connection, updateMode);
    updateRunner.runUpdate();

  }

  public void runUpdate() {
    methodMap.get(updateMode).run();
  }

  private void runUpdateFull() {
    String sql = "SELECT * FROM game WHERE steamid is not null AND steam_attributes IS NULL AND steam_page_gone IS NULL";

    try {
      ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql);
      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

  }

  private void runUpdateOnSingle() {
    String gameTitle = "Fallout 4 VR";

    String sql = "SELECT * FROM game " +
        "WHERE title = ? ";

    try {
      ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql, gameTitle);
      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

  }

  private void runUpdateOnResultSet(ResultSet resultSet) throws SQLException {


    int i = 0;

    ChromeDriver chromeDriver = new ChromeDriver();

    while (resultSet.next()) {
      i++;

      Game game = new Game();
      try {
        game.initializeFromDBObject(resultSet);

        debug("Updating game: " + game);

        SteamAttributeUpdater steamAttributeUpdater = new SteamAttributeUpdater(game, connection, chromeDriver);
        steamAttributeUpdater.runUpdater();
      } catch (SQLException e) {
        e.printStackTrace();
        debug("Game failed to load from DB.");
      } catch (GameFailedException e) {
        e.printStackTrace();
        debug("Game failed: " + game);
      }

      debug(i + " processed.");
    }

    chromeDriver.close();
  }

  private static void setDriverPath() {
    String driverPath = System.getProperty("user.dir") + "\\resources\\chromedriver.exe";
    System.setProperty("webdriver.chrome.driver", driverPath);
  }


  protected void debug(Object object) {
    System.out.println(object);
  }

  @Override
  public String getRunnerName() {
    return "Steam Attribute Update Runner";
  }

  @Override
  public @Nullable UpdateMode getUpdateMode() {
    return null;
  }
}

