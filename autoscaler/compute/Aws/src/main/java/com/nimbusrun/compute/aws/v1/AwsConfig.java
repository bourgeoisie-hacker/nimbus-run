package com.nimbusrun.compute.aws.v1;


import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class AwsConfig {
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
    private static final String IS_NVME = "isNvme";
    private static final String KEY_PAIR_NAME = "keyPairName";
    private static final String ACTION_POOLS = "actionPools";

    private static final String DEFAULT_SETTINGS = "defaultSettings";
    private static final String DEFAULT_ACTION_POOL = "defaultActionPool";
    private static final String IDLE_SCALE_DOWN_IN_MINUTES = "idleScaleDownInMinutes";


    private ActionPool defaultSettings;
    private ActionPool defaultActionPool;
    private List<ActionPool> actionPools;



    public void fillInActionPoolWithDefaults(){
        for(var actionPool : this.actionPools){
            fillInActionPoolWithDefaults(actionPool, this.defaultSettings);
        }
        fillInActionPoolWithDefaults(this.defaultActionPool, this.defaultSettings);

    }
    private void fillInActionPoolWithDefaults(ActionPool actionPool, ActionPool defaults) {
        setFromDefault(actionPool::getRegion, defaults::getRegion, actionPool::setRegion);
        setFromDefault(actionPool::getSubnet, defaults::getSubnet, actionPool::setSubnet);
        setFromDefault(actionPool::getSecurityGroup, defaults::getSecurityGroup, actionPool::setSecurityGroup);
        setFromDefault(actionPool::getCredentialsProfile, defaults::getCredentialsProfile, actionPool::setCredentialsProfile);
        setFromDefault(actionPool::getDiskSettings, defaults::getDiskSettings, actionPool::setDiskSettings);
        setFromDefault(actionPool::getInstanceType, defaults::getInstanceType, actionPool::setInstanceType);
        setFromDefault(actionPool::getMaxInstanceCount, defaults::getMaxInstanceCount, actionPool::setMaxInstanceCount);
        setFromDefault(actionPool::getIdleScaleDownInMinutes, defaults::getIdleScaleDownInMinutes, actionPool::setIdleScaleDownInMinutes);
        setFromDefault(actionPool::getIdleScaleDownInMinutes, defaults::getIdleScaleDownInMinutes, actionPool::setIdleScaleDownInMinutes);
        setFromDefault(actionPool::getKeyPairName, defaults::getKeyPairName, actionPool::setKeyPairName);
    }

    private <T> void setFromDefault(Supplier<T> action, Supplier<T> defaultAction, Consumer<T> setActionFromDefault) {
        if (action.get() == null) {
            setActionFromDefault.accept(defaultAction.get());
        }
    }

    public static AwsConfig createAwsConfig(String computeConfig){

        PropertyUtils propertyUtils = new PropertyUtils();
        propertyUtils.setSkipMissingProperties(true);
        Constructor constructor = new Constructor(AwsConfig.class,new LoaderOptions() );
        constructor.setPropertyUtils(propertyUtils);
        Yaml yaml = new Yaml(constructor);

        AwsConfig awsConfig = yaml.load(computeConfig);
        awsConfig.fillInActionPoolWithDefaults();
        return awsConfig;
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
        private Boolean nvme;
        private String subnet;
        private String securityGroup;
        private DiskSettings diskSettings;
        private boolean isDefault;
        private String keyPairName;

        public com.nimbusrun.compute.ActionPool toAutoScalerActionPool() {
            return new com.nimbusrun.compute.ActionPool(this.name, this.maxInstanceCount, this.idleScaleDownInMinutes, isDefault );
        }


        public Optional<String> getCredentialsProfileOpt(){
            return Optional.ofNullable(this.credentialsProfile);
        }
        public Optional<String> getKeyPairNameOpt(){
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

        public Boolean getNvme() {
            return nvme;
        }

        public void setNvme(Boolean nvme) {
            this.nvme = nvme;
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
