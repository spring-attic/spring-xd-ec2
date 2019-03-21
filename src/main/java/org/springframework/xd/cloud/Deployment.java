/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.cloud;

import java.net.InetAddress;

/**
Represents a provisioned instance and its status.  
 * 
 * @author Glenn Renfro
 */
public class Deployment {

	private final InetAddress address;

	private final InstanceType type;

	private final DeploymentStatus status;

	public Deployment(InetAddress address, InstanceType type,
			DeploymentStatus status) {
		super();
		this.address = address;
		this.type = type;
		this.status = status;
	}

	public InetAddress getAddress() {
		return address;
	}

	public InstanceType getType() {
		return type;
	}

	public DeploymentStatus getStatus() {
		return status;
	}
}
