package com.nimbusrun.compute.aws.v1;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusrun.Utils;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.ComputeConfigResponse;
import com.nimbusrun.compute.Constants;
import com.nimbusrun.compute.DeleteInstanceRequest;
import com.nimbusrun.compute.GithubApi;
import com.nimbusrun.compute.ListInstanceResponse;
import com.nimbusrun.compute.ProcessorArchitecture;
import com.nimbusrun.compute.aws.v1.AwsConfig.DiskSettings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.BlockDeviceMapping;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.EbsBlockDevice;
import software.amazon.awssdk.services.ec2.model.EbsBlockDevice.Builder;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Image;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.ResourceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TagSpecification;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;
import software.amazon.awssdk.services.ec2.model.VolumeType;

@Component
public class AWSComputeService extends Compute {

  private static final Logger log = LoggerFactory.getLogger(AWSComputeService.class);
  public static final String DEFAULT_DISK_TYPE = "gp3";
  private static final Integer DEFAULT_DISK_SIZE = 20;
  private final GithubApi githubService;
  private AwsConfig clientConfig;
  private Map<String, AwsConfig.ActionPool> awsActionPoolMap = new HashMap<>();
  private final Map<String, String> DEFAULT_INSTANCE_LABELS = new HashMap<>();
  private final String APPLICATION_NAME_LABEL_KEY = "NimbusRun";
  private Map<String, Ec2Client> actionPoolToEc2Client = new HashMap<>();
  private final Cache<AwsConfig.ActionPool, String> amiCache;
  private static final ProcessorArchitecture DEFAULT_PROCESSOR_ARCHITECTURE = ProcessorArchitecture.X64;
  private static final AwsOperatingSystem DEFAULT_OPERATING_SYSTEM = AwsOperatingSystem.UBUNTU_24_04;

  //    private String applicationName;
  public AWSComputeService(GithubApi githubService) {
    this.githubService = githubService;
    amiCache = Caffeine.newBuilder()
        .maximumSize(1_000_000)
        .expireAfterWrite(Duration.ofHours(24))
        .build();

  }


