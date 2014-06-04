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

import static org.jclouds.util.Predicates2.retry;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.jclouds.aws.ec2.AWSEC2Api;
import org.jclouds.compute.ComputeService;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.ec2.predicates.InstanceStateRunning;
import org.jclouds.predicates.SocketOpen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;
import org.springframework.xd.cloud.DeployTimeoutException;

import com.google.common.base.Predicate;
import com.google.common.net.HostAndPort;

/**
 * Verifies services are available.  
 */
public class AWSInstanceChecker {

	private static final Logger LOGGER = LoggerFactory.getLogger(AWSDeployer.class);

	private AWSEC2Api client;

	private ComputeService computeService;

	private int redisPort;

	private int rabbitPort;

	private int zookeeperPort;

	public AWSInstanceChecker(Properties properties, AWSEC2Api client,
			ComputeService computeService) {
		Assert.notNull(properties, "properties can not be null");
		Assert.notNull(client, "client can not be null");
		Assert.notNull(computeService, "computeService can not be null");
		this.client = client;
		this.computeService = computeService;
		redisPort = Integer.valueOf(properties.getProperty("spring.redis.port"));
		rabbitPort = Integer.valueOf(properties.getProperty("spring.rabbitmq.port"));
		zookeeperPort = Integer.valueOf(properties.getProperty("spring.zookeeper.port"));

	}

	/**
	 * Verfies that XD admin or single node is up and running
	 * @param instance The instance where the XD server is deployed
	 * @param port the port to monitor.
	 */
	public void checkServerInstance(RunningInstance instance, final int port) {
		Assert.notNull(instance, "instance can not be null");
		RunningInstance localInstance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		final SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		final Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		LOGGER.info(String.format("Awaiting XD server to start"));
		if (!socketTester.apply(HostAndPort.fromParts(localInstance.getIpAddress(),
				port))) {
			throw new DeployTimeoutException("timeout waiting for server to start: "
					+ localInstance.getIpAddress());
		}
		LOGGER.info(String.format("Server started%n"));

	}

	/**
	 * Verifies that the redis, rabbit and zookeeper are running.
	 * @param instanceParam The instance that the xd is deployed.
	 * @param isEmbeddedZookeeper if false it checks that zookeeper is up and running, if true it does not check.
	 * @return check
	 */
	public RunningInstance checkServerResources(RunningInstance instanceParam, boolean isEmbeddedZookeeper)
	{
		Assert.notNull(instanceParam, "instanceParam can not be null");
		RunningInstance instance = checkAWSInstance(instanceParam);
		LOGGER.info("*******Verifying Required XD Resources.*******");

		SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		LOGGER.info(String.format("Awaiting Redis service to start"));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				redisPort)))
			throw new DeployTimeoutException("timeout waiting for Redis to start: "
					+ instance.getIpAddress());
		LOGGER.info(String.format("Redis service started"));

		LOGGER.info(String.format("Awaiting Rabbit service to start"));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				rabbitPort)))
			throw new DeployTimeoutException("timeout waiting for Rabbit to start: "
					+ instance.getIpAddress());
		LOGGER.info(String.format("Rabbit service started"));
		if (!isEmbeddedZookeeper) {
			LOGGER.info(String.format("Awaiting ZooKeeper service to start"));
			if (!socketTester.apply(HostAndPort.fromParts(
					instance.getIpAddress(), zookeeperPort)))
				throw new DeployTimeoutException(
						"timeout waiting for zookeeper to start: "
								+ instance.getIpAddress());
			LOGGER.info(String.format("Zoo Keeper service started%n"));
		}
		LOGGER.info("*******EC2 Instance and required XD Resources have started.*******");

		LOGGER.info(String.format("instance %s ready", instance.getId()));
		LOGGER.info(String.format("ip address: %s", instance.getIpAddress()));
		LOGGER.info(String.format("dns name: %s%n", instance.getDnsName()));
		return instance;
	}

	/**
	 * Verfies that the EC2 instance is running.  Also verfies ssh service is running
	 * @param instanceParam The instance to be monitored.
	 * @return  RunningInstance object.
	 */
	public RunningInstance checkAWSInstance(RunningInstance instanceParam) {
		Assert.notNull(instanceParam, "instanceParam can not be null");
		Predicate<RunningInstance> runningTester = retry(
				new InstanceStateRunning(client), 180, 1, TimeUnit.SECONDS);

		LOGGER.info("*******Verifying EC2 Instance*******");
		LOGGER.info(String.format("Awaiting instance to run"));

		if (!runningTester.apply(instanceParam))
			throw new DeployTimeoutException("timeout waiting for instance to run: "
					+ instanceParam.getId());

		RunningInstance instance = AWSInstanceProvisioner.findInstanceById(client,
				instanceParam.getId());
		SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		LOGGER.info(String.format("Awaiting ssh service to start"));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				22)))
			throw new DeployTimeoutException("timeout waiting for ssh to start: "
					+ instance.getIpAddress());

		LOGGER.info(String.format("ssh service started%n"));
		return instance;
	}

	/**
	 * In cases where the service does not have any exposed ports we can check
	 * to see if it started. We do have JMX capabilities, but they are not
	 * active at this time.
	 * 
	 * @param instance
	 *            The running instance you want to examine.
	 * @param managment port 
	 *            the jmx port .
	 * @return
	 */
	public boolean checkContainerProcess(RunningInstance instance, int managementPort) {
		Assert.notNull(instance, "instance can not be null");
		boolean result = true;
		RunningInstance localInstance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		final SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		final Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		LOGGER.info(String.format("Awaiting XD container to start %n"));
		if (!socketTester.apply(HostAndPort.fromParts(localInstance.getIpAddress(),
				managementPort))) {
			result = false;
			LOGGER.warn("timeout waiting for container to start: "
					+ localInstance.getIpAddress());
			return result;
		}
		LOGGER.info(String.format("Container started%n"));
		return result;
	}
}
