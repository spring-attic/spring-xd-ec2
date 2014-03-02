package org.springframework.xd.ec2.cloud;

import static org.jclouds.util.Predicates2.retry;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jclouds.aws.ec2.AWSEC2Api;
import org.jclouds.compute.ComputeService;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.ec2.predicates.InstanceStateRunning;
import org.jclouds.predicates.SocketOpen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.net.HostAndPort;

public class AWSInstanceChecker {

	private static final Logger LOGGER = LoggerFactory.getLogger(AWSDeployer.class);
	private AWSEC2Api client;
	private ComputeService computeService;
	private int redisPort;
	private int rabbitPort;

	public AWSInstanceChecker(Properties properties, AWSEC2Api client,
			ComputeService computeService) {
		this.client = client;
		this.computeService = computeService;
		redisPort = Integer.valueOf(properties.getProperty("spring.redis.port"));
		rabbitPort = Integer.valueOf(properties.getProperty("spring.rabbitmq.port"));

	}

	public void checkServerInstance(RunningInstance instance, final int port)
			throws TimeoutException {
		RunningInstance localInstance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		final SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		final Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		LOGGER.info(String.format("Awaiting XD server to start"));
		if (!socketTester.apply(HostAndPort.fromParts(localInstance.getIpAddress(),
				port))){
			throw new TimeoutException("timeout waiting for server to start: "
					+ localInstance.getIpAddress());
		}
		LOGGER.info(String.format("Server started%n"));

	}

	public RunningInstance checkServerResources(RunningInstance instanceParam)
			throws TimeoutException {
		RunningInstance instance = checkAWSInstance(instanceParam);
		LOGGER.info("*******Verifying Required XD Resources.*******");

		SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		LOGGER.info(String.format("Awaiting Redis service to start"));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				redisPort)))
			throw new TimeoutException("timeout waiting for Redis to start: "
					+ instance.getIpAddress());
		LOGGER.info(String.format("Redis service started"));

		LOGGER.info(String.format("Awaiting Rabbit service to start"));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				rabbitPort)))
			throw new TimeoutException("timeout waiting for Rabbit to start: "
					+ instance.getIpAddress());
		LOGGER.info(String.format("Rabbit service started%n"));

		LOGGER.info("*******EC2 Instance and required XD Resources have started.*******");

		LOGGER.info(String.format("instance %s ready", instance.getId()));
		LOGGER.info(String.format("ip address: %s", instance.getIpAddress()));
		LOGGER.info(String.format("dns name: %s%n", instance.getDnsName()));
		return instance;
	}

	public RunningInstance checkAWSInstance(RunningInstance instanceParam)
			throws TimeoutException {
		Predicate<RunningInstance> runningTester = retry(
				new InstanceStateRunning(client), 180, 1, TimeUnit.SECONDS);

		LOGGER.info("*******Verifying EC2 Instance*******");
		LOGGER.info(String.format("Awaiting instance to run"));

		if (!runningTester.apply(instanceParam))
			throw new TimeoutException("timeout waiting for instance to run: "
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
			throw new TimeoutException("timeout waiting for ssh to start: "
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
		boolean result = true;
		RunningInstance localInstance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		final SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		final Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		LOGGER.info(String.format("Awaiting XD container to start %n"));
		if (!socketTester.apply(HostAndPort.fromParts(localInstance.getIpAddress(),
				managementPort))){
			result = false;
			LOGGER.warn("timeout waiting for container to start: "
					+ localInstance.getIpAddress());
			return result;
		}
		LOGGER.info(String.format("Container started%n"));
		return result;
	}
}
