package com.mayhew3.mediamogulscheduler.games;

import com.google.common.collect.Lists;
import com.mayhew3.mediamogulscheduler.ExternalServiceHandler;
import com.mayhew3.mediamogulscheduler.ExternalServiceType;
import com.mayhew3.mediamogulscheduler.model.games.Game;
import com.mayhew3.mediamogulscheduler.scheduler.UpdateRunner;
import com.mayhew3.mediamogulscheduler.tv.helper.UpdateMode;
import com.mayhew3.postgresobject.ArgumentChecker;
import com.mayhew3.postgresobject.db.PostgresConnectionFactory;
import com.mayhew3.postgresobject.db.SQLConnection;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HowLongToBeatUpdateRunner implements UpdateRunner {

  private SQLConnection connection;
  private UpdateMode updateMode;
  private ExternalServiceHandler howLongServiceHandler;

  private final Map<UpdateMode, Runnable> methodMap;

  public HowLongToBeatUpdateRunner(SQLConnection connection, UpdateMode updateMode, ExternalServiceHandler howLongServiceHandler) {
    methodMap = new HashMap<>();
    methodMap.put(UpdateMode.QUICK, this::runUpdateQuick);
    methodMap.put(UpdateMode.FULL, this::runUpdateFull);
    methodMap.put(UpdateMode.SINGLE, this::runUpdateOnSingle);

    this.connection = connection;

    if (!methodMap.keySet().contains(updateMode)) {
      throw new IllegalArgumentException("Update type '" + updateMode + "' is not applicable for this updater.");
    }

    this.updateMode = updateMode;
    this.howLongServiceHandler = howLongServiceHandler;
  }

  public static void main(String[] args) throws FileNotFoundException, SQLException, URISyntaxException {
    List<String> argList = Lists.newArrayList(args);
    Boolean logToFile = argList.contains("LogToFile");
    ArgumentChecker argumentChecker = new ArgumentChecker(args);

    if (logToFile) {
      String mediaMogulLogs = System.getenv("MediaMogulLogs");

      File file = new File(mediaMogulLogs + "\\HowLongToBeatUpdater.log");
      FileOutputStream fos = new FileOutputStream(file, true);
      PrintStream ps = new PrintStream(fos);
      System.setErr(ps);
      System.setOut(ps);
    }

    UpdateMode updateMode = UpdateMode.getUpdateModeOrDefault(argumentChecker, UpdateMode.QUICK);
    SQLConnection connection = PostgresConnectionFactory.createConnection(argumentChecker);
    ExternalServiceHandler howLongServiceHandler = new ExternalServiceHandler(connection, ExternalServiceType.HowLongToBeat);

    setDriverPath();

    HowLongToBeatUpdateRunner updateRunner = new HowLongToBeatUpdateRunner(connection, updateMode, howLongServiceHandler);
    updateRunner.runUpdate();
  }

  @Override
  public String getRunnerName() {
    return "HowLongToBeat Updater";
  }

  @Override
  public @Nullable UpdateMode getUpdateMode() {
    return updateMode;
  }

  @Override
  public void runUpdate() {
    methodMap.get(updateMode).run();
  }

  private void runUpdateQuick() {
    Date date = new DateTime().minusDays(7).toDate();
    Timestamp timestamp = new Timestamp(date.getTime());

    String sql = "SELECT * FROM game WHERE howlong_updated IS NULL "
        + " AND (howlong_failed IS NULL OR howlong_failed < ?)";

    try {
      ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql, timestamp);

      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void runUpdateFull() {
    String sql = "SELECT * FROM game WHERE howlong_updated IS NULL";

    try {
      ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql);
      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void runUpdateOnSingle() {
    String gameTitle = "Return of the Obra Dinn";
    String sql = "SELECT * FROM game WHERE title = ? ";

    try {
      ResultSet resultSet = connection.prepareAndExecuteStatementFetch(sql, gameTitle);
      runUpdateOnResultSet(resultSet);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void runUpdateOnResultSet(ResultSet resultSet) throws SQLException {
    int i = 1;
    int failures = 0;

    ChromeOptions chromeOptions = new ChromeOptions()
        .setHeadless(true);
    WebDriver chromeDriver = new ChromeDriver(chromeOptions);

    while (resultSet.next()) {
      Game game = new Game();
      try {
        game.initializeFromDBObject(resultSet);

        debug("Updating game: " + game);

        HowLongToBeatUpdater updater = new HowLongToBeatUpdater(game, connection, chromeDriver, howLongServiceHandler);
        updater.runUpdater();
        howLongServiceHandler.connectionSuccess();
      } catch (SQLException e) {
        e.printStackTrace();
        debug("Game failed to load from DB.");
        howLongServiceHandler.connectionFailed();
        failures++;
      } catch (GameFailedException e) {
        e.printStackTrace();
        debug("Game failed: " + game);
        howLongServiceHandler.connectionFailed();
        logFailure(game);
        failures++;
      } catch (WebDriverException e) {
        e.printStackTrace();
        debug("WebDriver error: " + game);
        howLongServiceHandler.connectionFailed();
        logFailure(game);
        failures++;
      }

      debug(i + " processed.");
      i++;
    }

    debug("Operation completed! Failed on " + failures + "/" + (i-1) + " games (" + (failures/i*100) + "%)");

    chromeDriver.quit();
  }

  private void logFailure(Game game) throws SQLException {
    game.howlong_failed.changeValue(new Timestamp(new Date().getTime()));
    game.commit(connection);
  }

  private static void setDriverPath() {
    String driverPath = System.getProperty("user.dir") + "\\resources\\chromedriver.exe";
    System.setProperty("webdriver.chrome.driver", driverPath);
  }

  protected void debug(Object object) {
    System.out.println(object);
  }
}

