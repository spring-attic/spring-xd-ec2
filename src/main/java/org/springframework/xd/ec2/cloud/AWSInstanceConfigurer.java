/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.ec2.cloud;

import static org.jclouds.scriptbuilder.domain.Statements.exec;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.scriptbuilder.domain.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.xd.cloud.InstanceConfigurer;

/**
 * Creates the scripts that will be used to install, bootstrap and configure XD
 * on an AWS instance.
 * 
 * @author glenn renfro
 * 
 */

public class AWSInstanceConfigurer implements InstanceConfigurer {
	private transient String xdDistUrl;
	private transient String xdRelease;
	private transient String xdZipCacheUrl;
	private transient boolean useEmbeddedZookeeper = true;
	private transient Properties properties;
	private static final String RABBIT_HOST = "spring_rabbitmq_host";
	private static final String REDIS_HOST = "spring_redis_host";
	private static final String ZK_CLIENT_CONNECT = "ZK_CLIENT_CONNECT";

	private static final String USE_EMBEDDED_ZOOKEEPER = "use_embedded_zookeeper";

	static final Logger LOGGER = LoggerFactory
			.getLogger(AWSInstanceConfigurer.class);
	private static final String UBUNTU_HOME = "/home/ubuntu/";

	public AWSInstanceConfigurer(Properties properties) {
		xdDistUrl = properties.getProperty("xd-dist-url");
		xdZipCacheUrl = properties.getProperty("xd-zip-cache-url");
		xdRelease = properties.getProperty("xd-release");
		if (properties.containsKey(USE_EMBEDDED_ZOOKEEPER)) {
			useEmbeddedZookeeper = Boolean.parseBoolean(properties
					.getProperty(USE_EMBEDDED_ZOOKEEPER));
		}
		this.properties = properties;
	}

	/**
	 * Creates the bash script that will start the resources needed for the XD
	 * Admin
	 */
	public String createStartXDResourcesScript() {
		return renderStatement(startXDResourceStatement());
	}

	/**
	 * Execute steps necessary to setup a container node for XD.
	 * 
	 * @return
	 */
	public String bootstrapXDNodeScript() {
		return renderStatement(bootstrapNodeStatement());
	}

	/**
	 * Generate the command script that will install and setup a single node
	 * 
	 * @param hostName
	 * @return
	 */
	public String createSingleNodeScript(String hostName) {
		return renderStatement(deploySingleNodeXDStatement(hostName));
	}

	/**
	 * Generate the command script that will install and setup an administrator
	 * 
	 * @param hostName
	 * @return
	 */
	public String createAdminNodeScript(String hostName) {
		return renderStatement(deployAdminNodeXDStatement(hostName));
	}

	/**
	 * Generate the command script that will install and setup a container node
	 * 
	 * @param hostName
	 * @return
	 */
	public String createContainerNodeScript(String hostName,
			String transport) {
		return renderStatement(deployContainerNodeXDStatement(hostName,
				transport));
	}

	/**
	 * Extracts the file's name from the xdDistURL property.
	 * 
	 * @return the file name of the distribution.
	 */
	public String getFileName() {
		File file = new File(xdDistUrl);
		return file.getName();
	}

	/**
	 * Verifies that the URL is active and returns a 200. If not it will throw
	 * an exception.
	 * 
	 * @param url
	 *            The URL to verify.
	 */
	public void checkURL(String url) {
		RestTemplate template = new RestTemplate();
		template.headForHeaders(url);
	}

	/**
	 * Takes the statements and converts them to a string which will be written
	 * as a configuration script on the Instance OS.
	 * 
	 * @param statements
	 *            The command statements that will need to be converted to a
	 *            string.
	 * @return String that will be streamed to the OS Instance and saved as a
	 *         configuration script.
	 */
	private String renderStatement(List<Statement> statements) {
		final ScriptBuilder builder = new ScriptBuilder();
		for (Statement statement : statements) {
			builder.addStatement(statement);
		}
		Map<String, String> environmentVariables = new HashMap<String, String>();
		environmentVariables.put("XD_HOME", getInstalledDirectory());
		builder.addEnvironmentVariableScope("default", environmentVariables);
		String script = builder.render(OsFamily.UNIX);
		return script;
	}

