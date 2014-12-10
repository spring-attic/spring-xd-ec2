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

import com.google.common.base.Predicate;
import com.google.common.net.HostAndPort;
import org.jclouds.aws.ec2.AWSEC2Api;
import org.jclouds.compute.ComputeService;
import org.jclouds.ec2.domain.InstanceState;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.ec2.predicates.InstanceStateRunning;
import org.jclouds.predicates.SocketOpen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.xd.cloud.DeployTimeoutException;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.jclouds.util.Predicates2.retry;

/**
 * Verifies services are available.  
 */
public class AWSInstanceChecker {

	private static final String REDIS_ADDRESS = "spring.redis.address";
	private static final String RABBIT_ADDRESSES = "spring.rabbitmq.addresses";
	private static final String ZOOKEEPER_ADDRESSES = "spring.zookeeper.addresses";
	private static final String KAFKA_BROKER_ADDRESSES = "xd.messagebus.kafka.brokers";
	private static final String KAFKA_ZK_ADDRESSES = "xd.messagebus.kafka.zkAddress";


	private static final Logger LOGGER = LoggerFactory.getLogger(AWSDeployer.class);

	private AWSEC2Api client;

	private ComputeService computeService;

	private Properties properties;

	public AWSInstanceChecker(Properties properties, AWSEC2Api client,
			ComputeService computeService) {
		Assert.notNull(properties, "properties can not be null");
		Assert.notNull(client, "client can not be null");
		Assert.notNull(computeService, "computeService can not be null");
		this.client = client;
		this.computeService = computeService;
		this.properties = properties;

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
	public RunningInstance checkServerResources(RunningInstance instanceParam, boolean isEmbeddedZookeeper) {
		Assert.notNull(instanceParam, "instanceParam can not be null");
		RunningInstance instance = checkAWSInstance(instanceParam);
		LOGGER.info("*******Verifying Required XD Resources.*******");

		SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);

		LOGGER.info(String.format("Awaiting Redis service to start at " + properties.getProperty(REDIS_ADDRESS)));
		if (!verifyResourceAddress(properties.getProperty(REDIS_ADDRESS))) {
			throw new DeployTimeoutException("timeout waiting for Redis to start: "
					+ properties.getProperty(REDIS_ADDRESS));
		}
		LOGGER.info(String.format("Redis service started"));

		LOGGER.info(String.format("Awaiting Rabbit service to start at " + properties.getProperty(RABBIT_ADDRESSES)));
		if (!verifyResourceAddress(properties.getProperty(RABBIT_ADDRESSES))) {
			throw new DeployTimeoutException("timeout waiting for Rabbit to start: "
					+ properties.getProperty(RABBIT_ADDRESSES));
		}
		LOGGER.info(String.format("Rabbit service started"));

		if (!isEmbeddedZookeeper) {
			LOGGER.info(String.format("Awaiting ZooKeeper service to start at " + properties.getProperty(ZOOKEEPER_ADDRESSES)));
			if (!verifyResourceAddress(properties.getProperty(ZOOKEEPER_ADDRESSES))) {
				throw new DeployTimeoutException("timeout waiting for zookeeper to start: "
						+ properties.getProperty(ZOOKEEPER_ADDRESSES));
			}
			LOGGER.info(String.format("Zoo Keeper service started%n"));
		}

		LOGGER.info(String.format("Awaiting Kafka Zookeeper service to start at " + properties.getProperty(KAFKA_ZK_ADDRESSES)));
		if (!verifyResourceAddress(properties.getProperty(KAFKA_ZK_ADDRESSES))) {
			throw new DeployTimeoutException("timeout waiting for kafka Zookeeper to start: "
					+ properties.getProperty(KAFKA_ZK_ADDRESSES));
		}
		LOGGER.info(String.format("Kafka ZK service started%n"));

		LOGGER.info(String.format("Awaiting Kafka Broker service to start at " + properties.getProperty(KAFKA_BROKER_ADDRESSES)));
		if (!verifyResourceAddress(properties.getProperty(KAFKA_BROKER_ADDRESSES))) {
			throw new DeployTimeoutException("timeout waiting for kafka broker to start: "
					+ properties.getProperty(KAFKA_BROKER_ADDRESSES));
		}
		LOGGER.info(String.format("Kafka Broker service started%n"));

		LOGGER.info("*******EC2 Instance and required XD Resources have started.*******");

		LOGGER.info(String.format("instance %s ready", instance.getId()));
		LOGGER.info(String.format("ip address: %s", instance.getIpAddress()));
		LOGGER.info(String.format("dns name: %s%n", instance.getDnsName()));
		return instance;
	}

	private boolean verifyResourceAddress(String addresses){
		SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		Predicate<HostAndPort> socketTester = retry(socketOpen, 180, 1,
				TimeUnit.SECONDS);
		boolean result = false;
		String[] addressList = StringUtils.commaDelimitedListToStringArray(addresses);
		for(String address:addressList){
			String[] tokens = StringUtils.delimitedListToStringArray(address,":");
			if (socketTester.apply(HostAndPort.fromParts(tokens[0],
					Integer.valueOf(tokens[1])))){
				result = true;
				break;
			}

		}
		return result;
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
	 * @param managementPort
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

	/**
	 * Awaits for a AWS Instance to be provisioned or until the wait time expires.
	 * @param instance The aws instance to monitor
	 * @param waitTime The max time in millis to wait
	 * @return true if the instance was provisioned, false if it was not.
	 */
	public boolean waitForInstanceToBeProvisioned(RunningInstance instance, long waitTime) {
		boolean result = instance.getInstanceState().equals(InstanceState.RUNNING);
		long timeout = System.currentTimeMillis() + waitTime;
		while (!result && System.currentTimeMillis() < timeout) {
			try {
				Thread.sleep(1000);
				result = instance.getInstanceState().equals(InstanceState.RUNNING);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e.getMessage(), e);
			}
		}
		return result;
	}

	public Properties getProperties() {
		return properties;
	}

	public void setProperties(Properties properties) {
		this.properties = properties;
	}

}
