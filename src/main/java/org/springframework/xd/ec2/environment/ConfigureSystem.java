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

package org.springframework.xd.ec2.environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.springframework.core.env.SimpleCommandLinePropertySource;

/**
 * This class is used to configure container nodes. Configuration includes
 * setting up the container to reference the XD Admin Server, Redis and rabbit
 * instances. This class is copied to the container at which time the setup
 * script will execute this class.
 * 
 * @author glenn renfro
 * 
 */
public class ConfigureSystem {
	public static String REDIS_PROPS_FILE = "redis.props.file";
	public static String RABBIT_PROPS_FILE = "rabbit.props.file";

	public static String REDIS_HOST = "redis.hostname";
	public static String REDIS_PORT = "redis.port";
	public static String RABBIT_HOST = "rabbit.hostname";
	public static String RABBIT_PORT = "rabbit.port";

	public static void main(String[] args) {
		SimpleCommandLinePropertySource propertySource = new SimpleCommandLinePropertySource(
				args);
		File redisPropertyFile = new File(getPropFileForRedis(propertySource));
		File rabbitPropertyFile = new File(getPropFileForRabbit(propertySource));

		Properties redisProperties = getPropertiesForRedis(propertySource);
		Properties rabbitProperties = getPropertiesForRabbit(propertySource);

		try {
			redisProperties.store(new FileOutputStream(redisPropertyFile),
					"Updated by EC2 Configuration Script");

			rabbitProperties.store(new FileOutputStream(rabbitPropertyFile),
					"Updated by EC2 Configuration Script");

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

	}

	private static String getPropFileForRedis(
			SimpleCommandLinePropertySource propertySource) {
		if (propertySource.containsProperty(REDIS_PROPS_FILE)) {
			throw new IllegalArgumentException(
					"Redis property file param not present.");
		}
		return propertySource.getProperty(REDIS_PROPS_FILE);
	}

	private static String getPropFileForRabbit(
			SimpleCommandLinePropertySource propertySource) {
		if (propertySource.containsProperty(RABBIT_PROPS_FILE)) {
			throw new IllegalArgumentException(
					"Rabbit property file param not present.");
		}
		return propertySource.getProperty(RABBIT_PROPS_FILE);
	}

	private static Properties getPropertiesForRedis(
			SimpleCommandLinePropertySource propertySource) {
		Properties props = new Properties();
		if (propertySource.containsProperty(REDIS_HOST)) {
			throw new IllegalArgumentException("Redis Host Not Present.");
		}
		if (propertySource.containsProperty(REDIS_PORT)) {
			throw new IllegalArgumentException("Redis Port Not Present.");
		}

		props.setProperty(REDIS_HOST, propertySource.getProperty(REDIS_HOST));
		props.setProperty(REDIS_PORT, propertySource.getProperty(REDIS_PORT));
		return props;
	}

	private static Properties getPropertiesForRabbit(
			SimpleCommandLinePropertySource propertySource) {
		Properties props = new Properties();
		if (propertySource.containsProperty(RABBIT_HOST)) {
			throw new IllegalArgumentException("Rabbit Host Not Present.");
		}
		if (propertySource.containsProperty(RABBIT_PORT)) {
			throw new IllegalArgumentException("Rabbit Port Not Present.");
		}

		props.setProperty(RABBIT_HOST, propertySource.getProperty(RABBIT_HOST));
		props.setProperty(RABBIT_PORT, propertySource.getProperty(RABBIT_PORT));
		return props;
	}

}
