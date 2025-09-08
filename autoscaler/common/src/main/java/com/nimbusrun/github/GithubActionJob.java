package com.nimbusrun.github;


import com.nimbusrun.Constants;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.json.JSONArray;
import org.json.JSONObject;

public class GithubActionJob implements Comparable<GithubActionJob>, Comparator<GithubActionJob> {

  private final String id;
  private final String runId;
  private final WorkflowJobAction action;
  private final String workflowName;
  private final String htmlUrl;
  private final String name;
  private final String conclusion;
  private final Long startedAt;
  private final Long completedAt;
  private final String runUrl;

  private final List<String> labels;
  private final List<String> invalidLabels;
  private final String actionPoolName;
  private final String actionGroupName;
  private final String repositoryFullName;
  private final String jsonStr;

  public GithubActionJob(String id, String runId, WorkflowJobAction action, String workflowName,
      String htmlUrl, String name, String conclusion, Long startedAt, Long completedAt,
      String runUrl,
      List<String> labels, List<String> invalidLabels, String actionPoolName,
      String actionGroupName,
      String repositoryFullName, String jsonStr) {
    this.id = id;
    this.runId = runId;
    this.action = action;
    this.workflowName = workflowName;
    this.htmlUrl = htmlUrl;
    this.name = name;
    this.conclusion = conclusion;
    this.startedAt = startedAt;
    this.completedAt = completedAt;
    this.runUrl = runUrl;
    this.labels = labels;
    this.invalidLabels = invalidLabels;
    this.actionPoolName = actionPoolName;
    this.actionGroupName = actionGroupName;
    this.repositoryFullName = repositoryFullName;
    this.jsonStr = jsonStr;
  }

  public static GithubActionJob fromJson(JSONObject object) {
    JSONObject job = object.getJSONObject("workflow_job");
    JSONObject repository = object.getJSONObject("repository");

    UnaryOperator<String> jobGetStr = (key) -> {
      if (job.has(key)) {
        Object obj = job.get(key);
        if (obj == null) {
          return null;
        }
        return obj.toString();
      }
      return null;
    };
    UnaryOperator<String> repoGetStr = (key) -> {
      if (repository.has(key)) {
        Object obj = repository.get(key);
        if (obj == null) {
          return null;
        }
        return obj.toString();
      }
      return null;
    };
    String id = jobGetStr.apply("id");
    String runId = jobGetStr.apply("run_id");
    WorkflowJobAction status = WorkflowJobAction.fromString(object.getString("action"));
    String htmlUrl = jobGetStr.apply("html_url");
    String name = jobGetStr.apply("name");
    String workflowName = jobGetStr.apply("workflow_name");
    String conclusion = jobGetStr.apply("conclusion");
    Long startedAt = Optional.ofNullable(jobGetStr.apply("started_at")).map(i -> {
      try {
        return Instant.parse(i).toEpochMilli();
      } catch (Exception e) {
      }
      return null;
    }).orElse(null);
    Long completedAt = Optional.ofNullable(jobGetStr.apply("completed_at")).map(i -> {
      try {
        return Instant.parse(i).toEpochMilli();
      } catch (Exception e) {
      }
      return null;
    }).orElse(null);
    String repositoryName = repoGetStr.apply("full_name");
    String runUrl = job.getString("run_url");
    JSONArray labels = job.getJSONArray("labels");
    List<String> labelList = new ArrayList<>();
    String actionGroupName = null;
    String actionPoolName = null;
    List<String> invalidLabels = new ArrayList<>();
    for (int i = 0; i < labels.length(); i++) {
      labelList.add(labels.getString(i));
      Optional<String> actionGroupValue = findNimbusRunLabel(labels.getString(i),
          Constants.ACTION_GROUP_LABEL_KEY);
      Optional<String> actionPoolValue = findNimbusRunLabel(labels.getString(i),
          Constants.ACTION_POOL_LABEL_KEY);
      if (actionGroupValue.isPresent()) {
        actionGroupName = actionGroupValue.get();
      } else if (actionPoolValue.isPresent()) {
        actionPoolName = actionPoolValue.get();
      } else {
        invalidLabels.add(labels.getString(i));
      }
    }

    return new GithubActionJobBuilder().withId(id).withRunId(runId).withAction(status)
        .withHtmlUrl(htmlUrl).withName(name).withConclusion(conclusion).withStartedAt(startedAt)
        .withCompletedAt(completedAt)
        .withWorkflowName(workflowName)
        .withRunUrl(runUrl)
        .withLabels(labelList)
        .withInvalidLabels(invalidLabels)
        .withActionGroupName(actionGroupName)
        .withActionPoolName(actionPoolName)
        .withJsonStr(object.toString())
        .withRepositoryFullName(repositoryName).build();
  }

  public static Optional<String> findNimbusRunLabel(String label, String expectedKey) {
    String cleanedLabel = label.trim().replace(" ", "");
    String[] labelParts = cleanedLabel.split("=");
    if (labelParts.length != 2) {
      return Optional.empty();
    }
    String key = labelParts[0];
    String value = labelParts[1];
    if (key.equalsIgnoreCase(expectedKey)) {
      return Optional.of(value);
    }
    return Optional.empty();
  }

