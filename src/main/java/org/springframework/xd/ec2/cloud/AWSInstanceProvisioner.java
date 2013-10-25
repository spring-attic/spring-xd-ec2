package org.springframework.xd.ec2.cloud;

import static org.jclouds.ec2.options.RunInstancesOptions.Builder.asType;

import org.jclouds.ec2.EC2Client;
import org.jclouds.ec2.domain.InstanceType;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

	public Reservation<? extends RunningInstance> runInstance(EC2Client client,
			String script, int numberOfInstances) {
		Reservation<? extends RunningInstance> reservation = client
				.getInstanceServices().runInstancesInRegion(null, null,
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
