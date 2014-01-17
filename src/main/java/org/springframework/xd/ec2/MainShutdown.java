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
package org.springframework.xd.ec2;

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author glenn renfro
 * 
 */
public class MainShutdown {

	public static void main(String[] args) throws Exception {

		@SuppressWarnings("resource")
		AbstractApplicationContext ctx = new ClassPathXmlApplicationContext(
				"META-INF/xdinstaller-context.xml");
		// shutdown the context along with the VM
		ctx.registerShutdownHook();
		ctx.refresh();
		// Shutdown all instances with this cluster name
		Ec2Maintenance tools = ctx.getBean(Ec2Maintenance.class);
		String name = null;
		if (args.length == 2) {
			if (args[0].equals("--cluster-name")) {
				name = args[1];
			}
		}
		tools.shutdown(name);
	}

}
