package com.mayhew3.mediamogulscheduler;

import com.mashape.unirest.http.exceptions.UnirestException;
import com.mayhew3.mediamogulscheduler.games.*;
import com.mayhew3.mediamogulscheduler.games.provider.IGDBProvider;
import com.mayhew3.mediamogulscheduler.games.provider.IGDBProviderImpl;
import com.mayhew3.mediamogulscheduler.games.provider.SteamProvider;
import com.mayhew3.mediamogulscheduler.games.provider.SteamProviderImpl;
import com.mayhew3.mediamogulscheduler.scheduler.UpdateRunner;
import com.mayhew3.mediamogulscheduler.tv.*;
import com.mayhew3.mediamogulscheduler.tv.helper.ConnectionLogger;
import com.mayhew3.mediamogulscheduler.tv.helper.UpdateMode;
import com.mayhew3.mediamogulscheduler.tv.provider.TVDBJWTProvider;
import com.mayhew3.mediamogulscheduler.tv.provider.TVDBJWTProviderImpl;
import com.mayhew3.mediamogulscheduler.xml.JSONReader;
import com.mayhew3.mediamogulscheduler.xml.JSONReaderImpl;
import com.mayhew3.postgresobject.db.PostgresConnectionFactory;
import com.mayhew3.postgresobject.db.SQLConnection;
import org.jetbrains.annotations.Nullable;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;

public class TaskScheduleRunner {
  private List<TaskSchedule> taskSchedules = new ArrayList<>();

  private SQLConnection connection;

  @Nullable
  private TVDBJWTProvider tvdbjwtProvider;
  private JSONReader jsonReader;
  ExternalServiceHandler howLongServiceHandler;
  private IGDBProvider igdbProvider;
  private SteamProvider steamProvider;
  private Integer person_id;

  private TaskScheduleRunner(SQLConnection connection,
                             @Nullable TVDBJWTProvider tvdbjwtProvider,
                             JSONReader jsonReader,
                             ExternalServiceHandler howLongServiceHandler, IGDBProvider igdbProvider, SteamProvider steamProvider, Integer person_id) {
    this.connection = connection;
    this.tvdbjwtProvider = tvdbjwtProvider;
    this.jsonReader = jsonReader;
    this.howLongServiceHandler = howLongServiceHandler;
    this.igdbProvider = igdbProvider;
    this.steamProvider = steamProvider;
    this.person_id = person_id;
  }

  public static void main(String... args) throws URISyntaxException, SQLException, InterruptedException {
    String postgresURL_heroku = System.getenv("postgresURL_heroku");
    if (postgresURL_heroku == null) {
      throw new IllegalStateException("No env 'postgresURL_heroku' found!");
    }

    SQLConnection connection = PostgresConnectionFactory.initiateDBConnect(postgresURL_heroku);
    JSONReader jsonReader = new JSONReaderImpl();
    ExternalServiceHandler tvdbServiceHandler = new ExternalServiceHandler(connection, ExternalServiceType.TVDB);
    ExternalServiceHandler howLongServiceHandler = new ExternalServiceHandler(connection, ExternalServiceType.HowLongToBeat);
    IGDBProviderImpl igdbProvider = new IGDBProviderImpl();
    String mediaMogulPersonID = System.getenv("MediaMogulPersonID");
    if (mediaMogulPersonID == null) {
      throw new IllegalStateException("No env 'MediaMogulPersonID' found!");
    }
    Integer person_id = Integer.parseInt(mediaMogulPersonID);

    TVDBJWTProvider tvdbjwtProvider = null;
    try {
      tvdbjwtProvider = new TVDBJWTProviderImpl(tvdbServiceHandler);
    } catch (UnirestException e) {
      e.printStackTrace();
    }

    setDriverPath();

    TaskScheduleRunner taskScheduleRunner = new TaskScheduleRunner(
        connection,
        tvdbjwtProvider,
        jsonReader,
        howLongServiceHandler,
        igdbProvider,
        new SteamProviderImpl(),
        person_id);
    taskScheduleRunner.runUpdates();
  }

