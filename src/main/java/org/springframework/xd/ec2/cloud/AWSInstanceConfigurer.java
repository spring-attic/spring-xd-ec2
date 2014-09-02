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

import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.scriptbuilder.domain.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.xd.cloud.InstanceConfigurer;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

import static org.jclouds.scriptbuilder.domain.Statements.exec;

/**
 * Creates the scripts that will be used to install, bootstrap and configure XD
 * on an AWS instance.
 * 
 * @author Glenn Renfro
 * 
 */

public class AWSInstanceConfigurer implements InstanceConfigurer {

	private String xdDistUrl;

	private String xdRelease;

	private List<String> xdThirdPartyJars;

	private boolean useEmbeddedZookeeper = true;

	private Properties properties;

	private static final String RABBIT_ADDRESSES = "spring_rabbitmq_addresses";

	private static final String REDIS_HOST = "spring_redis_host";

	private static final String REDIS_PORT = "spring_redis_port";

	private static final String ZK_CLIENT_CONNECT = "ZK_CLIENT_CONNECT";

	private static final String USE_EMBEDDED_ZOOKEEPER = "use_embedded_zookeeper";

	private static final String XD_THIRD_PARTY_JAR_URLS = "xd-third-party-jar-urls";

	private static final String REDIS_EC2_ADDRESS = "spring.redis.address";
	private static final String RABBIT_EC2_ADDRESSES = "spring.rabbitmq.addresses";
	private static final String ZOOKEEPER_EC2_ADDRESSES = "spring.zookeeper.addresses";


	static final Logger LOGGER = LoggerFactory
			.getLogger(AWSInstanceConfigurer.class);

	private static final String UBUNTU_HOME = "/home/ubuntu/";

