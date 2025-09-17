package com.nimbusrun.setup;

import com.nimbusrun.ComputeType;
import com.nimbusrun.GithubApi;
import com.nimbusrun.config.Config;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

public class SetupResources {
  public static Logger log = LoggerFactory.getLogger(SetupResources.class);
  public static final String HOST_NAME = "104.154.85.106";
  public static final String REPOSITORY = "test7";
  public static final String ORGANIZATION = "bourgeoisie-whacker";
  public static final String DEFAULT_BRANCH = "master";
  public static final String WORKFLOW_NAME_TEMPLATE = "${WORKFLOW_NAME}";
  public static final String RUNNER_GROUP_NAME_TEMPLATE = "${RUNNER_GROUP}";
  public static final String GITHUB_TOKEN_TEMPLATE = "${GITHUB_TOKEN}";
  public static final String DUMMY_WORKFLOW_FILE = "test-cases/dummy.yaml";
  public static final String AUTOSCALER_AWS_JAR = "/Users/austin.poole/git/github-actions-autoscaler/autoscaler/target/autoscaler-aws.jar";
  private final GHOrganization org;
  private final GHRepository repository;
  private final Map<String, Profile> profileMap;
  private final GithubApi githubApi;
  private final Config testConfigs;


  public static void main(String[] args) throws Exception {
    SetupResources setupResources = new SetupResources(new Config());
    setupResources.startUp("aws_test-os");
    Thread.sleep(20000);
    setupResources.getProfileMap().get("aws_test-os").getProcess().close();

  }


  public  SetupResources(Config testConfigs) throws IOException, InterruptedException {
    this.testConfigs = testConfigs;
    this.githubApi = new GithubApi(testConfigs.getGitToken(), testConfigs.getOrganizationName());
    this.org = createGHOrganization(testConfigs.getGitToken());
    this.repository = createRepo(org, testConfigs.getRepositoryName(), testConfigs.getDefaultBranch());
    Thread.sleep(3000);//Give api time to think
    List<Profile> profiles = crawlProfiles();
    validateProfileNamesUnique(profiles);
    this.profileMap = profiles.stream().collect(Collectors.toMap(Profile::getWorkflowName, Function.identity()));
    createWorkflows(this.profileMap.values(), repository);
    List<String> errors = new ArrayList<>();
    matchProfileToHook(profiles, this.repository.listWorkflows().toList(), errors);
    if(!errors.isEmpty()){
      errors.forEach(log::error);
      System.exit(1);
    }

  }

  public Optional<Integer> startUp(String workflowName) throws IOException, InterruptedException {
    Profile profile = this.profileMap.get(workflowName);
    if(profile == null){
      log.error("profile: {} doesn't exists", workflowName);
      return Optional.empty();
    }
    profile.setHook(createWebhook(profile, this.org, listHooks()));
    boolean runnerGroup = this.githubApi.createRunnerGroup(profile.getRunnerGroupName());
    if(!runnerGroup){
      log.error("failed to generate runner Group for {} ", workflowName);
      return Optional.empty();
    }
    profile.startNimbusRun();
    return Optional.of(profile.getPort());
  }

  public boolean triggerWorkflow(String workflow) throws IOException {
    Profile profile = profileMap.get(workflow);
    if(profile == null){
      return false;
    }
    profile.getGhWorkflow().dispatch(testConfigs.getDefaultBranch());
    return true;
  }

  public void stopAll(){
    this.profileMap.values().forEach(Profile::stopNimbusRun);
  }

  public boolean isNimbusRunReadyForTraffic(String workflowName){
    Profile profile = this.profileMap.get(workflowName);
    if(profile == null){
      log.error("profile: {} doesn't exists", workflowName);
      return false;
    }
    return profile.isNimbusRunReadForTraffic();
  }

  public boolean isWorkflowAlive(String workflowName){
    Profile profile = this.profileMap.get(workflowName);
    if(profile == null){
      log.error("profile: {} doesn't exists", workflowName);
      return false;
    }
    return profile.getProcess().isAlive();
  }

  private synchronized List<GHHook> listHooks() throws IOException, InterruptedException {
    Thread.sleep(2000);
    return this.org.getHooks();
  }

  private void validateProfileNamesUnique(List<Profile> profiles) {
   Map<String, List<String>> counting= profiles.stream().map(Profile::getWorkflowName).collect(Collectors.groupingBy(Function.identity()));
   List<String> duplicates = counting.keySet().stream().filter(k-> counting.get(k).size() > 1).toList();
   duplicates.forEach(d-> log.error("Profile workflow name appears multiple times {}", d));// Shouldn't be possible but meh things change.
   if(!duplicates.isEmpty()){
     throw new RuntimeException("There are repeated workflow names");
   }
  }

  private void createWorkflows(Collection<Profile> profiles, GHRepository repository)
      throws IOException, InterruptedException {
    for(Profile p : profiles){
      createWorkflows(p, repository);
    }
  }




