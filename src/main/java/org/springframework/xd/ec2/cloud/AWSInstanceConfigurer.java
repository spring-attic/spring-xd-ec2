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
import java.util.StringTokenizer;

import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.scriptbuilder.domain.Statement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.xd.cloud.InstanceConfigurer;
import org.springframework.xd.ec2.environment.ConfigureSystem;

/**
 * @author glenn renfro
 * 
 */

@Component
public class AWSInstanceConfigurer implements InstanceConfigurer {
	@Value("${xd-dist-url}")
	private String xdDistUrl;
	@Value("${redis-port}")
	private String redisPort;
	@Value("${rabbit-port}")
	private String rabbitPort;

	private static final String UBUNTU_HOME = "/home/ubuntu/";

	public String getSingleNodeStartupScript() {
		return renderStatement(startXDResourceStatement());
	}

	public String deploySingleNodeApplication(String hostName) {
		return renderStatement(deploySingleNodeXDStatement(hostName));
	}

	private String renderStatement(List<Statement> statements) {
		ScriptBuilder builder = new ScriptBuilder();
		for (Statement statement : statements) {
			builder.addStatement(statement);
		}
		Map<String, String> environmentVariables = new HashMap<String, String>();
		environmentVariables.put("XD_HOME", getInstalledDirectory());
		builder.addEnvironmentVariableScope("XD_HOME", environmentVariables);
		String script = builder.render(OsFamily.UNIX);
		return script;
	}

	private List<Statement> deploySingleNodeXDStatement(String hostName) {
		ArrayList<Statement> result = new ArrayList<Statement>();
		result.add(exec("wget -P " + UBUNTU_HOME + " " + xdDistUrl));
		result.add(exec("unzip " + UBUNTU_HOME + getFileName() + " -d "
				+ UBUNTU_HOME));
		result.add(exec(constructConfigurationCommand(hostName)));
		result.add(exec(getBinDirectory() + "xd-singlenode &"));
		return result;
	}

	private List<Statement> startXDResourceStatement() {
		ArrayList<Statement> result = new ArrayList<Statement>();
		result.add(exec("/etc/init.d/redis-server start"));
		result.add(exec("/etc/init.d/rabbitmq-server start"));
		return result;
	}

	private String getFileName() {
		File file = new File(xdDistUrl);
		return file.getName();
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
	private String getBinDirectory(){
		return getInstalledDirectory()+"/xd/bin/";
	}
	private String getConfigDirectory() {
		return getInstalledDirectory()+"/xd/config/";
		}
	private String constructConfigurationCommand(String hostName){
		String configCommand = String.format("java -cp /home/ubuntu/deploy.jar org.springframework.xd.ec2.environment.ConfigureSystem --%s=%s --%s=%s --%s=%s --%s=%s --%s=%s --%s=%s > /home/ubuntu/config.txt 2> /home/ubuntu/configError.txt",
		ConfigureSystem.RABBIT_PROPS_FILE, getConfigDirectory()+"rabbit.properties",
		ConfigureSystem.REDIS_PROPS_FILE, getConfigDirectory()+"redis.properties",
		ConfigureSystem.RABBIT_HOST, hostName,
		ConfigureSystem.RABBIT_PORT, rabbitPort,
		ConfigureSystem.REDIS_HOST, hostName,
		ConfigureSystem.REDIS_PORT, redisPort
		);
		return configCommand;
	}
}
