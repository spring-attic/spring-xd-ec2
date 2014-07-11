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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_PORT_OPEN;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE;
import static org.springframework.xd.ec2.Ec2Installer.HIGHLIGHT;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jclouds.ContextBuilder;
import org.jclouds.aws.ec2.AWSEC2Api;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.http.handlers.BackoffLimitedRetryHandler;
import org.jclouds.io.payloads.FilePayload;
import org.jclouds.sshj.SshjSshClient;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StopWatch;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.xd.cloud.DeployTimeoutException;
import org.springframework.xd.cloud.Deployer;
import org.springframework.xd.cloud.Deployment;
import org.springframework.xd.cloud.DeploymentStatus;
import org.springframework.xd.cloud.InstanceType;
import org.springframework.xd.cloud.InvalidXDZipUrlException;
import org.springframework.xd.cloud.ServerFailStartException;
import org.springframework.xd.ec2.Main;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Module;

/**
 * Provisions EC2 Instances, installs required software for a XDCluster as specified by configuration.
 * @author glenn renfro 
 */

public class AWSDeployer implements Deployer {

	static final Logger LOGGER = LoggerFactory.getLogger(AWSDeployer.class);

	private static final String UBUNTU_HOME = "/home/ubuntu/";

	private String clusterName;

	private String awsAccessKey;

	private String awsSecretKey;

	private String privateKeyFile;

	private String multiNode;

	private String description;

	private String userName;

	private String region;

	private String numberOfInstances;

	private int managementPort;

	private AWSEC2Api client;

	private ComputeService computeService;

	private AWSInstanceChecker instanceChecker;

	private AWSInstanceConfigurer configurer;

	private AWSInstanceProvisioner instanceProvisioner;

	private String hadoopVersion;

	private long instanceProvisionWaitTime;

	final int RETRY_COUNT = 3;

	/**
	 * Initializes the state of the an instance of AWSDeployer.
	 * @param properties The environment variables that declare how the XD-Cluster should be provisioned.
	 */
	public AWSDeployer(Properties properties) {
		Assert.notNull(properties, "properties can not be null");
		Iterable<Module> modules = ImmutableSet
				.<Module> of(new SshjSshClientModule());
		clusterName = properties.getProperty("cluster-name");
		awsAccessKey = properties.getProperty("aws-access-key");
		awsSecretKey = properties.getProperty("aws-secret-key");
		privateKeyFile = properties.getProperty("private-key-file");
		multiNode = properties.getProperty("multi-node");
		description = properties.getProperty("description");
		userName = properties.getProperty("user-name");
		region = properties.getProperty("region");
		numberOfInstances = properties.getProperty("number-nodes");
		hadoopVersion = properties.getProperty("XD_HADOOP_DISTRO");
		managementPort = Integer.parseInt(properties.getProperty("management.port"));
		instanceProvisionWaitTime = Long.valueOf(properties.getProperty("instance-provision-wait-time"));


		ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				// key I created above
				.modules(modules).overrides(getTimeoutPolicy())
				.buildView(ComputeServiceContext.class);
		computeService = context.getComputeService();

		client = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				.buildApi(AWSEC2Api.class);
		instanceChecker = new AWSInstanceChecker(properties, client,
				computeService);
		instanceProvisioner = new AWSInstanceProvisioner(client, properties);
		configurer = new AWSInstanceConfigurer(properties);
		if (multiNode.equalsIgnoreCase("true")) {
			configurer.setUseEmbeddedZookeeper(false);
		}
		validateURLs(properties);
	}

	/**
	 * Deploys the XD Cluster as specified by the user.
	 */
	@Override
	public List<Deployment> deploy() {

		ArrayList<Deployment> result = new ArrayList<Deployment>();
		String script = null;
		if (multiNode.equalsIgnoreCase("false")) {
			result.add(deploySingleNode(script));
		}
		else if (multiNode.equalsIgnoreCase("true")) {
			Deployment admin = deployAdminServer(script);
			result.add(admin);
			result.addAll(deployContainerServers(admin.getAddress()
					.getHostAddress()));
		}
		else {
			throw new IllegalArgumentException(
					"multi-node property must either be true or false");
		}

		return result;
	}

