package org.springframework.xd.cloud;

import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;

public interface InstanceProvisioner {
	/**
	 * Provisions n number of instances and executes the configuration script you specified.
	 * @param script
	 * @param numberOfInstances
	 * @return
	 */
	public Reservation<? extends RunningInstance> runInstance(String script,
			int numberOfInstances);
}
