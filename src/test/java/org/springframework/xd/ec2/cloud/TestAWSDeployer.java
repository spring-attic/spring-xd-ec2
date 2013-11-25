package org.springframework.xd.ec2.cloud;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.jclouds.aws.ec2.AWSEC2Client;
import org.jclouds.aws.ec2.domain.AWSRunningInstance;
import org.jclouds.aws.ec2.services.AWSInstanceClient;
import org.jclouds.compute.ComputeService;
import org.jclouds.ec2.domain.Reservation;
import org.jclouds.ec2.domain.RunningInstance;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.xd.cloud.Deployment;

import com.google.common.base.Predicate;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration

public class TestAWSDeployer {

/*	AWSEC2Client  client;
	ComputeService computeService;
	AWSInstanceProvisioner instanceProvisioner;
	@Autowired
	AWSDeployer deployer;
	@Autowired
	AWSInstanceConfigurer configurer;
	
	RunningInstance runningInstance;
	AWSInstanceClient awsInstanceClient;
	AWSRunningInstance awsRunningInstance;
	AWSInstanceChecker checker;
*/	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
/*		client = mock(AWSEC2Client.class);
		computeService = mock(ComputeService.class);
		instanceProvisioner = mock(AWSInstanceProvisioner.class);
		runningInstance = mock(RunningInstance.class);
		awsRunningInstance = mock(AWSRunningInstance.class);
		awsInstanceClient = mock(AWSInstanceClient.class);
		checker = mock(AWSInstanceChecker.class);
*/
	}

	@After
	public void tearDown() throws Exception {
	}


	@Test
	public void testSingleNodeHappyPath() throws TimeoutException{
//Setup the test variables
/*		ArrayList<String> groups = new ArrayList<String>();
		ArrayList<AWSRunningInstance> instances = new ArrayList<AWSRunningInstance>();
		groups.add("group1");
		instances.add(awsRunningInstance);
		Reservation<AWSRunningInstance> reservation = new Reservation<AWSRunningInstance>("FOO", groups, instances, "foo", "bar", "1234"); 
		Predicate<RunningInstance> runningTester = (Predicate<RunningInstance>)mock(Predicate.class);
		HashSet<Reservation<AWSRunningInstance>> set = new HashSet<Reservation<AWSRunningInstance>>();
		set.add(reservation);

//mock up result		
		
		when(runningTester.apply((RunningInstance)anyObject())).thenReturn(true);
		
		when(instanceProvisioner.runInstance( configurer.createStartXDResourcesScript(), 1)).thenReturn(Reservation.class.cast(reservation));
		when(client.getInstanceServices()).thenReturn(awsInstanceClient);
		when(awsInstanceClient.describeInstancesInRegion(anyString(), anyString())).thenReturn(set);
		
//		when(checker.checkServerResources(any(RunningInstance.class), any(AWSEC2Client.class), any(ComputeService.class))).thenReturn(runningInstance);

		deployer.setClient(client);
		deployer.setComputeService(computeService);
		deployer.setInstanceProvisioner(instanceProvisioner);
		deployer.setAwsInstanceChecker(checker);
		

		List<Deployment> result = deployer.deploy();
		
		assertEquals(1, result.size());
	*/
	}

	
	@Test
	public void testDeployMultiNode() throws TimeoutException{
/*		deployer.setMultiNode("true");
		List<Deployment> result = deployer.deploy();
		assertEquals(1, result.size());
*/
	}

	@Test
	public void testDeploySingleNode() {

	}

}
