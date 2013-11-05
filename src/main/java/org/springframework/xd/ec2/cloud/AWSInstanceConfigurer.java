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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.scriptbuilder.domain.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.xd.cloud.InstanceConfigurer;
import org.springframework.xd.cloud.InvalidXDZipUrlException;
import org.springframework.xd.ec2.environment.ConfigureSystem;

/**
 * Creates the scripts that will be used to install, bootstrap and configure XD
 * on an AWS instance.
 * 
 * @author glenn renfro
 * 
 */

public class AWSInstanceConfigurer implements InstanceConfigurer {
	private String xdDistUrl;
	private String redisPort;
	private String rabbitPort;
	private String xdZipCacheUrl;
	static final Logger logger = LoggerFactory
			.getLogger(AWSInstanceConfigurer.class);
	private static final String UBUNTU_HOME = "/home/ubuntu/";

	public AWSInstanceConfigurer(Properties properties) {
		xdDistUrl = properties.getProperty("xd-dist-url");
		redisPort = properties.getProperty("redis-port");
		rabbitPort = properties.getProperty("rabbit-port");
		xdZipCacheUrl = properties.getProperty("xd-zip-cache-url");
	}

	/**
	 * Creates the bash script that will start the resources needed for the XD
	 * Admin
	 */
	public String createStartXDResourcesScript() {
		return renderStatement(startXDResourceStatement());
	}

	/**
	 * Generate the command script that will install and setup
	 * 
	 * @param hostName
	 * @return
	 */
	public String createSingleNodeScript(String hostName) {
		return renderStatement(deploySingleNodeXDStatement(hostName));
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
		ScriptBuilder builder = new ScriptBuilder();
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
		ArrayList<Statement> result = new ArrayList<Statement>();
		result.add(exec("export XD_HOME=" + getInstalledDirectory() + "/xd"));
		logger.info("Using the following host to obtain XD Distribution: "
				+ getDistributionURL());
		result.add(exec("wget -P " + UBUNTU_HOME + " " + getDistributionURL()));
		result.add(exec("unzip " + UBUNTU_HOME + getFileName() + " -d "
				+ UBUNTU_HOME));
		result.add(exec(constructConfigurationCommand(hostName)));
		result.add(exec(getBinDirectory() + "xd-singlenode &"));
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
		return result;
	}

	private String getInstalledDirectory() {
		File file = new File(xdDistUrl);
		String path = file.getPath();
		StringTokenizer tokenizer = new StringTokenizer(path, "/");
		int tokenCount = tokenizer.countTokens();
		ArrayList<String> tokens = new ArrayList<String>(tokenCount);
		while (tokenizer.hasMoreElements()) {
			tokens.add(tokenizer.nextToken());
		}
		return String.format(UBUNTU_HOME + "spring-xd-%s",
				tokens.get(tokenCount - 2));
	}

	private String getBinDirectory() {
		return getInstalledDirectory() + "/xd/bin/";
	}

	private String getConfigDirectory() {
		return getInstalledDirectory() + "/xd/config/";
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

	private String constructConfigurationCommand(String hostName) {
		String configCommand = String
				.format("java -cp /home/ubuntu/deploy.jar org.springframework.xd.ec2.environment.ConfigureSystem --%s=%s --%s=%s --%s=%s --%s=%s --%s=%s --%s=%s > /home/ubuntu/config.txt 2> /home/ubuntu/configError.txt",
						ConfigureSystem.RABBIT_PROPS_FILE, getConfigDirectory()
								+ "rabbit.properties",
						ConfigureSystem.REDIS_PROPS_FILE, getConfigDirectory()
								+ "redis.properties",
						ConfigureSystem.RABBIT_HOST, hostName,
						ConfigureSystem.RABBIT_PORT, rabbitPort,
						ConfigureSystem.REDIS_HOST, hostName,
						ConfigureSystem.REDIS_PORT, redisPort);
		return configCommand;
	}
}
