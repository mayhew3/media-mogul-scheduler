package com.mayhew3.mediamogulscheduler;

import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class TaskScheduleRunner {

  public static void main(String[] args)  {
    final ApplicationContext rabbitConfig = new AnnotationConfigApplicationContext(RabbitConfiguration.class);
    final ConnectionFactory rabbitConnectionFactory = rabbitConfig.getBean(ConnectionFactory.class);
    final Queue rabbitQueue = rabbitConfig.getBean(Queue.class);
    final MessageConverter messageConverter = new SimpleMessageConverter();

    // create a listener container, which is required for asynchronous message consumption.
    // AmqpTemplate cannot be used in this case
    final SimpleMessageListenerContainer listenerContainer = new SimpleMessageListenerContainer();
    listenerContainer.setConnectionFactory(rabbitConnectionFactory);
    listenerContainer.setQueueNames(rabbitQueue.getName());

    // set the callback for message handling
    listenerContainer.setMessageListener((MessageListener) message -> {
      final Object bigOp = messageConverter.fromMessage(message);

      // simply printing out the operation, but expensive computation could happen here
      System.out.println("Received from RabbitMQ: " + bigOp);
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
