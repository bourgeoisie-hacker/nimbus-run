package com.nimbusrun.compute.gcp.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nimbusrun.Utils;
import com.nimbusrun.compute.ProcessorArchitecture;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;


public class GCPConfig {

  private static Logger log = LoggerFactory.getLogger(GCPConfig.class);
  private ActionPool defaultSettings;
  private ActionPool defaultActionPool;
  private List<ActionPool> actionPools;


  public void fillInActionPoolWithDefaults() {
    for (var actionPool : this.actionPools) {
      fillInActionPoolWithDefaults(actionPool, this.defaultSettings);
    }
    if (this.defaultActionPool != null) {
      this.defaultActionPool.setDefault(true);
      fillInActionPoolWithDefaults(this.defaultActionPool, this.defaultSettings);
    }

  }

  private void fillInActionPoolWithDefaults(ActionPool actionPool, ActionPool defaults) {
    if (defaults == null || actionPool == null) {
      return;
    }
    setFromDefault(actionPool::getRegion, defaults::getRegion, actionPool::setRegion);
    setFromDefault(actionPool::getProjectId, defaults::getProjectId, actionPool::setProjectId);
    setFromDefault(actionPool::getSubnet, defaults::getSubnet, actionPool::setSubnet);
    setFromDefault(actionPool::getVpc, defaults::getVpc, actionPool::setVpc);
    setFromDefault(actionPool::getPublicIp, defaults::getPublicIp, actionPool::setPublicIp);
    setFromDefault(actionPool::getZones, defaults::getZones, actionPool::setZones);
    setFromDefault(actionPool::getServiceAccountPath, defaults::getServiceAccountPath,
        actionPool::setServiceAccountPath);
    setFromDefault(actionPool::getDiskSettings, defaults::getDiskSettings,
        actionPool::setDiskSettings);
    setFromDefault(actionPool::getInstanceType, defaults::getInstanceType,
        actionPool::setInstanceType);
    setFromDefault(actionPool::getMaxInstanceCount, defaults::getMaxInstanceCount,
        actionPool::setMaxInstanceCount);
    setFromDefault(actionPool::getIdleScaleDownInMinutes, defaults::getIdleScaleDownInMinutes,
        actionPool::setIdleScaleDownInMinutes);
    setFromDefault(actionPool::getIdleScaleDownInMinutes, defaults::getIdleScaleDownInMinutes,
        actionPool::setIdleScaleDownInMinutes);
    setFromDefault(actionPool::getArchitecture, defaults::getArchitecture,
        actionPool::setArchitecture);
    setFromDefault(actionPool::getOs, defaults::getOs, actionPool::setOs);

  }

  private <T> void setFromDefault(Supplier<T> action, Supplier<T> defaultAction,
      Consumer<T> setActionFromDefault) {
    if (action.get() == null) {
      setActionFromDefault.accept(defaultAction.get());
    }
  }

  public GCPConfig createGcpConfigs(String computeConfig) {
    Map<String, Object> yamlData;
    try {
      Yaml yaml = new Yaml();
      yamlData = yaml.load(computeConfig);
    } catch (Exception e) {
      Utils.excessiveErrorLog(
          "Failed to convert compute section of yaml into json due to %s".formatted(e.getMessage()),
          e, log);
      throw e;
    }

    try {
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      String jsonString = objectMapper.writeValueAsString(yamlData);
      GCPConfig gcpConfig = objectMapper.readValue(jsonString, GCPConfig.class);
      gcpConfig.fillInActionPoolWithDefaults();
      return gcpConfig;
    } catch (Exception e) {
      Utils.excessiveErrorLog("Failed to parse configuration due to %s".formatted(e.getMessage()),
          e, log);
      throw new RuntimeException(e);
    }
  }

  public ActionPool getDefaultSettings() {
    return defaultSettings;
  }

