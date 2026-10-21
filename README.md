# Java Messaging Examples

This repository contains two Java examples that integrate with ActiveMQ Artemis. Both projects require JDK 21 and are built independently with their own Gradle wrapper.

## Projects

### [MQ Health](mq-health/README.md)

A Jakarta Servlet WAR for JBoss EAP 8 that reports the Artemis broker's health at `/mq-health/health/messaging`. It checks broker state through JMX and can fall back to the EAP management model. See the [MQ Health README](mq-health/README.md) for deployment details and fallback configuration.

Build from the repository root:

```powershell
.\mq-health\gradlew.bat -p .\mq-health clean war
```

### [Quarkus JMS Example](quarkus-jms-example/README.md)

A Quarkus application that periodically sends sample messages to the `TestQueue01` Artemis queue and consumes and logs them. See the [Quarkus JMS README](quarkus-jms-example/README.md) for broker configuration and run instructions.

Build from the repository root:

```powershell
.\quarkus-jms-example\gradlew.bat -p .\quarkus-jms-example build
```

Both applications need access to an appropriately configured Artemis broker at runtime. The Quarkus JMS example's checked-in configuration includes a password; replace it with a deployment-specific secret and avoid committing real credentials.