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
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jclouds.ContextBuilder;
import org.jclouds.aws.ec2.AWSEC2Client;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.xd.cloud.Deployer;
import org.springframework.xd.cloud.Deployment;
import org.springframework.xd.cloud.DeploymentStatus;
import org.springframework.xd.cloud.InstanceSize;
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

	static final Logger logger = LoggerFactory.getLogger(AWSDeployer.class);
	private static final String UBUNTU_HOME = "/home/ubuntu/";

	private String clusterName;
	private String awsAccessKey;
	private String awsSecretKey;
	private String privateKeyFile;
	private String multiNode;
	private String description;
	private String userName;
	private String region;

	private static final String[] requiredEntries = { "cluster-name",
			"aws-access-key", "aws-secret-key", "private-key-file",
			"user_name", "region", "machine-size", "security-group",
			"private-key-name", "ami" };

	private AWSEC2Client client;
	private ComputeService computeService;
	private AWSInstanceChecker awsInstanceChecker;

	private AWSInstanceConfigurer configurer;

	private AWSInstanceProvisioner instanceProvisioner;

	public AWSDeployer() {
		Iterable<Module> modules = ImmutableSet
				.<Module> of(new SshjSshClientModule());

		Properties properties = getProperties();
		ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				// key I created above
				.modules(modules).overrides(getTimeoutPolicy())
				.buildView(ComputeServiceContext.class);
		computeService = context.getComputeService();

		client = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				.buildApi(AWSEC2Client.class);
		awsInstanceChecker = new AWSInstanceChecker(properties, client,
				computeService);
		instanceProvisioner = new AWSInstanceProvisioner(client, properties);
		configurer = new AWSInstanceConfigurer(properties);
		validateConfiguration(properties);

	}

	public List<Deployment> deploy() throws TimeoutException {

		ArrayList<Deployment> result = new ArrayList<Deployment>();
		String script = null;
		if (getMultiNode().equalsIgnoreCase("false")) {
			result.add(deploySingleNode(script));
		} else if (getMultiNode().equalsIgnoreCase("true")) {
			result.add(deployAdminServer(script));
			result.addAll(deployContainerServer(script));
		} else {
			throw new IllegalArgumentException(
					"multi-node property must either be true or false");
		}

		return result;
	}

	public Deployment deploySingleNode(String script) throws TimeoutException {
		logger.info("Deploying SingleNode");
		RunningInstance instance = Iterables.getOnlyElement(instanceProvisioner
				.runInstance(configurer.createStartXDResourcesScript(), 1));
		awsInstanceChecker.checkServerResources(instance);
		logger.info("*******Setting up your single XD instance.*******");
		instance = AWSInstanceProvisioner.findInstanceById(client,
				instance.getId());
		tagInitialization(instance);
		try {
			Thread.sleep(5000);
		} catch (Exception e) {

		}

		sshCopy(this.getLibraryJarLocation(), instance.getDnsName(),
				instance.getId());
		runCommands(configurer.createSingleNodeScript(instance.getDnsName()),
				instance.getId());
		tagInstance(instance);
		awsInstanceChecker.checkServerInstance(instance, 9393);
		Deployment result = null;
		try {
			InetAddress address = InetAddress.getByName(instance.getDnsName());
			result = new Deployment(address, InstanceType.SINGLE_NODE,
					DeploymentStatus.SUCCESS);
		} catch (Exception ex) {
			logger.error(ex.getMessage());
		}
		return result;
	}

	public Deployment deployAdminServer(String script) {
		Deployment result = null;
		logger.info("Deploying AdminServer");

		return result;
	}

	public List<Deployment> deployContainerServer(String script) {
		ArrayList<Deployment> result = new ArrayList<Deployment>();
		logger.info("Deploying Container Servers");

		return result;
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
		logger.debug(resp.getOutput());
		logger.debug(resp.getError());
		logger.debug("ExitStatus is " + resp.getExitStatus());
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

	private void tagInitialization(RunningInstance instance) {
		Map<String, String> tags = new HashMap<String, String>();
		tags.put("Name", "Initializing Instance for " + userName);
		addTags(instance, tags);

	}

	private void tagInstance(RunningInstance instance) {
		Map<String, String> tags = new HashMap<String, String>();
		tags.put("Name", clusterName);
		tags.put("User Name", userName);
		tags.put("Description", description);

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
		try {
			LoginCredentials credential = LoginCredentials
					.fromCredentials(new Credentials("ubuntu", getPrivateKey()));
			com.google.common.net.HostAndPort socket = com.google.common.net.HostAndPort
					.fromParts(host, 22);
			SshjSshClient client = new SshjSshClient(
					new BackoffLimitedRetryHandler(), socket, credential, 5000);
			FilePayload payload = new FilePayload(file);
			client.put(UBUNTU_HOME + "deploy.jar", payload);
		} catch (Exception e) {
			e.printStackTrace();
		}
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

	private Properties getProperties() {
		Resource resource = new ClassPathResource("xd-ec2.properties");
		Properties props = null;
		try {
			props = PropertiesLoaderUtils.loadProperties(resource);
		} catch (IOException ioe) {
			logger.error("Failed to open xd-ec2.properties file because: "
					+ ioe.getMessage());
		}
		clusterName = props.getProperty("cluster-name");
		awsAccessKey = props.getProperty("aws-access-key");
		awsSecretKey = props.getProperty("aws-secret-key");
		privateKeyFile = props.getProperty("private-key-file");
		multiNode = props.getProperty("multi-node");
		description = props.getProperty("description");
		userName = props.getProperty("user_name");
		region = props.getProperty("region");
		return props;
	}

	/**
	 * Verifies that all properties that the application needs are setup
	 * properly.
	 */
	private Properties validateConfiguration(Properties props) {
		ArrayList<String> errorList = new ArrayList<String>();
		String value;
		for (int i = 0; i < requiredEntries.length; i++) {
			value = props.getProperty(requiredEntries[i]);
			if (value == null || value.length() == 0) {
				errorList.add(requiredEntries[i]);
			}
		}
		if (errorList.size() > 0) {
			String errorMessage = "The following entries are not configured in your xd-ec2.properties:\n";
			errorMessage += errorList.get(0);
			for (int i = 1; i < errorList.size(); i++) {
				errorMessage += ",\n" + errorList.get(i);
			}
			throw new IllegalArgumentException(errorMessage);
		}
		if(!verifyMachineSize(props.getProperty("machine-size"))){
			throw new IllegalArgumentException("Invalid machine size specified.  Valid values are small, medium, large");
		}
		try {
			configurer.checkURL(props.getProperty("xd-dist-url"));
		} catch (HttpClientErrorException httpException) {
			throw new InvalidXDZipUrlException(
					"Unable to download the XD Distribution you specified because, \" "
							+ httpException.getMessage() + "\"");
		}

		return props;
	}

	private boolean verifyMachineSize(String machineSize) {
		boolean verified = false;
		if (machineSize.equalsIgnoreCase(InstanceSize.SMALL.name())) {
			verified = true;
		} else if (machineSize.equalsIgnoreCase(InstanceSize.MEDIUM.name())) {
			verified = true;
		} else if (machineSize.equalsIgnoreCase(InstanceSize.LARGE.name())) {
			verified = true;
		}
		return verified;
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
		return awsInstanceChecker;
	}

	public void setAwsInstanceChecker(AWSInstanceChecker awsInstanceChecker) {
		this.awsInstanceChecker = awsInstanceChecker;
	}

}
