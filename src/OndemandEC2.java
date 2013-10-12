import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AllocateAddressResult;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.CreateImageRequest;
import com.amazonaws.services.ec2.model.CreateImageResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DetachVolumeRequest;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.opsworks.model.StartInstanceRequest;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;


public class OndemandEC2 {
	AmazonEC2 ec2;
	AmazonS3Client s3;
	AWSCredentials credentials;
	String instanceID;
	String keyname;
	String groupname;
	String machinename;
	String zone;
	String imageID;
	String volumeID;
	String ipAddress;
	Boolean isTerminated = true;
	
    public OndemandEC2(String securitygroup, String keypair, String imageid,String zone1, String computername) throws IOException, InterruptedException{
    	credentials = new PropertiesCredentials(
				OndemandEC2.class.getResourceAsStream("AwsCredentials.properties"));
		
		ec2 = new AmazonEC2Client(credentials);
		s3  = new AmazonS3Client(credentials);		
		
		groupname = securitygroup;
		keyname = keypair;
		machinename = computername;
		zone = zone1;
		imageID = imageid;
		this.createVolume(10);
    	}
    
    public void createInstane(){
    	
		Placement place = new Placement();
		place.setAvailabilityZone(zone);
		
    	int minInstanceCount = 1; 
        int maxInstanceCount = 1;
        RunInstancesRequest rir = new RunInstancesRequest(imageID, minInstanceCount, maxInstanceCount);
        
        rir.setMonitoring(true); //open monitering
        rir.setPlacement(place);
        rir.setInstanceType("t1.micro");
        rir.setKeyName(keyname);// set key pair
        
        List<String> securitygroups = new ArrayList<String>();
        securitygroups.add(groupname);
        rir.setSecurityGroups(securitygroups);// set security group
        RunInstancesResult result = ec2.runInstances(rir);
        
        //get instanceId from the result
        List<Instance> resultInstance = result.getReservation().getInstances();
        InstanceState state = null;
        
        int instancenum = resultInstance.size();
        Instance ins = resultInstance.get(instancenum-1);
        instanceID = ins.getInstanceId();
        state = ins.getState();
        
        System.out.println("The instance ID is " +instanceID);
        
        List<String> resources = new LinkedList<String>();
        List<Tag> tags = new LinkedList<Tag>();
        Tag nameTag = new Tag("Name", "Assignment1");
        
        resources.add(instanceID);
        tags.add(nameTag);
        
        CreateTagsRequest ctr = new CreateTagsRequest(resources, tags);
        ec2.createTags(ctr);
        
        //Wait for the new machine to run
        while(state.getName().equalsIgnoreCase("pending")){
        	try{
        		
        		Thread.sleep(20000);
        		//System.out.println(state.getName());
        		}
        		catch (InterruptedException e){
        			e.printStackTrace();
        		}	
        	state = this.getInstance().getState();
        }
        
        if (this.ipAddress == null){
        	this.createElasticIp();
        }
        //System.out.println("Machine " + machinename +"created ip");
        this.assignElasticIp();
        
        isTerminated = false;
        System.out.println("Instance created: " + machinename + " with ID = " + this.instanceID + " IP = " + this.ipAddress + " and AMI = " + this.imageID);
        System.out.println("PublicDNS = "+this.getInstance().getPublicDnsName());
    }
    
    public Instance getInstance(){
    	
        DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();
        Set<Instance> instances = new HashSet<Instance>();
        // add all instances to a Set.
        for (Reservation reservation : reservations) {
        	instances.addAll(reservation.getInstances());
        }
                
        for ( Instance ins : instances){
           	if ( instanceID.equalsIgnoreCase(ins.getInstanceId()) == true ){
        		return ins;
        	}
        }
        
        return null;
        
    }
    
    public void createElasticIp(){
    	AllocateAddressResult elasticResult = ec2.allocateAddress();
		ipAddress = elasticResult.getPublicIp();	
    }
    
    public void assignElasticIp(){ 	
    	AssociateAddressRequest aar = new AssociateAddressRequest();
		aar.setInstanceId(instanceID);
		aar.setPublicIp(ipAddress);
		ec2.associateAddress(aar);   	
    }
    
    public void disassociateElasticIp(){	
		DisassociateAddressRequest dar = new DisassociateAddressRequest();
		dar.setPublicIp(this.ipAddress);
		ec2.disassociateAddress(dar);
	}
    
    public void  shutDown(){
    	List<String> tmp = new ArrayList<String>();
    	tmp.add(instanceID);
    	TerminateInstancesRequest tir = new TerminateInstancesRequest(tmp);
        ec2.terminateInstances(tir);   	
    }
    
    public void startUp(){
    	List<String> tmp = new ArrayList<String>();
    	tmp.add(instanceID);
    	StartInstancesRequest sir = new StartInstancesRequest(tmp);
    	ec2.startInstances(sir);
    }
    
    public void createVolume(int size){
    	CreateVolumeRequest cvr = new CreateVolumeRequest();
        cvr.setAvailabilityZone(this.zone);
        cvr.setSize(size);
    	CreateVolumeResult volumeResult = ec2.createVolume(cvr);
    	String createdVolumeId = volumeResult.getVolume().getVolumeId();
    	this.volumeID = createdVolumeId;
    }
    
    public void attachVolume(){
    	AttachVolumeRequest avr = new AttachVolumeRequest();
    	//System.out.println(this.volumeID);
    	avr.setVolumeId(this.volumeID);
    	avr.setInstanceId(this.instanceID);
    	avr.setDevice("/dev/sdf");
    	ec2.attachVolume(avr);
    }
    
    public void detachVolume(){
    	DetachVolumeRequest dvr = new DetachVolumeRequest();
    	dvr.setVolumeId(this.volumeID);
    	dvr.setInstanceId(this.instanceID);
    	ec2.detachVolume(dvr);
    }
    
    
   	public void attachS3(String bucketName) throws IOException {
           //set key
           String key = "object-name.txt";
                   
           //get object
           S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));
           BufferedReader reader = new BufferedReader(
           	    new InputStreamReader(object.getObjectContent()));
           String dataS3 = null;
           System.out.print(this.machinename + ": ");
           while ((dataS3 = reader.readLine()) != null) {
               System.out.println(dataS3);
           }
   	}
   	
    public String saveSnapShot() {
	
	    CreateImageRequest cir = new CreateImageRequest();
	    cir.setInstanceId(this.instanceID);
	    cir.setName(this.instanceID + "__AMI"); //we can choose the name
	    
	    CreateImageResult createImageResult = ec2.createImage(cir);
	    String createdImageId = createImageResult.getImageId();
	    
	    System.out.println("AMI is created. AMI id=" + createdImageId);
	    
	    //Save the AMI
	    this.imageID = createdImageId;
	    
	    return createdImageId;
    }

    //Should be invoked after the saveSnapShot has been called
    public String getSnapShotState() {
		DescribeImagesRequest dir = new DescribeImagesRequest();
		dir.setImageIds(Arrays.asList(this.imageID));
		String state = ec2.describeImages(dir).getImages().get(0).getState();
		
		return state;
    }

    //Returns a bool value that indicates whether the machine is terminated
    public Boolean getIsTerminated(Boolean getFromService) {
    	if (!getFromService) return this.isTerminated;
	
    	Instance ins = this.getInstance();
    	if (ins == null || ins.getState().getCode() > 16) 
    		return true;
    	return false;
    }
}
