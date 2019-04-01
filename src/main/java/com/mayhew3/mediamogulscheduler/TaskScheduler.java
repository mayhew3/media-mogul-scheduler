package com.mayhew3.mediamogulscheduler;

import com.mayhew3.mediamogulscheduler.tv.SeriesDenormFactory;
import com.mayhew3.mediamogulscheduler.tv.SeriesDenormUpdater;
import com.mayhew3.mediamogulscheduler.tv.TVDBUpdateRunnerFactory;
import com.mayhew3.mediamogulscheduler.tv.helper.UpdateMode;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.repeatMinutelyForever;
import static org.quartz.TriggerBuilder.newTrigger;

public class TaskScheduler {

  private final static Logger logger = LoggerFactory.getLogger(TaskScheduler.class);

  private static AmqpTemplate amqpTemplate;

  public static void main(String[] args) throws Exception {
    logger.info("Starting message!");

    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RabbitConfiguration.class);
    amqpTemplate = context.getBean(AmqpTemplate.class);

    Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
    scheduler.start();


    // DENORMS

    JobDetail denormJob = JobBuilder.newJob(SendUpdaterJob.class).build();

    Trigger denormTrigger = TriggerBuilder.newTrigger()
        .startNow()
        .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(5))
        .build();

    scheduler.scheduleJob(denormJob, denormTrigger);


    // TVDB

    JobDetail tvdbJob = JobBuilder.newJob(SendTVDBUpdaterJob.class).build();

    Trigger tvdbTrigger = TriggerBuilder.newTrigger()
        .startNow()
        .withSchedule(SimpleScheduleBuilder.repeatMinutelyForever(30))
        .build();

    scheduler.scheduleJob(tvdbJob, tvdbTrigger);
  }

  public static class SendUpdaterJob implements Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
      SeriesDenormFactory factory = new SeriesDenormFactory();

      amqpTemplate.convertAndSend(factory);
      logger.info("Sent to RabbitMQ: " + factory);
    }
  }

  public static class SendTVDBUpdaterJob implements Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
      TVDBUpdateRunnerFactory factory = new TVDBUpdateRunnerFactory(UpdateMode.SMART);

      amqpTemplate.convertAndSend(factory);
      logger.info("Sent to RabbitMQ: " + factory);
    }
  }
}
