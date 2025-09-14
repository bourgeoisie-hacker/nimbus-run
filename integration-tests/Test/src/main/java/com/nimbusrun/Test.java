package com.nimbusrun;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public class Test {

  public static final String HOST_NAME = "104.154.85.106";
  public static final String REPOSITORY = "test7";
  public static final String ORGANIZATION = "bourgeoisie-whacker";
  public static final String DEFAULT_BRANCH = "master";
  public static final String WORKFLOW_NAME_TEMPLATE = "${WORKFLOW_NAME}";
  public static final String DUMMY_WORKFLOW_FILE = "test-cases/dummy.yaml";
  public static final String AUTOSCALER_AWS_JAR = "/Users/austin.poole/git/github-actions-autoscaler/autoscaler/target/autoscaler-aws.jar";
  //Collect all Profiles
  //Create a com.nimbusrun.Test repo and upload a workflow to triggered via workflow dispatch or push it'll depend on the order. It should be triggered by workflow dispatch. The reason for this is because the actual tests that will be running will need to trigger the process themselves.
  //Create a webhook for each test
  // There can be a max of 20 webhooks so the maximum parallel can be 20.


  // Each com.nimbusrun.Test should be isolated from each other
  // Each test should start up an instance

  //Part 2
  // Create Repository
  // Crawl Profiles
  // Assign org stuff to profile

  public static GithubResources createRepository(List<Profile> profiles)
      throws IOException, InterruptedException {
    String gitToken = System.getenv("GITHUB_TOKEN");
    GHOrganization org = createGHOrganization(gitToken);
    GHRepository repository = createRepo(org, REPOSITORY, DEFAULT_BRANCH);
    for(Profile p : profiles) {
      createWorkflows(p, repository);
    }
    return new GithubResources(org, repository);
  }


  public static void main(String[] args) throws IOException, InterruptedException {

    String gitToken = System.getenv("GITHUB_TOKEN");
    GHOrganization org = createGHOrganization(gitToken);
    List<GHHook> hooks = org.getHooks();
    GHRepository repository = createRepo(org, REPOSITORY, DEFAULT_BRANCH);
    List<Profile> profiles = crawlProfiles(gitToken);
    createWorkflows(profiles.get(0), repository);
    Thread.sleep(5000);
    List<GHWorkflow> workflows = repository.listWorkflows().toList();
    GHHook hook = createWebhook(profiles.get(0), org, hooks);
    profiles.get(0).setHook(hook);
    List<String> errors = new ArrayList<>();
    matchProfileToHook(profiles, workflows, errors);


    profiles.get(0).startNimbusRun();

//
//    String startup[] = ("java -jar " + AUTOSCALER_AWS_JAR).split(
//        " ");
//    String envp[] = ("GITHUB_TOKEN=" + gitToken
//        + ";NIMBUS_RUN_CONFIGURATION_FILE=/Users/austin.poole/git/github-actions-autoscaler/docker-compose-implied/config_examples/autoscaler/config-aws.yaml;SERVER_PORT=8081").split(
//            ";");
//
//    Process child = Runtime.getRuntime().exec(startup, envp);
//    pipeAsync("info",child.getInputStream(), System.out);
//    pipeAsync("error",child.getErrorStream(), System.err);
    Thread.sleep(90000);
//   r();

    // Create repository
    // push up all workflows
  }


  public static void createStartupCommand(Profile profile, String gitToken){
    String [] startup = ("java -jar " + AUTOSCALER_AWS_JAR).split(" ");
    String[] envp = new String[3];
    envp[0] = "GITHUB_TOKEN=" + gitToken;
    envp[1] = "NIMBUS_RUN_CONFIGURATION_FILE="+ profile.getConfig().toAbsolutePath().toString();
    envp[2] = "SERVER_PORT=" + profile.getPort();

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
  private static Thread pipeAsync(String name, InputStream in, PrintStream out) {
    Thread t = new Thread(() -> {
      try (BufferedReader r = new BufferedReader(
          new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = r.readLine()) != null) {
          out.println(line);
        }
      } catch (IOException ignored) {
      }
    }, "pipe-"+name );
    t.setDaemon(true);
    t.start();
    return t;
  }
//  public static void create() throws IOException {
//    GitHub gh = new GitHubBuilder().withOAuthToken(System.getenv("GITHUB_TOKEN")).build();
//    gh.getOrganization("b").we
//// Create under an org (or use gh.getMyself() to create under your user)
//    GHRepository repo = gh.createRepository("test1").private_(true).create();
//

  /// / Add several files (Contents API = one commit per file)
//    Map<String,String> files = Map.of(
//        "README.md", "# My Repo\n",
//        "src/App.java", "class App { public static void main(String[] a){ System.out.println(\"hi\"); }}");
//
//    for (var e : files.entrySet()) {
//      repo.createContent()
//          .path(e.getKey())
//          .message("Add " + e.getKey())
//          .content(e.getValue())
//          .branch("main")
//          .commit();
//    }
//  }
  public static void createWorkflows(Profile profile, GHRepository repository)
      throws IOException, InterruptedException {
    String path = ".github/workflows/%s.yaml".formatted(profile.getWorkflowName());
    try {
      GHContent content = repository.getFileContent(path);
      if (content != null && content.isFile()) {
        repository.getFileContent(path).update(Files.readString(profile.getWorkflow())
                .replace(WORKFLOW_NAME_TEMPLATE, profile.getWorkflowName()), "wazzup... so 2000s")
            .getCommit();
        return;
      }
    } catch (FileNotFoundException e) {
      log.warn("{} doesn't exist. Error produced: {}", path, e.getMessage());
    }

    repository.createContent()
        .path(path)
        .message("Add Add workflow")
        .content(Files.readString(Paths.get(DUMMY_WORKFLOW_FILE))
            .replace(WORKFLOW_NAME_TEMPLATE, profile.getWorkflowName()))
        .branch(DEFAULT_BRANCH)
        .commit();
    Thread.sleep(5000);
    // Because github won't recognize the workflow if it's' not updated or called
    repository.getFileContent(path).update(Files.readString(profile.getWorkflow())
        .replace(WORKFLOW_NAME_TEMPLATE, profile.getWorkflowName()), "wazzup").getCommit();


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

  public static GHOrganization createGHOrganization(String githubToken) throws IOException {
    GitHub gh = new GitHubBuilder().withOAuthToken(githubToken).build();
    return gh.getOrganization(ORGANIZATION);

  }

  public static GHRepository createRepo(GHOrganization org, String repoName, String defaultBranch)
      throws IOException {
    GHRepository repository = org.getRepository(repoName);
    if (repository != null) {
      return repository;
    }
    return org.createRepository(repoName).defaultBranch(defaultBranch).create();
  }

  public static List<Profile> crawlProfiles(String gitToken) throws IOException {
    List<Profile> profiles = new ArrayList<>();
    AtomicInteger ai = new AtomicInteger(8091);
    Files.walk(Paths.get("test-cases/aws"), 1)
        .filter(p -> !p.toString().equalsIgnoreCase("test-cases/aws")).forEach(t -> {
          Path config = t.resolve("nimbusconfig.yaml");
          Path workflow = t.resolve("workflow.yaml");
          if (Files.isRegularFile(config) && Files.isRegularFile(workflow)) {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(getInputStreamFromFile(config));
            Map<String, Object> githubMap = (Map<String, Object>) map.get("github");
            String webhookSecret = null;
            if (githubMap.containsKey("webhookSecret")) {
              webhookSecret = githubMap.get("webhookSecret").toString();
            }
            Profile p = new Profile(HOST_NAME, t.getFileName().toString(), config, workflow,
                t.getFileName().toString(), ai.getAndIncrement(), webhookSecret);
            createStartupCommand(p,gitToken);
            profiles.add(p);

          }
        });
    return profiles;

  }

  public static InputStream getInputStreamFromFile(Path p) {
    try {
      return Files.newInputStream(p);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Data
  public static class Profile {

    private final String workflowName;
    private final Path config;
    private final Path workflow;
    private final String runnerGroupName;
    private final int port;
    private final String webhook;
    private final String webhookSecret;
    private Process process;
    private GHWorkflow ghWorkflow;
    private GHHook hook;
    private String[] envp;
    private String[] startUp;
    private Thread infoThread;
    private Thread errorThread;

    public Profile(String hostName, String workflowName, Path config, Path workflow,
        String runnerGroupName, int port,
        String webhookSecret) {
      this.workflowName = workflowName;
      this.config = config;
      this.workflow = workflow;
      this.runnerGroupName = runnerGroupName;
      this.port = port;
      this.webhookSecret = webhookSecret;
      this.webhook = "http://%s:%d/webhook".formatted(hostName, port);
    }

    public void startNimbusRun() throws IOException {
      Process child = Runtime.getRuntime().exec(startUp, envp);
      this.infoThread = pipeAsync(this.workflowName+"-info", child.getInputStream(), System.out);
      this.errorThread = pipeAsync(this.workflowName+"-err", child.getErrorStream(), System.err);
    }
  }

}
