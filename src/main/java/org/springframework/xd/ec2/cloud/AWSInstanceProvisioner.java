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

import com.google.common.collect.Iterables;
import org.jclouds.aws.ec2.AWSEC2Api;
import org.jclouds.ec2.domain.InstanceType;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.springframework.util.Assert;
import org.springframework.xd.cloud.InstanceProvisioner;
import org.springframework.xd.cloud.InstanceSize;

import java.util.Properties;
import java.util.Set;

import static org.jclouds.ec2.options.RunInstancesOptions.Builder.asType;

/**
 * Provisions all necessary AWS resources for XD.
 * 
 * @author glenn renfro
 * 
 */

public class AWSInstanceProvisioner implements InstanceProvisioner {

	private String ami;

	private String machineSize;

	private String securityGroup;

	private String publicKeyName;

	private String region;

	private String zone;

	private AWSEC2Api client;

	public AWSInstanceProvisioner(AWSEC2Api client, Properties properties) {
		Assert.notNull(client, "client can not be null");
		Assert.notNull(properties, "properties can not be null");
		this.client = client;
		this.ami = properties.getProperty("ami");
		this.machineSize = properties.getProperty("machine-size");
		this.securityGroup = properties.getProperty("security-group");
		this.publicKeyName = properties.getProperty("public-key-name");
		this.region = properties.getProperty("region");
		if(properties.containsKey("zone")) {
			this.zone = properties.getProperty("zone");
		}
	}

	/**
	 * Creates an AWS Instance
	 * 
	 * @param script JClouds Builder script that bootstraps the instance.
	 * @param numberOfInstances How many instances you need.
	 * @return A list of created instances.
	 */
	@Override
	public Reservation<? extends RunningInstance> runInstance(String script,
			int numberOfInstances) {
		Assert.hasText(script, "script can not be empty nor null");
		Reservation<? extends RunningInstance> reservation = client.getInstanceApi().get().
				runInstancesInRegion(region, zone,
						ami, // XD Basic Image.
						1, // minimum instances
						numberOfInstances, // maximum instances
						asType(getInstanceType()).withKeyName(publicKeyName)
								.withSecurityGroup(securityGroup)
								.withUserData(script.getBytes()));
		return reservation;
	}

	/**
	 * Retrieve the instance information for the instance id based on the EC2Client
	 * @param client AWS Client that executes the commands necessary to create the instance.
	 * @param instanceId The id of the instance.
	 * @return Instance with that instance id.
	 */
	public static RunningInstance findInstanceById(AWSEC2Api client,
			String instanceId) {
		Assert.notNull(client, "client can not be null");
		// search my account for the instance I just created
		Set<? extends Reservation<? extends RunningInstance>> reservations = client.getInstanceApi().get().
				describeInstancesInRegion(null,
						instanceId); // last parameter (ids) narrows the
		// since we refined by instanceId there should only be one instance
		return Iterables.getOnlyElement(Iterables.getOnlyElement(reservations));
	}

	private String getInstanceType() {
		String type = null;
		if (machineSize.equalsIgnoreCase(InstanceSize.SMALL.name())) {
			type = InstanceType.M1_SMALL;
		}
		else if (machineSize.equalsIgnoreCase(InstanceSize.MEDIUM.name())) {
			type = InstanceType.M1_MEDIUM;
		}
		else if (machineSize.equalsIgnoreCase(InstanceSize.LARGE.name())) {
			type = InstanceType.M1_LARGE;
		}
		else if (machineSize.equalsIgnoreCase(InstanceSize.XLARGE.name())) {
			type = InstanceType.M1_XLARGE;
		}

		return type;
	}

}
