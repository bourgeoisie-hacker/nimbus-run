package com.nimbusrun.setup;

import com.nimbusrun.ComputeType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Data;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHWorkflow;

@Data
  public  class Profile {
    private final ComputeType computeType;
    private final String workflowName;
    private final Path config;
    private final Path workflow;
    private final int port;
    private final String webhook;
    private final String webhookSecret;
    private NimbusRunProcess process;
    private GHWorkflow ghWorkflow;
    private GHHook hook;
    private String[] envp;
    private String[] startUp;
    private final CopyOnWriteArrayList<String> logs;
    public Profile(ComputeType computeType, String hostName, String workflowName, Path config, Path workflow, int port,
        String webhookSecret) {
      this.computeType = computeType;
      this.workflowName = workflowName;
      this.config = config;
      this.workflow = workflow;
      this.port = port;
      this.webhookSecret = webhookSecret;
      this.webhook = "http://%s:%d/webhook".formatted(hostName, port);
      this.logs = new CopyOnWriteArrayList<>();
    }


    public void startNimbusRun() throws IOException {
      Process child = Runtime.getRuntime().exec(startUp, envp);
      Thread infoThread = pipeAsync(this.workflowName+"-info", child.getInputStream(), System.out);
      Thread errorThread = pipeAsync(this.workflowName+"-err", child.getErrorStream(), System.err);
      this.process = new NimbusRunProcess(child,infoThread, errorThread );

    }
  private  Thread pipeAsync(String name, InputStream in, PrintStream out) {
    Thread t = new Thread(() -> {
      try (BufferedReader r = new BufferedReader(
          new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = r.readLine()) != null) {
          out.println(line);
          logs.add(line);
        }
      } catch (IOException ignored) {
      }
    }, "pipe-" + name );
    t.setDaemon(true);
    t.start();
    return t;
  }


  public void stopNimbusRun() {
      if(this.process!=null && this.process.isAlive()){
        try {
          process.close();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
  }
  public boolean isNimbusRunReadForTraffic() {
    if (process != null && process.isAlive() && logs.stream()
        .anyMatch(log -> log.contains("Started NimbusRunApplication"))) {
      return true;
    }
    return false;
  }

  public String getWorkflowName() {
    return computeType.name().toLowerCase()+"_"+workflowName;
  }

  public String getRunnerGroupName() {
    return getWorkflowName();
  }
}