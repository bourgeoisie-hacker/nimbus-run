package com.nimbusrun.compute.gcp.v1;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.compute.v1.AccessConfig;
import com.google.cloud.compute.v1.AggregatedListInstancesRequest;
import com.google.cloud.compute.v1.AttachedDisk;
import com.google.cloud.compute.v1.AttachedDiskInitializeParams;
import com.google.cloud.compute.v1.Image;
import com.google.cloud.compute.v1.ImagesClient;
import com.google.cloud.compute.v1.InsertInstanceRequest;
import com.google.cloud.compute.v1.Instance;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.InstancesScopedList;
import com.google.cloud.compute.v1.Items;
import com.google.cloud.compute.v1.ListImagesRequest;
import com.google.cloud.compute.v1.ListInstancesRequest;
import com.google.cloud.compute.v1.Metadata;
import com.google.cloud.compute.v1.NetworkInterface;
import com.google.cloud.compute.v1.Operation;
import com.google.cloud.compute.v1.Zone;
import com.google.cloud.compute.v1.ZonesClient;
import com.nimbusrun.Utils;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.ComputeConfigResponse;
import com.nimbusrun.compute.Constants;
import com.nimbusrun.compute.DeleteInstanceRequest;
import com.nimbusrun.compute.GithubApi;
import com.nimbusrun.compute.ListInstanceResponse;
import com.nimbusrun.compute.ProcessorArchitecture;
import com.nimbusrun.compute.exceptions.InstanceCreateTimeoutException;
import com.nimbusrun.compute.gcp.v1.GCPConfig.DiskSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class GCPComputeService extends Compute {

  private static Logger log = LoggerFactory.getLogger(GCPComputeService.class);
  private Map<String, GCPConfig.ActionPool> gcpActionPoolMap;
  private GCPConfig clientConfig;
  final Map<String, String> DEFAULT_INSTANCE_LABELS = new HashMap<>();
  private final String APPLICATION_NAME_LABEL_KEY = "nimbus-run";
  private final Cache<String, Map<String, RegionZones>> projectIdToRegionZones;
  private final Cache<String, Set<String>> projectIdZones;
  private final Cache<String, String> latestMachineImage;

  private final boolean DEFAULT_IS_PUBLIC_IP = true;
  private final GcpOperatingSystem DEFAULT_OPERATING_SYSTEM = GcpOperatingSystem.UBUNTU_24_04;
  private final ProcessorArchitecture DEFAULT_PROCESSOR_ARCHITECTURE = ProcessorArchitecture.X64;
  private final Integer DEFAULT_DISK_SIZE_GB = 20;

  private static class RegionZones {

    private final String region;
    private final Set<String> zones;

    RegionZones(String region) {
      this.region = region;
      this.zones = new HashSet<>();
    }

    public String getRegion() {
      return region;
    }

    public Set<String> getZones() {
      return zones;
    }
  }

  private final GithubApi githubApi;

  public GCPComputeService(GithubApi githubApi) {
    this.githubApi = githubApi;
    projectIdToRegionZones = Caffeine.newBuilder()
        .maximumSize(1_000_000)
        .expireAfterWrite(Duration.ofDays(1))
        .build();
    projectIdZones = Caffeine.newBuilder()
        .maximumSize(1_000_000)
        .expireAfterWrite(Duration.ofDays(1))
        .build();
    latestMachineImage = Caffeine.newBuilder()
        .maximumSize(1_000_000)
        .expireAfterWrite(Duration.ofDays(1))
        .build();
  }


  public Optional<InstanceStaticInfo> findInstanceAcrossZones(GCPConfig.ActionPool actionPool,
      String instanceId) throws IOException {
    long id = Long.parseLong(instanceId);
    for (String zone : actionPool.getZones()) {
      try (InstancesClient instancesClient = GCPClients.createInstancesClient(
          actionPool.getServiceAccountPathOpt())) {
        ListInstancesRequest request = ListInstancesRequest.newBuilder()
            .setProject(actionPool.getProjectId())
            .setZone(zone)
            .build();

        for (Instance instance : instancesClient.list(request).iterateAll()) {
          if (instance.getId() == id) {
            return Optional.of(
                new InstanceStaticInfo(instance.getId() + "", parseZoneString(instance.getZone()),
                    instance.getName()));
          }
        }
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean deleteCompute(DeleteInstanceRequest deleteInst) throws Exception {
    log.info("Deleting vm name: %s id: %s".formatted(deleteInst.getActionPool().getName(),
        deleteInst.getInstanceId()));
    GCPConfig.ActionPool actionPool = this.gcpActionPoolMap.get(deleteInst.getActionPool().getName());

    InstanceStaticInfo instanceStaticInfo;
    if (deleteInst.getExtraProperties() instanceof InstanceStaticInfo info) {
      instanceStaticInfo = info;
    } else {
      Optional<InstanceStaticInfo> info = findInstanceAcrossZones(actionPool,
          deleteInst.getInstanceId());
      if (info.isEmpty()) {
        log.error("Couldn't find instance %s in action pool %s to delete".formatted(
            deleteInst.getInstanceId(), deleteInst.getActionPool().getName()));
        return false;
      }
      instanceStaticInfo = info.get();
    }

    try (InstancesClient instancesClient = GCPClients.createInstancesClient(
        actionPool.getServiceAccountPathOpt())) {
      com.google.cloud.compute.v1.DeleteInstanceRequest deleteInstanceRequest = com.google.cloud.compute.v1.DeleteInstanceRequest.newBuilder()
          .setProject(actionPool.getProjectId())
          .setInstance(instanceStaticInfo.getInstanceId())
          .setZone(instanceStaticInfo.getZone())
          .build();
      instancesClient.deleteAsync(deleteInstanceRequest).get(10, TimeUnit.MINUTES);
      log.info("Deleted vm name: %s id: %s".formatted(instanceStaticInfo.getName(),
          instanceStaticInfo.getInstanceId()));
      return true;
    } catch (java.util.concurrent.TimeoutException e) {
      log.info("vm name: %s id: %s is deleting but the request timed out ".formatted(
          instanceStaticInfo.getName(), instanceStaticInfo.getInstanceId()));
      return true;
    } catch (Exception e) {
      log.error("Failed to stop Instance vm name: %s id: %s".formatted(instanceStaticInfo.getName(),
          instanceStaticInfo.getInstanceId()), e);
    }

    return false;
  }

  @Override
  public List<ActionPool> listActionPools() {
    return List.of();
  }

  public String createLabelFilter(String actionPoolName) {
    String template = "labels.%s=%s";
    Map<String, String> map = createInstanceLabelMap(actionPoolName);
    return map.keySet().stream().map(k -> template.formatted(k, map.get(k)))
        .collect(Collectors.joining(" AND "));
  }

  private Map<String, String> createInstanceLabelMap(String actionPoolName) {
    Map<String, String> map = new HashMap<>();
    map.put(Constants.ACTION_POOL_LABEL_KEY, actionPoolName);
    map.putAll(DEFAULT_INSTANCE_LABELS);
    return map;
  }

  @Override
  public ListInstanceResponse listComputeInstances(ActionPool pool) {
    GCPConfig.ActionPool actionPool = this.gcpActionPoolMap.get(pool.getName());
    List<ListInstanceResponse.Instance> instances = new ArrayList<>();
    try (InstancesClient instancesClient = GCPClients.createInstancesClient(
        actionPool.getServiceAccountPathOpt())) {
      // Use the `setMaxResults` parameter to limit the number of results
      // that the API returns per response page.
      AggregatedListInstancesRequest aggregatedListInstancesRequest = AggregatedListInstancesRequest
          .newBuilder()
          .setProject(actionPool.getProjectId())
          .setFilter(createLabelFilter(actionPool.getName()))
          .setMaxResults(100)
          .build();
      InstancesClient.AggregatedListPagedResponse response = instancesClient
          .aggregatedList(aggregatedListInstancesRequest);

      for (Map.Entry<String, InstancesScopedList> zoneInstances : response.iterateAll()) {
        if (!zoneInstances.getValue().getInstancesList().isEmpty()) {
          for (Instance instance : zoneInstances.getValue().getInstancesList()) {
            ListInstanceResponse.Instance listInstance = new ListInstanceResponse.Instance(
                instance.getName(),
                instance.getId() + "",
                instance.getName(),
                Instant.parse(instance.getCreationTimestamp()).toEpochMilli(),
                new InstanceStaticInfo(instance.getId() + "", parseZoneString(instance.getZone()),
                    instance.getName()));
            instances.add(listInstance);
          }
        }
      }
    } catch (IOException e) {
      log.error("Error listing compute instances", e);
    }
    return new ListInstanceResponse(instances);
  }

  @Override
  public Map<String, ListInstanceResponse> listAllComputeInstances() {
    Map<String, ListInstanceResponse> responseMap = new HashMap<>();
    try {
      for (String actionPoolName : this.gcpActionPoolMap.keySet()) {
        responseMap.put(actionPoolName,
            listComputeInstances(this.gcpActionPoolMap.get(actionPoolName).toAutoScalerActionPool()));
      }
    } catch (Exception e) {
      Utils.excessiveErrorLog("Failed to list all compute instances", e, log);
    }
    return responseMap;
  }


  @Override
  public boolean createCompute(ActionPool autoscalerActionPool) {
    //TODO create a counter for when an instance is created
    String instanceName = createInstanceName();
    GCPConfig.ActionPool actionPool = this.gcpActionPoolMap.get(autoscalerActionPool.getName());
    try (InstancesClient instancesClient = GCPClients.createInstancesClient(
        actionPool.getServiceAccountPathOpt())) {
      Random random = new Random();

      String zone = actionPool.getZones().get(random.nextInt(actionPool.getZones().size()));

      Map<String, String> labels = createInstanceLabelMap(actionPool.getName());
      Optional<String> githubRunnerTokenOpt = githubApi.generateRunnerToken();

      if (githubRunnerTokenOpt.isEmpty()) {
        String msg = "Failed to generate token";
        log.error(msg);
        return false;
      }
      String githubRunnerToken = githubRunnerTokenOpt.get();
      Optional<String> sourceImage = cacheLatestImageVersion(actionPool);
      if (sourceImage.isEmpty()) {
        log.error("Failed to query latest ubuntu image");
        return false;
      }
      AttachedDisk attachedDisk = AttachedDisk.newBuilder()
          .setInitializeParams(
              AttachedDiskInitializeParams.newBuilder()
                  .setSourceImage(sourceImage.get())
                  .setDiskSizeGb(actionPool.getDiskSettings().getSize())
                  .build()
          )
          .setAutoDelete(true)
          .setBoot(true)
          .build();

      String startupScript = startUpScript(githubRunnerToken, this.githubApi.getRunnerGroupName(),
          autoscalerActionPool,
          instanceName,
          githubApi.getOrganization(),
          actionPool.getArchitecture(),
          actionPool.getOs().getOperatingSystem());

      Metadata md = Metadata.newBuilder()
          .addItems(Items.newBuilder().setKey("startup-script").setValue(startupScript).build())
          .build();

      Instance instance = Instance.newBuilder()
          .setName(instanceName)
          .addDisks(attachedDisk)
          .setMachineType("zones/%s/machineTypes/%s".formatted(zone, actionPool.getInstanceType()))
          .putAllLabels(labels)
          .addNetworkInterfaces(createNetworkInterface(actionPool.getVpc(), actionPool.getSubnet(),
              actionPool.getPublicIp()))
          .mergeMetadata(md)
          .build();

      // Create the insert instance request object.
      InsertInstanceRequest insertInstanceRequest = InsertInstanceRequest.newBuilder()
          .setProject(actionPool.getProjectId())
          .setZone(zone)
          .setInstanceResource(instance)
          .build();

      // Invoke the API with the request object and wait for the operation to complete.
      Operation response = instancesClient.insertAsync(insertInstanceRequest)
          .get(15, TimeUnit.MINUTES);

      // Check for errors.
      if (response.hasError()) {
        String msg = "Instance creation failed!!" + response;
        log.error(msg);
        return false;
      }
      log.info("Instance created : %s".formatted(instanceName));
      log.debug("Operation Status: " + response.getStatus());
      return true;
    } catch (ExecutionException | IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      log.warn(
          "Instance creation thread was interrupted for %s. The instance might have been created or not. ¯\\_(ツ)_/¯".formatted(
              instanceName));
    } catch (TimeoutException e) {
      log.warn(
          "Instance creation response timed out for %s. The instance might have been created or not. ¯\\_(ツ)_/¯".formatted(
              instanceName));
      throw new InstanceCreateTimeoutException(true);
    }
    return false;
  }

  private NetworkInterface createNetworkInterface(String vpcName, String subnetName,
      boolean publicIp) {
    if (publicIp) {
      return NetworkInterface.newBuilder().setNetwork(vpcName).setSubnetwork(subnetName)
          .addAccessConfigs(AccessConfig.newBuilder().setType(
              AccessConfig.Type.ONE_TO_ONE_NAT.toString()).setName("External NAT").build())
          .setName("nic0").build();
    }
    return NetworkInterface.newBuilder().setNetwork(vpcName).setSubnetwork(subnetName).build();
  }


  public Map<String, RegionZones> listRegions(GCPConfig.ActionPool actionPool) throws IOException {
    return this.projectIdToRegionZones.get(actionPool.getProjectId(), (proj) -> {
      try {
        return lookupRegionZone(actionPool);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public ComputeConfigResponse receiveComputeConfigs(Map<String, Object> map,
      String autoScalerName) {
    GCPConfig gcpConfig = new GCPConfig().createGcpConfigs(new Yaml().dump(map));
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<GCPConfig.ActionPool> actionPools = new ArrayList<>();
    actionPools.addAll(gcpConfig.getActionPools());
    actionPools.add(gcpConfig.getDefaultActionPool());
    validateActionPools(actionPools, errors, warnings);
    setDefaultValues(actionPools);
    clientConfig = gcpConfig;
    this.gcpActionPoolMap = actionPools.stream()
        .collect(Collectors.toMap(GCPConfig.ActionPool::getName, Function.identity(), (a, b) -> a));
    DEFAULT_INSTANCE_LABELS.put(APPLICATION_NAME_LABEL_KEY, autoScalerName);

    return new ComputeConfigResponse(errors, warnings,
        actionPools.stream().map(GCPConfig.ActionPool::toAutoScalerActionPool).toList());
  }


  public void validateActionPools(List<GCPConfig.ActionPool> actionPools, List<String> errors,
      List<String> warnings) {

    for (int i = 0; i < actionPools.size(); i++) {
      GCPConfig.ActionPool actionPool = actionPools.get(i);
      String name = "%s#".formatted(i);
      if (actionPool.getName() == null) {
        errors.add("Action pool %s is missing Name".formatted(name));
      } else {
        name = actionPool.getName();
      }
      if (actionPool.getProjectId() == null) {
        errors.add(
            "Action pool %s missing projectId. Please add to defaultSettings or on Action Pool".formatted(
                name));
      }
      if (actionPool.getRegion() == null) {
        errors.add(
            "Action pool %s missing region. Please add to defaultSettings or on Action Pool".formatted(
                name));
      } else if (actionPool.getProjectId() != null && !regionExists(actionPool,
          actionPool.getRegion())) {
        errors.add("Action pool %s region %s for projectId does not exist.".formatted(name,
            actionPool.getRegion(), actionPool.getProjectId()));
      }
      if (actionPool.getZones() == null) {
        errors.add(
            "Action pool %s missing zones. Please add to defaultSettings or on Action Pool".formatted(
                name));
      } else if (actionPool.getProjectId() != null) {
        for (String zone : actionPool.getZones()) {
          if (!zoneExists(actionPool, zone)) {
            errors.add(
                "Action pool %s zone %s for projectId %s does not exist".formatted(name, zone,
                    actionPool.getProjectId()));
          }
        }
      }
      if (actionPool.getInstanceType() == null) {
        errors.add(
            "Action pool %s missing instanceType. Please add to defaultSettings or on Action Pool".formatted(
                name));
      }
      if (actionPool.getMaxInstanceCount() == null) {
        warnings.add(
            "Action pool %s missing maxInstanceCount. Default value %s will be used".formatted(name,
                Constants.DEFAULT_MAX_INSTANCES));
      }
      if (actionPool.getIdleScaleDownInMinutes() == null) {
        warnings.add(
            "Action pool %s missing idleScaleDownInMinutes. Default value %s will be used".formatted(
                name, Constants.DEFAULT_INSTANCE_IDLE_TIME_IN_MINUTES));
      }
      if (actionPool.getServiceAccountPathOpt().isEmpty()) {
        warnings.add(
            "Action pool %s missing serviceAccountPath. Using environment default credentials");
      } else if (Files.notExists(Paths.get(actionPool.getServiceAccountPath()))
          || !Files.isRegularFile(Paths.get(actionPool.getServiceAccountPath()))) {
        errors.add(
            "Action pool %s serviceAccountPath file at \"%s\" does not exist or is not a regular file".formatted(
                name, actionPool.getServiceAccountPath()));
      }
      if (actionPool.getSubnet() == null) {
        errors.add(
            "Action pool %s missing subnet. Please add to defaultSettings or on Action Pool".formatted(
                name));
      }
      if (actionPool.getVpc() == null) {
        errors.add(
            "Action pool %s missing vpc. Please add to defaultSettings or on Action Pool".formatted(
                name));
      }
      if (actionPool.getDiskSettings() == null) {
        errors.add("Action pool %s missing vpc. Using default disk size %sGi ".formatted(name,
            DEFAULT_DISK_SIZE_GB));
      }
      if (actionPool.getOs() != null && actionPool.getOs() == GcpOperatingSystem.UNKNOWN) {
        errors.add("Action pool %s has unknown operating system specified".formatted(name));
      } else if (actionPool.getOs() != null && actionPool.getOs() != GcpOperatingSystem.UNKNOWN
          && !actionPool.getOs().isAvailable()) {
        errors.add(
            "Action pool %s has invalid operating system specified %s".formatted(name,
                actionPool.getOs().getOperatingSystem().getShortName()));
      }
      if (actionPool.getArchitecture() != null
          && actionPool.getArchitecture() == ProcessorArchitecture.UNKNOWN) {
        errors.add("Action pool %s has unknown cpu architecture specified".formatted(name));
      }
    }
  }

  public void setDefaultValues(List<GCPConfig.ActionPool> actionPools) {
    actionPools.stream().forEach(ap -> {
      if (ap.getPublicIp() == null) {
        log.info("Action pool {} using default publicIp value of {}", ap.getName(),
            DEFAULT_IS_PUBLIC_IP);
        ap.setPublicIp(DEFAULT_IS_PUBLIC_IP);
      }

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
      if(ap.getIdleScaleDownInMinutes() == null){
        log.info("Action pool {} using default idleScaleDownInMinutes value of {}", ap.getName(), Constants.DEFAULT_INSTANCE_IDLE_TIME_IN_MINUTES);
        ap.setIdleScaleDownInMinutes(Constants.DEFAULT_INSTANCE_IDLE_TIME_IN_MINUTES);
      }

      if (ap.getDiskSettings() == null || ap.getDiskSettings().getSize() == null) {
        DiskSettings diskSettings = new DiskSettings();
        diskSettings.setSize(DEFAULT_DISK_SIZE_GB);
        ap.setDiskSettings(diskSettings);
      }

    });
  }

  public boolean regionExists(GCPConfig.ActionPool actionPool, String region) {
    try {
      var map = listRegions(actionPool);
      return map.containsKey(region);
    } catch (IOException e) {
      log.error("Error looking up projectId: {}, region: {}, error: {}", actionPool.getProjectId(),
          region, e.getMessage());
      return false;
    }
  }

  public boolean zoneExists(GCPConfig.ActionPool actionPool, String zone) {
    try {
      Set<String> set = listZones(actionPool);
      return set.contains(zone);
    } catch (IOException e) {
      log.error("Error looking up projectId: {}, zone: {}, error: {}", actionPool.getProjectId(),
          zone, e.getMessage());
      return false;
    }
  }

  public Set<String> listZones(GCPConfig.ActionPool actionPool) throws IOException {
    return projectIdZones.get(actionPool.getProjectId(), (proj) -> {
      try {
        return lookupRegionZone(actionPool).values().stream().flatMap(rz -> rz.getZones().stream())
            .collect(Collectors.toSet());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  /**
   * Some companies my have early access to new regions and zones. So we need to look them up for
   * every project.
   *
   * @param actionPool
   * @return
   * @throws IOException
   */
  public Map<String, RegionZones> lookupRegionZone(GCPConfig.ActionPool actionPool)
      throws IOException {
    Map<String, RegionZones> regionZones = new HashMap<>();
    try (ZonesClient zonesClient = GCPClients.createZonesClient(
        actionPool.getServiceAccountPathOpt())) {
      for (Zone zone : zonesClient.list(actionPool.getProjectId()).iterateAll()) {
        String regionName = zone.getRegion().substring(zone.getRegion().lastIndexOf("/") + 1);
        RegionZones rz = regionZones.computeIfAbsent(regionName,
            (key) -> new RegionZones(regionName));
        rz.zones.add(zone.getName());
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return regionZones;
  }


  public Optional<String> cacheLatestImageVersion(GCPConfig.ActionPool actionPool) {
    try {
      return Optional.ofNullable(latestMachineImage.get(actionPool.getProjectId(),
          (projectId) -> latestMachineImage(actionPool)));
    } catch (NullPointerException e) {
      Utils.excessiveErrorLog(
          "Error when fetching latest image due to %s".formatted(e.getMessage()), e, log);
    }
    return Optional.empty();
  }


  public static String determineArch(ProcessorArchitecture architecture) {
    String arch = "X86_64";
    if (architecture == ProcessorArchitecture.ARM64) {
      arch = "ARM64";
    }
    return arch;
  }

  public static String latestMachineImage(GCPConfig.ActionPool actionPool) {
    ProcessorArchitecture architecture = actionPool.getArchitecture();
    GcpOperatingSystem operatingSystem = actionPool.getOs();
    String project = operatingSystem.gcpProviderProject();
    String arch = determineArch(architecture);
    String templ = operatingSystem.createRegex();
    try (ImagesClient imagesClient = GCPClients.createImagesClient(actionPool)) {
      ListImagesRequest request = ListImagesRequest.newBuilder()
          .setProject(project)
          .setMaxResults(10)
          .setOrderBy("creationTimestamp desc")  // newest first
          .build();
      Iterator<Image> latestImageIterator = imagesClient.list(request)
          .iterateAll()
          .iterator();
      while (latestImageIterator.hasNext()) {
        Image latestImage = latestImageIterator.next();
        if (arch.equalsIgnoreCase(latestImage.getArchitecture())
            && latestImage.hasCreationTimestamp() && latestImage.getName().matches(templ)) {
          return "projects/%s/global/images/%s".formatted(project, latestImage.getName());
        }
      }

    } catch (IOException e) {
      Utils.excessiveErrorLog(
          "Failed to query for latest image for action pool due to %s".formatted(e.getMessage()), e,
          log);
    }
    return null;
  }

  public String parseZoneString(String zone) {
    return zone.substring(zone.lastIndexOf("/") + 1);
  }

  @Override
  public Map<String, Object> actionPoolToApiResponse() {
    return this.gcpActionPoolMap.keySet().stream().collect(Collectors.toMap(Function.identity(), i-> (Object) this.gcpActionPoolMap.get(i)));
  }
}