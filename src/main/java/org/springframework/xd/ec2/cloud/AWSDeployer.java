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

import static org.jclouds.scriptbuilder.domain.Statements.exec;
import static org.jclouds.util.Predicates2.retry;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Resource;

import org.jclouds.ContextBuilder;
import org.jclouds.aws.ec2.AWSEC2Client;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.ec2.EC2Client;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.ec2.predicates.InstanceStateRunning;
import org.jclouds.predicates.SocketOpen;
import org.jclouds.scriptbuilder.ScriptBuilder;
import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;
import com.google.inject.Module;

@Component
public class AWSDeployer implements Deployer {

	final static Logger logger = LoggerFactory.getLogger(AWSDeployer.class);

	@Resource
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

	@Autowired
	AWSInstanceConfigurer configurer;
	@Autowired
	AWSInstanceProvisioner instanceManager;

	@Autowired
	public AWSDeployer(@Value("${aws-access-key}") final String awsAccessKey,
			@Value("${aws-secret-key}") String awsSecretKey) {
		Iterable<Module> modules = ImmutableSet
				.<Module> of(new SshjSshClientModule());
		ComputeServiceContext context = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey) // key I created above
				.modules(modules).buildView(ComputeServiceContext.class);
		computeService = context.getComputeService();
		client = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				.buildApi(AWSEC2Client.class);

	}

	public String deploy() {
		String result = null;
		String script = null;
		if (multiNode.equalsIgnoreCase("false")) {
			result = deploySingleNode(script);
		} else if (multiNode.equalsIgnoreCase("true")) {
			result = deployAdminServer(script);
			result += deployContainerServer(script);
		} else {
			throw new IllegalArgumentException(
					"multi-node property must either be true or false");
		}

		return result;
	}

	public String deploySingleNode(String script) {
		String hostName = null;
		logger.info("Deploying SingleNode");
		RunningInstance instance = Iterables
				.getOnlyElement(instanceManager.runInstance(client,
						configurer.getSingleNodeStartupScript(), 1));
		try {
			checkAdminServices(instance);

			runCommands(configurer.deploySingleNodeApplication(),
					instance.getId());
			tagInstance(instance);
			checkAdminInstance(instance);
		} catch (TimeoutException te) {
			te.printStackTrace();
		}
		instance = findInstanceById(client, instance.getId());
		logger.info(String.format("Single Node Instance: %s has been created",
				instance.getDnsName()));
		return hostName;
	}

	public String deployAdminServer(String script) {
		String hostName = null;
		logger.info("Deploying AdminServer");

		return hostName;
	}

	public String[] deployContainerServer(String script) {
		String hostNames[] = null;
		logger.info("Deploying Container Servers");

		return hostNames;
	}

	public void runCommands(String script, String nodeId) {

		RunScriptOptions options = RunScriptOptions.Builder
				.blockOnComplete(true).overrideLoginUser("ubuntu")
				.overrideLoginPrivateKey(getKeyPair());
		options.runAsRoot(false);
		ExecResponse resp = computeService.runScriptOnNode(nodeId, script,
				options);
		logger.debug(resp.getOutput());
	}

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

	private void checkAdminInstance(RunningInstance instance)
			throws TimeoutException {
		instance = findInstanceById(client, instance.getId());
		SocketOpen socketOpen = computeService.getContext().utils().injector()
				.getInstance(SocketOpen.class);
		Predicate<HostAndPort> socketTester = retry(socketOpen, 300, 1,
				TimeUnit.SECONDS);
		logger.info(String.format(
				"%d: %s awaiting XD admin server to start %n",
				System.currentTimeMillis(), instance.getIpAddress()));
		if (!socketTester.apply(HostAndPort.fromParts(instance.getIpAddress(),
				9393)))
			throw new TimeoutException(
					"timeout waiting for admin server to start: "
							+ instance.getIpAddress());

		logger.info(String.format("%d: %s admin server started%n",
				System.currentTimeMillis(), instance.getIpAddress()));

	}

	private RunningInstance checkAdminServices(RunningInstance instance)
			throws TimeoutException {

		logger.info(String.format("%d: %s awaiting instance to run %n",
				System.currentTimeMillis(), instance.getId()));
		Predicate<RunningInstance> runningTester = retry(
				new InstanceStateRunning(client), 180, 5, TimeUnit.SECONDS);

		if (!runningTester.apply(instance))
			throw new TimeoutException("timeout waiting for instance to run: "
					+ instance.getId());

		instance = findInstanceById(client, instance.getId());
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

		logger.info(String.format("%d: %s http service started%n",
				System.currentTimeMillis(), instance.getIpAddress()));
		logger.info(String.format("instance %s ready%n", instance.getId()));
		logger.info(String.format("ip address: %s%n", instance.getIpAddress()));
		logger.info(String.format("dns name: %s%n", instance.getDnsName()));
		return instance;
	}

	private static RunningInstance findInstanceById(EC2Client client,
			String instanceId) {
		// search my account for the instance I just created
		Set<? extends Reservation<? extends RunningInstance>> reservations = client
				.getInstanceServices().describeInstancesInRegion(null,
						instanceId); // last parameter (ids) narrows the
		// search

		// since we refined by instanceId there should only be one instance
		return Iterables.getOnlyElement(Iterables.getOnlyElement(reservations));
	}

	private String getKeyPair() {
		String result = "";
		BufferedReader br = null;
		try {
			System.out.println(privateKeyFile);
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
		// System.out.println(result);
		return result;
	}

}
