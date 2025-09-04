package com.nimbusrun.autoscaler.github.orm.runner;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Runner {

  private int id;
  private String name;
  private String os;
  private String status;
  private boolean busy;
  private List<Label> labels;

  public Runner(int id, String name, String os, String status, boolean busy,
      List<Label> labels) {
    this.id = id;
    this.name = name;
    this.os = os;
    this.status = status;
    this.busy = busy;
    this.labels = labels;
  }

  public static final class RunnerBuilder {

    private int id;
    private String name;
    private String os;
    private String status;
    private boolean busy;
    private List<Label> labels;

    private RunnerBuilder() {
    }

    public static RunnerBuilder aRunner() {
      return new RunnerBuilder();
    }

    public RunnerBuilder withId(int id) {
      this.id = id;
      return this;
    }

    public RunnerBuilder withName(String name) {
      this.name = name;
      return this;
    }

    public RunnerBuilder withOs(String os) {
      this.os = os;
      return this;
    }

    public RunnerBuilder withStatus(String status) {
      this.status = status;
      return this;
    }

    public RunnerBuilder withBusy(boolean busy) {
      this.busy = busy;
      return this;
    }

    public RunnerBuilder withLabels(List<Label> labels) {
      this.labels = labels;
      return this;
    }

    public Runner build() {
      Runner runner = new Runner();
      runner.setId(id);
      runner.setName(name);
      runner.setOs(os);
      runner.setStatus(status);
      runner.setBusy(busy);
      runner.setLabels(labels);
      return runner;
    }
  }
}