  @Override
  public int compareTo(GithubActionJob o) {
    return this.compare(this, o);
  }

  @Override
  public int compare(GithubActionJob o1, GithubActionJob o2) {
    int w1 = Optional.ofNullable(o1).map(o->o.getAction().getOrder() ).orElse(5555);
    int w2 = Optional.ofNullable(o2).map(o->o.getAction().getOrder() ).orElse(5555);
    return w1-w2;
  }

  @Override
  public Comparator<GithubActionJob> thenComparing(Comparator<? super GithubActionJob> other) {
    return Comparator.super.thenComparing(other);
  }

  @Override
  public boolean equals(Object object) {
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    GithubActionJob that = (GithubActionJob) object;
    return Objects.equals(id, that.id) && Objects.equals(runId, that.runId) && Objects.equals(action, that.action);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, runId);
  }



  public String simpleDescription() {
    return "id: %s, run_id: %s, repository_name: %s, run_html_url: %s".formatted(this.id,
        this.runId, this.repositoryFullName, this.htmlUrl);
  }

  public String getId() {
    return id;
  }

  public String getRunId() {
    return runId;
  }

  public WorkflowJobAction getAction() {
    return action;
  }

  public String getHtmlUrl() {
    return htmlUrl;
  }

  public String getWorkflowName() {
    return workflowName;
  }

  public String getName() {
    return name;
  }

  public String getConclusion() {
    return conclusion;
  }

  public Long getStartedAt() {
    return startedAt;
  }

  public Long getCompletedAt() {
    return completedAt;
  }

  public String getJsonStr() {
    return jsonStr;
  }

  public String getRunUrl() {
    return runUrl;
  }


  public List<String> getLabels() {
    return labels;
  }

  public List<String> getInvalidLabels() {
    return invalidLabels;
  }


  public String getRepositoryFullName() {
    return repositoryFullName;
  }

  public Optional<String> getActionPoolName() {
    return Optional.ofNullable(actionPoolName);
  }

  public Optional<String> getActionGroupName() {
    return Optional.ofNullable(actionGroupName);
  }


  public static final class GithubActionJobBuilder {

    private String id;
    private String runId;
    private WorkflowJobAction action;
    private String workflowName;
    private String htmlUrl;
    private String name;
    private String conclusion;
    private Long startedAt;
    private Long completedAt;
    private String runUrl;
    private List<String> labels;
    private List<String> invalidLabels;
    private String actionPoolName;
    private String actionGroupName;
    private String repositoryFullName;
    private String jsonStr;

    private GithubActionJobBuilder() {
    }

    public static GithubActionJobBuilder aGithubActionJob() {
      return new GithubActionJobBuilder();
    }

    public GithubActionJobBuilder withId(String id) {
      this.id = id;
      return this;
    }

    public GithubActionJobBuilder withRunId(String runId) {
      this.runId = runId;
      return this;
    }

    public GithubActionJobBuilder withAction(WorkflowJobAction action) {
      this.action = action;
      return this;
    }

    public GithubActionJobBuilder withWorkflowName(String workflowName) {
      this.workflowName = workflowName;
      return this;
    }

    public GithubActionJobBuilder withHtmlUrl(String htmlUrl) {
      this.htmlUrl = htmlUrl;
      return this;
    }

    public GithubActionJobBuilder withName(String name) {
      this.name = name;
      return this;
    }

    public GithubActionJobBuilder withConclusion(String conclusion) {
      this.conclusion = conclusion;
      return this;
    }

    public GithubActionJobBuilder withStartedAt(Long startedAt) {
      this.startedAt = startedAt;
      return this;
    }

    public GithubActionJobBuilder withCompletedAt(Long completedAt) {
      this.completedAt = completedAt;
      return this;
    }

    public GithubActionJobBuilder withRunUrl(String runUrl) {
      this.runUrl = runUrl;
      return this;
    }

    public GithubActionJobBuilder withLabels(List<String> labels) {
      this.labels = labels;
      return this;
    }

    public GithubActionJobBuilder withInvalidLabels(List<String> invalidLabels) {
      this.invalidLabels = invalidLabels;
      return this;
    }

    public GithubActionJobBuilder withActionPoolName(String actionPoolName) {
      this.actionPoolName = actionPoolName;
      return this;
    }

    public GithubActionJobBuilder withActionGroupName(String actionGroupName) {
      this.actionGroupName = actionGroupName;
      return this;
    }

    public GithubActionJobBuilder withRepositoryFullName(String repositoryFullName) {
      this.repositoryFullName = repositoryFullName;
      return this;
    }

    public GithubActionJobBuilder withJsonStr(String jsonStr) {
      this.jsonStr = jsonStr;
      return this;
    }

    public GithubActionJob build() {
      return new GithubActionJob(id, runId, action, workflowName, htmlUrl, name, conclusion,
          startedAt, completedAt, runUrl, labels, invalidLabels, actionPoolName, actionGroupName,
          repositoryFullName, jsonStr);
    }
  }
}
