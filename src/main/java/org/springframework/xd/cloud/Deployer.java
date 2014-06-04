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
	 */
	public List<Deployment> deploy();

}
