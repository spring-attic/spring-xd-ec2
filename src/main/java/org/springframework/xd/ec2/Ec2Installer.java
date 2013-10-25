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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.xd.ec2.cloud.AWSDeployer;
import org.springframework.xd.ec2.cloud.Deployer;

/**
 * @author glenn renfro
 * 
 */
public class Ec2Installer {
	/**
	 * @param args
	 */
	final static Logger logger = LoggerFactory.getLogger(Ec2Installer.class);

	public static void main(String[] args) throws Exception {
		ClassPathXmlApplicationContext context = null;
		try {
			context = new ClassPathXmlApplicationContext(
					"META-INF/xdinstaller-context.xml");

			context.refresh();
			EC2Util util = context.getBean(EC2Util.class);
			util.printBanner();
			Deployer deployer = context.getBean(AWSDeployer.class);
			String result = deployer.deploy();
			logger.info("Installation Complete");
		} finally {
			if (context != null) {
				context.close();
			}
		}
	}
}
