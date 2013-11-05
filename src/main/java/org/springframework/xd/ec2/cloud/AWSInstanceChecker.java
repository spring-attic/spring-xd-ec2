package org.springframework.xd.ec2.cloud;

import static org.jclouds.util.Predicates2.retry;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jclouds.aws.ec2.AWSEC2Client;
import org.jclouds.compute.ComputeService;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.ec2.predicates.InstanceStateRunning;
import org.jclouds.predicates.SocketOpen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.net.HostAndPort;

public class AWSInstanceChecker {

	static final Logger logger = LoggerFactory.getLogger(AWSDeployer.class);
	private AWSEC2Client client;
	private ComputeService computeService;
	private int redisPort;
	private int rabbitPort;
	
	public AWSInstanceChecker(Properties properties, AWSEC2Client client, ComputeService computeService) {
		this.client = client;
		this.computeService = computeService;
		redisPort = Integer.valueOf(properties.getProperty("redis-port"));
		rabbitPort = Integer.valueOf(properties.getProperty("rabbit-port"));
		
	}

	public void checkServerInstance(RunningInstance instance, int port)
			throws TimeoutException {
		instance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		logger.info(String.format("Awaiting XD server to start %n"));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				port)))
			throw new TimeoutException("timeout waiting for server to start: "
					+ instance.getIpAddress());

		logger.info(String.format("Server started%n"));

	}

	public RunningInstance checkServerResources(RunningInstance instance)
			throws TimeoutException {
		Predicate<RunningInstance> runningTester = retry(
				new InstanceStateRunning(client), 180, 10, TimeUnit.SECONDS);

		logger.info("*******Verifying EC2 Instance and Required XD Resources.*******");
		logger.info(String.format("Awaiting instance to run %n"));

		if (!runningTester.apply(instance))
			throw new TimeoutException("timeout waiting for instance to run: "
					+ instance.getId());

		instance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		logger.info(String.format("Awaiting ssh service to start %n"));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				22)))
			throw new TimeoutException("timeout waiting for ssh to start: "
					+ instance.getIpAddress());

		logger.info(String.format("ssh service started%n"));

		logger.info(String.format("Awaiting Redis service to start%n"));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				redisPort)))
			throw new TimeoutException("timeout waiting for Redis to start: "
					+ instance.getIpAddress());
		logger.info(String.format("Redis service started%n"));
		
		logger.info(String.format("Awaiting Rabbit service to start%n"));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				rabbitPort)))
			throw new TimeoutException("timeout waiting for Rabbit to start: "
					+ instance.getIpAddress());
		logger.info(String.format("Rabbit service started%n"));

		logger.info("*******EC2 Instance and required XD Resources have started.*******\n");

		logger.info(String.format("instance %s ready%n", instance.getId()));
		logger.info(String.format("ip address: %s%n", instance.getIpAddress()));
		logger.info(String.format("dns name: %s%n", instance.getDnsName()));
		return instance;
	}

}