	public AWSInstanceConfigurer(Properties properties) {
		Assert.notNull(properties, "properties can not be null");
		xdDistUrl = properties.getProperty("xd-dist-url");
		xdRelease = properties.getProperty("xd-release");
		xdThirdPartyJars = getThirdPartyUrls(properties);
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
	@Override
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
	 * @param hostName The host where the single node will be deployed
	 * @param hadoopVersion The version of hadoop this instance will execute against.
	 * @return String containing singlenode install script
	 */
	public String createSingleNodeScript(String hostName, String hadoopVersion) {
		Assert.hasText(hostName, "hostName can not be empty nor null");
		Assert.hasText(hadoopVersion, "hadoopVersion can not be empty nor null");
		return renderStatement(deploySingleNodeXDStatement(hostName, hadoopVersion));
	}

	/**
	 * Generate the command script that will install and setup an administrator
	 * @param hostName the host where the admin server will be deployed.
	 * @return String containing the admin nod install script
	 */
	public String createAdminNodeScript(String hostName) {
		Assert.hasText(hostName, "hostName can not be empty nor null");
		return renderStatement(deployAdminNodeXDStatement(hostName));
	}

	/**
	 * Generate the command script that will install and setup a container node
	 * 
	 * @param hostName the host where this container will be deployed
	 * @param hadoopVersion The version of hadoop this instance will execute against.
	 * @param instanceIndex The index associated with the container.
	 * @return String containing the container installation script.
	 */
	public String createContainerNodeScript(String hostName, String hadoopVersion, int instanceIndex) {
		Assert.hasText(hostName, "hostName can not be empty nor null");
		Assert.hasText(hadoopVersion, "hadoopVersion can not be empty nor null");
		return renderStatement(deployContainerNodeXDStatement(hostName, hadoopVersion, instanceIndex));
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
	 * @param url The URL to verify.
	 */
	public void checkURL(String url) {
		Assert.hasText(url, "url cannot be null nor empty");
		RestTemplate template = new RestTemplate();
		template.headForHeaders(url);
	}

	/**
	 * Retrieves a boolean stating whether the tool is setting up a embedded zookeeper (singlenode)
	 * @return true if using embedded zookeeper.  Else false.
	 */
	public boolean isUseEmbeddedZookeeper() {
		return useEmbeddedZookeeper;
	}

	/**
	 * Establishes if the XD instance is going to use an embedded zookeeper (singlenode)
	 * @param useEmbeddedZookeeper True if xd instance will use embedded zookeeper.  Else false.
	 */
	public void setUseEmbeddedZookeeper(boolean useEmbeddedZookeeper) {
		this.useEmbeddedZookeeper = useEmbeddedZookeeper;
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
	private List<Statement> deploySingleNodeXDStatement(String hostName, String hadoopVersion) {
		List<Statement> result = initializeEnvironmentStatements(hostName);
		result = addGetResourceStatements(result);
		result.add(exec(constructConfigurationCommand(hostName)));
		result.add(exec(getBinDirectory() + "xd-singlenode " + getHadoopVersion(hadoopVersion) + " &"));
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
		List<Statement> result = initializeEnvironmentStatements(hostName);
		result = addGetResourceStatements(result);
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
	 * @param hostName the host of the admin server
	 * @param hadoopVersion the hadoop version that the container will use to load the correct libs.
	 * @param instanceIndex identifes which index specific environment variables will be added to this instance.
	 * @return the script that will used to initialize the application.
	 */
	private List<Statement> deployContainerNodeXDStatement(String hostName, String hadoopVersion, int instanceIndex) {

		List<Statement> result = initializeEnvironmentStatements(hostName, instanceIndex);
		result.add(exec("export XD_HOME=" + getInstalledDirectory() + "/xd"));
		result = addGetResourceStatements(result);
		result.add(exec(constructConfigurationCommand(hostName, instanceIndex)));
		result.add(exec(getBinDirectory() + "xd-container " + getHadoopVersion(hadoopVersion) + " &"));
		return result;
	}

	private String getHadoopVersion(String hadoopVersion) {
		final String BASE_HADOOP_VERSION_PREFIX = "--hadoopDistro ";
		String result = "";
		if (hadoopVersion != null && hadoopVersion.length() != 0) {
			result = BASE_HADOOP_VERSION_PREFIX + hadoopVersion;
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

	private String getLibDirectory() {
		return getInstalledDirectory() + "/xd/lib/";
	}

	/**
	 * Constructs the command script that will add the environment variables to
	 * the .bashrc. Also establishes the host environment variable. In our case
	 * we ignore the host the user specfies assuming that the redis and rabbit
	 * are hosted on the admin server. 
	 * @param hostName The host where the admin server is deployed
	 * @return The statement that will be used to initialize the environment in the .bashrc.
	 */
	private String constructConfigurationCommand(String hostName) {
		return constructConfigurationCommand(hostName, null);

	}

	/**
	 * Constructs the command script that will add the environment variables to
	 * the .bashrc. Also establishes the host environment variable. In our case
	 * we ignore the host the user specfies assuming that the redis and rabbit
	 * are hosted on the admin server. 
	 * @param hostName The host where the admin server is deployed
	 * @param containerIndex the index that will be used to identify if a specific property 
	 * should be added to a container's environment.  If null, container specific entries will not be searched.
	 * @return The statement that will be used to initialize the environment in the .bashrc.
	 */
	private String constructConfigurationCommand(String hostName,
			Integer containerIndex) {
		String configCommand = getBaseConfigurationCommand(hostName);
		String suffix = " > /home/ubuntu/config.txt 2> /home/ubuntu/configError.txt";
		Iterator<Entry<Object, Object>> iter = properties.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<Object, Object> entry = iter.next();
			String key = (String) entry.getKey();
			boolean isValidKey = false;

			if (key.startsWith("spring.") || key.startsWith("brokerURL")
					|| key.startsWith("mqtt.") || key.startsWith("endpoints.")
					|| key.startsWith("XD_") || key.startsWith("server.")
					|| key.startsWith("management.") || key.startsWith("PORT")) {
				isValidKey = true;
			}
			else if (containerIndex != null
					&& key.startsWith("XD" + containerIndex + ".")) {
				key = key.substring(key.indexOf(".") + 1);
				isValidKey = true;
			}
			if (isValidKey) {
				configCommand = configCommand.concat(" --"
						+ key.replace(".", "_") + "="
						+ entry.getValue());
			}
		}
		return configCommand.concat(suffix);
	}

	private String getBaseConfigurationCommand(String hostName) {
		String redisAddress = properties.getProperty(REDIS_EC2_ADDRESS);
		String rabbitAddresses = properties.getProperty(RABBIT_EC2_ADDRESSES);
		String zookeeperAddresses = properties.getProperty(ZOOKEEPER_EC2_ADDRESSES);

		String configCommand = "java -cp /home/ubuntu/deploy.jar org.springframework.xd.ec2.environment.ConfigureSystem  ";
		configCommand = configCommand + " --XD_HOME=" + getInstalledDirectory()
				+ "/xd";
		configCommand = configCommand + " --" + RABBIT_ADDRESSES + "=" + rabbitAddresses;
		String redisHostPort[] = StringUtils.delimitedListToStringArray(redisAddress,":");
		configCommand = configCommand + " --" + REDIS_HOST + "=" + redisHostPort[0];
		configCommand = configCommand + " --" + REDIS_PORT + "=" + redisHostPort[1];

		if (!useEmbeddedZookeeper) {
			configCommand = configCommand + " --" + ZK_CLIENT_CONNECT + "=" + zookeeperAddresses;
		}
		return configCommand;
	}

	/**
	 * Adds the statements to the initialization script that setup the
	 * environment variables. Also establishes the host environment variable. In
	 * our case we ignore the host the user specfies assuming that the redis and
	 * rabbit are hosted on the admin server.
	 * @param hostName the host where the admin is deployed
	 */
	private List<Statement> initializeEnvironmentStatements(String hostName) {
		return initializeEnvironmentStatements(hostName, null);
	}

	/**
	 * Adds the statements to the initialization script that setup the
	 * environment variables. Also establishes the host environment variable. In
	 * our case we ignore the host the user specfies assuming that the redis and
	 * rabbit are hosted on the admin server.
	 * @param hostName the host where the admin is deployed
	 * @param containerIndex the index that will be used to identify if a specific property 
	 * should be added to a container's environment.  If null, container specific entries will not be searched.
	 */
	private List<Statement> initializeEnvironmentStatements(String hostName,
			Integer containerIndex) {
		List<Statement> result = getBaseEnvironmentList(hostName);
		Iterator<Entry<Object, Object>> iter = properties.entrySet().iterator();

		while (iter.hasNext()) {
			boolean isValidKey = false;
			Entry<Object, Object> entry = iter.next();
			String key = (String) entry.getKey();
			if (key.startsWith("spring.") || key.startsWith("brokerURL")
					|| key.startsWith("mqtt.") || key.startsWith("endpoints.")
					|| key.startsWith("XD_") || key.startsWith("server.")
					|| key.startsWith("management.") || key.startsWith("PORT")) {
				isValidKey = true;
			}
			else if (containerIndex != null
					&& key.startsWith("XD" + containerIndex + ".")) {
				key = key.substring(key.indexOf(".") + 1);
				isValidKey = true;
			}
			if (isValidKey) {
				result.add(exec("export " + key.replace(".", "_") + "="
						+ entry.getValue()));
			}
		}
		return result;
	}

	private List<Statement> getBaseEnvironmentList(String hostName) {
		List<Statement> result = new ArrayList<Statement>();

		result.add(exec("export XD_HOME=" + getInstalledDirectory() + "/xd"));
		result.add(exec("export " + RABBIT_ADDRESSES + "=" + properties.getProperty(RABBIT_EC2_ADDRESSES)));
		String redisHostPort[] = StringUtils.delimitedListToStringArray(properties.getProperty(REDIS_EC2_ADDRESS),":");
		result.add(exec("export " + REDIS_HOST + "=" + redisHostPort[0]));
		result.add(exec("export " + REDIS_PORT + "=" + redisHostPort[1]));

		if (!useEmbeddedZookeeper) {
			result.add(exec("export " + ZK_CLIENT_CONNECT + "=" + properties.getProperty(ZOOKEEPER_EC2_ADDRESSES)));
		}
		return result;
	}

	private List<String> getThirdPartyUrls(Properties properties) {
		List<String> result = new ArrayList<String>();
		if (properties.containsKey(XD_THIRD_PARTY_JAR_URLS)) {
			String urls = properties.getProperty(XD_THIRD_PARTY_JAR_URLS);
			Iterator<String> urlIter = StringUtils.commaDelimitedListToSet(urls).iterator();
			while (urlIter.hasNext())
			{
				result.add(urlIter.next());
			}
		}
		return result;
	}

	List<Statement> addGetResourceStatements(List<Statement> statements) {
		statements = new ArrayList<Statement>(statements);
		String xdGetDist= properties.getProperty("spring-xd-get-dist", "true");
		if(xdGetDist.equalsIgnoreCase("true")) {
			statements.add(exec("wget -P " + UBUNTU_HOME + " " + xdDistUrl));
		}
		statements.add(exec("unzip " + UBUNTU_HOME + getFileName() + " -d "
				+ UBUNTU_HOME));
		//Add jars to xd/lib
		Iterator<String> urlIter = xdThirdPartyJars.iterator();
		while (urlIter.hasNext()) {
			statements.add(exec("wget -P " + getLibDirectory() + " " + urlIter.next()));
		}
		return statements;
	}

	public Properties getProperties() {
		return properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}


}
