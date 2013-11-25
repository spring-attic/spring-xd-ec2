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

package org.springframework.xd.ec2.environment;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * This class is used to configure container nodes. Configuration includes
 * setting up the container to reference the XD Admin Server, Redis and rabbit
 * instances. This class is copied to the container at which time the setup
 * script will execute this class.
 * 
 * @author glenn renfro
 * 
 */
public class ConfigureSystem {

	public static void main(String[] args) {
		ConfigureSystem configureSystem = new ConfigureSystem();
		Properties props = configureSystem.getCommandLineProperties(args);

		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(configureSystem.getBashRC(), true)));
			Iterator<Entry<Object,Object>>iter = props.entrySet().iterator();
		    out.println("\n Updated by XD Configurer");
			while(iter.hasNext()){
				Entry<Object,Object> entry = iter.next();
				out.print("\n export ".concat((String) entry.getKey()).concat("=").concat((String) entry.getValue()));
			}
		    out.println("\n");
		    out.close();
		} catch (IOException e) {
		   e.printStackTrace();
		}
		System.out.println("Complete");

	}

	private String getBashRC(){
			return "/home/ubuntu/.bashrc";
	}

	private Properties getCommandLineProperties(String []args){
		Properties props = new Properties();
		for(String arg:args){
			if(!arg.startsWith("--")||arg.indexOf('=')<0){
				continue;
			}
			arg = arg.substring(2);
			StringTokenizer tokenizer = new StringTokenizer(arg,"=");
			props.put(tokenizer.nextToken(),tokenizer.nextToken());
		}
		return props;
	}
}
