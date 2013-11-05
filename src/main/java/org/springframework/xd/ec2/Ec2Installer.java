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

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.xd.cloud.Deployer;
import org.springframework.xd.cloud.Deployment;
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
	final static Logger logger = LoggerFactory.getLogger(Ec2Installer.class);
	private final static String HIGHLIGHT = "************************************************************************";

	@Autowired
	private Banner banner;

	private Deployer deployer;
	public void install() throws Exception {
		try {
			deployer = new AWSDeployer();
			banner.print();
			List<Deployment> result = deployer.deploy();
			logger.info(HIGHLIGHT);
			for (Deployment instance : result) {
				if (instance.getType() == InstanceType.SINGLE_NODE) {
					logger.info(String.format(
							"Single Node Instance: %s has been created",
							instance.getAddress().getHostName()));
				}
				if (instance.getType() == InstanceType.ADMIN) {
					logger.info(String.format(
							"Admin Node Instance: %s has been created",
							instance.getAddress().getHostName()));
				}
				if (instance.getType() == InstanceType.NODE) {
					logger.info(String.format(
							"Container Node Instance: %s has been created",
							instance.getAddress().getHostName()));
				}
			}
			logger.info(HIGHLIGHT);
			logger.info("\n\nInstallation Complete");
		} catch (TimeoutException te) {
			logger.error("Installation FAILED");
			te.printStackTrace();
		} catch (InvalidXDZipUrlException zipException) {
			logger.error(zipException.getMessage());
			zipException.printStackTrace();
		} catch (IllegalArgumentException iae) {
			logger.info(HIGHLIGHT);
			logger.error("An IllegalArgumentException has been thrown with the following message: \n"
					+ iae.getMessage());
			logger.error("\nMake sure you updated the config/xd-ec2.properties");
			logger.info(HIGHLIGHT);
		}
	}
}