	/**
	 * Deploys a single node instance of XD. 
	 * @param script - The script built by JClouds Script Builder that initializes the Node
	 * @return The instance information for a successfully created XD-Node
	 */
	private Deployment deploySingleNode(String script) {
		LOGGER.info("Deploying SingleNode");
		RunningInstance instance = Iterables.getOnlyElement(instanceProvisioner
				.runInstance(configurer.createStartXDResourcesScript(), 1));
		if (instanceChecker.waitForInstanceToBeProvisioned(instance, instanceProvisionWaitTime)) {
			throw new ServerFailStartException("Instance " + instance.getId()
					+ " did not get into a running state before timeout of " + instanceProvisionWaitTime);
		}
		tagInitialization(instance, InstanceType.SINGLE_NODE);
		instanceChecker.checkServerResources(instance, configurer.isUseEmbeddedZookeeper());
		LOGGER.info("*******Setting up your single XD instance.*******");
		instance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		return deploySingleServer(
				configurer.createSingleNodeScript(instance.getDnsName(), hadoopVersion),
				instance, InstanceType.SINGLE_NODE);
	}

	/**
	 * Deploys a Admin instance of XD.
	 * @param script - The script built by JClouds Script Builder that initializes the Admin Server
	 * @return The instance information for a successfully created Admin Server
	 */
	private Deployment deployAdminServer(String script) {
		LOGGER.info("\n\n" + HIGHLIGHT);
		LOGGER.info("*Deploying Admin Node");
		LOGGER.info(HIGHLIGHT);
		RunningInstance instance = Iterables.getOnlyElement(instanceProvisioner
				.runInstance(configurer.createStartXDResourcesScript(), 1));
		if (instanceChecker.waitForInstanceToBeProvisioned(instance, instanceProvisionWaitTime)) {
			throw new ServerFailStartException("Instance " + instance.getId()
					+ " did not get into a running state before timeout of " + instanceProvisionWaitTime);
		}
		tagInitialization(instance, InstanceType.ADMIN);
		instanceChecker.checkServerResources(instance, false);
		LOGGER.info("*******Setting up your Administrator XD instance.*******");
		instance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		return deploySingleServer(configurer.createAdminNodeScript(
				instance.getDnsName()), instance,
				InstanceType.ADMIN);
	}

	/**
	 * Deploys a Single Node instance of XD.
	 * @param script - The script built by JClouds Script Builder that initializes the single node Server
	 * @param instance the ec2 instance to apply the commands
	 * @param type the type of xd instance
	 * @return The instance information for a successfully created single node Server
	 */
	private Deployment deploySingleServer(String script, RunningInstance instance, InstanceType type) {
		LOGGER.info(">>>Copying Configurator to Instance");
		sshCopy(this.getLibraryJarLocation(), instance.getDnsName());
		LOGGER.info(">>>Setting up and Starting XD");
		try {
			Thread.sleep(1000);
		}
		catch (InterruptedException e) {
			LOGGER.error(e.getMessage(), e);
		}
		setupServer(script, instance, type);
		Deployment result = null;
		try {
			InetAddress address = InetAddress.getByName(instance.getDnsName());
			result = new Deployment(address, type, DeploymentStatus.SUCCESS);
		}
		catch (UnknownHostException ex) {
			LOGGER.error(ex.getMessage());
		}
		return result;
	}

	/**
	 * Executes the commands to setup a single XD server instance of XD. 
	 * @param script The commands to execute.
	 * @param instance The ec2 instance to apply the commands 
	 * @param type Declares the type of xd instance.  
	 */
	private void setupServer(String script, RunningInstance instance,
			InstanceType type) {
		boolean success = false;
		for (int retries = 0; retries < RETRY_COUNT && !success; retries++) {
			runCommands(script, instance.getId());
			tagInstance(instance, type);
			try {
				instanceChecker.checkServerInstance(instance, 9393);
				success = true;
			}
			catch (DeployTimeoutException te) {
				LOGGER.warn("TIMEOUT while trying to setup server.  Retry "
						+ retries + " of " + RETRY_COUNT);
			}
		}
		if (!success) {
			throw new ServerFailStartException("Failed to execute commands on ec2 server after " + RETRY_COUNT
					+ " attempts.");
		}
	}

