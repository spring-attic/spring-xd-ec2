package org.springframework.xd.ec2;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import javax.annotation.Resource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class EC2Util implements ApplicationContextAware {
	@Resource
	private ApplicationContext context;
	@Value("${cluster-name}")
	String clusterName;
	@Value("${aws-access-key}")
	String awsAccessKey;
	@Value("${aws-secret-key}")
	String awsSecretKey;
	@Value("${private-key-file}")
	String privateKeyFile;
	@Value("${multi-node}")
	String multiNode;
	@Value("${number-nodes}")
	String numberNodes;
	@Value("${machine-size}")
	String machineSize;
	@Value("${redis-port}")
	String redisPort;
	@Value("${rabbit-port}")
	String rabbitPort;
	@Value("${xd-dist-url}")
	String xdDistUrl;
	@Value("${ami}")
	String ami;

	public String getProperties(String propertyPath) throws IOException {
		Properties props = null;
		if (propertyPath == null) {
			props = new Properties();
			props.put("cluster-name", clusterName);
			props.put("aws-access-key", awsAccessKey);
			props.put("aws-secret-key", awsSecretKey);
			props.put("private-key-file", privateKeyFile);
			props.put("multi-node", multiNode);
			props.put("machine-size", machineSize);
			props.put("number-nodes", numberNodes);
			props.put("redis-port", redisPort);
			props.put("rabbit-port", rabbitPort);
			System.out.println(props);
			// propertyPath = context.getResource("xd-ec2.properties").getFile()
			// .getAbsolutePath();
		}
		File f = new File(propertyPath);
		if (!f.exists()) {
			throw new IllegalArgumentException();
		}
		System.out.println(clusterName);
		return propertyPath;
	}

	public void printBanner() {
		BufferedReader stream = null;
		try {
			InputStream inputStream = context.getResource("banner.txt")
					.getInputStream();
			stream = new BufferedReader(new InputStreamReader(inputStream));
			while (stream.ready()) {
				System.out.println(stream.readLine());
			}
		} catch (IOException ioe) {
			// Ignore as this is not essential for the application
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException ioe) {
					// Not essential, just keep going.
				}
			}
		}
	}

	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.context = applicationContext;

	}

}
