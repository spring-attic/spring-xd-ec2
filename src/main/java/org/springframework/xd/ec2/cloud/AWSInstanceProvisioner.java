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

import static org.jclouds.ec2.options.RunInstancesOptions.Builder.asType;

import java.util.Properties;
import java.util.Set;

import org.jclouds.ec2.EC2Client;
import org.jclouds.ec2.domain.InstanceType;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.springframework.xd.cloud.InstanceProvisioner;
import org.springframework.xd.cloud.InstanceSize;

import com.google.common.collect.Iterables;

/**
 * Provisions all necessary AWS resources for XD.
 * 
 * @author glenn renfro
 * 
 */

public class AWSInstanceProvisioner implements InstanceProvisioner{

	private String ami;
	private String machineSize;
	private String securityGroup;
	private String privateKeyName;
	private String region;

	private EC2Client client;

	public AWSInstanceProvisioner(EC2Client client, Properties properties) {
		this.client = client;
		this.ami = properties.getProperty("ami");
		this.machineSize = properties.getProperty("machine-size");
		this.securityGroup = properties.getProperty("security-group");
		this.privateKeyName = properties.getProperty("private-key-name");
		this.region = properties.getProperty("region");
	}

	/**
	 * Creates an AWS Instance
	 * 
	 * @param client
	 *            - AWS Client that executes the commands necessary to create
	 *            the instance.
	 * @param script
	 *            - JClouds Builder script that bootstraps the instance.
	 * @param numberOfInstances
	 *            - How many instances you need.
	 * @return A list of created instances.
	 */
	public Reservation<? extends RunningInstance> runInstance(String script,
			int numberOfInstances) {
		Reservation<? extends RunningInstance> reservation = client
				.getInstanceServices().runInstancesInRegion(region, null,
						ami, // XD Basic Image.
						1, // minimum instances
						numberOfInstances, // maximum instances
						asType(getInstanceType()).withKeyName(privateKeyName)
								.withSecurityGroup(securityGroup)
								.withUserData(script.getBytes()));
		return reservation;
	}

	/**
	 * Retrieve the instance information for the instance id based on the
	 * EC2Client
	 * 
	 * @param client
	 *            AWS Client that executes the commands necessary to create the
	 *            instance.
	 * @param instanceId
	 *            The id of the instance.
	 * @return Instance with that instance id.
	 */
	public static RunningInstance findInstanceById(EC2Client client,
			String instanceId) {
		// search my account for the instance I just created
		Set<? extends Reservation<? extends RunningInstance>> reservations = client
				.getInstanceServices().describeInstancesInRegion(null,
						instanceId); // last parameter (ids) narrows the
		// since we refined by instanceId there should only be one instance
		return Iterables.getOnlyElement(Iterables.getOnlyElement(reservations));
	}

		private String getInstanceType() {
		String type = null;
		if (machineSize.equalsIgnoreCase(InstanceSize.SMALL.name())) {
			type = InstanceType.M1_SMALL;
		} else if (machineSize.equalsIgnoreCase(InstanceSize.MEDIUM.name())) {
			type = InstanceType.M1_MEDIUM;
		} else if (machineSize.equalsIgnoreCase(InstanceSize.LARGE.name())) {
			type = InstanceType.M1_LARGE;
		}

		return type;
	}

}
