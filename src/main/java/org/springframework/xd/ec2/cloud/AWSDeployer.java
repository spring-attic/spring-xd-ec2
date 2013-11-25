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

import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_PORT_OPEN;
import static org.jclouds.compute.config.ComputeServiceProperties.TIMEOUT_SCRIPT_COMPLETE;
import static org.jclouds.scriptbuilder.domain.Statements.exec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jclouds.ContextBuilder;
import org.jclouds.aws.ec2.AWSEC2Client;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.callables.ScriptStillRunningException;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.http.handlers.BackoffLimitedRetryHandler;
import org.jclouds.io.payloads.FilePayload;
import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.sshj.SshjSshClient;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.xd.cloud.Deployer;
import org.springframework.xd.cloud.Deployment;
import org.springframework.xd.cloud.DeploymentStatus;
import org.springframework.xd.cloud.InstanceType;
import org.springframework.xd.cloud.InvalidXDZipUrlException;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Module;

/**
 * @author glenn renfro
 * 
 */

public class AWSDeployer implements Deployer {

	static final Logger LOGGER = LoggerFactory.getLogger(AWSDeployer.class);
	private static final String UBUNTU_HOME = "/home/ubuntu/";

	private transient String clusterName;
	private transient String awsAccessKey;
	private transient String awsSecretKey;
	private transient String privateKeyFile;
	private transient String multiNode;
	private transient String description;
	private transient String userName;
	private transient String region;
	private transient String numberOfInstances;
	private transient String transport;

	private AWSEC2Client client;
	private ComputeService computeService;
	private AWSInstanceChecker instanceChecker;

	private AWSInstanceConfigurer configurer;

	private AWSInstanceProvisioner instanceProvisioner;

	public AWSDeployer(Properties properties) {
		Iterable<Module> modules = ImmutableSet
				.<Module> of(new SshjSshClientModule());
		clusterName = properties.getProperty("cluster-name");
		awsAccessKey = properties.getProperty("aws-access-key");
		awsSecretKey = properties.getProperty("aws-secret-key");
		privateKeyFile = properties.getProperty("private-key-file");
		multiNode = properties.getProperty("multi-node");
		description = properties.getProperty("description");
		userName = properties.getProperty("user_name");
		region = properties.getProperty("region");
		numberOfInstances = properties.getProperty("number-nodes");
		transport = properties.getProperty("xd-transport");

		ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				// key I created above
				.modules(modules).overrides(getTimeoutPolicy())
				.buildView(ComputeServiceContext.class);
		computeService = context.getComputeService();

		client = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				.buildApi(AWSEC2Client.class);
		instanceChecker = new AWSInstanceChecker(properties, client,
				computeService);
		instanceProvisioner = new AWSInstanceProvisioner(client, properties);
		configurer = new AWSInstanceConfigurer(properties);
		validateURLs(properties);
	}

	public List<Deployment> deploy() throws TimeoutException {

		ArrayList<Deployment> result = new ArrayList<Deployment>();
		String script = null;
		if (getMultiNode().equalsIgnoreCase("false")) {
			result.add(deploySingleNode(script));
		} else if (getMultiNode().equalsIgnoreCase("true")) {
			Deployment admin = deployAdminServer(script);
			result.add(admin);
			result.addAll(deployContainerServer(admin.getAddress().getHostAddress()));
		} else {
			throw new IllegalArgumentException(
					"multi-node property must either be true or false");
		}

		return result;
	}

	public Deployment deploySingleNode(String script) throws TimeoutException {
		LOGGER.info("Deploying SingleNode");
		RunningInstance instance = Iterables.getOnlyElement(instanceProvisioner
				.runInstance(configurer.createStartXDResourcesScript(), 1));
		instanceChecker.checkServerResources(instance);
		LOGGER.info("*******Setting up your single XD instance.*******");
		instance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		return deploySingleServer(
				configurer.createSingleNodeScript(instance.getDnsName()),
				instance, InstanceType.SINGLE_NODE);
	}