	/**
	 * Sets up a container instance.
	 * @param script The script to install container on the ec2 instance.
	 * @param instance The ec2 instance where the container will be installed.
	 * @param type The type of server deployed.
	 * @return Deployment object containing the status of the install.
	 */
	private Deployment installContainerServer(String script,
			RunningInstance instance, InstanceType type) {
		sshCopy(this.getLibraryJarLocation(), instance.getDnsName());
		boolean isInitialized = false;
		for (int retries = 0; retries < RETRY_COUNT && !isInitialized; retries++) {
			boolean commandsHaveRun = false;
			try {
				runCommands(script, instance.getId());
				commandsHaveRun = true;
			}
			catch (Exception ssre) {
				LOGGER.warn(ssre.getLocalizedMessage());
				commandsHaveRun = false;
			}
			tagInstance(instance, type);
			try {
				if (commandsHaveRun
						&& instanceChecker.checkContainerProcess(instance,
								managementPort)) {
					isInitialized = true;
				}
				else {
					LOGGER.warn("Failure to setup container.  Retry " + retries
							+ " of " + RETRY_COUNT);
				}
			}
			catch (DeployTimeoutException te) {
				LOGGER.warn("Failure to setup container because of timeout.  Retry " + retries
						+ " of " + RETRY_COUNT);
			}
		}
		if (isInitialized) {
			LOGGER.info("Container " + instance.getId() + " started\n");
		}
		else {
			LOGGER.info("Container " + instance.getId() + " did not start\n");
		}

		Deployment result = null;
		try {
			InetAddress address = InetAddress.getByName(instance.getDnsName());
			if (isInitialized) {
				result = new Deployment(address, type, DeploymentStatus.SUCCESS);
			}
			else {
				result = new Deployment(address, type, DeploymentStatus.FAILURE);

			}
		}
		catch (UnknownHostException ex) {
			LOGGER.error(ex.getMessage(), ex);
		}
		return result;
	}

	/**
	 * Deploys the container instances for XD.
	 * @param script - The admin server this container will be associated.
	 * @return A list of instances and whether they were successfully created or not.
	 */
	private List<Deployment> deployContainerServers(final String hostName) {
		LOGGER.info(HIGHLIGHT);
		LOGGER.info("*Deploying Container Nodes*");
		LOGGER.info(HIGHLIGHT);

		int instanceCount = Integer.parseInt(numberOfInstances);
		Reservation<? extends RunningInstance> reservation = instanceProvisioner
				.runInstance(configurer.bootstrapXDNodeScript(), instanceCount);
		int i = 0;
		ExecutorService executorService = Executors
				.newFixedThreadPool(reservation.size());
		List<Future<Deployment>> futures = new ArrayList<>();
		StopWatch outerStopWatch = new StopWatch("Overall");
		outerStopWatch.start();
		for (final RunningInstance instance : reservation) {
			final int currentInstance = i++;
			Callable<Deployment> task = new Callable<Deployment>() {

				@Override
				public Deployment call() {
					StopWatch inner = new StopWatch("instance "
							+ currentInstance);
					inner.start("checkAWSInstance");
					instanceChecker.waitForInstanceToBeProvisioned(instance, instanceProvisionWaitTime);
					tagInitialization(instance, InstanceType.NODE);
					instanceChecker.checkAWSInstance(instance);
					inner.stop();
					LOGGER.info(String
							.format("*******Setting up your Container XD instance %d.*******",
									currentInstance));
					RunningInstance refreshed = AWSInstanceProvisioner
							.findInstanceById(client, instance.getId());
					addTags(refreshed,
							Collections.singletonMap("Container_Node", ""
									+ currentInstance));
					inner.start("installContainerServer");
					Deployment deployment = installContainerServer(
							configurer.createContainerNodeScript(hostName, hadoopVersion, currentInstance),
							refreshed, InstanceType.NODE);
					inner.stop();
					LOGGER.debug(inner.prettyPrint());
					return deployment;
				}
			};
			futures.add(executorService.submit(task));
		}
		try {
			executorService.shutdown();
			executorService.awaitTermination(((RETRY_COUNT + 1) * 300) + 5, SECONDS);
			executorService.shutdownNow();
			outerStopWatch.stop();
			LOGGER.debug(outerStopWatch.prettyPrint());
			List<Deployment> result = new ArrayList<>();
			for (Future<Deployment> future : futures) {
				result.add(future.get(0, SECONDS));
			}
			return result;
		}
		catch (InterruptedException interruptedException) {
			throw new IllegalStateException(interruptedException.getMessage(), interruptedException);
		}
		catch (ExecutionException executionException) {
			throw new IllegalStateException(executionException.getMessage(), executionException);
		}
		catch (TimeoutException timeoutException) {
			throw new DeployTimeoutException(timeoutException.getMessage(), timeoutException);
		}

	}

