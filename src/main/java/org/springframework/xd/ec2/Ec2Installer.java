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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.xd.cloud.Deployer;
import org.springframework.xd.cloud.Deployment;
import org.springframework.xd.cloud.InstanceSize;
import org.springframework.xd.cloud.InstanceType;
import org.springframework.xd.cloud.InvalidXDZipUrlException;
import org.springframework.xd.ec2.cloud.AWSDeployer;

/**
 * The component that kicks off the installation process.
 * 
 * @author glenn renfro
 * 
 */
@Component
public class Ec2Installer {
	/**
	 * @param args
	 */
	private final static Logger LOGGER = LoggerFactory
			.getLogger(Ec2Installer.class);
	private final static String HIGHLIGHT = "************************************************************************";

	@Autowired
	private transient Banner banner;

	private transient Deployer deployer;
	private static final String[] REQUIRED_ENTRIES = { "cluster-name",
			"aws-access-key", "aws-secret-key", "private-key-file",
			"user_name", "region", "machine-size", "security-group",
			"public-key-name", "ami", "multi-node" };

	public void install() throws TimeoutException, IOException {
		try {
			banner.print();
			final Properties properties = getProperties();
			validateConfiguration(properties);
			deployer = new AWSDeployer(properties);
			final List<Deployment> result = deployer.deploy();
			LOGGER.info(HIGHLIGHT);
			for (final Deployment instance : result) {
				if (instance.getType() == InstanceType.SINGLE_NODE) {
					LOGGER.info(String.format(
							"Single Node Instance: %s has been created",
							instance.getAddress().getHostName()));
				}
				if (instance.getType() == InstanceType.ADMIN) {
					LOGGER.info(String.format(
							"Admin Node Instance: %s has been created",
							instance.getAddress().getHostName()));
				}
				if (instance.getType() == InstanceType.NODE) {
					LOGGER.info(String.format(
							"Container Node Instance: %s has been created",
							instance.getAddress().getHostName()));
				}
			}
			LOGGER.info(HIGHLIGHT);
			LOGGER.info("\n\nInstallation Complete");
		} catch (TimeoutException te) {
			LOGGER.error("Installation FAILED");
			te.printStackTrace();
		} catch (InvalidXDZipUrlException zipException) {
			LOGGER.error(zipException.getMessage());
			zipException.printStackTrace();
		} catch (IllegalArgumentException iae) {
			LOGGER.info(HIGHLIGHT);
			LOGGER.error("An IllegalArgumentException has been thrown with the following message: \n"
					+ iae.getMessage());
			LOGGER.error("\nMake sure you updated the config/xd-ec2.properties");
			LOGGER.info(HIGHLIGHT);
			LOGGER.info(iae.getMessage());
		}
	}

	private Properties getProperties() throws IOException {
		Resource resource = new ClassPathResource("xd-ec2.properties");
		Properties props = null;
		try {
			props = PropertiesLoaderUtils.loadProperties(resource);
		} catch (IOException ioe) {
			LOGGER.error("Failed to open xd-ec2.properties file because: "
					+ ioe.getMessage());
			throw ioe;
		}
		return props;
	}

	/**
	 * Verifies that all properties that the application needs are setup
	 * properly.
	 */
	private Properties validateConfiguration(Properties props) {
		ArrayList<String> errorList = new ArrayList<String>();
		String value;
		for (int i = 0; i < REQUIRED_ENTRIES.length; i++) {
			value = props.getProperty(REQUIRED_ENTRIES[i]);
			if (value == null || value.length() == 0) {
				errorList.add(REQUIRED_ENTRIES[i]);
			}
		}
		if (errorList.size() > 0) {
			String errorMessage = "The following entries are not configured in your xd-ec2.properties:\n";
			errorMessage += errorList.get(0);
			for (int i = 1; i < errorList.size(); i++) {
				errorMessage += ",\n" + errorList.get(i);
			}
			throw new IllegalArgumentException(errorMessage);
		}

		if (!verifyMachineSize(props.getProperty("machine-size"))) {
			throw new IllegalArgumentException(
					"Invalid machine size specified.  Valid values are small, medium, large");
		}
		if (Boolean.parseBoolean(props.getProperty("multi-node"))) {
			try {
				Integer.getInteger(props.getProperty("number-nodes"));
			} catch (Exception e) {
				throw new IllegalArgumentException(
						"Invalid number-nodes value.  Valid values are integers greater than zero");
			}
		}
		return props;
	}

	private boolean verifyMachineSize(String machineSize) {
		boolean verified = false;
		if (machineSize.equalsIgnoreCase(InstanceSize.SMALL.name())) {
			verified = true;
		} else if (machineSize.equalsIgnoreCase(InstanceSize.MEDIUM.name())) {
			verified = true;
		} else if (machineSize.equalsIgnoreCase(InstanceSize.LARGE.name())) {
			verified = true;
		}
		return verified;
	}

}
