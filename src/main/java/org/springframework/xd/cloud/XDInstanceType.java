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

package org.springframework.xd.cloud;

/**
 * @author glenn renfro
 * 
 */
public class XDInstanceType {
	private final String dns;
	private final String ip;
	private final InstanceType type;
	private final String status;

	public XDInstanceType(String dns, String ip, InstanceType type,
			String status) {
		super();
		this.dns = dns;
		this.ip = ip;
		this.type = type;
		this.status = status;
	}

	public String getDns() {
		return dns;
	}

	public String getIp() {
		return ip;
	}

	public InstanceType getType() {
		return type;
	}

	public enum InstanceType {
		SINGLE_NODE, ADMIN, NODE;
	}
}