  public void createStartupCommand(Profile profile){
    String jar;
    if(profile.getComputeType() == ComputeType.AWS){
      jar = testConfigs.getAwsJarPath();
    }else{
      jar = testConfigs.getGcpJarPath();
    }

    String [] startup = ("java -jar " + jar).split(" ");
    String[] envp = new String[5];
    envp[0] = "GITHUB_TOKEN=" + this.testConfigs.getGitToken();
    envp[1] = "NIMBUS_RUN_CONFIGURATION_FILE="+ profile.getConfig().toAbsolutePath().toString();
    envp[2] = "SERVER_PORT=" + profile.getPort();
    envp[3] = "RUNNER_GROUP=" + profile.getRunnerGroupName();
    envp[4] = "ORGANIZATION_NAME=" + testConfigs.getOrganizationName();
    profile.setEnvp(envp);
    profile.setStartUp(startup);

  }
  public static void matchProfileToHook(List<Profile> profiles,List<GHWorkflow> workflows, List<String> errors){
    for (Profile p : profiles){
      Optional<GHWorkflow> workflow = workflows.stream()
          .filter(w -> w.getName().equalsIgnoreCase(p.getWorkflowName())).findFirst();
      if(workflow.isPresent()){
        p.setGhWorkflow(workflow.get());
      }else{
        errors.add("Missing workflow for Profile: " +p.getWorkflowName());
      }
    }
  }


  public static void createWorkflows(Profile profile, GHRepository repository)
      throws IOException, InterruptedException {
    String path = ".github/workflows/%s.yaml".formatted(profile.getWorkflowName());
    String contentStr = Files.readString(profile.getWorkflow())
        .replace(WORKFLOW_NAME_TEMPLATE, profile.getWorkflowName())
        .replace(RUNNER_GROUP_NAME_TEMPLATE, profile.getRunnerGroupName());
    String dummyContentStr = Files.readString(Paths.get(DUMMY_WORKFLOW_FILE))
        .replace(WORKFLOW_NAME_TEMPLATE, profile.getWorkflowName());
    try {
      GHContent content = repository.getFileContent(path);
      if (content != null && content.isFile()) {
        repository.getFileContent(path).update(contentStr, "wazzup... so 2000s")
            .getCommit();
        return;
      }
    } catch (FileNotFoundException e) {
      log.warn("{} doesn't exist. Error produced: {}", path, e.getMessage());
    }

    repository.createContent()
        .path(path)
        .message("Add Add workflow")
        .content(dummyContentStr)
        .branch(DEFAULT_BRANCH)
        .commit();
    Thread.sleep(2000);
    // Because github won't recognize the workflow if it's' not updated or called
    repository.getFileContent(path).update(contentStr, "wazzup").getCommit();


  }

  public static GHHook createWebhook(Profile profile, GHOrganization org, List<GHHook> hooks)
      throws IOException {
    Optional<GHHook> opt = hooks.stream()
        .filter(i -> profile.getWebhook().equalsIgnoreCase(i.getConfig().get("url"))).findFirst();
    if (opt.isPresent()) {

      return opt.get();
    }
    Map<String, String> configs = new HashMap<>();
    configs.put("url", profile.getWebhook());
    configs.put("content_type", "json");
    configs.put("insecure_ssl", "1");
    if (profile.getWebhookSecret() != null) {
      configs.put("secret", profile.getWebhookSecret());
    }
    return org.createHook("web", configs, List.of(GHEvent.WORKFLOW_RUN, GHEvent.WORKFLOW_JOB),
        true);
  }

  public GHOrganization createGHOrganization(String githubToken) throws IOException {
    GitHub gh = new GitHubBuilder().withOAuthToken(githubToken).build();
    return gh.getOrganization(this.testConfigs.getOrganizationName());

  }

  public static GHRepository createRepo(GHOrganization org, String repoName, String defaultBranch)
      throws IOException {
    GHRepository repository = org.getRepository(repoName);
    if (repository != null) {
      return repository;
    }
    return org.createRepository(repoName).defaultBranch(defaultBranch).create();
  }

  public List<Profile> crawlProfiles(String basePath, ComputeType computeType) throws IOException {
    List<Profile> profiles = new ArrayList<>();
    AtomicInteger ai = new AtomicInteger(8091);
    Files.walk(Paths.get(basePath), 1)
        .filter(p -> !p.toString().equalsIgnoreCase("test-cases/aws")).forEach(directory -> {
          Path config = directory.resolve("nimbusconfig.yaml");
          Path workflow = directory.resolve("workflow.yaml");
          if (Files.isRegularFile(config) && Files.isRegularFile(workflow)) {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(getInputStreamFromFile(config));
            Map<String, Object> githubMap = (Map<String, Object>) map.get("github");
            String webhookSecret = null;
            if (githubMap.containsKey("webhookSecret")) {
              webhookSecret = githubMap.get("webhookSecret").toString();
            }
            String workflowName = directory.getFileName().toString();
            Profile p = new Profile(computeType, HOST_NAME, workflowName, config, workflow,
                ai.getAndIncrement(), webhookSecret);
            createStartupCommand(p);

            profiles.add(p);

          }
        });
    return profiles;
  }
  public List<Profile> crawlProfiles() throws IOException {
    return Stream.of(crawlProfiles("test-cases/aws", ComputeType.AWS),crawlProfiles("test-cases/gcp", ComputeType.GCP)).flatMap(List::stream).toList();
  }



    public static InputStream getInputStreamFromFile(Path p) {
    try {
      return Files.newInputStream(p);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Map<String, Profile> getProfileMap() {
    return profileMap;
  }


}
