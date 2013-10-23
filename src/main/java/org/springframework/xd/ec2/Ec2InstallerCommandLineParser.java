package org.springframework.xd.ec2;

import org.springframework.core.env.SimpleCommandLinePropertySource;


public class Ec2InstallerCommandLineParser extends SimpleCommandLinePropertySource {

	public Ec2InstallerCommandLineParser(String[] args){
		super(args);
	}
	public String getPropertyFilePath() throws java.lang.IllegalArgumentException
	{
		if(!this.getNonOptionArgs().isEmpty()){
			throw new IllegalArgumentException();
		}
		return this.getProperty("p");
	}
}