  @Override
  public ListInstanceResponse listComputeInstances(ActionPool autoScalePool) {
    List<Instance> instanceIds = new ArrayList<>();
    AwsConfig.ActionPool actionPool = this.awsActionPoolMap.get(autoScalePool.getName());
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
          return new ListInstanceResponse.Instance(name, i.instanceId(), name,
              i.launchTime().toEpochMilli());
        }).toList());
  }

  /**
   * Unfortunately we cannot aggregate the regions and list all the instance for that region with
   * the {@link AWSComputeService#DEFAULT_INSTANCE_LABELS}. because each action pool could be using
   * a user/role restricted to just one region. :(.
   *
   * @return a map of action pool name to instances associated with them
   */
  @Override
  public Map<String, ListInstanceResponse> listAllComputeInstances() {
    Map<String, ListInstanceResponse> responseMap = new HashMap<>();
    try {
      for (String actionPoolName : this.awsActionPoolMap.keySet()) {
        responseMap.put(actionPoolName,
            listComputeInstances(this.awsActionPoolMap.get(actionPoolName).toAutoScalerActionPool()));
      }
    } catch (Exception e) {
    }
    return responseMap;
  }


  @Override
  public boolean deleteCompute(DeleteInstanceRequest deleteInstanceRequest) {
    AwsConfig.ActionPool actionPool = this.awsActionPoolMap.get(
        deleteInstanceRequest.getActionPool().getName());

    try {
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
  public ComputeConfigResponse receiveComputeConfigs(Map<String, Object> map,
      String autoScalerName) {
    AwsConfig awsConfig = new AwsConfig().createAwsConfig(new Yaml().dump(map));
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<AwsConfig.ActionPool> actionPools = new ArrayList<>();
    actionPools.addAll(awsConfig.getActionPools());
    actionPools.add(awsConfig.getDefaultActionPool());
    validateActionPools(actionPools, errors, warnings);
    setDefaultValues(actionPools);
    clientConfig = awsConfig;
    this.awsActionPoolMap = actionPools.stream()
        .collect(Collectors.toMap(AwsConfig.ActionPool::getName, Function.identity(), (a, b) -> a));
    DEFAULT_INSTANCE_LABELS.put(APPLICATION_NAME_LABEL_KEY, autoScalerName);
    generateEc2ClientPerActionPool(this.awsActionPoolMap);
    return new ComputeConfigResponse(errors, warnings,
        actionPools.stream().map(AwsConfig.ActionPool::toAutoScalerActionPool).toList());
  }

  public void generateEc2ClientPerActionPool(Map<String, AwsConfig.ActionPool> actionPoolMap) {
    for (AwsConfig.ActionPool ap : actionPoolMap.values()) {
      this.actionPoolToEc2Client.put(ap.getName(),
          AwsClients.ec2Client(ap.getCredentialsProfileOpt(), Region.of(ap.getRegion())));
    }
  }


  private void validateActionPools(List<AwsConfig.ActionPool> actionPools, List<String> errors,
      List<String> warnings) {
    Map<String, Long> nameHistogram = actionPools.stream()
        .collect(Collectors.groupingBy(AwsConfig.ActionPool::getName, Collectors.counting()));
    nameHistogram.keySet().stream().filter(key -> nameHistogram.get(key) > 1).forEach(key ->
        errors.add(
            "Action pool name: %s appears multiple %s times. Names must be unique".formatted(key,
                nameHistogram.get(key))));
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
        errors.add(
            "Action Pool %s missing subnet. Please add to defaultSettings or on Action Pool".formatted(
                name));
      }
      if (actionPool.getSecurityGroups() == null || actionPool.getSecurityGroups().isEmpty()) {
        errors.add(
            "Action Pool %s missing security groups. Please add to defaultSettings or on Action Pool".formatted(
                name));
      }
      if (actionPool.getCredentialsProfile() == null) {
        warnings.add(
            "Action Pool %s not configured credentials profile. Using Default Credentials Provider".formatted(
                name));
      }
      if (actionPool.getDiskSettings() == null) {
        warnings.add(
            "Action Pool %s not configured with diskSettings. Using default disk configurations".formatted(
                name));
      }
      if (actionPool.getInstanceType() == null) {
        errors.add(
            "Action Pool %s not configured with instanceType. Please add to defaultSettings or on Action Pool".formatted(
                name));
      } else if (InstanceType.fromValue(actionPool.getInstanceType())
          == InstanceType.UNKNOWN_TO_SDK_VERSION) {
        warnings.add("Action Pool %s has unknown  instanceType: \"%s\".".formatted(name,
            actionPool.getInstanceType()));
      }
      if (actionPool.getMaxInstanceCount() == null) {
        warnings.add(
            "Action Pool %s not configured with maxInstanceCount. Please add to defaultSettings or on Action Pool".formatted(
                name));
      }
      if (actionPool.getIdleScaleDownInMinutes() == null) {
        warnings.add(
            "Action Pool %s not configured with idleScaleDownInMinutes. Please add to defaultSettings or on Action Pool".formatted(
                name));
      }
      if (actionPool.getOs() != null && actionPool.getOs() == AwsOperatingSystem.UNKNOWN) {
        errors.add("Invalid operating system specified for action pool %s".formatted(name));
      } else if (actionPool.getOs() != null && actionPool.getOs() != AwsOperatingSystem.UNKNOWN
          && !actionPool.getOs().isAvailable()) {
        errors.add(
            "Operating system %s specified for action pool %s is not available as a Runner".formatted(
                actionPool.getOs().getOperatingSystem().getShortName(), name));
      }
      if (actionPool.getArchitecture() != null
          && actionPool.getArchitecture() == ProcessorArchitecture.UNKNOWN) {
        errors.add("Invalid cpu architecture specified for action pool %s".formatted(name));
      }
    }
  }

  public void setDefaultValues(List<AwsConfig.ActionPool> actionPools) {
    actionPools.stream().forEach(ap -> {

      if (ap.getArchitecture() == null) {
        log.info("Action pool {} using default architecture value of {}", ap.getName(),
            DEFAULT_PROCESSOR_ARCHITECTURE);
        ap.setArchitecture(DEFAULT_PROCESSOR_ARCHITECTURE);
      }

      if (ap.getOs() == null) {
        log.info("Action pool {} using default os  value of {}", ap.getName(),
            DEFAULT_OPERATING_SYSTEM);
        ap.setOs(DEFAULT_OPERATING_SYSTEM);
      }

      if (ap.getMaxInstanceCount() == null) {
        log.info("Action pool {} using default maxInstanceCount value of {}", ap.getName(),
            Constants.DEFAULT_MAX_INSTANCES);
        ap.setMaxInstanceCount(Constants.DEFAULT_MAX_INSTANCES);
      }
      if (ap.getIdleScaleDownInMinutes() == null) {
        log.info("Action pool {} using default idleScaleDownInMinutes value of {}", ap.getName(),
            Constants.DEFAULT_INSTANCE_IDLE_TIME_IN_MINUTES);
        ap.setIdleScaleDownInMinutes(Constants.DEFAULT_INSTANCE_IDLE_TIME_IN_MINUTES);
      }
      if (ap.getDiskSettings() == null ) {
        DiskSettings diskSettings = new DiskSettings();
        diskSettings.setSize(DEFAULT_DISK_SIZE);
        diskSettings.setType(DEFAULT_DISK_TYPE);
        ap.setDiskSettings(diskSettings);
      }else {
        if (ap.getDiskSettings().getSize() == null){
          ap.getDiskSettings().setSize(DEFAULT_DISK_SIZE);
        }
        if(ap.getDiskSettings().getType() == null){
          ap.getDiskSettings().setType(DEFAULT_DISK_TYPE);
        }
      }

    });
  }

  private List<Tag> generateInstanceTags(String actionPoolName) {
    return generateInstanceTags(actionPoolName, null);
  }

  private List<Tag> generateInstanceTags(String actionPoolName, String runnerName) {
    Map<String, String> map = new HashMap<>();
    map.putAll(DEFAULT_INSTANCE_LABELS);
    map.put(Constants.ACTION_POOL_LABEL_KEY, actionPoolName);
    if (runnerName != null) {
      map.put("Name", runnerName);
    }
    return map.keySet().stream().map(k -> Tag.builder().key(k).value(map.get(k)).build()).toList();
  }

  private List<Filter> tagFilters(String actionPoolName) {
    List<Tag> tags = this.generateInstanceTags(actionPoolName);
    return tags.stream().map(t -> Filter.builder().name("tag:" + t.key()).values(t.value()).build())
        .toList();
  }

  @Override
  public boolean createCompute(ActionPool autoScalerActionPool) {
    AwsConfig.ActionPool actionPool = this.awsActionPoolMap.get(autoScalerActionPool.getName());

    // Configure AWS client
    Ec2Client ec2 = actionPoolToEc2Client.get(actionPool.getName());

    try {
      Region region = Region.of(actionPool.getRegion());
      // Set up instance parameters
      String amiId = amiCache.get(actionPool, (r) ->
          latestAmi(ec2, actionPool).orElse(null));
      if (amiId == null) {
        log.error("Ubuntu AMI does not exist for region {}", actionPool.getRegion());
        return false;
      }
      // Create startup script for GitHub runner
      Optional<String> runnerToken = this.githubService.generateRunnerToken();
      if (runnerToken.isEmpty()) {
        log.error("Failed to retrieve github runner token");
        return false;
      }
      String instanceName = this.createInstanceName();

      // Generate the startup script
      String startupScript = startUpScript(
          runnerToken.get(),
          githubService.getRunnerGroupName(),
          autoScalerActionPool,
          instanceName,
          githubService.getOrganization(),
          actionPool.getArchitecture(),
          actionPool.getOs().getOperatingSystem()
      );

      // Encode the startup script in Base64
      String encodedScript = Base64.getEncoder().encodeToString(startupScript.getBytes());

      // Create the request to run a new EC2 instance
      RunInstancesRequest.Builder runRequest = RunInstancesRequest.builder()

          .imageId(amiId)
          .instanceType(actionPool.getInstanceType())
          .maxCount(1)
          .minCount(1)
          .securityGroupIds(actionPool.getSecurityGroups())
          .subnetId(actionPool.getSubnet())
          .userData(encodedScript)

          .tagSpecifications(
              TagSpecification.builder()
                  .resourceType(ResourceType.INSTANCE)
                  .tags(generateInstanceTags(actionPool.getName(), instanceName))
                  .build()
          );

      if (actionPool.getKeyPairNameOpt().isPresent()) {
        runRequest.keyName(actionPool.getKeyPairNameOpt().get());
      }
      // Create block device mapping for root volume with specified size and type
      int diskSize = actionPool.getDiskSettings().getSize();
      String diskType = actionPool.getDiskSettings().getType();
      Builder ebs = EbsBlockDevice.builder()
          .volumeSize(diskSize)
          .volumeType(VolumeType.fromValue(diskType))
          .deleteOnTermination(true);
      if(actionPool.getDiskSettings().getIops().isPresent()){
        ebs.iops(actionPool.getDiskSettings().getIops().get());
      }

      BlockDeviceMapping rootVolume = BlockDeviceMapping.builder()
          .deviceName("/dev/sda1") // Root device name for Ubuntu
          .ebs(ebs.build())
          .build();
      runRequest.blockDeviceMappings(rootVolume);

      log.info("Creating instance {} for action pool {} ", instanceName, actionPool.getName());
      // Launch the instance
      RunInstancesResponse response = ec2.runInstances(runRequest.build());
      log.info("Instance created : %s".formatted(instanceName));

      return true;
    } catch (Exception e) {
      Utils.excessiveErrorLog("Failed to create instance %s".formatted(e.getMessage()), e, log);

    }
    return false;
  }

  public Optional<Region> regionFromString(String region) {
    return Region.regions().stream().filter(r -> r.id().equalsIgnoreCase(region)).findAny();
  }

  public static String determineArch(ProcessorArchitecture architecture) {
    String type = "x86_64";
    if (architecture.getType().equalsIgnoreCase("arm64")) {
      type = "arm64";
    }
    return type;
  }

  public static Optional<String> latestAmi(Ec2Client ec2Client, AwsConfig.ActionPool actionPool) {
    try {
      AwsOperatingSystem os = actionPool.getOs();
      ProcessorArchitecture architecture = actionPool.getArchitecture();
      DescribeImagesRequest request = DescribeImagesRequest.builder()
          .owners(os.gcpProviderProject()) // Canonical's owner ID. Company managing the images.
          .filters(
              Filter.builder()
                  .name("name")
                  .values(os.createRegex()) // Adjust for specific Ubuntu version if needed
                  .build(),
              Filter.builder()
                  .name("architecture")
                  .values(determineArch(architecture))
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
        log.info("Found ami %s for action pool %s".formatted(latestAmiId, actionPool.getName()));
        return Optional.of(latestAmiId);
      } else {
        log.warn("No Ubuntu AMIs found matching the criteria for action pool %s.".formatted(
            actionPool.getName()));

      }
    } catch (Exception e) {
      Utils.excessiveErrorLog(
          "Error finding latest Ubuntu AMI for action pool %s due to %s".formatted(actionPool.getName(),
              e.getMessage()), e, log);

    }
    return Optional.empty();
  }
  @Override
  public Map<String, Object> actionPoolToApiResponse() {
    return this.awsActionPoolMap.keySet().stream().collect(Collectors.toMap(Function.identity(), i-> (Object) this.awsActionPoolMap.get(i)));
  }
}