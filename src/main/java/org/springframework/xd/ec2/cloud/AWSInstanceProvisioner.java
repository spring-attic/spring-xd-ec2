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

import org.jclouds.ec2.EC2Client;
import org.jclouds.ec2.domain.InstanceType;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provisions all necessary AWS resources for XD.
 * 
 * @author glenn renfro
 * 
 */

@Component
public class AWSInstanceProvisioner {

	public static final String SMALL = "small";
	public static final String MEDIUM = "medium";
	public static final String LARGE = "large";

	@Value("${ami}")
	private String ami;
	@Value("${machine-size}")
	private String machineSize;
	@Value("${private-key-file}")
	private String privateKeyFile;
	@Value("${security-group}")
	private String securityGroup;
	@Value("${private-key-name}")
	private String privateKeyName;
	@Value("${region}")
	private String region;

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
	public Reservation<? extends RunningInstance> runInstance(EC2Client client,
			String script, int numberOfInstances) {
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

	private String getInstanceType() {
		String type = null;
		if (machineSize.equalsIgnoreCase(SMALL)) {
			type = InstanceType.M1_SMALL;
		} else if (machineSize.equalsIgnoreCase(MEDIUM)) {
			type = InstanceType.M1_MEDIUM;
		} else if (machineSize.equalsIgnoreCase(LARGE)) {
			type = InstanceType.M1_LARGE;
		}

		return type;
	}
}
