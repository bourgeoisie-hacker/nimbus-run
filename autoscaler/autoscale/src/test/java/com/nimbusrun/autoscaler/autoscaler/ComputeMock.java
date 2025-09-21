package com.nimbusrun.autoscaler.autoscaler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusrun.compute.ActionPool;
import com.nimbusrun.compute.Compute;
import com.nimbusrun.compute.ComputeConfigResponse;
import com.nimbusrun.compute.DeleteInstanceRequest;
import com.nimbusrun.compute.ListInstanceResponse;
import com.nimbusrun.compute.ListInstanceResponse.Instance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.yaml.snakeyaml.Yaml;

public class ComputeMock extends Compute {

  @Getter
  private final Map<String, ActionPool> actionPoolMap = new HashMap<>();
  @Getter
  private final Map<String, InstanceManager> instanceMap = new HashMap<>();

  @Override
  public ListInstanceResponse listComputeInstances(ActionPool actionPool) {
    return new ListInstanceResponse(this.instanceMap.get(actionPool.getName()).getInstanceMap().values().stream().toList());
  }

  @Override
  public Map<String, ListInstanceResponse> listAllComputeInstances() {
    Map<String, ListInstanceResponse> map = new HashMap<>();
    this.actionPoolMap.forEach((name, actionPool)->{
      map.put(name, listComputeInstances(actionPool));
    });
    return map;
  }

  @Override
  public boolean createCompute(ActionPool actionPool) throws Exception {
    this.instanceMap.get(actionPool.getName()).putInstance(this.createInstanceName());
    Thread.sleep(10);//Represents api taking its sweet time in making an instance
    return true;
  }

  @Override
  public boolean deleteCompute(DeleteInstanceRequest deleteInstanceRequest) {
    try {
      this.instanceMap.get(deleteInstanceRequest.getActionPool().getName())
          .deleteInstance(deleteInstanceRequest.getInstanceId());
    } catch (NullPointerException e){
      e.printStackTrace();
    }
    return true;
  }

  @Override
  public List<ActionPool> listActionPools() {
    return this.actionPoolMap.values().stream().toList();
  }

  String blah = """
      actionPool:
        - name: one
          maxInstances: 10
          instanceIdleScaleDownTimeInMinutes: 10
          isDefault: true
        - name: two
          maxInstances: 10
          instanceIdleScaleDownTimeInMinutes: 10
          default: false
      """;

  @Override
  public ComputeConfigResponse receiveComputeConfigs(Map<String, Object> map,
      String autoScalerName) {
    MockConfig mockConfig = d(map);
    this.actionPoolMap.putAll(mockConfig.getActionPool().stream()
        .collect(Collectors.toMap(ActionPool::getName, Function.identity())));

    actionPoolMap.forEach((name, actionPoolMap)->{
      instanceMap.put(name, new InstanceManager(name));
    });
    return new ComputeConfigResponse(new ArrayList<>(), new ArrayList<>(),
        mockConfig.getActionPool());
  }

  public MockConfig d(Map<String, Object> yamlData) {
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      String jsonString = objectMapper.writeValueAsString(yamlData);
      return objectMapper.readValue(jsonString, MockConfig.class);
    } catch (Exception e) {
      e.printStackTrace();
      Assertions.fail(e.getMessage());
    }
    return null;
  }
}
class InstanceManager{
  @Getter
  private String actionPoolName;
  @Getter
  private ConcurrentHashMap<String, ListInstanceResponse.Instance> instanceMap;
  public InstanceManager(String actionPoolName){
    this.actionPoolName = actionPoolName;
    this.instanceMap = new ConcurrentHashMap<>();
  }

  public void putInstance(String name){
    instanceMap.put(name,new ListInstanceResponse.Instance(name, name,System.currentTimeMillis()));
  }

  public void deleteInstance(String name){
    try{
      instanceMap.remove(name);
    }catch (NullPointerException e){
      //could happen but unlikely
      e.printStackTrace();
    }
  }

}