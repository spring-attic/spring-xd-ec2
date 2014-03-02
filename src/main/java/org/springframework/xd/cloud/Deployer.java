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

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Deployers install XD into a cloud. This includes copying all necessary files,
 * launching resources, configuring XD and finally launching XD.
 * 
 * @author glenn renfro
 * 
 */
public interface Deployer {
	/**
	 * Executes associated deployment based on the properties.
	 * 
	 * @return A list of Instances that were successfully created. And their
	 *         status.
	 * @throws TimeoutException
	 * @throws ServerFailStartException 
	 */
	public List<Deployment> deploy() throws TimeoutException, ServerFailStartException;

	/**
	 * Deploys a single node instance of XD.
	 * 
	 * @param script
	 *            - The script built by JClouds Script Builder that initializes
	 *            the Node
	 * @return The instance information for a successfully created XD-Node
	 * @throws TimeoutException
	 * @throws ServerFailStartException 
	 */
	public Deployment deploySingleNode(String script)
			throws TimeoutException, ServerFailStartException;

	/**
	 * Deploys a Admin instance of XD.
	 * 
	 * @param script
	 *            - The script built by JClouds Script Builder that initializes
	 *            the Admin Server
	 * @return The instance information for a successfully created Admin Server
	 * @throws TimeoutException
	 * @throws ServerFailStartException 
	 */
	public Deployment deployAdminServer(String script)
			throws TimeoutException, ServerFailStartException;

	/**
	 * Deploys the node instances for XD.
	 * 
	 * @param script
	 *            - The admin server this container will be associated.
	 * @return A list of instances and whether they were successfully created or
	 *         not.
	 * @throws TimeoutException
	 */
	public List<Deployment> deployContainerServers(String hostName) throws TimeoutException;

}
