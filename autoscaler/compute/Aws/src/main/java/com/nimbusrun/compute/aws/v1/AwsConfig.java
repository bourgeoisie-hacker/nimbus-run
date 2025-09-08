package com.nimbusrun.compute.aws.v1;


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


public class AwsConfig {

  private static Logger log = LoggerFactory.getLogger(AwsConfig.class);
  private static final String TYPE = "type";
  private static final String REGION = "region";
  private static final String SUBNET = "subnet";
  private static final String SECURITY_GROUP = "securityGroup";
  private static final String CREDENTIALS_PROFILE = "credentialsProfile";
  private static final String DISK_SETTINGS = "diskSettings";
  private static final String DISK_SETTINGS_TYPE = "type";
  private static final String DISK_SETTINGS_SIZE = "size";
  private static final String INSTANCE = "instanceType";
  private static final String MAX_INSTANCE_COUNT = "maxInstanceCount";
  private static final String NAME = "name";
  private static final String KEY_PAIR_NAME = "keyPairName";
  private static final String ACTION_POOLS = "actionPools";

  private static final String DEFAULT_SETTINGS = "defaultSettings";
  private static final String DEFAULT_ACTION_POOL = "defaultActionPool";
  private static final String IDLE_SCALE_DOWN_IN_MINUTES = "idleScaleDownInMinutes";


  private ActionPool defaultSettings;
  private ActionPool defaultActionPool;
  private List<ActionPool> actionPools;


  public void fillInActionPoolWithDefaults() {
    for (var actionPool : this.actionPools) {
      fillInActionPoolWithDefaults(actionPool, this.defaultSettings);
    }
    if (this.defaultActionPool != null) {
      fillInActionPoolWithDefaults(this.defaultActionPool, this.defaultSettings);
      this.defaultActionPool.setDefault(true);
    }


  }

  private void fillInActionPoolWithDefaults(ActionPool actionPool, ActionPool defaults) {
    setFromDefault(actionPool::getRegion, defaults::getRegion, actionPool::setRegion);
    setFromDefault(actionPool::getSubnet, defaults::getSubnet, actionPool::setSubnet);
    setFromDefault(actionPool::getSecurityGroup, defaults::getSecurityGroup,
        actionPool::setSecurityGroup);
    setFromDefault(actionPool::getCredentialsProfile, defaults::getCredentialsProfile,
        actionPool::setCredentialsProfile);
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
    setFromDefault(actionPool::getKeyPairName, defaults::getKeyPairName,
        actionPool::setKeyPairName);
    setFromDefault(actionPool::getOs, defaults::getOs, actionPool::setOs);
    setFromDefault(actionPool::getArchitecture, defaults::getArchitecture,
        actionPool::setArchitecture);

  }

  private <T> void setFromDefault(Supplier<T> action, Supplier<T> defaultAction,
      Consumer<T> setActionFromDefault) {
    if (action.get() == null) {
      setActionFromDefault.accept(defaultAction.get());
    }
  }

  public static AwsConfig createAwsConfig(String computeConfig) {

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
      AwsConfig awsConfig = objectMapper.readValue(jsonString, AwsConfig.class);
      awsConfig.fillInActionPoolWithDefaults();
      return awsConfig;
    } catch (Exception e) {
      Utils.excessiveErrorLog("Failed to parse configuration due to %s".formatted(e.getMessage()),
          e, log);
      throw new RuntimeException(e);
    }
  }

  public static class DiskSettings {

    private String type;
    private Integer size;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Integer getSize() {
      return size;
    }

    public void setSize(Integer size) {
      this.size = size;
    }
  }

  public static class ActionPool {

    private String name;
    private String region;
    private String instanceType;
    private Integer maxInstanceCount;
    private Integer idleScaleDownInMinutes;
    private String credentialsProfile;
    private String subnet;
    private String securityGroup;
    private DiskSettings diskSettings;
    private boolean isDefault;
    @JsonDeserialize(using = ProcessorArchitecture.Deserialize.class)
    private ProcessorArchitecture architecture;
    @JsonDeserialize(using = AwsOperatingSystem.Deserialize.class)
    @JsonSerialize(using = AwsOperatingSystem.Serializer.class)
    private AwsOperatingSystem os;
    private String keyPairName;

    public com.nimbusrun.compute.ActionPool toAutoScalerActionPool() {
      return new com.nimbusrun.compute.ActionPool(this.name, this.maxInstanceCount,
          this.idleScaleDownInMinutes, isDefault);
    }

    @JsonDeserialize
    public Optional<String> getCredentialsProfileOpt() {
      return Optional.ofNullable(this.credentialsProfile);
    }

    public Optional<String> getKeyPairNameOpt() {
      return Optional.ofNullable(this.keyPairName);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
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

    public String getCredentialsProfile() {
      return credentialsProfile;
    }

    public void setCredentialsProfile(String credentialsProfile) {
      this.credentialsProfile = credentialsProfile;
    }



    public String getSubnet() {
      return subnet;
    }

    public void setSubnet(String subnet) {
      this.subnet = subnet;
    }

    public String getSecurityGroup() {
      return securityGroup;
    }

    public void setSecurityGroup(String securityGroup) {
      this.securityGroup = securityGroup;
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

    public String getKeyPairName() {
      return keyPairName;
    }

    public void setKeyPairName(String keyPairName) {
      this.keyPairName = keyPairName;
    }

    public ProcessorArchitecture getArchitecture() {
      return architecture;
    }

    public void setArchitecture(ProcessorArchitecture architecture) {
      this.architecture = architecture;
    }

    public AwsOperatingSystem getOs() {
      return os;
    }

    public void setOs(AwsOperatingSystem os) {
      this.os = os;
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
}
