package com.nimbusrun.config;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Data
@Slf4j
public class Config {

  public static final String ENV_PREFIX = "NIMBUS_";
  public static final String TEST_CONFIG_PATH = ENV_PREFIX + "TEST_CONFIG_PATH";
  public static final String HOST_NAME = ENV_PREFIX + "HOST";
  public static final String GITHUB_TOKEN = ENV_PREFIX + "GITHUB_TOKEN";
  public static final String ORGANIZATION_NAME = ENV_PREFIX + "ORGANIZATION";
  public static final String DEFAULT_BRANCH_NAME = ENV_PREFIX + "master";
  public static final String REPOSITORY_NAME = ENV_PREFIX + "REPOSITORY";
  public static final String AUTOSCALER_AWS_JAR_NAME = ENV_PREFIX + "AUTOSCALER_AWS_JAR_PATH";
  public static final String AUTOSCALER_GCP_JAR_NAME = ENV_PREFIX + "AUTOSCALER_GCP_JAR_PATH";


  public static final String AWS_PREFIX = ENV_PREFIX + "AWS_";
  public static final String AWS_REGION = AWS_PREFIX + "REGION";
  public static final String AWS_SUBNET = AWS_PREFIX + "SUBNET";
  public static final String AWS_SECURITY_GROUP = AWS_PREFIX + "SECURITY_GROUP";


  public static final String GCP_PREFIX = ENV_PREFIX + "GCP_";
  public static final String GCP_PROJECT_ID = GCP_PREFIX + "PROJECT_ID";
  public static final String GCP_REGION = GCP_PREFIX + "REGION";
  public static final String GCP_SUBNET = GCP_PREFIX + "SUBNET";
  public static final String GCP_VPC= GCP_PREFIX + "VPC";
  public static final String GCP_ZONES= GCP_PREFIX + "ZONES";


  public static final String WORKFLOW_NAME_TEMPLATE = "${WORKFLOW_NAME}";
  public static final String DUMMY_WORKFLOW_FILE = "test-cases/dummy.yaml";
  public static final String DEFAULT_CONFIG_LOCATION = "config.json";

  private final String hostName;
  private final String gitToken;
  private final String organizationName;
  private final String defaultBranch;
  private final String repositoryName;
  private final String awsJarPath;
  private final String gcpJarPath;
  private final AwsConfig awsConfig;
  private final GcpConfig gcpConfig;

  public String getValue(JSONObject obj, String objAttr, String envName, List<String> errors) {
    if (System.getenv(envName) != null) {
      return (System.getenv(envName));
    } else if (obj.has(objAttr)) {
      return obj.getString(objAttr);
    } else {
      errors.add("missing " + objAttr);
    }
    return null;
  }
  public List<String> getList(JSONObject obj, String objAttr, String envName, List<String> errors) {
    if (System.getenv(envName) != null) {
      return Arrays.asList(System.getenv(envName).split(","));
    } else if (obj.has(objAttr)) {
      return obj.getJSONArray(objAttr).toList().stream().map(Object::toString).toList();
    } else {
      errors.add("missing " + objAttr);
    }
    return null;
  }


  public void jarExists(String jarPath, String templatedMsg, List<String> errors) {
    if (jarPath == null || !Files.exists(Paths.get(jarPath)) || !Files.isRegularFile(Paths.get(jarPath))) {
      errors.add(templatedMsg.formatted(jarPath));
    }
  }

  public Config() throws IOException {
    JSONObject object;
    String configPath = System.getenv(TEST_CONFIG_PATH);
    Path defaultConfigPath = Paths.get(DEFAULT_CONFIG_LOCATION);

    if (configPath != null) {
      Path path = Paths.get(configPath);
      if (Files.exists(path) && Files.isRegularFile(path)) {
        object = new JSONObject(Files.readAllBytes(path));
      }else {
        throw new RuntimeException("%s environment variable set to %s but is a regular file or not found on file system.".formatted(TEST_CONFIG_PATH, configPath));
      }

    } else if (Files.exists(defaultConfigPath) && Files.isRegularFile(defaultConfigPath)) {
      object = new JSONObject(Files.readString(defaultConfigPath));
    }else {
      object = new JSONObject();
    }

    List<String> errors = new ArrayList<>();
    this.hostName = getValue(object, "hostName", HOST_NAME, errors);
    this.gitToken = getValue(object, "githubToken", GITHUB_TOKEN, errors);
    this.organizationName = getValue(object, "organizationName", ORGANIZATION_NAME, errors);
    this.defaultBranch = getValue(object, "defaultBranch", DEFAULT_BRANCH_NAME, errors);
    this.repositoryName = getValue(object, "repositoryName", REPOSITORY_NAME, errors);
    this.awsJarPath = getValue(object, "awsJarPath", AUTOSCALER_AWS_JAR_NAME, errors);
    this.gcpJarPath = getValue(object, "gcpJarPath", AUTOSCALER_GCP_JAR_NAME, errors);
    jarExists(awsJarPath, "AWS Autoscaler jar at path %s either doesn't exist or isn't a file",
        errors);
    jarExists(gcpJarPath, "GCP Autoscaler jar at path %s either doesn't exist or isn't a file",
        errors);
    this.awsConfig = null;//parseAwsConfg(object, errors);
    this.gcpConfig = null;//parseGcpConfg(object, errors);
    if (!errors.isEmpty()) {
      for (String err : errors) {
        log.error(err);
      }
      throw new RuntimeException("Missing Configs");
    }
  }

  public AwsConfig parseAwsConfig(JSONObject obj, List<String> errors){
    String region = getValue(obj, "region", AWS_REGION, errors);
    String subnet = getValue(obj, "subnet", AWS_SUBNET, errors);
    List<String> securityGroup = getList(obj, "securityGroup", AWS_SECURITY_GROUP, errors);
    return new AwsConfig(region,subnet,securityGroup);

  }
  public GcpConfig parseGcpConfig(JSONObject obj, List<String> errors){
    String projectId = getValue(obj, "projectId", GCP_PROJECT_ID, errors);
    String region = getValue(obj, "region", GCP_REGION, errors);
    String subnet = getValue(obj, "subnet", GCP_SUBNET, errors);
    String vpc = getValue(obj, "vpc", GCP_VPC, errors);
    List<String> zones = getList(obj, "zones", GCP_ZONES, errors);
    return new GcpConfig(projectId,region,subnet,vpc,zones);

  }


}
