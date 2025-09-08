package com.nimbusrun.github;


import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.json.JSONObject;

public class GithubActionRun implements Comparable<GithubActionRun> , Comparator<GithubActionRun> {

  private final String name;
  private final String id;
  private final String runNumber;
  private final String event;
  private final WorkflowRunAction action;
  private final String htmlUrl;
  private final String conclusion;
  private final Long createdAt;
  private final Long updatedAt;
  private final String runHtmlUrl;

  private final String repositoryFullName;
  private final String jsonStr;

  public GithubActionRun(String name, String id, String runNumber, String event,
      WorkflowRunAction action, String htmlUrl, String conclusion, Long createdAt, Long updatedAt,
      String runHtmlUrl, String repositoryFullName, String jsonStr) {
    this.name = name;
    this.id = id;
    this.runNumber = runNumber;
    this.event = event;
    this.action = action;
    this.htmlUrl = htmlUrl;
    this.conclusion = conclusion;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.runHtmlUrl = runHtmlUrl;
    this.repositoryFullName = repositoryFullName;
    this.jsonStr = jsonStr;
  }

  public static GithubActionRun fromJson(JSONObject object) {
    JSONObject run = object.getJSONObject("workflow_run");
    JSONObject repository = object.getJSONObject("repository");

    UnaryOperator<String> runGetStr = (key) -> {
      if (run.has(key)) {
        Object obj = run.get(key);
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
    String id = runGetStr.apply("id");
    String runNumber = runGetStr.apply("run_number");
    WorkflowRunAction action = WorkflowRunAction.fromString(object.getString("action"));
    String htmlUrl = runGetStr.apply("html_url");
    String name = runGetStr.apply("name");
    String conclusion = runGetStr.apply("conclusion");
    Long createdAt = Optional.ofNullable(runGetStr.apply("created_at")).map(i -> {
      try {
        return Instant.parse(i).toEpochMilli();
      } catch (Exception e) {
      }
      return null;
    }).orElse(null);
    Long updatedAt = Optional.ofNullable(runGetStr.apply("updated_at")).map(i -> {
      try {
        return Instant.parse(i).toEpochMilli();
      } catch (Exception e) {
      }
      return null;
    }).orElse(null);
    String repositoryName = repoGetStr.apply("full_name");
    String event = run.getString("event");
    String runHtmlUrl = run.getString("html_url");




    return new GithubActionRunBuilder()
        .withName(name).withId(id)
        .withRunNumber(runNumber)
        .withEvent(event)
        .withAction(action)
        .withHtmlUrl(htmlUrl)
        .withConclusion(conclusion)
        .withCreatedAt(createdAt)
        .withUpdatedAt(updatedAt)
        .withRunHtmlUrl(runHtmlUrl)
        .withRepositoryFullName(repositoryName)
        .withJsonStr(object.toString()).build();

  }

  @Override
  public int compareTo(GithubActionRun o) {
    if(o == null || o.getUpdatedAt() == null){
      return 1;
    }
    return compare(this, o);
  }

  @Override
  public int compare(GithubActionRun o1, GithubActionRun o2) {
    int w1 = Optional.ofNullable(o1).map(o->o.getAction().getOrder() ).orElse(5555);
    int w2 = Optional.ofNullable(o2).map(o->o.getAction().getOrder() ).orElse(5555);
    return w1-w2;
  }

  @Override
  public boolean equals(Object object) {
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    GithubActionRun run = (GithubActionRun) object;
    return Objects.equals(id, run.id) && Objects.equals(runNumber, run.runNumber) && Objects.equals(action, run.action)
        && action == run.action;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, runNumber, action);
  }

  public String simpleDescription() {
    return "id: %s, run_attempt: %s, repository_name: %s, run_html_url: %s".formatted(this.id,
        this.runNumber, this.repositoryFullName, this.runHtmlUrl);
  }

  public String getName() {
    return name;
  }

  public String getId() {
    return id;
  }

  public String getRunNumber() {
    return runNumber;
  }

  public String getEvent() {
    return event;
  }

  public WorkflowRunAction getAction() {
    return action;
  }

  public String getHtmlUrl() {
    return htmlUrl;
  }

  public String getConclusion() {
    return conclusion;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public Long getUpdatedAt() {
    return updatedAt;
  }

  public String getRunHtmlUrl() {
    return runHtmlUrl;
  }

  public String getRepositoryFullName() {
    return repositoryFullName;
  }

  public String getJsonStr() {
    return jsonStr;
  }




  public static final class GithubActionRunBuilder {

    private String name;
    private String id;
    private String runNumber;
    private String event;
    private WorkflowRunAction action;
    private String htmlUrl;
    private String conclusion;
    private Long createdAt;
    private Long updatedAt;
    private String runHtmlUrl;
    private String repositoryFullName;
    private String jsonStr;

    private GithubActionRunBuilder() {
    }

    public static GithubActionRunBuilder aGithubActionRun() {
      return new GithubActionRunBuilder();
    }

    public GithubActionRunBuilder withName(String name) {
      this.name = name;
      return this;
    }

    public GithubActionRunBuilder withId(String id) {
      this.id = id;
      return this;
    }

    public GithubActionRunBuilder withRunNumber(String runNumber) {
      this.runNumber = runNumber;
      return this;
    }

    public GithubActionRunBuilder withEvent(String event) {
      this.event = event;
      return this;
    }

    public GithubActionRunBuilder withAction(WorkflowRunAction action) {
      this.action = action;
      return this;
    }

    public GithubActionRunBuilder withHtmlUrl(String htmlUrl) {
      this.htmlUrl = htmlUrl;
      return this;
    }

    public GithubActionRunBuilder withConclusion(String conclusion) {
      this.conclusion = conclusion;
      return this;
    }

    public GithubActionRunBuilder withCreatedAt(Long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public GithubActionRunBuilder withUpdatedAt(Long updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public GithubActionRunBuilder withRunHtmlUrl(String runHtmlUrl) {
      this.runHtmlUrl = runHtmlUrl;
      return this;
    }

    public GithubActionRunBuilder withRepositoryFullName(String repositoryFullName) {
      this.repositoryFullName = repositoryFullName;
      return this;
    }

    public GithubActionRunBuilder withJsonStr(String jsonStr) {
      this.jsonStr = jsonStr;
      return this;
    }

    public GithubActionRun build() {
      return new GithubActionRun(name, id, runNumber, event, action, htmlUrl, conclusion, createdAt,
          updatedAt, runHtmlUrl, repositoryFullName, jsonStr);
    }
  }
}
