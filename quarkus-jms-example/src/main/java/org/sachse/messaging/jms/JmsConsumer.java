/**
 * 
 */
package org.sachse.messaging.jms;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.logging.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;

/**
 * 
 */
@ApplicationScoped
public class JmsConsumer implements Runnable {

	private static final Logger logger = Logger.getLogger(JmsConsumer.class);

	@Inject
	private ConnectionFactory connectionFactory;

	private final ExecutorService scheduler = Executors.newSingleThreadExecutor();

	void onStart(@Observes StartupEvent ev) {
		scheduler.submit(this);
	}

	void onStop(@Observes ShutdownEvent ev) {
		scheduler.shutdown();
	}

	@Override
	public void run() {
		try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
			 JMSConsumer consumer = context.createConsumer(context.createQueue("TestQueue01"));) {
			while (true) {
				Message message = consumer.receive();
				if (message == null)
					return;
				String data = message.getBody(String.class);
				logger.infov("Received data from TestQueue01 [{0}]", data);
			}
		} catch (JMSException e) {
			throw new JMSRuntimeException(e.getMessage());
		}
	}

}
