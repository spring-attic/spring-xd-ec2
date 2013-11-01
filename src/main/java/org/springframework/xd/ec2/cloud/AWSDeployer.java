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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.xd.cloud.Deployer;
import org.springframework.xd.cloud.XDInstanceType;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Module;

/**
 * @author glenn renfro
 * 
 */

@Component
public class AWSDeployer implements Deployer {

	static final Logger logger = LoggerFactory.getLogger(AWSDeployer.class);
	private static final String UBUNTU_HOME = "/home/ubuntu/";


	@Value("${cluster-name}")
	private String clusterName;
	@Value("${aws-access-key}")
	private String awsAccessKey;
	@Value("${aws-secret-key}")
	private String awsSecretKey;
	@Value("${private-key-file}")
	private String privateKeyFile;
	@Value("${multi-node}")
	private String multiNode;
	@Value("${number-nodes}")
	private String numberNodes;
	@Value("${description}")
	private String description;
	@Value("${user_name}")
	private String userName;
	@Value("${region}")
	private String region;

	private AWSEC2Client client;
	private ComputeService computeService;
	AWSInstanceChecker awsInstanceChecker;
	

	@Autowired
	AWSInstanceConfigurer configurer;
	@Autowired
	AWSInstanceProvisioner instanceProvisioner;

	@Autowired
	public AWSDeployer(@Value("${aws-access-key}") final String awsAccessKey,
			@Value("${aws-secret-key}") String awsSecretKey) {
		Iterable<Module> modules = ImmutableSet
				.<Module> of(new SshjSshClientModule());
		ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				// key I created above
				.modules(modules).overrides(getTimeoutPolicy())
				.buildView(ComputeServiceContext.class);
		computeService = context.getComputeService();

		client = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				.buildApi(AWSEC2Client.class);
		awsInstanceChecker = new AWSInstanceChecker();

	}

	public List<XDInstanceType> deploy() throws TimeoutException {
		ArrayList<XDInstanceType> result = new ArrayList<XDInstanceType>();
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

	public XDInstanceType deploySingleNode(String script)
			throws TimeoutException {
		logger.info("Deploying SingleNode");
		RunningInstance instance = Iterables
				.getOnlyElement(instanceProvisioner.runInstance(client,
						configurer.getSingleNodeStartupScript(), 1));
		
		awsInstanceChecker.checkServerResources(instance,client,computeService);
		instance = AWSInstanceProvisioner.findInstanceById(client, instance.getId());
		logger.info("*******Setting up your single XD instance.*******");

		sshCopy(this.getLibraryJarLocation(), instance.getDnsName(), instance.getId());
		runCommands(configurer.deploySingleNodeApplication(instance.getDnsName()), instance.getId());
		tagInstance(instance);
		awsInstanceChecker.checkServerInstance(instance,client,computeService,9393);

		return new XDInstanceType(instance.getDnsName(),
				instance.getIpAddress(),
				XDInstanceType.InstanceType.SINGLE_NODE, "Success");
	}

	public XDInstanceType deployAdminServer(String script) {
		XDInstanceType result = null;
		logger.info("Deploying AdminServer");

		return result;
	}

	public List<XDInstanceType> deployContainerServer(String script) {
		ArrayList<XDInstanceType> result = new ArrayList<XDInstanceType>();
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

	private void tagInstance(RunningInstance instance) {
		ArrayList<String> list = new ArrayList<String>();
		list.add(instance.getId());
		Map<String, String> tags = new HashMap<String, String>();
		tags.put("Name", clusterName);
		tags.put("User Name", userName);
		tags.put("Description", description);

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
	
	private File getLibraryJarLocation(){
		File result = null;
		File buildFile = new File("build/libs/spring-xd-ec2-1.0.jar");
		File deployFile = new File("lib/spring-xd-ec2-1.0.jar");
		if(buildFile.exists()){
			result = buildFile;
		}else if(deployFile.exists()){
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
			client.put(
					UBUNTU_HOME+"deploy.jar",
					payload);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private  String getPrivateKey() {
		String result = "";
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(
					privateKeyFile));
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
