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

package org.springframework.xd.ec2;

import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.xd.ec2.cloud.AWSTools;

/**
 * Allows user to execute cleanup or preparation on AWS services that support XD clusters deployed on EC2.  
 * 
 * @author glenn renfro
 * 
 */
@Component
public class Ec2Maintenance {

	/**
	 * @param args
	 */
	private final static Logger LOGGER = LoggerFactory
			.getLogger(Ec2Maintenance.class);

	public final static String HIGHLIGHT = "************************************************************************";

	private static final String AWS_ACCESS_KEY = "aws-access-key";

	private static final String AWS_SECRET_KEY = "aws-secret-key";

	private static final String PRIVATE_KEY_FILE = "private-key-file";

	@Autowired
	private Banner banner;

	public static final String[] REQUIRED_ENTRIES = { "cluster-name",
		"aws-access-key", "aws-secret-key", "private-key-file",
		"user_name", "region", "machine-size", "security-group",
		"public-key-name", "ami", "multi-node" };

	/**
	 * Terminates an XD instance(s) with a specific name.  
	 * This uses the "name" tag to determine the instance name.
	 * @param name  The name of the instance to shutdown.  
	 */
	public void shutdown(String name) {
		Assert.hasText(name, "name must not be empty nor null");
		banner.print("maintenance.txt");
		AWSTools tools = new AWSTools(getProperties());
		tools.shutdown(name);
	}

	private Properties getProperties() {
		Resource resource = new ClassPathResource("xd-ec2.properties");
		Properties props = null;
		try {
			props = PropertiesLoaderUtils.loadProperties(resource);
			props.setProperty(AWS_ACCESS_KEY, getAWSProperty(props, AWS_ACCESS_KEY));
			props.setProperty(AWS_SECRET_KEY, getAWSProperty(props, AWS_SECRET_KEY));
			props.setProperty(PRIVATE_KEY_FILE, getAWSProperty(props, PRIVATE_KEY_FILE));


		}
		catch (IOException ioe) {
			LOGGER.error("Failed to open xd-ec2.properties file because: "
					+ ioe.getMessage());
			throw new IllegalStateException(ioe.getMessage(), ioe);
		}
		return props;
	}

	private String getAWSProperty(Properties props, String propKey) {
		if (props.containsKey(propKey) && !props.getProperty(propKey).equals("")) {
			return props.getProperty(propKey);
		}
		Properties systemProperties = System.getProperties();

		if (systemProperties.containsKey(propKey) && !systemProperties.getProperty(propKey).equals("")) {
			return systemProperties.getProperty(propKey);
		}
		return "";
	}

}
