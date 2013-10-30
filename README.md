spring-xd-ec2
=============
This project simply allows you to deploy an XD cluster to ec2.  

Compiling
----------
* Once you have pulled the project from github go to the project root directory.
* To create an zip distribution execute the following: gradle distZip
	* Once the compile is complete the distribution zip will be located in:
	 ${spring-xd-ec2}/build/distributions/spring-xd-ec2-1.0.zip

Installing
----------
* Unzip spring-xd-ec2-1.0.zip
* cd to the spring-ec2-1.0 directory
* edit the config/xd-ec2.properties
	* set aws-access-key property to your AWS Access Key
	* set aws-secret-key property to your AWS Secret Key
	* set the cluster-name property to the name you want to show up in the EC2 Instance console
	* set the user-name property to your name
	* set the description property, to define the purpose of this cluster.
	* Save 

Running
----------
* From the spring-xd-ec2 directory run the install: ./bin/spring-xd-ec2
	* When the install is complete it will list the DNS name to your newly created instance. 

properties
----------