  private void createTaskList() {
    // REGULAR

    addPeriodicTask(new HowLongToBeatUpdateRunner(connection, UpdateMode.QUICK, howLongServiceHandler),
        30);
    addPeriodicTask(new SeriesDenormUpdater(connection),
        5);
    addPeriodicTask(new TVDBUpdateRunner(connection, tvdbjwtProvider, jsonReader, UpdateMode.MANUAL),
        1);
    addPeriodicTask(new TVDBUpdateFinder(connection, tvdbjwtProvider, jsonReader),
        2);
    addPeriodicTask(new TVDBUpdateProcessor(connection, tvdbjwtProvider, jsonReader),
        1);
    addPeriodicTask(new TVDBSeriesMatchRunner(connection, tvdbjwtProvider, jsonReader, UpdateMode.SMART),
        3);

    addPeriodicTask(new IGDBUpdateRunner(connection, igdbProvider, jsonReader, UpdateMode.SMART),
        5);
    addPeriodicTask(new SteamPlaySessionGenerator(connection, 1),
        10);
    addPeriodicTask(new TVDBUpdateRunner(connection, tvdbjwtProvider, jsonReader, UpdateMode.SMART),
        30);
    addPeriodicTask(new SteamGameUpdater(connection, 1, steamProvider),
        60);
    addPeriodicTask(new CloudinaryUploader(connection, UpdateMode.QUICK),
        60);

    // NIGHTLY
//    addNightlyTask(new MetacriticTVUpdater(connection, UpdateMode.FULL));
    addNightlyTask(new IGDBUpdateRunner(connection, igdbProvider, jsonReader, UpdateMode.SANITY));
    addNightlyTask(new TVDBUpdateRunner(connection, tvdbjwtProvider, jsonReader, UpdateMode.SANITY));
    addNightlyTask(new EpisodeGroupUpdater(connection));
    addNightlyTask(new CloudinaryUploader(connection, UpdateMode.FULL));
    addNightlyTask(new GiantBombUpdater(connection));
  }

  private void addPeriodicTask(UpdateRunner updateRunner, Integer minutesBetween) {
    taskSchedules.add(new PeriodicTaskSchedule(updateRunner, connection, minutesBetween));
  }

  private void addNightlyTask(UpdateRunner updateRunner) {
    taskSchedules.add(new NightlyTaskSchedule(updateRunner, connection, 1));
  }

  @SuppressWarnings("InfiniteLoopStatement")
  private void runUpdates() throws InterruptedException {
    if (tvdbjwtProvider == null) {
      throw new IllegalStateException("Can't currently run updater with no TVDB token. TVDB is the only thing it can handle yet.");
    }

    createTaskList();

    debug("");
    debug("SESSION START!");
    debug("");

    while (true) {

      List<TaskSchedule> eligibleTasks = taskSchedules.stream()
          .filter(TaskSchedule::isEligibleToRun)
          .collect(Collectors.toList());

      for (TaskSchedule taskSchedule : eligibleTasks) {
        UpdateRunner updateRunner = taskSchedule.getUpdateRunner();
        try {
          ConnectionLogger connectionLogger = new ConnectionLogger(connection);

          debug("Starting update for '" + updateRunner.getUniqueIdentifier() + "'");

          connectionLogger.logConnectionStart(updateRunner);
          updateRunner.runUpdate();
          connectionLogger.logConnectionEnd();

          debug("Update complete for '" + updateRunner.getUniqueIdentifier() + "'");

        } catch (Exception e) {
          debug("Exception encountered during run of update '" + updateRunner.getUniqueIdentifier() + "'.");
          e.printStackTrace();
        } finally {
          // mark the task as having been run, whether it succeeds or errors out.
          taskSchedule.updateLastRanToNow();
        }
      }

      sleep(15000);
    }
  }


  private static void setDriverPath() {
    String driverPath = System.getProperty("user.dir") + "\\resources\\chromedriver.exe";
    System.setProperty("webdriver.chrome.driver", driverPath);
  }

  protected static void debug(Object message) {
    System.out.println(new Date() + ": " + message);
  }

}
