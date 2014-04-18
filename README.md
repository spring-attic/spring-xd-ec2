spring-xd-ec2
=============
This project simply allows you to deploy an XD cluster to ec2.  

Compiling
----------
* Once you have pulled the project from github go to the project root directory.
* To create an zip distribution execute the following: ./gradlew distZip
	* Once the compile is complete the distribution zip will be located in:
	 ${spring-xd-ec2}/build/distributions/spring-xd-ec2-1.0.zip

Installing
----------
1) Unzip spring-xd-ec2-1.0.zip

2) cd to the spring-ec2-1.0 directory

3) edit the config/xd-ec2.properties

	* set aws-access-key property to your AWS Access Key
	* set aws-secret-key property to your AWS Secret Key
	* set private-key-file to the location of your RSA private key and matching public-key-name
	* set the cluster-name property to the name you want to show up in the EC2 Instance console
	* set the user-name property to your name
	* set the description property, to define the purpose of this cluster.
	* set the xd-dist-url to be a specific .zip located in the one of the Spring repositories
	  * http://repo.spring.io/simple/libs-snapshot-local/org/springframework/xd/spring-xd/1.0.0.BUILD-SNAPSHOT/
	  * http://repo.spring.io/simple/libs-milestone-local/org/springframework/xd/spring-xd
	* To run multiple nodes, set multi-node=true and set the number-nodes key to the number of nodes you want to run.
        * Optionally set machine-size,region
	* Save 

Running
----------
1) From the spring-xd-ec2 directory run the install: ./bin/spring-xd-ec2
	* When the install is complete it will list the DNS name to your newly created instance. e.g.

	Admin Node Instance: ec2-54-205-186-126.compute-1.amazonaws.com has been created
	Container Node Instance: ec2-54-197-79-170.compute-1.amazonaws.com has been created


properties
----------
XD allows a user to change it's behavior by updating environment variables.  Since XD-EC2 allows users to deploy a multi node xd instance it will allow you to set these environment variables on all the nodes.  This is done by adding the XD Environment variables you want updated to the xd-ec2.properties.  
For example if you  wanted to update the rabbit and amq locations you would add these to the bottom of your xd-ec2.properties file.

```
mqtt.url=tcp://ec2-54-205-58-170.compute-1.amazonaws.com:1883
mqtt.default.client.id=xd.mqtt.client.id
mqtt.username=guest
mqtt.password=guest
mqtt.default.topic=xd.mqtt.test

amq.url=tcp://ec2-54-205-58-170.compute-1.amazonaws.com:61616
```

Setting Up Job Repository
----------
Unlike singlenode deloyments, clusters (1 admin with 1+ containers) require that a job RDBMS datasource be specified for the admin server.
Through XD-EC2 this can be done by setting up the datasource properties in you xd-ec2.properties file.
For example:
```
#Database settings
spring.datasource.url=jdbc:mysql://xdjobasd.adsfadsa.us-east-1.rds.amazonaws.com:3306/xdjob
spring.datasource.username=myxdjob
spring.datasource.password=mypassword
spring.datasource.driverClassName=com.mysql.jdbc.Driver
```

Using
----------

1) SSH into the boxes using the Public DNS names that were listed at the end of the install process, e.g.

$ ssh -i xd-key-pair.pem ubuntu@ec2-54-205-186-126.compute-1.amazonaws.com






