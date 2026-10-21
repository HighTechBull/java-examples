# MQ Health

MQ Health is a Jakarta Servlet application that exposes an HTTP health check for an ActiveMQ Artemis broker managed by JBoss EAP 8. It is packaged as a WAR and is intended to be deployed to the application server alongside the broker.

## Health endpoint

Send a `GET` request to:

```text
/mq-health/health/messaging
```

The endpoint checks the broker's `Started` and `Active` state. It first looks for the Artemis broker MBean in the application server's platform MBean server. If no broker MBean is found, it attempts to query the WildFly management model.

An available broker returns HTTP `200` with a response such as:

```json
{"status":"UP","broker":"default","started":true,"active":true}
```

An unavailable or indeterminate broker returns HTTP `503` with `"status":"DOWN"` and, when available, a `"reason"` describing the result. The response is JSON and is sent with cache-prevention headers.

## Build

Requires JDK 21. From this project directory, run:

```shell
./gradlew clean war
```

On Windows, use:

```powershell
.\gradlew.bat clean war
```

The resulting WAR is `build/libs/mq-health-1.0.0.war`. The Servlet API and WildFly controller client are compile-time-only dependencies; JBoss EAP provides the runtime APIs and management modules.

## Deploy

Deploy `build/libs/mq-health-1.0.0.war` to JBoss EAP 8. Once deployment is successful, request the health endpoint through the server's HTTP(S) listener, for example:

```text
http://localhost:8080/mq-health/health/messaging
```

The deployment requires access to the Artemis broker MBean. If the servlet cannot find that MBean, its fallback uses the EAP management interface and the `org.jboss.as.controller-client` and `org.jboss.dmr` server modules.

## Management fallback configuration

The fallback connection defaults to `remote+http://127.0.0.1:9990`. Configure it with Java system properties or environment variables; a nonblank system property takes precedence over its environment variable.

| Java system property | Environment variable | Default |
| --- | --- | --- |
| `mq.health.management.protocol` | `MQ_HEALTH_MANAGEMENT_PROTOCOL` | `remote+http` |
| `mq.health.management.host` | `MQ_HEALTH_MANAGEMENT_HOST` | `127.0.0.1` |
| `mq.health.management.port` | `MQ_HEALTH_MANAGEMENT_PORT` | `9990` |
| `mq.health.management.user` | `MQ_HEALTH_MANAGEMENT_USER` | unset |
| `mq.health.management.password` | `MQ_HEALTH_MANAGEMENT_PASSWORD` | unset |

If a username is configured, the servlet also supplies the configured password to the management client. Keep management credentials in the server's secret-management facility rather than in source control.

The health endpoint itself does not implement authentication. Restrict access to it using the application server, reverse proxy, or network policy appropriate to your deployment.