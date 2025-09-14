package com.nimbusrun.compute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public abstract class Compute {

  public Compute() {

  }

  /**
   * List instances that are associated with the action pool by name
   *
   * @param actionPool - action pool
   * @return ListInstanceResponse
   */
  public abstract ListInstanceResponse listComputeInstances(ActionPool actionPool);

  /**
   * The key should be the action pool name. All action pools should be returned even if the value is empty
   */
  public abstract Map<String, ListInstanceResponse> listAllComputeInstances();

  /**
   * Creates a compute instance for the associated action pool. Please keep in mind that the compute
   * instance should be discoverable in the {@link Compute#listComputeInstances} method. For
   * example: AWS instances are tagged with key/value pairs - action-pool=some_name1 -
   * nimbusrun=some_name2
   *
   * @param actionPool
   * @throws Exception
   */
  public abstract boolean createCompute(ActionPool actionPool) throws Exception;

  /**
   * Deletes the compute instance.
   *
   * @param deleteInstanceRequest
   * @return
   * @throws Exception
   */
  public abstract boolean deleteCompute(DeleteInstanceRequest deleteInstanceRequest)
      throws Exception;

  public abstract List<ActionPool> listActionPools() throws Exception;

  public abstract ComputeConfigResponse receiveComputeConfigs(Map<String, Object> map,
      String autoScalerName) throws Exception;

  public final String createRunnerLabels(Map<String, String> map) {
    return map.keySet().stream().map((key) -> {
      if (map.get(key) == null) {
        return key;
      } else {
        return "%s=%s".formatted(key, map.get(key));
      }
    }).collect(Collectors.joining(","));
  }


  private String createRunnerLabels(ActionPool actionPool, String runnerGroup) {
    Map<String, String> map = Map.of(Constants.ACTION_GROUP_LABEL_KEY, runnerGroup,
        Constants.ACTION_POOL_LABEL_KEY, actionPool.getName());
    return createRunnerLabels(map);
  }

  public String createInstanceName() {
    Random random = new Random();
    int randomInt = random.nextInt();
    String hex = Integer.toHexString(randomInt);

    return "github-runner-" + hex;
  }

  public String startUpScript(String runnerToken, String runnerGroup, ActionPool actionPool,
      String runnerName, String organization, ProcessorArchitecture architecture, OperatingSystem os) {
    if (os.getFamily() == OperatingSystemFamily.UBUNTU) {
      return startUpScriptUbuntu(runnerToken, runnerGroup, actionPool, runnerName, organization,
          architecture);
    } else if (os.getFamily() == OperatingSystemFamily.DEBIAN) {
      return startUpScriptDebian(runnerToken, runnerGroup, actionPool, runnerName, organization,
          architecture);
    }
    throw new RuntimeException("Unsupported selected");
  }

  public String startUpScriptUbuntu(String runnerToken, String runnerGroup, ActionPool actionPool,
      String runnerName, String organization, ProcessorArchitecture architecture) {
    String archStr = "";
    if (ProcessorArchitecture.X64 == architecture) {
      archStr = "x64";
    } else if (ProcessorArchitecture.ARM64 == architecture) {
      archStr = "arm64";
    } else {
      throw new RuntimeException(
          "%s is not supported processor architecture".formatted(architecture));
    }
    createRunnerLabels(actionPool, runnerGroup);
    Map<String, String> env = new HashMap<>();
    env.put("RUNNER_GROUP", runnerGroup);
    env.put("RUNNER_TOKEN", runnerToken);
    env.put("RUNNER_NAME", runnerName);
    env.put("ORGANIZATION", organization);
    env.put("RUNNER_LABELS", createRunnerLabels(actionPool, runnerGroup));
    env.put("ACTION_RUNNER_URL",
        "$(curl -s https://api.github.com/repos/actions/runner/releases/latest | jq -r '.assets[] | select(.name | startswith(\"actions-runner-linux-%s\")) | .browser_download_url')".formatted(
            archStr));
    return """
        #!/bin/bash
        export RUNNER_GROUP=${RUNNER_GROUP}
        export RUNNER_LABELS=${RUNNER_LABELS}
        export RUNNER_TOKEN=${RUNNER_TOKEN}
        export RUNNER_NAME=${RUNNER_NAME}
        export ORGANIZATION=${ORGANIZATION}
        export USER_AGENT=action-runner
        useradd -ms /bin/bash $USER_AGENT
        apt update -y
        apt-get install -y --no-install-recommends \\
                curl jq build-essential libssl-dev libffi-dev python3 python3-venv python3-dev python3-pip nano vim sudo
        sudo adduser $USER_AGENT sudo
        echo "$USER_AGENT ALL=(ALL) NOPASSWD: ALL" >>  /etc/sudoers
        # Install Docker
        apt-get install ca-certificates curl -y && \\
              install -m 0755 -d /etc/apt/keyrings && \\
              curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc && \\
              chmod a+r /etc/apt/keyrings/docker.asc && \\
              echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null && \\
              apt-get update -y && \\
              apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y
        
        #Add $USER_AGENT user to docker group
        usermod -aG docker $USER_AGENT && newgrp docker
        service docker start
        
        sudo -i -u $USER_AGENT bash << EOF
        mkdir /home/$USER_AGENT/actions-runner
        cd /home/$USER_AGENT/actions-runner
        curl -o actions-runner.tar.gz -L ${ACTION_RUNNER_URL}
        tar xzf ./actions-runner.tar.gz
        EOF
        
        cd /home/$USER_AGENT/actions-runner
        /home/$USER_AGENT/actions-runner/bin/installdependencies.sh
        echo 'setting up config'
        sudo -i -u $USER_AGENT bash << EOF
        cd /home/$USER_AGENT/actions-runner
        ./config.sh --ephemeral --url https://github.com/$ORGANIZATION --token $RUNNER_TOKEN --runnergroup $RUNNER_GROUP --name $RUNNER_NAME --labels $RUNNER_LABELS --unattended
        EOF
        ./svc.sh install $USER_AGENT
        ./svc.sh start
        """
        .replace("${RUNNER_GROUP}", runnerGroup)
        .replace("${RUNNER_TOKEN}", runnerToken)
        .replace("${RUNNER_NAME}", runnerName)
        .replace("${ORGANIZATION}", organization)
        .replace("${RUNNER_LABELS}", createRunnerLabels(actionPool, runnerGroup))
        .replace("${ACTION_RUNNER_URL}",
            "$(curl -s https://api.github.com/repos/actions/runner/releases/latest | jq -r '.assets[] | select(.name | startswith(\"actions-runner-linux-%s\")) | .browser_download_url')".formatted(
                archStr));
  }

  public String startUpScriptDebian(String runnerToken, String runnerGroup, ActionPool actionPool,
      String runnerName, String organization, ProcessorArchitecture architecture) {
    String archStr;
    if (ProcessorArchitecture.X64 == architecture) {
      archStr = "x64";
    } else if (ProcessorArchitecture.ARM64 == architecture) {
      archStr = "arm64";
    } else {
      throw new RuntimeException(
          "%s is not supported processor architecture".formatted(architecture));
    }
    createRunnerLabels(actionPool, runnerGroup);
    Map<String, String> env = new HashMap<>();
    env.put("RUNNER_GROUP", runnerGroup);
    env.put("RUNNER_TOKEN", runnerToken);
    env.put("RUNNER_NAME", runnerName);
    env.put("ORGANIZATION", organization);
    env.put("RUNNER_LABELS", createRunnerLabels(actionPool, runnerGroup));
    env.put("ACTION_RUNNER_URL",
        "$(curl -s https://api.github.com/repos/actions/runner/releases/latest | jq -r '.assets[] | select(.name | startswith(\"actions-runner-linux-%s\")) | .browser_download_url')".formatted(
            archStr));
    return """
        #!/bin/bash
        export RUNNER_GROUP=${RUNNER_GROUP}
        export RUNNER_LABELS=${RUNNER_LABELS}
        export RUNNER_TOKEN=${RUNNER_TOKEN}
        export RUNNER_NAME=${RUNNER_NAME}
        export ORGANIZATION=${ORGANIZATION}
        export USER_AGENT=action-runner
        
        # Install Docker
        apt update && sudo apt upgrade -y
        apt install ca-certificates curl gnupg lsb-release jq -y
        install -m 0755 -d /etc/apt/keyrings
        curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
        echo \\
          "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \\
          $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \\
          tee /etc/apt/sources.list.d/docker.list > /dev/null
        apt update
        apt install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y
        
        useradd -ms /bin/bash $USER_AGENT
        sudo adduser $USER_AGENT sudo
        echo "$USER_AGENT ALL=(ALL) NOPASSWD: ALL" >>  /etc/sudoers
        
        #Add $USER_AGENT user to docker group
        usermod -aG docker $USER_AGENT && newgrp docker
        service docker start
        
        sudo -i -u $USER_AGENT bash << EOF
        mkdir /home/$USER_AGENT/actions-runner
        cd /home/$USER_AGENT/actions-runner
        curl -o actions-runner.tar.gz -L ${ACTION_RUNNER_URL}
        tar xzf ./actions-runner.tar.gz
        EOF
        
        cd /home/$USER_AGENT/actions-runner
        /home/$USER_AGENT/actions-runner/bin/installdependencies.sh
        echo 'setting up config'
        sudo -i -u $USER_AGENT bash << EOF
        cd /home/$USER_AGENT/actions-runner
        ./config.sh --ephemeral --url https://github.com/$ORGANIZATION --token $RUNNER_TOKEN --runnergroup $RUNNER_GROUP --name $RUNNER_NAME --labels $RUNNER_LABELS --unattended
        EOF
        ./svc.sh install $USER_AGENT
        ./svc.sh start
        """
        .replace("${RUNNER_GROUP}", runnerGroup)
        .replace("${RUNNER_TOKEN}", runnerToken)
        .replace("${RUNNER_NAME}", runnerName)
        .replace("${ORGANIZATION}", organization)
        .replace("${RUNNER_LABELS}", createRunnerLabels(actionPool, runnerGroup))
        .replace("${ACTION_RUNNER_URL}",
            "$(curl -s https://api.github.com/repos/actions/runner/releases/latest | jq -r '.assets[] | select(.name | startswith(\"actions-runner-linux-%s\")) | .browser_download_url')".formatted(
                archStr));
  }

  public  Map<String, Object> actionPoolToApiResponse(){
    return Map.of();
  }
}
