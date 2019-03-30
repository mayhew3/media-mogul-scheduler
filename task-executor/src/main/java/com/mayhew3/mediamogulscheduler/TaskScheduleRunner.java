package com.mayhew3.mediamogulscheduler;

import com.mayhew3.mediamogulscheduler.tv.SeriesDenormFactory;
import com.mayhew3.mediamogulscheduler.tv.SeriesDenormUpdater;
import com.mayhew3.postgresobject.db.PostgresConnectionFactory;
import com.mayhew3.postgresobject.db.SQLConnection;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.URISyntaxException;
import java.sql.SQLException;

public class TaskScheduleRunner {

  public static void main(String[] args) throws URISyntaxException, SQLException {
    final ApplicationContext rabbitConfig = new AnnotationConfigApplicationContext(RabbitConfiguration.class);
    final ConnectionFactory rabbitConnectionFactory = rabbitConfig.getBean(ConnectionFactory.class);
    final Queue rabbitQueue = rabbitConfig.getBean(Queue.class);
    final MessageConverter messageConverter = new SimpleMessageConverter();

    String postgresURL_heroku = System.getenv("postgresURL_heroku");
    if (postgresURL_heroku == null) {
      throw new IllegalStateException("No env 'postgresURL_heroku' found!");
    }

    final SQLConnection connection = PostgresConnectionFactory.initiateDBConnect(postgresURL_heroku);

    // create a listener container, which is required for asynchronous message consumption.
    // AmqpTemplate cannot be used in this case
    final SimpleMessageListenerContainer listenerContainer = new SimpleMessageListenerContainer();
    listenerContainer.setConnectionFactory(rabbitConnectionFactory);
    listenerContainer.setQueueNames(rabbitQueue.getName());

    // set the callback for message handling
    listenerContainer.setMessageListener((MessageListener) message -> {
      if (message.getMessageProperties().getConsumerQueue().equalsIgnoreCase("mm.local.queue")) {
        Object receivedMessage = messageConverter.fromMessage(message);
        if (receivedMessage instanceof SeriesDenormFactory) {
          final SeriesDenormFactory seriesDenormFactory = (SeriesDenormFactory) receivedMessage;

          // simply printing out the operation, but expensive computation could happen here
          System.out.println("Received from RabbitMQ: " + seriesDenormFactory);

          try {
            SeriesDenormUpdater updater = seriesDenormFactory.generate(connection);
            updater.runUpdate();
          } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error running update.");
          }
        }
      }
    });

    // set a simple error handler
    listenerContainer.setErrorHandler(Throwable::printStackTrace);

    // register a shutdown hook with the JVM
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("Shutting down BigOperationWorker");
      listenerContainer.shutdown();
    }));

    // start up the listener. this will block until JVM is killed.
    listenerContainer.start();
    System.out.println("BigOperationWorker started");
  }

}
