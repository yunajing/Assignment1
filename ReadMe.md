Read Me
Team Member: yj2270 Yuna Jing, yf2289 Yangliu Feng.
Part I
This part implements the on-demand infrastructure. Two machines are created firstly, both attached with EBS and S3.  We create some workload manually by SSH for each machine to make them busy. The machines are monitered by CloudWatch.  Moerover, we set Daytime and Nighttime. When machines are idel or nighttime comes, we detach volumes, snapshot each machine, create AMIs and terminate them. When next daytime comes, machines are recovered from AMIs. Volumes and S3 are attached to them as well. The maximum day we set for test is 2. 

Part II
This part is implemented using common line only. The commands we used are in the file called Part II. What we did in this part is to specify a certain type of machine that we use to auto-scale and create a auto-scale group. Then we define two policies, one to create instance, the other to shut down machine. After creating the two policies, we set up two cloud watch to monitor the instances. On monitor do policy one action when CPU utilization gets greater than the threshold, the other executes policy two when CPU utilization gets lower than the threshold. 