package com.nimbusrun.compute.gcp.v1;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusrun.compute.Constants;
import com.nimbusrun.compute.GithubApi;
import com.nimbusrun.compute.gcp.v1.GCPConfig.ActionPool;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.yaml.snakeyaml.Yaml;

@ExtendWith(SpringExtension.class)
public class GCPComputeServiceTest {


  @Value("classpath:configs/bad_compute_config_1.yaml")
  Resource badCompute1;

  @Value("classpath:configs/bad_compute_config_2.yaml")
  Resource badCompute2;
  GithubApi githubApi;

  public GCPComputeServiceTest() throws IOException {
    this.githubApi = new TestGithubApi("org", 1, "prod");
  }

  @Test
  public void testCreateLabelFilter(){
    GCPComputeService gcpComputeService = Mockito.spy(new GCPComputeService(githubApi));
    gcpComputeService.DEFAULT_INSTANCE_LABELS.put("house", "value");
    String filter = gcpComputeService.createLabelFilter("test");
    String templ = "labels.%s=%s";
    assertEquals("%s AND %s".formatted(templ.formatted(Constants.ACTION_POOL_LABEL_KEY, "test"), templ.formatted("house","value")),filter);
  }
  @Test
  public void testConfigs() throws IOException {
    GCPComputeService gcpComputeService = Mockito.spy(new GCPComputeService(githubApi));

    // In production this "map" is the 'compute' section. We'll mimic that.
    Map<String, Object> computeMap = new Yaml().load(badCompute1.getInputStream());

    // Map -> JSON -> GCPConfig + fill defaults
    GCPConfig cfg = new GCPConfig().createGcpConfigs(new Yaml().dump(computeMap));

    Mockito.doCallRealMethod().when(gcpComputeService)
        .validateActionPools(Mockito.any(), Mockito.any(), Mockito.any());
    Mockito.doReturn(true).when(gcpComputeService).regionExists(Mockito.any(), Mockito.any());
    Mockito.doReturn(true).when(gcpComputeService).zoneExists(Mockito.any(), Mockito.any());

    String actionPoolName = cfg.getActionPools().get(0).getName();
    String tepl = "Action pool " + actionPoolName + " %s";
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    gcpComputeService.validateActionPools(cfg.getActionPools(), errors, warnings);
    List<String> expectedFirstActionPoolErrors = List.of(tepl.formatted("missing projectId"),
        tepl.formatted("missing region"),tepl.formatted("missing zones"),
        tepl.formatted("missing instanceType"), "serviceAccountPath file at",
        tepl.formatted("missing subnet"), tepl.formatted("missing vpc"),
        tepl.formatted("has unknown operating system"));
    List<String> missing = assertValues(expectedFirstActionPoolErrors, errors);
    assertLinesMatch(List.of(), missing, "Missing errors from validation");
    assertTrue(cfg.getActionPools().stream().noneMatch(ActionPool::isDefault));
  }

  @Test
  public void testOsArch() throws IOException {
    GCPComputeService gcpComputeService = Mockito.spy(new GCPComputeService(githubApi));

    // In production this "map" is the 'compute' section. We'll mimic that.
    Map<String, Object> computeMap = new Yaml().load(badCompute2.getInputStream());

    // Map -> JSON -> GCPConfig + fill defaults
    GCPConfig cfg = new GCPConfig().createGcpConfigs(new Yaml().dump(computeMap));

    Mockito.doCallRealMethod().when(gcpComputeService)
        .validateActionPools(Mockito.any(), Mockito.any(), Mockito.any());
    Mockito.doReturn(true).when(gcpComputeService).regionExists(Mockito.any(), Mockito.any());
    Mockito.doReturn(true).when(gcpComputeService).zoneExists(Mockito.any(), Mockito.any());

    String actionPoolName1 = cfg.getActionPools().get(0).getName();
    String actionPoolName2 = cfg.getActionPools().get(1).getName();

    String tepl1 = "Action pool " + actionPoolName1 + " %s";
    String tepl2 = "Action pool " + actionPoolName2 + " %s";
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    gcpComputeService.validateActionPools(cfg.getActionPools(), errors, warnings);
    List<String> expectedErrors = List.of(
        tepl1.formatted("has invalid operating system specified ubuntu23.04"),
        tepl1.formatted("has unknown cpu architecture specified"),
        tepl2.formatted("has invalid operating system specified debian13")
    );
    List<String> missing = assertValues(expectedErrors, errors);
    assertLinesMatch(List.of(), missing, "Missing errors from validation");
    assertTrue(cfg.getActionPools().stream().noneMatch(ActionPool::isDefault));
  }

  public List<String> assertValues(List<String> items, List<String> messages) {
    List<String> missing = new ArrayList<>();
    OUTER:
    for (String item : items) {
      for (String msg : messages) {
        if (msg.contains(item)) {
          continue OUTER; //Oh this feels so bad hahahahahaha
        }
      }
      missing.add(item);
    }
    return missing;
  }

}