	/**
	 * Generates the script that will be executed on the instance Operating
	 * SYstem. The script that will be generated will: -- retrieve the
	 * distribution the user requested -- install distribution on the OS. --
	 * configure the distribution e -- start the distribution in single node
	 * mode.
	 * 
	 * @param hostName
	 * @return the script that will used to initialize the application.
	 */
	private List<Statement> deploySingleNodeXDStatement(String hostName) {
		List<Statement> result = initializeEnvironmentStatements(hostName, true);
		LOGGER.info("Using the following host to obtain XD Distribution: "
				+ getDistributionURL());
		result.add(exec("wget -P " + UBUNTU_HOME + " " + getDistributionURL()));
		result.add(exec("unzip " + UBUNTU_HOME + getFileName() + " -d "
				+ UBUNTU_HOME));
		result.add(exec(constructConfigurationCommand(hostName)));
		result.add(exec(getBinDirectory() + "xd-singlenode &"));
		return result;
	}

	/**
	 * Generates the script that will be executed on the instance Operating
	 * SYstem. The script that will be generated will: -- retrieve the
	 * distribution the user requested -- install distribution on the OS. --
	 * configure the distribution e -- start the distribution as an admin.
	 * 
	 * @param hostName
	 * @return the script that will used to initialize the application.
	 */
	private List<Statement> deployAdminNodeXDStatement(String hostName) {
		List<Statement> result = initializeEnvironmentStatements(hostName,
				false);
		LOGGER.info("Using the following host to obtain XD Distribution: "
				+ getDistributionURL());
		result.add(exec("wget -P " + UBUNTU_HOME + " " + getDistributionURL()));
		result.add(exec("unzip " + UBUNTU_HOME + getFileName() + " -d "
				+ UBUNTU_HOME));
		result.add(exec(constructConfigurationCommand(hostName)));
		result.add(exec(getBinDirectory() + "xd-admin &"));
		return result;
	}

	/**
	 * Generates the script that will be executed on the instance Operating
	 * SYstem. The script that will be generated will: -- retrieve the
	 * distribution the user requested -- install distribution on the OS. --
	 * configure the distribution e -- start the distribution as an container.
	 * 
	 * @param hostName
	 * @param logConfig
	 *            the name of the
	 * @return the script that will used to initialize the application.
	 */
	private List<Statement> deployContainerNodeXDStatement(String hostName,
			 String transport) {
		List<Statement> result = initializeEnvironmentStatements(hostName,
				false);
		result.add(exec("export XD_HOME=" + getInstalledDirectory() + "/xd"));
		LOGGER.info("Using the following host to obtain XD Distribution: "
				+ getDistributionURL());
		result.add(exec("wget -P " + UBUNTU_HOME + " " + getDistributionURL()));
		result.add(exec("unzip " + UBUNTU_HOME + getFileName() + " -d "
				+ UBUNTU_HOME));
		result.add(exec(constructConfigurationCommand(hostName)));
		result.add(exec(getBinDirectory() + "xd-container "
				+ getTransportString(transport) + " &"));
		return result;
	}

	private String getTransportString(String transport) {
		final String BASE_TRANSPORT_PREFIX = "--transport ";
		String result = "";
		if (transport != null && transport.length() != 0) {
			result = BASE_TRANSPORT_PREFIX + transport;
		}
		return result;
	}

	/**
	 * Generates the statements that will start the resources needed for the XD
	 * admin.
	 * 
	 * @return a listing of statements used to start resources needed for XD.
	 */
	private List<Statement> startXDResourceStatement() {
		ArrayList<Statement> result = new ArrayList<Statement>();
		result.add(exec("/etc/init.d/redis-server start"));
		result.add(exec("/etc/init.d/rabbitmq-server start"));
		if (!useEmbeddedZookeeper) {
			result.add(exec("/home/ubuntu/startZooKeeper.sh"));
		}
		return result;
	}

	/**
	 * Generates the statements that will start the resources needed for the XD
	 * admin.
	 * 
	 * @return a listing of statements used to start resources needed for XD.
	 */
	private List<Statement> bootstrapNodeStatement() {
		ArrayList<Statement> result = new ArrayList<Statement>();
		result.add(exec("ls -al"));
		return result;
	}

