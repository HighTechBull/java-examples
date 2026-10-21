/**
 * 
 */
package org.sachse.messaging.jms;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;

/**
 * 
 */
@ApplicationScoped
public class JmsProducer implements Runnable {

	private static final Logger logger = Logger.getLogger(JmsProducer.class);
	
	@Inject
	private ConnectionFactory connectionFactory;

	private final Random random = new Random();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	void onStart(@Observes StartupEvent ev) {
		scheduler.scheduleWithFixedDelay(this, 0L, 5L, TimeUnit.SECONDS);
	}

	void onStop(@Observes ShutdownEvent ev) {
		scheduler.shutdown();
	}

	@Override
	public void run() {
		try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
			String data = Integer.toString(random.nextInt(100));
			logger.infov("Sending data to TestQueue01 [{0}]", data);
			context.createProducer().send(context.createQueue("TestQueue01"), data);
		}
	}

}