	/**
	 * Executes the XD setup commands on a specified node id.
	 * @param script JCloud Builder script that initializes XD.
	 * @param nodeId The node ID of the instance to execute the commands
	 */
	private void runCommands(String script, String nodeId) {

		RunScriptOptions options = RunScriptOptions.Builder
				.blockOnComplete(false).overrideLoginUser("ubuntu")
				.overrideLoginPrivateKey(getPrivateKey());
		options.runAsRoot(false);
		ExecResponse resp = computeService.runScriptOnNode(nodeId, script,
				options);
		LOGGER.debug(resp.getOutput());
		LOGGER.debug(resp.getError());
		LOGGER.debug("ExitStatus is " + resp.getExitStatus());
	}

	/**
	 * Set the tags to state that the instance passed in is being configured for xd.
	 * @param instance The instance that the new labels will be applied.
	 * @param type tags the instance with the type of server 
	 */
	private void tagInitialization(RunningInstance instance, InstanceType type) {
		Map<String, String> tags = new HashMap<String, String>();
		tags.put("Name", "Initializing Instance for " + userName);
		tags.put("Type", type.name());
		addTags(instance, tags);

	}

	/**
	 * Tags the instance with the official cluster name and description
	 * @param instance The instance that the labels will be applied.
	 * @param type tags the instance with the type of server.
	 */
	private void tagInstance(RunningInstance instance, InstanceType type) {
		Map<String, String> tags = new HashMap<String, String>();
		tags.put("Name", clusterName);
		tags.put("User Name", userName);
		tags.put("Description", description);
		tags.put("Type", type.name());

		addTags(instance, tags);
	}

	/**
	 * Applies the tags in the map to the EC2 instances.
	 * @param instance The instance that the labels will be applied.
	 * @param tags A map containing the tag name (key) and the tag value.
	 */
	private void addTags(RunningInstance instance, Map<String, String> tags) {
		ArrayList<String> list = new ArrayList<String>();
		list.add(instance.getId());
		client.getTagApiForRegion(region).get().applyToResources(tags, list);
	}

	/**
	 * Retrieves the private key required for running OS commands.
	 * @return The contents of the private key file
	 */
	private String getPrivateKey() {
		String result = "";
		try {
			result = FileCopyUtils.copyToString(new FileReader(privateKeyFile));
		}
		catch (IOException ioException) {
			throw new IllegalStateException(ioException.getMessage(), ioException);
		}
		return result;
	}

	/**
	 * Retrieves location where the XD-EC2 jars are deployed.
	 * @return File that contains the dir where the jars are located
	 */
	private File getLibraryJarLocation() {
		File buildFile = new File(Main.class.getProtectionDomain()
				.getCodeSource().getLocation().getFile());
		return buildFile;
	}

	/**
	 * Copies the local file to a remote server via SSH
	 * @param file The file to be copied.
	 * @param host The host of the remote server.
	 */
	private void sshCopy(File file, String host) {
		final LoginCredentials credential = LoginCredentials
				.fromCredentials(new Credentials("ubuntu", getPrivateKey()));
		final com.google.common.net.HostAndPort socket = com.google.common.net.HostAndPort
				.fromParts(host, 22);
		final SshjSshClient client = new SshjSshClient(
				new BackoffLimitedRetryHandler(), socket, credential, 5000);
		final FilePayload payload = new FilePayload(file);
		client.put(UBUNTU_HOME + "deploy.jar", payload);
	}

	/**
	 * Sets the timeouts for the deployment.
	 * @return Properties object with the timeout policy.
	 */
	private Properties getTimeoutPolicy() {
		Properties properties = new Properties();
		long scriptTimeout = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
		properties.setProperty("jclouds.ssh.max-retries", "100");
		properties.setProperty("jclouds.max-retries", "1000");
		properties.setProperty("jclouds.request-timeout", "18000");
		properties.setProperty("jclouds.connection-timeout", "18000");

		properties.setProperty(TIMEOUT_PORT_OPEN, scriptTimeout + "");
		properties.setProperty(TIMEOUT_SCRIPT_COMPLETE, scriptTimeout + "");
		return properties;
	}

	/**
	 * Verifies that the URL with the XD Distro is valid.
	 * @param properties the configuration properties for the deployment.
	 */
	private void validateURLs(Properties properties) {
		try {
			configurer.checkURL(properties.getProperty("xd-dist-url"));
		}
		catch (HttpClientErrorException httpException) {
			throw new InvalidXDZipUrlException(
					"Unable to download the XD Distribution you specified because, \" "
							+ httpException.getMessage() + "\"");
		}
	}

}
