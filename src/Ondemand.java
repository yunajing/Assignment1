import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AllocateAddressResult;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairResult;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.PutObjectRequest;


public class Ondemand {
	
	//Time to get statistics from cloud watch
	public static int cloudwatchtime = 20;
	//Maximum days for a VM to exist
	public static int maxDays = 2;
	//Night and day duration in seconds
	public static int nightTime = 5*30;
	public static int dayTime = 8*60;
	//Threshold of a computer being idle
	public static double cpuIdle = 0.00; 
	/*
	 * private key storage path
	 */
	public static String keyPath = "";
	
	//Upper/lower threshold
	public static double upperTh = 50.0;
	public static double lowerTh = 20.0;
	
	 static KeyPair privateKey;
	 static int groupnum = 15;
	
	public static void main(String[] args) throws Exception{
		
		AWSCredentials credentials = new PropertiesCredentials(
   			 Ondemand.class.getResourceAsStream("AwsCredentials.properties"));
		
        AmazonEC2 ec2 = new AmazonEC2Client(credentials);
        AmazonS3Client s3 = new AmazonS3Client(credentials);
        AmazonCloudWatchClient cloudWatch = new AmazonCloudWatchClient(credentials);
        
           String counter = "1";
    	   String securitygroup = "assignment1-" + counter;
    	   String keypair = "workkey" + counter;
    	   String zone = "us-east-1a";
    	   String bucketname = "assign1bucket" + counter;
    	   String imageID = "ami-76f0061f";
    	   
    	   createSecurityGroup(ec2, securitygroup);
    	   createKey(ec2, keypair);
    	   createS3Bucket(s3, bucketname,zone);
    	   createS3File(s3,bucketname);
    	   
    	   OndemandEC2 worker1 = new OndemandEC2(securitygroup,keypair,imageID,zone,"worker1");
    	   OndemandEC2 worker2 = new OndemandEC2(securitygroup,keypair,imageID,zone,"worker2");
    	   
    	   List<OndemandEC2> machines = Arrays.asList(worker1,worker2);
    	   
    	   int days = 1;
    	   int count = 0;
    	   
    	   while (true){
    		   
    		   if (startTime(count)){
    			   count++;
    			   System.out.println("Daytime begins. This is : DAY "+days);
    			   
    			   //create the instances for each user
    			   for(OndemandEC2 machine : machines)
    				   createMachine(machine, bucketname);
    			   
    			   System.out.println("Now all machines are created.\r\nPlease wait for initialization.");
    			   
    			   //if (days <=1){
    			   	   
    				   Thread.sleep(2*60*1000);
    				   System.out.println("Start working now.");
    				   increaseCPU(worker1, keypair);  //ssh initialization, start working
    				   increaseCPU(worker2, keypair);
    			 //  }		   
    			   
    			   sleep(cloudwatchtime*2); 
    			   
    			   continue;  
    		   }
    		   else if (endTime(count)){
    			   count = 0;
    			   days++;
    			   
    			   //Time to end, terminate all machines
    			   System.out.println("End of Daytime. Terminate all machines.\r\nNighttime begins.");
    			   for (OndemandEC2 machine : machines){
    				   closeMachine(machine);
    			   }
    			   
    			   if (days>maxDays){
    				   System.out.println("Reach the maximum day of working!");
    				   break;
    			   }
    			   
    			   sleep(nightTime);
    			   continue;
    		   }
    		   
    		   count++;
    		   
    		   if (count == 8){ 
    			   if (!worker1.getIsTerminated(false))
    			   releaseCPU(worker1, keypair);
    			   if (!worker2.getIsTerminated(false))
    			   releaseCPU(worker2, keypair);
    		   } 
    		   
    		   //Terminate idle machines
    		   for(OndemandEC2 machine : machines){
    			   if(isIdle(cloudWatch, machine, cpuIdle)){
    					   System.out.println(machine.machinename + " is idle, terminate it.");  
    					   closeMachine(machine);
    				   }   				   
    			   }   	   
    		   int count3= count-1;
    		   System.out.println("Hours of running: " + count3);
    		   
    		   sleep(cloudwatchtime);
    	   }
    	   
    	   ec2.shutdown();
    	   s3.shutdown();
    	   cloudWatch.shutdown();
	}
	
	
	public static void createSecurityGroup(AmazonEC2 ec2, String groupname){
		
		CreateSecurityGroupRequest createSecurityGroupRequest = new CreateSecurityGroupRequest();
        createSecurityGroupRequest.withGroupName(groupname).withDescription("Assignment1");
        ec2.createSecurityGroup(createSecurityGroupRequest);
        AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = new AuthorizeSecurityGroupIngressRequest();
        authorizeSecurityGroupIngressRequest.setGroupName(groupname);
        
        /**********
         * IP permissions 
         * **********/
        //http
        IpPermission http = new IpPermission();
        http.setIpProtocol("tcp");
        http.setFromPort(80);
        http.setToPort(80);
        List<String> ipRangeshttp = new ArrayList<String>();
        ipRangeshttp.add("0.0.0.0/0");
        http.setIpRanges(ipRangeshttp);
        
        //ssh
        IpPermission ssh = new IpPermission();
        ssh.setIpProtocol("tcp");
        ssh.setFromPort(22);
        ssh.setToPort(22);
        List<String> ipRangesssh = new ArrayList<String>();
        ipRangesssh.add("0.0.0.0/0");
        ssh.setIpRanges(ipRangesssh);
        
        //tcp 
        IpPermission tcp  = new IpPermission();
        tcp.setIpProtocol("tcp");
        tcp.setFromPort(0);
        tcp.setToPort(65535);
        List<String> ipRangestcp = new ArrayList<String>();
        ipRangestcp.add("0.0.0.0/0"); 
        tcp.setIpRanges(ipRangestcp);
        
        List<IpPermission> permissions = new ArrayList<IpPermission>();
        permissions.add(http);
        permissions.add(ssh);
        permissions.add(tcp);
        authorizeSecurityGroupIngressRequest.setIpPermissions(permissions);
        
        ec2.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);
        List<String> groupNames = new ArrayList<String>();
        groupNames.add(groupname);
        System.out.println("Security Group created successfully: " + groupname);
	}
	
	public static void createKey(AmazonEC2 ec2, String keypair) throws IOException{
		 
        CreateKeyPairRequest newKeyRequest = new CreateKeyPairRequest();
        newKeyRequest.setKeyName(keypair);
        CreateKeyPairResult keyresult = ec2.createKeyPair(newKeyRequest);
        
        privateKey = keyresult.getKeyPair();
      
        String fileName=keypair+".pem"; 
        File distFile = new File(fileName); 
        BufferedReader bufferedReader = new BufferedReader(new StringReader(privateKey.getKeyMaterial()));
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(distFile)); 
        char buf[] = new char[1024];        
        int len; 
        while ((len = bufferedReader.read(buf)) != -1) { 
                bufferedWriter.write(buf, 0, len); 
        } 
        bufferedWriter.flush(); 
        bufferedReader.close(); 
        bufferedWriter.close(); 
        
        System.out.println("Private key created successfully: " + keypair);
	}
	
	public static void createS3Bucket(AmazonS3Client s3, String bucketname, String zone){
		List <Bucket> bucketnames = s3.listBuckets();
		
		for (Bucket bucket : bucketnames){
			if (bucketname.equalsIgnoreCase(bucket.getName())){
				System.out.println("Bucket already exists");
				return;
			}
		}
		s3.createBucket(bucketname);
		System.out.println("Bucket created successfully: " + bucketname);
		
		return;
		
	}
	
	public static void createS3File(AmazonS3Client s3, String bucketname) throws IOException{
		
        //set key
        String key = "object-name.txt";
        
        //set value
        File file = File.createTempFile("temp", ".txt");
        //file.deleteOnExit();
        Writer writer = new OutputStreamWriter(new FileOutputStream(file));
        writer.write("This is a sample sentence.");
        writer.close();
        
        //put object - bucket, key, value(file)
        s3.putObject(new PutObjectRequest(bucketname, key, file));
        System.out.println("A file "+ key + " is put in to the bucket: " +bucketname);
	}
	
	private static Boolean startTime(int count){
		return count == 0;
	}
	
	private static Boolean endTime(int count){
		return count*cloudwatchtime > dayTime;
	}
	
	private static void createMachine(OndemandEC2 machine, String bucketname) throws IOException{
		
		System.out.println("Instance is creating...");
		machine.createInstane();
		
		sleep(10);  //Sleep before starting up
		
		machine.startUp();
		System.out.println("Attach EBS volume " +machine.volumeID +" to the instance " +machine.instanceID);
		machine.attachVolume();
		System.out.println("Attach S3 " +bucketname +" to the instance " +machine.instanceID);
		machine.attachS3(bucketname);
		
		
	}
	
	private static void closeMachine(OndemandEC2 machine){
		
		if(machine.getIsTerminated(true)){
			return;
		}
		
		System.out.println("Detach the EBS volume " +machine.volumeID);
		machine.detachVolume();
		System.out.println("Save Snapshot, please wait for available...");
		machine.saveSnapShot();
		do {
			sleep(10); //Wait for AMI available
			System.out.println("AMI Status is " +machine.getSnapShotState());
		}while(!machine.getSnapShotState().equalsIgnoreCase("available"));
		
		System.out.println("AMI is available now.\r\nTerminate the machine");
		machine.shutDown();

	}
	
	static void increaseCPU(OndemandEC2 worker, String keypair) throws InterruptedException {
		
		File file = new File(keypair + ".pem");
		
		try{
			Connection connection = new Connection(worker.ipAddress);
			connection.connect();
			
			boolean auth = connection.authenticateWithPublicKey("ec2-user", file, "none");
			if (auth == true)
				System.out.println("Successfully log in. \r\nIncrease CPU of "+worker.machinename);
			if (auth == false)
				throw new IOException("Authentification failed");
			
			Session session = connection.openSession();	
			session.execCommand("while true; do true; done");
			session.close();
			connection.close();
			
		}
		catch (IOException e){
			e.printStackTrace(System.err);
			System.out.println("Please use the attached script to start and stop cpu remotely");
		}
	}
	
	static void  releaseCPU(OndemandEC2 worker, String keypair) throws InterruptedException{
		File file = new File(keypair + ".pem");
		//String filePass = "none";
		
		try{
			Connection connection = new Connection(worker.ipAddress);
			connection.connect();
			
			boolean auth = connection.authenticateWithPublicKey("ec2-user", file, "none");
			if (auth == false)
				throw new IOException("Authentification failed");
			
			Session session = connection.openSession();
			System.out.println("Release CPU of "+ worker.machinename);
			session.execCommand("killall bash");
			session.close();
			connection.close();
			
		}
		catch (IOException e){
			e.printStackTrace(System.err);
			System.out.println("Please use the attached script to start and stop cpu remotely");
		}
	}
	
	public static void sleep(int sec){
		try{
			Thread.sleep(sec*1000);
		}
		catch(InterruptedException e){
			System.out.println("Caught Exception: " + e.getMessage());
		}
	}
	
	private static Boolean isIdle(AmazonCloudWatchClient cloudWatch, OndemandEC2 machine, double cpuIdle){
		
		if (machine.getIsTerminated(true))
			return false;
		double utilization = cpuUtil(cloudWatch, machine.machinename, machine.instanceID);
		if (utilization < 0) 
			return false;
		return utilization <= cpuIdle;  
	}

	public static double cpuUtil(AmazonCloudWatchClient cloudWatch, String machinename, String instanceID){

		try{
			
			//create request message
			GetMetricStatisticsRequest statRequest = new GetMetricStatisticsRequest();
			
			//set up request message
			statRequest.setNamespace("AWS/EC2"); //namespace
			statRequest.setPeriod(60); //period of data
			ArrayList<String> stats = new ArrayList<String>();
			
			//Use one of these strings: Average, Maximum, Minimum, SampleCount, Sum 
			stats.add("Average"); 
			stats.add("Sum");
			statRequest.setStatistics(stats);
			
			//Use one of these strings: CPUUtilization, NetworkIn, NetworkOut, DiskReadBytes, DiskWriteBytes, DiskReadOperations  
			statRequest.setMetricName("CPUUtilization"); 
			
			// set time
			GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
			calendar.add(GregorianCalendar.SECOND, -1 * calendar.get(GregorianCalendar.SECOND)); // 1 second ago
			Date endTime = calendar.getTime();
			calendar.add(GregorianCalendar.MINUTE, -5); // 5 minutes ago
			Date startTime = calendar.getTime();
			statRequest.setStartTime(startTime);
			statRequest.setEndTime(endTime);
			
			//specify an instance
			ArrayList<Dimension> dimensions = new ArrayList<Dimension>();
			dimensions.add(new Dimension().withName("InstanceId").withValue(instanceID));
			statRequest.setDimensions(dimensions);
			//System.out.println(dimensions);
			
			//get statistics
			GetMetricStatisticsResult statResult = cloudWatch.getMetricStatistics(statRequest);
			
			//display
			//System.out.println(statResult.toString());
			List<Datapoint> dataList = statResult.getDatapoints();
			Double averageCPU = null;
			Date timeStamp = null;
			for (Datapoint data : dataList){
				averageCPU = data.getAverage();
				timeStamp = data.getTimestamp();
				//System.out.println("Average CPU utililization for last 10 minutes: "+averageCPU);
				//System.out.println("Total CPU utililization for last 10 minutes: "+data.getSum());
			}
            
            if (averageCPU == null) {
            	System.out.println(machinename + " : average CPU utlilization for last hour is 0");
            	return 0;
            }
            else{
            	System.out.println( machinename + " : average CPU utlilization for last hour is "+ averageCPU);
            	return averageCPU;
            }
            
		} catch (AmazonServiceException ase) {
		    System.out.println("Caught Exception: " + ase.getMessage());
		    System.out.println("Reponse Status Code: " + ase.getStatusCode());
		    System.out.println("Error Code: " + ase.getErrorCode());
		    System.out.println("Request ID: " + ase.getRequestId());
		    return 0;
		}
	}
}
	