	public Deployment deployAdminServer(String script) throws TimeoutException {
		LOGGER.info("Deploying Administrator");
		RunningInstance instance = Iterables.getOnlyElement(instanceProvisioner
				.runInstance(configurer.createStartXDResourcesScript(),
						1));
		instanceChecker.checkServerResources(instance);
		LOGGER.info("*******Setting up your Administrator XD instance.*******");
		instance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		return deploySingleServer(
				configurer.createAdminNodeScript(instance.getDnsName(),transport),
				instance, InstanceType.ADMIN);
	}

	private Deployment deploySingleServer(String script,
			RunningInstance instance, InstanceType type)
			throws TimeoutException {
		tagInitialization(instance, type);
		sshCopy(this.getLibraryJarLocation(), instance.getDnsName(),
				instance.getId());
		runCommands(script, instance.getId());
		tagInstance(instance, type);
		instanceChecker.checkServerInstance(instance, 9393);
		Deployment result = null;
		try {
			InetAddress address = InetAddress.getByName(instance.getDnsName());
			result = new Deployment(address, type, DeploymentStatus.SUCCESS);
		} catch (Exception ex) {
			LOGGER.error(ex.getMessage());
		}
		return result;
	}
	
	private Deployment installContainerServer(String script,
			RunningInstance instance, InstanceType type)
			throws TimeoutException {
		tagInitialization(instance, type);
		sshCopy(this.getLibraryJarLocation(), instance.getDnsName(),
				instance.getId());
		boolean isInitialized = true;
		try{
		runCommands(script, instance.getId());
		}catch(Exception ssre){
			LOGGER.warn(ssre.getLocalizedMessage());
			isInitialized = false;
		}
		tagInstance(instance, type);
		if(isInitialized && instanceChecker.checkContainerProcess(instance,getKeyPair())){
			LOGGER.info("Container "+ instance.getId() +" started/n" );
		}else{
			LOGGER.info("Container "+ instance.getId() +" did not start/n" );
		}
		Deployment result = null;
		try {
			InetAddress address = InetAddress.getByName(instance.getDnsName());
			if(isInitialized){
				result = new Deployment(address, type, DeploymentStatus.SUCCESS);
			}else{
				result = new Deployment(address, type, DeploymentStatus.FAILURE);
				
			}
		} catch (Exception ex) {
			LOGGER.error(ex.getMessage());
		}
		return result;
	}
	
	public List<Deployment> deployContainerServer(String hostName)
			throws TimeoutException {
		LOGGER.info("Deploying Container Servers");
		List<Deployment> deploymentList = new ArrayList<Deployment>();
		int instanceCount = Integer.parseInt(numberOfInstances);
		Iterator<? extends RunningInstance> iter = instanceProvisioner
				.runInstance(configurer.bootstrapXDNodeScript(), instanceCount)
				.iterator();
		int currentInstance = 0;
		while (iter.hasNext()) {
			RunningInstance instance = iter.next();
			instanceChecker.checkAWSInstance(instance);
			LOGGER.info("*******Setting up your Container XD instance.*******");
			instance = AWSInstanceProvisioner.findInstanceById(client,
					instance.getId());
			deploymentList
					.add(installContainerServer(configurer
							.createContainerNodeScript(hostName,transport),
							instance, InstanceType.NODE));
			Map<String, String> nodeId = new HashMap<String,String>();
			nodeId.put("Container_Node", String.valueOf(currentInstance));
			addTags(instance, nodeId);
			currentInstance++;
		}
		return deploymentList;
	}

	/**
	 * Executes the XD setup commands on a specified node id.
	 * 
	 * @param script
	 *            JCloud Builder script that initializes XD.
	 * @param nodeId
	 *            The node ID of the instance to execute the commands
	 */
	public void runCommands(String script, String nodeId) {

		RunScriptOptions options = RunScriptOptions.Builder
				.blockOnComplete(true).overrideLoginUser("ubuntu")
				.overrideLoginPrivateKey(getKeyPair());
		options.runAsRoot(false);
		ExecResponse resp = computeService.runScriptOnNode(nodeId, script,
				options);
		LOGGER.debug(resp.getOutput());
		LOGGER.debug(resp.getError());
		LOGGER.debug("ExitStatus is " + resp.getExitStatus());
	}

