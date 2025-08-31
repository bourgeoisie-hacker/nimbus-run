package com.nimbusrun.compute.gcp.v1;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class GCPConfig {
    private ActionPool defaultSettings;
    private ActionPool defaultActionPool;
    private List<ActionPool> actionPools;


    public void fillInActionPoolWithDefaults(){
        for(var actionPool : this.actionPools){
            fillInActionPoolWithDefaults(actionPool, this.defaultSettings);
        }
        if(this.defaultActionPool != null) {
            this.defaultActionPool.setDefault(true);
            fillInActionPoolWithDefaults(this.defaultActionPool, this.defaultSettings);
        }

    }
    private void fillInActionPoolWithDefaults(ActionPool actionPool, ActionPool defaults) {
        if(defaults == null || actionPool == null){
            return;
        }
        setFromDefault(actionPool::getRegion, defaults::getRegion, actionPool::setRegion);
        setFromDefault(actionPool::getProjectId, defaults::getProjectId, actionPool::setProjectId);
        setFromDefault(actionPool::getSubnet, defaults::getSubnet, actionPool::setSubnet);
        setFromDefault(actionPool::getVpc, defaults::getVpc, actionPool::setVpc);
        setFromDefault(actionPool::getZones, defaults::getZones, actionPool::setZones);
        setFromDefault(actionPool::getServiceAccountPath, defaults::getServiceAccountPath, actionPool::setServiceAccountPath);
        setFromDefault(actionPool::getDiskSettings, defaults::getDiskSettings, actionPool::setDiskSettings);
        setFromDefault(actionPool::getInstanceType, defaults::getInstanceType, actionPool::setInstanceType);
        setFromDefault(actionPool::getMaxInstanceCount, defaults::getMaxInstanceCount, actionPool::setMaxInstanceCount);
        setFromDefault(actionPool::getIdleScaleDownInMinutes, defaults::getIdleScaleDownInMinutes, actionPool::setIdleScaleDownInMinutes);
        setFromDefault(actionPool::getIdleScaleDownInMinutes, defaults::getIdleScaleDownInMinutes, actionPool::setIdleScaleDownInMinutes);

    }

    private <T> void setFromDefault(Supplier<T> action, Supplier<T> defaultAction, Consumer<T> setActionFromDefault) {
        if (action.get() == null) {
            setActionFromDefault.accept(defaultAction.get());
        }
    }
    public GCPConfig createGcpConfigs(String computeConfig){
        PropertyUtils propertyUtils = new PropertyUtils();
        propertyUtils.setSkipMissingProperties(true);
        Constructor constructor = new Constructor(GCPConfig.class,new LoaderOptions() );
        constructor.setPropertyUtils(propertyUtils);
        Yaml yaml = new Yaml(constructor);
        GCPConfig gcpConfig = yaml.load(computeConfig);
        gcpConfig.fillInActionPoolWithDefaults();
        return gcpConfig;
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
        private Boolean isNvme;
        private String subnet;
        private String vpc;
        private List<String> zones;
        private DiskSettings diskSettings;
        private boolean isDefault;

        public com.nimbusrun.compute.ActionPool toAutoScalerActionPool() {
            return new com.nimbusrun.compute.ActionPool(this.name, this.maxInstanceCount, this.idleScaleDownInMinutes, isDefault);
        }


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

        public Boolean getNvme() {
            return isNvme;
        }

        public void setNvme(Boolean nvme) {
            isNvme = nvme;
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
    }

}