	private String getInstalledDirectory() {
		return String.format(UBUNTU_HOME + xdRelease);
	}

	private String getBinDirectory() {
		return getInstalledDirectory() + "/xd/bin/";
	}

	private String getDistributionURL() {
		// check to see if the distribution is cached on S3 before pulling from
		// server
		String result = xdDistUrl;
		try {
			String cacheLocation = xdZipCacheUrl + "/" + getFileName();
			checkURL(cacheLocation);
			result = cacheLocation;
		} catch (Exception exception) {
			// in this case we are catching the exception, only to state that
			// the file is not in the cache.
		}
		return result;
	}

	/**
	 * Constructs the command script that will add the environment variables to
	 * the .bashrc. Also establishes the host environment variable. In our case
	 * we ignore the host the user specfies assuming that the redis and rabbit
	 * are hosted on the admin server..
	 * 
	 * @param hostName
	 * @return
	 */
	private String constructConfigurationCommand(String hostName) {
		String configCommand = "java -cp /home/ubuntu/deploy.jar org.springframework.xd.ec2.environment.ConfigureSystem  ";
		String suffix = " > /home/ubuntu/config.txt 2> /home/ubuntu/configError.txt";
		Iterator<Entry<Object, Object>> iter = properties.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<Object, Object> entry = (Entry<Object, Object>) iter.next();
			String key = (String) entry.getKey();
			if (key.startsWith("spring.") || key.startsWith("amq.")
					|| key.startsWith("mqtt.") || key.startsWith("endpoints.")
					|| key.startsWith("XD_JMX_ENABLED")
					|| key.startsWith("server.")
					|| key.startsWith("management.") || key.startsWith("PORT")) {
				configCommand = configCommand.concat(" --"
						+ ((String) entry.getKey()).replace(".", "_") + "="
						+ entry.getValue());
			}
		}
		configCommand = configCommand + " --XD_HOME=" + getInstalledDirectory()
				+ "/xd";
		configCommand = configCommand + " --" + RABBIT_HOST + "=" + hostName;
		configCommand = configCommand + " --" + REDIS_HOST + "=" + hostName;
		if (!useEmbeddedZookeeper) {
			configCommand = configCommand + " --" + ZK_CLIENT_CONNECT + "="
					+ hostName + ":2181";
		}
		return configCommand.concat(suffix);
	}

	/**
	 * Adds the statements to the initialization script that setup the
	 * environment variables. Also establishes the host environment variable. In
	 * our case we ignore the host the user specfies assuming that the redis and
	 * rabbit are hosted on the admin server..
	 */
	public List<Statement> initializeEnvironmentStatements(String hostName,
			boolean isStandAlone) {
		List<Statement> result = new ArrayList<Statement>();
		Iterator<Entry<Object, Object>> iter = properties.entrySet().iterator();

		result.add(exec("export XD_HOME=" + getInstalledDirectory() + "/xd"));
		result.add(exec("export " + RABBIT_HOST + "=" + hostName));
		result.add(exec("export " + REDIS_HOST + "=" + hostName));
		if (!useEmbeddedZookeeper) {
			result.add(exec("export " + ZK_CLIENT_CONNECT + "=" + hostName
					+ ":2181"));
		}
		while (iter.hasNext()) {
			Entry<Object, Object> entry = (Entry<Object, Object>) iter.next();
			String key = ((String) entry.getKey());

			if (key.startsWith("spring.") || key.startsWith("amq.")
					|| key.startsWith("mqtt.") || key.startsWith("endpoints.")
					|| key.startsWith("XD_JMX_ENABLED")
					|| key.startsWith("server.")
					|| key.startsWith("management.") || key.startsWith("PORT")) {
				result.add(exec("export " + key.replace(".", "_") + "="
						+ entry.getValue()));
			}

		}

		return result;
	}

	public boolean isUseEmbeddedZookeeper() {
		return useEmbeddedZookeeper;
	}

	public void setUseEmbeddedZookeeper(boolean useEmbeddedZookeeper) {
		this.useEmbeddedZookeeper = useEmbeddedZookeeper;
	}
}
