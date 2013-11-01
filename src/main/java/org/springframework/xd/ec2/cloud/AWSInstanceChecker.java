package org.springframework.xd.ec2.cloud;

import static org.jclouds.util.Predicates2.retry;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jclouds.aws.ec2.AWSEC2Client;
import org.jclouds.compute.ComputeService;
import org.jclouds.ec2.EC2Client;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.ec2.predicates.InstanceStateRunning;
import org.jclouds.predicates.SocketOpen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;

public class AWSInstanceChecker {

	static final Logger logger = LoggerFactory.getLogger(AWSDeployer.class);

	public  void checkServerInstance(RunningInstance instance,AWSEC2Client client, ComputeService computeService, int port)
			throws TimeoutException {
		instance = AWSInstanceProvisioner.findInstanceById(client, instance.getId());
		SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		logger.info(String.format("%d: %s awaiting XD server to start %n",
				System.currentTimeMillis(), instance.getIpAddress()));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				port)))
			throw new TimeoutException("timeout waiting for server to start: "
					+ instance.getIpAddress());

		logger.info(String.format("%d: %s server started%n",
				System.currentTimeMillis(), instance.getIpAddress()));

	}

	public  RunningInstance checkServerResources(RunningInstance instance,AWSEC2Client client, ComputeService computeService)
			throws TimeoutException {
		Predicate<RunningInstance> runningTester = retry(
				new InstanceStateRunning(client), 180, 10, TimeUnit.SECONDS);

		logger.info("*******Verifying EC2 Instance and Required XD Resources.*******");
		logger.info(String.format("%d: %s awaiting instance to run %n",
				System.currentTimeMillis(), instance.getId()));

		if (!runningTester.apply(instance))
			throw new TimeoutException("timeout waiting for instance to run: "
					+ instance.getId());

		instance = AWSInstanceProvisioner.findInstanceById(client, instance.getId());
		SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		logger.info(String.format("%d: %s awaiting ssh service to start %n",
				System.currentTimeMillis(), instance.getIpAddress()));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				22)))
			throw new TimeoutException("timeout waiting for ssh to start: "
					+ instance.getIpAddress());

		logger.info(String.format("%d: %s ssh service started%n",
				System.currentTimeMillis(), instance.getIpAddress()));

		logger.info(String.format("%d: %s awaiting Redis service to start%n",
				System.currentTimeMillis(), instance.getIpAddress()));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				6379)))
			throw new TimeoutException("timeout waiting for http to start: "
					+ instance.getIpAddress());

		logger.info(String.format("instance %s ready%n", instance.getId()));
		logger.info(String.format("ip address: %s%n", instance.getIpAddress()));
		logger.info(String.format("dns name: %s%n", instance.getDnsName()));
		return instance;
	}


}
