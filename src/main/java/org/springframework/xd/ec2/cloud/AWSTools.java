package org.springframework.xd.ec2.cloud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jclouds.ContextBuilder;
import org.jclouds.aws.ec2.AWSEC2Client;
import org.jclouds.aws.ec2.domain.AWSRunningInstance;
import org.jclouds.ec2.domain.IpPermission;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.jclouds.ec2.domain.SecurityGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

public class AWSTools {
	static final Logger LOGGER = LoggerFactory.getLogger(AWSTools.class);

	private transient String awsAccessKey;
	private transient String awsSecretKey;
	private transient String region;

	private transient HashSet<String> ipSet;

	private AWSEC2Client client;

	public AWSTools(Properties properties) {
		awsAccessKey = properties.getProperty("aws-access-key");
		awsSecretKey = properties.getProperty("aws-secret-key");
		region = properties.getProperty("region");
		ipSet = new HashSet<String>();
		ipSet.add("0.0.0.0/0");

		client = ContextBuilder.newBuilder("aws-ec2")
				.credentials(awsAccessKey, awsSecretKey)
				.buildApi(AWSEC2Client.class);
	}

	public String shutdown(String name) {
		Iterator<String> iter = getInstanceIdsByClusterName(name).iterator();
		while (iter.hasNext()) {
			String id = iter.next();
			System.out.println(id);
			client.getInstanceServices().terminateInstancesInRegion(region,
					id);
		}
		return null;
	}

	private List<String> getInstanceIdsByClusterName(String name) {
		ArrayList<String> instanceList = new ArrayList<String>();
		client.getInstanceServices();
		Set<? extends Reservation<? extends AWSRunningInstance>> reservations = client
				.getInstanceServices().describeInstancesInRegion(region);
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

	public void resetGroupPermissions(String name) {
		client.getInstanceServices();
		Set<SecurityGroup> securityGroups = client.getSecurityGroupServices()
				.describeSecurityGroupsInRegion(region);
		Iterator<SecurityGroup> iter = securityGroups.iterator();
		while (iter.hasNext()) {
			SecurityGroup group = iter.next();
			if (name.equals(group.getName())) {
				LOGGER.info("Updating group " + group.getId() + " Name==> "
						+ group.getName());

				Iterator<IpPermission> ipIter = group.iterator();
				ArrayList<IpPermission> permissions = new ArrayList<IpPermission>();

				while (ipIter.hasNext()) {
					IpPermission permission = ipIter.next();
					IpPermission newPermission = new IpPermission(
							permission.getIpProtocol(),
							permission.getFromPort(), permission.getToPort(),
							permission.getUserIdGroupPairs(),
							permission.getGroupIds(), ipSet);
					permissions.add(newPermission);
					client.getSecurityGroupServices()
							.revokeSecurityGroupIngressInRegion(region,
									group.getId(), permission);
				}

				client.getSecurityGroupServices()
						.authorizeSecurityGroupIngressInRegion(region,
								group.getId(), permissions);
			}
		}

		LOGGER.info("Complete");
	}
}
