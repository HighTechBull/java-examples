# Quarkus JMS Example

A Quarkus application that demonstrates sending and receiving messages with Jakarta Messaging (JMS) and ActiveMQ Artemis.

## What it does

At application startup, `JmsProducer` sends a randomly generated integer (0 through 99), represented as a string, to the `TestQueue01` queue every five seconds. `JmsConsumer` listens to the same queue and logs each received message. Both components use the configured Artemis connection factory and stop their executors when Quarkus shuts down.

The project uses the Quarkus Artemis JMS extension and Java 21. It also includes the Quarkus SmallRye Health extension.

## Configure Artemis

Connection settings are in `src/main/resources/application.properties`:

| Property | Purpose |
| --- | --- |
| `quarkus.artemis.url` | Artemis broker connection URL; the current value configures multiple endpoints and high availability. |
| `quarkus.artemis.username` | Broker authentication username. |
| `quarkus.artemis.password` | Broker authentication password. |
| `quarkus.artemis.xa-enabled` | Enables XA support for the connection factory. |

Set these values for the Artemis environment you intend to use. The checked-in configuration includes a password; replace it with a deployment-specific secret and do not commit real credentials to source control. The broker must be reachable from the application, and the `TestQueue01` queue must be available to the configured user.

## Run in development mode

Requires JDK 21. From this project directory, start Quarkus with:

```shell
./gradlew quarkusDev
```

On Windows, use `.\gradlew.bat quarkusDev`. The producer and consumer start automatically; watch the application log for `Sending data` and `Received data` messages.

## Build and run

Build the application:

```shell
./gradlew build
```

On Windows, use `.\gradlew.bat build`. The fast-jar output can be run with:

```shell
java -jar build/quarkus-app/quarkus-run.jar
```

The application requires a reachable Artemis broker at runtime.

## Related documentation

- [Quarkus Artemis JMS](https://docs.quarkiverse.io/quarkus-artemis/dev/index.html)
- [Quarkus Gradle tooling](https://quarkus.io/guides/gradle-tooling)