	/**
	 * Run a single command on the XD instance.
	 * 
	 * @param command
	 * @param nodeId
	 * @return
	 */
	@Deprecated
	public ExecResponse runCommand(String command, String nodeId) {
		String script = new ScriptBuilder().addStatement(exec(command)).render(
				OsFamily.UNIX);
		RunScriptOptions options = RunScriptOptions.Builder
				.blockOnComplete(true).overrideLoginUser("ubuntu")
				.overrideLoginPrivateKey(getKeyPair());
		options.runAsRoot(false);
		ExecResponse resp = computeService.runScriptOnNode(nodeId, script,
				options);
		return resp;
	}

	private void tagInitialization(RunningInstance instance, InstanceType type) {
		Map<String, String> tags = new HashMap<String, String>();
		tags.put("Name", "Initializing Instance for " + userName);
		tags.put("Type", type.name());
		addTags(instance, tags);

	}

	private void tagInstance(RunningInstance instance, InstanceType type) {
		Map<String, String> tags = new HashMap<String, String>();
		tags.put("Name", clusterName);
		tags.put("User Name", userName);
		tags.put("Description", description);
		tags.put("Type", type.name());

		addTags(instance, tags);
	}

	private void addTags(RunningInstance instance, Map<String, String> tags) {
		ArrayList<String> list = new ArrayList<String>();
		list.add(instance.getId());
		client.getTagApiForRegion(region).get().applyToResources(tags, list);
	}

	private String getKeyPair() {
		String result = "";
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(privateKeyFile));
			while (br.ready()) {
				result += br.readLine() + "\n";
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
		return result;
	}

	private File getLibraryJarLocation() {
		File result = null;
		File buildFile = new File("build/libs/spring-xd-ec2-1.0.jar");
		File deployFile = new File("lib/spring-xd-ec2-1.0.jar");
		if (buildFile.exists()) {
			result = buildFile;
		} else if (deployFile.exists()) {
			result = deployFile;
		}

		return result;
	}

	private void sshCopy(File file, String host, String nodeId) {
			final LoginCredentials credential = LoginCredentials
					.fromCredentials(new Credentials("ubuntu", getPrivateKey()));
			final com.google.common.net.HostAndPort socket = com.google.common.net.HostAndPort
					.fromParts(host, 22);
			final SshjSshClient client = new SshjSshClient(
					new BackoffLimitedRetryHandler(), socket, credential, 5000);
			final FilePayload payload = new FilePayload(file);
			client.put(UBUNTU_HOME + "deploy.jar", payload);
	}

	private String getPrivateKey() {
		String result = "";
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(privateKeyFile));
			while (br.ready()) {
				result += br.readLine() + "\n";
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
		return result;
	}

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

	private void validateURLs(Properties properties){
		try {
			configurer.checkURL(properties.getProperty("xd-dist-url"));
		} catch (HttpClientErrorException httpException) {
			throw new InvalidXDZipUrlException(
					"Unable to download the XD Distribution you specified because, \" "
							+ httpException.getMessage() + "\"");
		}
	}

	public String getMultiNode() {
		return multiNode;
	}

	public void setMultiNode(String multiNode) {
		this.multiNode = multiNode;
	}

	public AWSEC2Client getClient() {
		return client;
	}

	public void setClient(AWSEC2Client client) {
		this.client = client;
	}

	public ComputeService getComputeService() {
		return computeService;
	}

	public void setComputeService(ComputeService computeService) {
		this.computeService = computeService;
	}

	public AWSInstanceProvisioner getInstanceProvisioner() {
		return instanceProvisioner;
	}

	public void setInstanceProvisioner(AWSInstanceProvisioner instanceManager) {
		this.instanceProvisioner = instanceManager;
	}

	public AWSInstanceChecker getAwsInstanceChecker() {
		return instanceChecker;
	}

	public void setAwsInstanceChecker(AWSInstanceChecker awsInstanceChecker) {
		this.instanceChecker = awsInstanceChecker;
	}

}
