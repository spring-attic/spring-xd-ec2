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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jclouds.ContextBuilder;
import org.jclouds.aws.ec2.AWSEC2Api;
import org.jclouds.aws.ec2.domain.AWSRunningInstance;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;

import com.google.common.collect.Iterables;

/**
 * 
 * Provides a suite of AWS commands that can applied to a XD CLuster.
 * @author Glenn Renfro
 */
public class AWSTools {

	static final Logger LOGGER = LoggerFactory.getLogger(AWSTools.class);

	private String awsAccessKey;

	private String awsSecretKey;

	private String region;

	private AWSEC2Api client;

	public AWSTools(Properties properties) {
		Assert.notNull(properties, "properties can not be null");
		awsAccessKey = properties.getProperty("aws-access-key");
		awsSecretKey = properties.getProperty("aws-secret-key");
		region = properties.getProperty("region");
		client = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				.buildApi(AWSEC2Api.class);
	}

	/**
	 * Iterates over all EC2 instances that have a "name" tag that has the value in the name param.
	 * @param name The name of the cluster to shutdown.
	 */
	public void shutdown(String name) {
		Assert.hasText(name, "name can not be empty nor null");
		Iterator<String> iter = getInstanceIdsByClusterName(name).iterator();
		while (iter.hasNext()) {
			String id = iter.next();
			client.getInstanceApi().get()
					.terminateInstancesInRegion(region, id);
		}
	}

	private List<String> getInstanceIdsByClusterName(String name) {
		ArrayList<String> instanceList = new ArrayList<String>();

		Set<? extends Reservation<? extends AWSRunningInstance>> reservations = client
				.getInstanceApi().get().describeInstancesInRegion(region);
		int instanceCount = reservations.size();
		for (int x = 0; x < instanceCount; x++) {
			Reservation<? extends AWSRunningInstance> instances = Iterables
					.get(reservations, x);
			int groupCount = instances.size();
			for (int y = 0; y < groupCount; y++) {
				RunningInstance ri = Iterables.get(instances, y);
				if (ri.getTags().containsKey("Name")) {
					if (ri.getTags().get("Name").equals(name)) {
						instanceList.add(ri.getId());
					}
				}
			}
		}
		return instanceList;
	}

}
