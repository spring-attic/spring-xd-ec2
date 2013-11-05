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

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class TestAWSInstanceConfigurer {
	@Value("${xd-dist-url}")
	private String xdDistUrl;
	@Value("${redis-port}")
	private String redisPort;
	@Value("${rabbit-port}")
	private String rabbitPort;

	private static final String XD_HOME_VALUE = "export XD_HOME=\"/home/ubuntu/spring-xd-X.X.X.BUILD-SNAPSHOT\"";
	private static final String WGET_COMMAND = "wget -P /home/ubuntu/ http://repo.springsource.org/libs-snapshot-local/org/springframework/xd/spring-xd/X.X.X.BUILD-SNAPSHOT/spring-xd-1.0.0.XXXX-20131024.235055-1.zip";
	private static final String REDIS_INIT_VALUE = "/etc/init.d/redis-server start";
	private static final String RABBIT_INIT_VALUE = "/etc/init.d/rabbitmq-server start";
	private static final String UNZIP_COMMAND = "unzip /home/ubuntu/spring-xd-1.0.0.XXXX-20131024.235055-1.zip -d /home/ubuntu/";

	@Autowired
	AWSInstanceConfigurer configurer;


	/**
	 * This test verifies that the generated script will start redis and rabbit.
	 * This assumes that the redis and rabbit have been installed by wget which
	 * should have put the entries in the /etc/init.d directory on a ubuntu OS.
	 */
	@Test
	public void testGetSingleNodeStartupScript() {
		String result = configurer.createStartXDResourcesScript();
		assertTrue("Was not able to find where XD_HOME is set",
				result.indexOf(XD_HOME_VALUE) > -1);
		assertTrue("Was not able to find where Rabbit startup",
				result.indexOf(RABBIT_INIT_VALUE) > -1);
		assertTrue("Was not able to find where Redis startup",
				result.indexOf(REDIS_INIT_VALUE) > -1);
	}

	/**
	 * Checks to see that the script will pull the correct xd version. Checks to
	 * see if the script will run the commands to install XD. Checks to see if
	 * script will start a single node.
	 */
	@Test
	public void testDeploySingleNodeApplication() {
		System.out.println(xdDistUrl);
		String result = configurer.createSingleNodeScript("MYHOST");
		assertTrue("Was not able to find wget command ",result.indexOf(WGET_COMMAND)>-1);
		assertTrue("XD was not unzipped to the correct location ",result.indexOf(UNZIP_COMMAND)>-1);

		assertTrue("Redis port was not setup properly",result.indexOf("--redis.port=7878")>-1);
		assertTrue("Redis hostname was not setup properly",result.indexOf("--redis.hostname=MYHOST")>-1);

		assertTrue("Rabbit port was not setup properly",result.indexOf("--rabbit.port=9898")>-1);
		assertTrue("Rabbit hostname was not setup properly",result.indexOf("--rabbit.hostname=MYHOST")>-1);

		System.out.println(result);
	}

}