  public void setDefaultSettings(ActionPool defaultSettings) {
    this.defaultSettings = defaultSettings;
  }

  public ActionPool getDefaultActionPool() {
    return defaultActionPool;
  }

  public void setDefaultActionPool(ActionPool defaultActionPool) {
    this.defaultActionPool = defaultActionPool;
  }

  public List<ActionPool> getActionPools() {
    return actionPools;
  }

  public void setActionPools(List<ActionPool> actionPools) {
    this.actionPools = actionPools;
  }

  public static class DiskSettings {

    private Integer size;

    public void setSize(Integer size) {
      this.size = size;
    }

    public Integer getSize() {
      return size;
    }
  }

  public static class ActionPool {

    private String name;
    private String projectId;
    private String region;
    private String instanceType;
    private Integer maxInstanceCount;
    private Integer idleScaleDownInMinutes;
    private String serviceAccountPath;
    private String subnet;
    private String vpc;
    private Boolean publicIp;
    private List<String> zones;
    private DiskSettings diskSettings;
    @JsonDeserialize(using = ProcessorArchitecture.Deserialize.class)
    private ProcessorArchitecture architecture;
    @JsonDeserialize(using = GcpOperatingSystem.Deserialize.class)
    @JsonSerialize(using = GcpOperatingSystem.Serializer.class)
    private GcpOperatingSystem os;
    private boolean isDefault;

    public com.nimbusrun.compute.ActionPool toAutoScalerActionPool() {
      return new com.nimbusrun.compute.ActionPool(this.name, this.maxInstanceCount,
          this.idleScaleDownInMinutes, isDefault);
    }


    @JsonIgnore
    public Optional<String> getServiceAccountPathOpt() {
      return Optional.ofNullable(this.serviceAccountPath);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getProjectId() {
      return projectId;
    }

    public void setProjectId(String projectId) {
      this.projectId = projectId;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getInstanceType() {
      return instanceType;
    }

    public void setInstanceType(String instanceType) {
      this.instanceType = instanceType;
    }

    public Integer getMaxInstanceCount() {
      return maxInstanceCount;
    }

    public void setMaxInstanceCount(Integer maxInstanceCount) {
      this.maxInstanceCount = maxInstanceCount;
    }

    public Integer getIdleScaleDownInMinutes() {
      return idleScaleDownInMinutes;
    }

    public void setIdleScaleDownInMinutes(Integer idleScaleDownInMinutes) {
      this.idleScaleDownInMinutes = idleScaleDownInMinutes;
    }

    public String getServiceAccountPath() {
      return serviceAccountPath;
    }

    public void setServiceAccountPath(String serviceAccountPath) {
      this.serviceAccountPath = serviceAccountPath;
    }

    public String getSubnet() {
      return subnet;
    }

    public void setSubnet(String subnet) {
      this.subnet = subnet;
    }

    public String getVpc() {
      return vpc;
    }

    public void setVpc(String vpc) {
      this.vpc = vpc;
    }

    public Boolean getPublicIp() {
      return publicIp;
    }

    public void setPublicIp(Boolean publicIp) {
      this.publicIp = publicIp;
    }

    public List<String> getZones() {
      return zones;
    }

    public void setZones(List<String> zones) {
      this.zones = zones;
    }

    public DiskSettings getDiskSettings() {
      return diskSettings;
    }

    public void setDiskSettings(DiskSettings diskSettings) {
      this.diskSettings = diskSettings;
    }

    public boolean isDefault() {
      return isDefault;
    }

    public void setDefault(boolean aDefault) {
      isDefault = aDefault;
    }

    public ProcessorArchitecture getArchitecture() {
      return architecture;
    }

    public void setArchitecture(ProcessorArchitecture architecture) {
      this.architecture = architecture;
    }

    public GcpOperatingSystem getOs() {
      return os;
    }

    public void setOs(GcpOperatingSystem os) {
      this.os = os;
    }
  }

}
