package com.mayhew3.mediamogulscheduler;

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

    JobDetail jobDetail = newJob(SendUpdaterJob.class).build();

    Trigger trigger = newTrigger()
        .startNow()
        .withSchedule(repeatMinutelyForever(5))
        .build();

    scheduler.scheduleJob(jobDetail, trigger);
  }

  public static class SendUpdaterJob implements Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
      SeriesDenormUpdater updater = new SeriesDenormUpdater();

      amqpTemplate.convertAndSend(updater);
      logger.info("Sent to RabbitMQ: " + updater);
    }
  }
}
