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
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.xd.cloud.Deployer;
import org.springframework.xd.cloud.XDInstanceType;
import org.springframework.xd.ec2.cloud.AWSDeployer;

/**
 * @author glenn renfro
 * 
 */
public class Ec2Installer {
	/**
	 * @param args
	 */
	final static Logger logger = LoggerFactory.getLogger(Ec2Installer.class);
	private final static String HIGHLIGHT = "************************************************************************";

	public static void main(String[] args) throws Exception {
		ClassPathXmlApplicationContext context = null;
		try {
			context = new ClassPathXmlApplicationContext(
					"META-INF/xdinstaller-context.xml");

			context.refresh();
			EC2Util util = context.getBean(EC2Util.class);
			util.printBanner();
			Deployer deployer = context.getBean(AWSDeployer.class);
			List<XDInstanceType> result = deployer.deploy();
			logger.info(HIGHLIGHT);
			for (XDInstanceType instance : result) {
				if (instance.getType() == XDInstanceType.InstanceType.SINGLE_NODE) {
					logger.info(String.format(
							"Single Node Instance: %s has been created",
							instance.getDns()));
				}
				if (instance.getType() == XDInstanceType.InstanceType.ADMIN) {
					logger.info(String.format(
							"Admin Node Instance: %s has been created",
							instance.getDns()));
				}
				if (instance.getType() == XDInstanceType.InstanceType.NODE) {
					logger.info(String.format(
							"Container Node Instance: %s has been created",
							instance.getDns()));
				}
			}
			logger.info(HIGHLIGHT);
			logger.info("\n\nInstallation Complete");
		} catch (TimeoutException te) {
			logger.error("Installation FAILED");
			te.printStackTrace();
		} catch (IllegalArgumentException iae) {
			logger.info(HIGHLIGHT);
			logger.error("An IllegalArgumentException has been thrown with the following message: "
					+ iae.getMessage());
			logger.error("Make sure you updated the config/xd-ec2.properties to include the aws-access-key, aws-secret-key and private-key-file");
			logger.info(HIGHLIGHT);
		} finally {
			if (context != null) {
				context.close();
			}
		}
	}
}
