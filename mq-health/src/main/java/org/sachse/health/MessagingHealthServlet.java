package org.sachse.health;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@WebServlet(name = "MessagingHealthServlet", urlPatterns = "/health/messaging", loadOnStartup = 1)
public class MessagingHealthServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final String ARTEMIS_JMX_DOMAIN = "org.apache.activemq.artemis";

	private static final String DEFAULT_MANAGEMENT_PROTOCOL = "remote+http";

	private static final String DEFAULT_MANAGEMENT_HOST = "127.0.0.1";

	private static final int DEFAULT_MANAGEMENT_PORT = 9990;

	private static final String BROKER = "broker";

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");

		response.setHeader("Pragma", "no-cache");
		response.setHeader("X-Content-Type-Options", "nosniff");

		BrokerStatus status = determineBrokerStatus();

		if (status.up()) {
			response.setStatus(HttpServletResponse.SC_OK);
		} else {
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}

		try (PrintWriter writer = response.getWriter()) {
			writer.print(toJson(status));
		} catch (IOException e) {
			getServletContext().log("Unable to write the ActiveMQ Artemis broker health response", e);
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}
	}

	private BrokerStatus determineBrokerStatus() {
		try {
			MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

			ObjectName brokerObjectName = findBrokerMBean(mBeanServer);

			if (brokerObjectName == null) {
				return determineBrokerStatusFromManagementClient("No ActiveMQ Artemis broker MBean was found");
			}

			Boolean started = readBooleanAttribute(mBeanServer, brokerObjectName, "Started");

			Boolean active = readBooleanAttribute(mBeanServer, brokerObjectName, "Active");

			String brokerName = getBrokerName(brokerObjectName);

			boolean brokerUp = Boolean.TRUE.equals(started) && Boolean.TRUE.equals(active);

			if (brokerUp) {
				return BrokerStatus.up(brokerName, started, active);
			}

			String reason;

			if (!Boolean.TRUE.equals(started)) {
				reason = "The broker is not started";
			} else {
				reason = "The broker is started but is not active";
			}

			return BrokerStatus.down(brokerName, started, active, reason);

		} catch (Exception exception) {
			getServletContext().log("Unable to determine ActiveMQ Artemis broker health", exception);

			return BrokerStatus.down(null, null, null, "Unable to query the ActiveMQ Artemis broker");
		}
	}

	private BrokerStatus determineBrokerStatusFromManagementClient(String mBeanFailureReason) {

		ManagementClientConfiguration configuration = getManagementClientConfiguration();

		try (ModelControllerClient client = createManagementClient(configuration)) {

			String serverState = readServerState(client);

			List<String> brokerNames = readBrokerNames(client);

			if (brokerNames.isEmpty()) {
				String reason = serverState == null ? "The management model did not report a messaging-activemq server"
						: "The server is " + serverState + " but no messaging-activemq server was reported";

				return BrokerStatus.down(null, null, null, reason);
			}

			BrokerStatus unknownStatus = null;

			for (String brokerName : brokerNames) {
				Boolean started = readBrokerAttribute(client, brokerName, "started");

				Boolean active = readBrokerAttribute(client, brokerName, "active");

				if (started == null && active == null) {

					if (unknownStatus == null) {
						unknownStatus = BrokerStatus.down(brokerName, null, null,
								"The management model could not determine the broker runtime state");
					}

					continue;
				}

				boolean brokerUp = Boolean.TRUE.equals(started) && Boolean.TRUE.equals(active);

				if (brokerUp) {
					return BrokerStatus.up(brokerName, started, active);
				}

				return BrokerStatus.down(brokerName, started, active,
						describeBrokerState(started, active, serverState, true));
			}

			if (unknownStatus != null) {
				return unknownStatus;
			}

			return BrokerStatus.down(null, null, null,
					mBeanFailureReason + ". Management client fallback could not determine the broker status");

		} catch (Exception exception) {
			getServletContext().log(
					"Unable to determine ActiveMQ Artemis broker health via the WildFly controller client", exception);

			return BrokerStatus.down(null, null, null,
					mBeanFailureReason + ". WildFly controller client fallback failed");
		}
	}

	private ObjectName findBrokerMBean(MBeanServer mBeanServer) {

		Set<ObjectName> registeredMBeans = mBeanServer.queryNames(null, null);

		if (registeredMBeans == null || registeredMBeans.isEmpty()) {

			return null;
		}

		return registeredMBeans.stream().filter(this::isBrokerMBean)
				.sorted(Comparator.comparing(ObjectName::getCanonicalName)).findFirst().orElse(null);
	}

	private String describeBrokerState(Boolean started, Boolean active, String serverState,
			boolean fromManagementClient) {

		String reason;

		if (!Boolean.TRUE.equals(started)) {
			reason = "The broker is not started";
		} else if (!Boolean.TRUE.equals(active)) {
			reason = "The broker is started but is not active";
		} else {
			reason = "The broker status could not be determined";
		}

		if (fromManagementClient && serverState != null) {

			reason = reason + " (server-state=" + serverState + ')';
		}

		return reason;
	}

	private boolean isBrokerMBean(ObjectName objectName) {

		if (!ARTEMIS_JMX_DOMAIN.equals(objectName.getDomain())) {

			return false;
		}

		String type = objectName.getKeyProperty("type");

		if ("Broker".equalsIgnoreCase(type)) {
			return true;
		}

		String broker = objectName.getKeyProperty(BROKER);

		String brokerName = objectName.getKeyProperty("brokerName");

		String component = objectName.getKeyProperty("component");

		String subcomponent = objectName.getKeyProperty("subcomponent");

		return (broker != null || brokerName != null) && component == null && subcomponent == null;
	}

	private ModelControllerClient createManagementClient(ManagementClientConfiguration configuration)
			throws IOException {

		CallbackHandler callbackHandler = createCallbackHandler(configuration);

		if (callbackHandler == null) {
			return ModelControllerClient.Factory.create(configuration.protocol(), configuration.host(),
					configuration.port());
		}

		return ModelControllerClient.Factory.create(configuration.protocol(), configuration.host(),
				configuration.port(), callbackHandler);
	}

	private CallbackHandler createCallbackHandler(ManagementClientConfiguration configuration) {

		if (configuration.username() == null || configuration.username().isBlank()) {

			return null;
		}

		char[] password = configuration.password() == null ? new char[0] : configuration.password().toCharArray();

		return callbacks -> handleManagementCallbacks(callbacks, configuration.username(), password);
	}

	private void handleManagementCallbacks(Callback[] callbacks, String username, char[] password)
			throws UnsupportedCallbackException {

		for (Callback callback : callbacks) {
			if (callback instanceof NameCallback nameCallback) {
				nameCallback.setName(username);
			} else if (callback instanceof PasswordCallback passwordCallback) {
				passwordCallback.setPassword(password);
			} else if (callback instanceof RealmCallback realmCallback) {
				realmCallback.setText(realmCallback.getDefaultText());
			} else if (callback instanceof RealmChoiceCallback realmChoiceCallback) {
				realmChoiceCallback.setSelectedIndex(realmChoiceCallback.getDefaultChoice());
			} else if (callback instanceof AuthorizeCallback authorizeCallback) {
				authorizeCallback.setAuthorized(
						authorizeCallback.getAuthenticationID().equals(authorizeCallback.getAuthorizationID()));
			} else {
				throw new UnsupportedCallbackException(callback);
			}
		}
	}

	private String readServerState(ModelControllerClient client) throws IOException {

		ModelNode operation = createReadAttributeOperation(List.of(), "server-state");

		ModelNode response = client.execute(operation);

		return readStringResult(response);
	}

	private List<String> readBrokerNames(ModelControllerClient client) throws IOException {

		ModelNode operation = new ModelNode();

		operation.get("operation").set("read-children-names");

		operation.get("child-type").set("server");

		operation.get("address").add("subsystem", "messaging-activemq");

		ModelNode response = client.execute(operation);

		ModelNode result = requireSuccessfulResult(response);

		if (!result.isDefined()) {
			return List.of();
		}

		List<String> brokerNames = new ArrayList<>();

		for (ModelNode brokerNode : result.asList()) {
			brokerNames.add(brokerNode.asString());
		}

		return brokerNames;
	}

	private Boolean readBrokerAttribute(ModelControllerClient client, String brokerName, String attributeName)
			throws IOException {

		ModelNode operation = createReadAttributeOperation(
				List.of(addressElement("subsystem", "messaging-activemq"), addressElement("server", brokerName)),
				attributeName);

		ModelNode response = client.execute(operation);

		ModelNode result = requireSuccessfulResult(response);

		if (!result.isDefined()) {
			return null;
		}

		return convertToBoolean(result.asString());
	}

	private ModelNode createReadAttributeOperation(List<AddressElement> address, String attributeName) {

		ModelNode operation = new ModelNode();

		operation.get("operation").set("read-attribute");

		operation.get("name").set(attributeName);

		ModelNode addressNode = operation.get("address");

		for (AddressElement element : address) {
			addressNode.add(element.name(), element.value());
		}

		return operation;
	}

	private ModelNode requireSuccessfulResult(ModelNode response) throws IOException {

		String outcome = response.get("outcome").asString();

		if (!"success".equalsIgnoreCase(outcome)) {
			String failure = response.get("failure-description").asString();

			throw new IOException(failure);
		}

		return response.get("result");
	}

	private String readStringResult(ModelNode response) throws IOException {

		ModelNode result = requireSuccessfulResult(response);

		if (!result.isDefined()) {
			return null;
		}

		return result.asString();
	}

	private AddressElement addressElement(String name, String value) {

		return new AddressElement(name, value);
	}

	private ManagementClientConfiguration getManagementClientConfiguration() {

		return new ManagementClientConfiguration(
				getConfigurationValue("mq.health.management.protocol", "MQ_HEALTH_MANAGEMENT_PROTOCOL",
						DEFAULT_MANAGEMENT_PROTOCOL),
				getConfigurationValue("mq.health.management.host", "MQ_HEALTH_MANAGEMENT_HOST",
						DEFAULT_MANAGEMENT_HOST),
				getConfigurationIntValue("mq.health.management.port", "MQ_HEALTH_MANAGEMENT_PORT",
						DEFAULT_MANAGEMENT_PORT),
				getConfigurationValue("mq.health.management.user", "MQ_HEALTH_MANAGEMENT_USER", null),
				getConfigurationValue("mq.health.management.password", "MQ_HEALTH_MANAGEMENT_PASSWORD", null));
	}

	private int getConfigurationIntValue(String propertyName, String environmentName, int defaultValue) {

		String value = getConfigurationValue(propertyName, environmentName, null);

		if (value == null) {
			return defaultValue;
		}

		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			getServletContext().log("Invalid integer value for " + propertyName + '/' + environmentName + ": " + value,
					exception);

			return defaultValue;
		}
	}

	private String getConfigurationValue(String propertyName, String environmentName, String defaultValue) {

		String value = System.getProperty(propertyName);

		if (value == null || value.isBlank()) {

			value = System.getenv(environmentName);
		}

		if (value == null || value.isBlank()) {

			return defaultValue;
		}

		return value.trim();
	}

	private Boolean readBooleanAttribute(MBeanServer mBeanServer, ObjectName objectName, String attributeName)
			throws InstanceNotFoundException, ReflectionException, MBeanException, AttributeNotFoundException {

		try {
			Object value = mBeanServer.getAttribute(objectName, attributeName);

			return convertToBoolean(value);

		} catch (AttributeNotFoundException exception) {
			String fallbackName = Character.toLowerCase(attributeName.charAt(0)) + attributeName.substring(1);

			Object value = mBeanServer.getAttribute(objectName, fallbackName);

			return convertToBoolean(value);
		}
	}

	private Boolean convertToBoolean(Object value) {

		if (value instanceof Boolean booleanValue) {
			return booleanValue;
		}

		if (value == null) {
			return null;
		}

		return Boolean.valueOf(value.toString());
	}

	private String getBrokerName(ObjectName brokerObjectName) {

		String value = brokerObjectName.getKeyProperty(BROKER);

		if (value == null) {
			value = brokerObjectName.getKeyProperty("brokerName");
		}

		if (value == null) {
			value = brokerObjectName.getKeyProperty("name");
		}

		if (value == null) {
			return null;
		}

		try {
			return ObjectName.unquote(value);
		} catch (IllegalArgumentException exception) {
			return value;
		}
	}

	private String toJson(BrokerStatus status) {

		StringBuilder json = new StringBuilder(192);

		json.append('{');

		appendJsonString(json, "status", status.up() ? "UP" : "DOWN");

		if (status.broker() != null) {
			json.append(',');

			appendJsonString(json, BROKER, status.broker());
		}

		if (status.started() != null) {
			json.append(",\"started\":").append(status.started());
		}

		if (status.active() != null) {
			json.append(",\"active\":").append(status.active());
		}

		if (status.reason() != null) {
			json.append(',');

			appendJsonString(json, "reason", status.reason());
		}

		json.append('}');

		return json.toString();
	}

	private void appendJsonString(StringBuilder json, String name, String value) {

		json.append('"').append(escapeJson(name)).append("\":\"").append(escapeJson(value)).append('"');
	}

	private String escapeJson(String value) {

		if (value == null) {
			return "";
		}

		StringBuilder escaped = new StringBuilder(value.length() + 16);

		for (int index = 0; index < value.length(); index++) {

			char character = value.charAt(index);

			switch (character) {
			case '"' -> escaped.append("\\\"");
			case '\\' -> escaped.append("\\\\");
			case '\b' -> escaped.append("\\b");
			case '\f' -> escaped.append("\\f");
			case '\n' -> escaped.append("\\n");
			case '\r' -> escaped.append("\\r");
			case '\t' -> escaped.append("\\t");

			default -> {
				if (character < 0x20) {
					escaped.append(String.format("\\u%04x", (int) character));
				} else {
					escaped.append(character);
				}
			}
			}
		}

		return escaped.toString();
	}

	private record AddressElement(String name, String value) {
	}

	private record ManagementClientConfiguration(String protocol, String host, int port, String username,
			String password) {
	}

	private record BrokerStatus(boolean up, String broker, Boolean started, Boolean active, String reason) {

		private static BrokerStatus up(String broker, Boolean started, Boolean active) {

			return new BrokerStatus(true, broker, started, active, null);
		}

		private static BrokerStatus down(String broker, Boolean started, Boolean active, String reason) {

			return new BrokerStatus(false, broker, started, active, reason);
		}
	}
}