package com.nimbusrun.compute.aws.v1;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.ComputeConfigResponse;
import com.nimbusrun.compute.Constants;
import com.nimbusrun.compute.DeleteInstanceRequest;
import com.nimbusrun.compute.GithubApi;
import com.nimbusrun.compute.ListInstanceResponse;
import com.nimbusrun.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.Ec2ClientBuilder;
import software.amazon.awssdk.services.ec2.model.*;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AWSComputeService extends Compute {
    private static final Logger log = LoggerFactory.getLogger(AWSComputeService.class);
    public static final String DEFAULT_DISK_TYPE = "gp3";
    private static final Integer DEFAULT_DISK_SIZE = 20;
    private final GithubApi githubService;
    private AwsConfig clientConfig;
    private Map<String, AwsConfig.ActionPool> awsActionPool = new HashMap<>();
    private final Map<String, String> DEFAULT_INSTANCE_LABELS = new HashMap<>();
    private final String APPLICATION_NAME_LABEL_KEY = "NimbusRun";
    private Map<String, Ec2Client> actionPoolToEc2Client = new HashMap<>();
    private final Cache<Region, String> regionUbuntuAmiCache;
    //    private String applicationName;
    public AWSComputeService(GithubApi githubService) {
        this.githubService = githubService;
        regionUbuntuAmiCache = Caffeine.newBuilder()
                .maximumSize(1_000_000)
                .expireAfterWrite(Duration.ofHours(24))
                .build();

    }


    @Override
    public ListInstanceResponse listComputeInstances(ActionPool autoScalePool) {
        List<Instance> instanceIds = new ArrayList<>();
        AwsConfig.ActionPool actionPool = this.awsActionPool.get(autoScalePool.getName());
        Ec2Client ec2 = actionPoolToEc2Client.get(actionPool.getName());
        Filter notTerminatedFilter = Filter.builder().name("instance-state-name")
                .values(
                        InstanceStateName.PENDING.toString(),
                        InstanceStateName.RUNNING.toString(),
//                            InstanceStateName.SHUTTING_DOWN.toString(),
                        InstanceStateName.STOPPING.toString(),
                        InstanceStateName.STOPPED.toString()
                )
                .build();
        List<Filter> filters = new ArrayList<>();
        filters.add(notTerminatedFilter);
        filters.addAll(tagFilters(actionPool.getName()));
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(filters)
                .build();

        DescribeInstancesResponse response = ec2.describeInstances(request);

        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                instanceIds.add(instance);
            }
        }


        return new ListInstanceResponse(instanceIds.stream()
                .map(i -> {
                    String name = i.tags()
                            .stream()
                            .filter(t -> t.key().equalsIgnoreCase("name"))
                            .map(Tag::value)
                            .findAny()
                            .orElse(i.instanceId() + "");
                    return new ListInstanceResponse.Instance(name, i.instanceId(), name, i.launchTime().toEpochMilli());
                }).toList());
    }

    /**
     * Unfortunately we cannot aggregate the regions and list all the instance for that region with the {@link AWSComputeService#DEFAULT_INSTANCE_LABELS}.
     * because each action pool could be using a user/role restricted to just one region. :(.
     *
     * @return a map of action pool name to instances associated with them
     */
    @Override
    public Map<String, ListInstanceResponse> listAllComputeInstances() {
        Map<String, ListInstanceResponse> responseMap = new HashMap<>();
        try {
            for (String actionPoolName : this.awsActionPool.keySet()) {
                responseMap.put(actionPoolName, listComputeInstances(this.awsActionPool.get(actionPoolName).toAutoScalerActionPool()));
            }
        } catch (Exception e) {
        }
        return responseMap;
    }


    @Override
    public boolean deleteCompute(DeleteInstanceRequest deleteInstanceRequest) {
        AwsConfig.ActionPool actionPool = this.awsActionPool.get(deleteInstanceRequest.getActionPool().getName());

        try  {
            Ec2Client ec2 = actionPoolToEc2Client.get(actionPool.getName());
            TerminateInstancesRequest request = TerminateInstancesRequest.builder()
                    .instanceIds(deleteInstanceRequest.getInstanceId())
                    .build();

            TerminateInstancesResponse response = ec2.terminateInstances(request);

            log.info("Termination initiated for instance {}. Current state: {}",
                    deleteInstanceRequest.getInstanceId(),
                    response.terminatingInstances().get(0).currentState().name());

        } catch (Ec2Exception e) {
            log.error("EC2 termination failed: {}", e.awsErrorDetails().errorMessage());
            return false;
        }
        return true;
    }

    @Override
    public List<ActionPool> listActionPools() {
        return List.of();
    }

    @Override
    public ComputeConfigResponse receiveComputeConfigs(Map<String, Object> map, String autoScalerName) {
        AwsConfig awsConfig = new AwsConfig().createAwsConfig(new Yaml().dump(map));
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<AwsConfig.ActionPool> actionPools = new ArrayList<>();
        actionPools.addAll(awsConfig.getActionPools());
        actionPools.add(awsConfig.getDefaultActionPool());
        validateActionPools(actionPools, errors, warnings);
        clientConfig = awsConfig;
        this.awsActionPool = actionPools.stream().collect(Collectors.toMap(AwsConfig.ActionPool::getName, Function.identity(), (a, b) -> a));
        DEFAULT_INSTANCE_LABELS.put(APPLICATION_NAME_LABEL_KEY, autoScalerName);
        generateEc2ClientPerActionPool(this.awsActionPool);
        return new ComputeConfigResponse(errors, warnings, actionPools.stream().map(AwsConfig.ActionPool::toAutoScalerActionPool).toList());
    }
    public void generateEc2ClientPerActionPool(Map<String, AwsConfig.ActionPool> actionPoolMap){
        for(AwsConfig.ActionPool ap : actionPoolMap.values() ){
            this.actionPoolToEc2Client.put(ap.getName(),AwsClients.ec2Client(ap.getCredentialsProfileOpt(), Region.of(ap.getRegion())));
        }
    }


    private void validateActionPools(List<AwsConfig.ActionPool> actionPools, List<String> errors, List<String> warnings) {
        Map<String, Long> nameHistogram = actionPools.stream().collect(Collectors.groupingBy(AwsConfig.ActionPool::getName, Collectors.counting()));
        nameHistogram.keySet().stream().filter(key -> nameHistogram.get(key) > 1).forEach(key ->
                errors.add("Action pool name: %s appears multiple %s times. Names must be unique".formatted(key, nameHistogram.get(key))));
        for (int i = 0; i < actionPools.size(); i++) {
            AwsConfig.ActionPool actionPool = actionPools.get(i);
            String name = "%s#".formatted(i);
            if (actionPool.getName() == null) {
                errors.add("Action Pool %s is missing Name".formatted(name));
            } else {
                name = actionPool.getName();
            }
            if (actionPool.getRegion() == null) {
                errors.add("Action Pool %s is missing region".formatted(name));
            } else if (regionFromString(actionPool.getRegion()).isEmpty()) {
                errors.add("Invalid Region: %s for action: %s".formatted(actionPool.getRegion(), name));
            }
            if (actionPool.getSubnet() == null) {
                errors.add("Action Pool %s missing subnet. Please add to defaultSettings or on Action Pool".formatted(name));
            }
            if (actionPool.getSecurityGroup() == null) {
                errors.add("Action Pool %s missing security group. Please add to defaultSettings or on Action Pool".formatted(name));
            }
            if (actionPool.getCredentialsProfile() == null) {
                warnings.add("Action Pool %s not configured credentials profile. Using Default Credentials Provider".formatted(name));
            }
            if (actionPool.getDiskSettings() == null) {
                warnings.add("Action Pool %s not configured with diskSettings. Using default disk configurations".formatted(name));
            }
            if (actionPool.getInstanceType() == null) {
                errors.add("Action Pool %s not configured with instanceType. Please add to defaultSettings or on Action Pool".formatted(name));
            } else if (InstanceType.fromValue(actionPool.getInstanceType()) == InstanceType.UNKNOWN_TO_SDK_VERSION) {
                warnings.add("Action Pool %s has unknown  instanceType: \"%s\".".formatted(name, actionPool.getInstanceType()));
            }
            if (actionPool.getMaxInstanceCount() == null) {
                errors.add("Action Pool %s not configured with maxInstanceCount. Please add to defaultSettings or on Action Pool".formatted(name));
            }
            if (actionPool.getIdleScaleDownInMinutes() == null) {
                warnings.add("Action Pool %s not configured with idleScaleDownInMinutes. Please add to defaultSettings or on Action Pool".formatted(name));
            }
        }
    }



    private List<Tag> generateInstanceTags(String actionPoolName){
        return generateInstanceTags(actionPoolName, null);
    }

    private List<Tag> generateInstanceTags(String actionPoolName, String runnerName){
        Map<String, String> map = new HashMap<>();
        map.putAll(DEFAULT_INSTANCE_LABELS);
        map.put(Constants.ACTION_POOL_LABEL_KEY,actionPoolName);
        if(runnerName != null){
            map.put("Name", runnerName);
        }
        return map.keySet().stream().map(k->Tag.builder().key(k).value(map.get(k)).build()).toList();
    }
    private List<Filter> tagFilters(String actionPoolName){
        List<Tag> tags = this.generateInstanceTags(actionPoolName);
        return tags.stream().map(t-> Filter.builder().name("tag:"+t.key()).values(t.value()).build()).toList();
    }

    @Override
    public boolean createCompute(ActionPool autoScalerActionPool) {
        AwsConfig.ActionPool actionPool = this.awsActionPool.get(autoScalerActionPool.getName());

        // Configure AWS client
        Ec2Client ec2 = actionPoolToEc2Client.get(actionPool.getName());

        try {

            Region region = regionFromString(actionPool.getRegion()).get();
            // Set up instance parameters
            String amiId = regionUbuntuAmiCache.get(region, (r)->latestAmi(ec2).orElse(null)); // Ubuntu 24.04 LTS AMI ID for us-east-1
            if(amiId == null){
                log.error("Ubuntu AMI does not exist for region {}", actionPool.getRegion());
                return false;
            }
            // Create startup script for GitHub runner
            Optional<String> runnerToken = this.githubService.generateRunnerToken();
            if(runnerToken.isEmpty()){
                log.error("Failed to retrieve github runner token");
                return false;
            }
            String runnerName = this.createInstanceName();

            // Generate the startup script
            String startupScript = startUpScript(
                    runnerToken.get(),
                    githubService.getRunnerGroupName(),
                    autoScalerActionPool,
                    runnerName,
                    githubService.getOrganization()
            );

            // Encode the startup script in Base64
            String encodedScript = Base64.getEncoder().encodeToString(startupScript.getBytes());


            // Create the request to run a new EC2 instance
            RunInstancesRequest.Builder runRequest = RunInstancesRequest.builder()

                    .imageId(amiId)
                    .instanceType(actionPool.getInstanceType())
                    .maxCount(1)
                    .minCount(1)
                    .securityGroupIds(actionPool.getSecurityGroup())
                    .subnetId(actionPool.getSubnet())
                    .userData(encodedScript)

                    .tagSpecifications(
                            TagSpecification.builder()
                                    .resourceType(ResourceType.INSTANCE)
                                    .tags(generateInstanceTags(actionPool.getName(), runnerName))
                                    .build()
                    );

            if(actionPool.getKeyPairNameOpt().isPresent()){
                runRequest.keyName(actionPool.getKeyPairNameOpt().get());
            }
            // Create block device mapping for root volume with specified size and type
            if (!Boolean.TRUE.equals(actionPool.getNvme())) {
                int diskSize = Optional.ofNullable(actionPool.getDiskSettings()).map(AwsConfig.DiskSettings::getSize).orElse(DEFAULT_DISK_SIZE);
                String diskType = Optional.ofNullable(actionPool.getDiskSettings()).map(AwsConfig.DiskSettings::getType).orElse(DEFAULT_DISK_TYPE);
                BlockDeviceMapping rootVolume = BlockDeviceMapping.builder()
                        .deviceName("/dev/sda1") // Root device name for Ubuntu
                        .ebs(EbsBlockDevice.builder()
                                .volumeSize(diskSize)
                                .volumeType(VolumeType.fromValue(diskType))
                                .deleteOnTermination(true)
                                .build())
                        .build();
                runRequest.blockDeviceMappings(rootVolume);
            }
            log.info("Creating instance {} for action pool {} ",runnerName,actionPool.getName());
            // Launch the instance
            RunInstancesResponse response = ec2.runInstances(runRequest.build());
            return true;
        }catch (Exception e){
            Utils.excessiveErrorLog("Failed to create instance %s".formatted(e.getMessage()), e, log);

        }
        return false;
    }

    public Optional<Region> regionFromString(String region){
        return Region.regions().stream().filter(r->r.id().equalsIgnoreCase(region)).findAny();
    }
    public Optional<String> latestAmi(Ec2Client ec2Client){
        try  {
            DescribeImagesRequest request = DescribeImagesRequest.builder()
                    .owners("099720109477") // Canonical's owner ID. Company managing the images.
                    .filters(
                            Filter.builder()
                                    .name("name")
                                    .values("ubuntu/images/hvm-ssd/ubuntu-*-amd64-server-*") // Adjust for specific Ubuntu version if needed
                                    .build(),
                            Filter.builder()
                                    .name("state")
                                    .values("available")
                                    .build()
                    )
                    .build();

            List<Image> imagesUn = ec2Client.describeImages(request).images();

            // Sort images by creation date in descending order to get the latest
            List<Image> images = imagesUn.stream()
                    .sorted(Comparator.comparing(Image::creationDate).reversed())
                    .toList();

            if (!images.isEmpty()) {
                String latestAmiId = images.get(0).imageId();
                log.debug("Latest Ubuntu AMI ID: " + latestAmiId);
                return Optional.of(latestAmiId);
            } else {
               log.warn("No Ubuntu AMIs found matching the criteria.");
            }
        } catch (Exception e) {
            Utils.excessiveErrorLog("Error finding latest Ubuntu AMI due to %s".formatted(e.getMessage()), e, log);

        }
        return Optional.empty();
    }

}