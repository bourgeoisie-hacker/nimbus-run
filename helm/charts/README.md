# NimbusRun Helm Chart  

Deploy **NimbusRun Autoscaler** on Kubernetes using Helm. This chart provisions everything needed to run the autoscaler and connect it to your GitHub organization and cloud compute backend.  

## How Best to Deploy

While you can deploy this chart as-is, the recommended approach is to deploy it as a sub-chart or alongside another chart that manages your networking components. This allows you to properly configure ingress, load balancers, and secrets.

**Key considerations:**

1. **Networking** – Ensure you configure an ingress and/or load balancer that only accepts requests from valid sources. This provides a secure entry point to your autoscaler service.
2. **Secrets Management** – Sensitive values such as GitHub tokens, AWS credential keys, and GCP service accounts should **never** be stored directly in configuration files. Instead, use a secrets management tool such as [External Secrets](https://external-secrets.io/latest/) or [Helm Secrets](https://github.com/jkroepke/helm-secrets) to manage and mount these values securely. Once mounted, the autoscaler pod can reference them safely.

This chart is intentionally **unopinionated**, giving you the flexibility to integrate it into your existing infrastructure and security practices.


# 🚀 Setup Helm for NimbusRun

### 1. Add the Helm Repository

```bash
helm repo add nimbus-run https://bourgeoisie-hacker.github.io/nimbus-run/
helm repo update
helm search repo nimbus-run/nimbus-run --versions
```

---

### 2. Configure Required Values

Before installing the Helm chart, you’ll need to choose your compute engine (**AWS** or **GCP**) and provide the required settings via an **overrides file**.

Create a file called `overrides.yaml` and populate it with values for your environment.

Here’s an **AWS example**:

```yaml
compute:
  computeType: "aws" # aws | gcp | none (fails if unset)
  aws:
    defaultSettings:
      idleScaleDownInMinutes: 10        # Minutes of inactivity before scale-down
      region: us-east-1                 # AWS region
      subnet: subnet-257dbf7d           # Subnet ID
      securityGroup: sg-0189c3298c7be64ca # Security Group ID
      diskSettings:
        type: gp3                       # gp3 | gp2 | io2 | io1 | st1
        size: 20                        # Disk size in GiB
      instanceType: t3.medium           # Instance type
      maxInstanceCount: 10              # Max instances (0 = unlimited)
      keyPairName: Testers              # SSH key pair name

    defaultActionPool:
      name: megan
    actionPools:
      - name: t3.medium
        instanceType: t3.medium
        maxInstanceCount: 3

autoscaler:
  settings:
    name: autoscaler
    logLevel: info
    github:
      groupName: "prod"
      organizationName: "bourgeoisie-whacker"
      token: "ghp_Ljkajioj8j8j8ajfasdf"  # Replace with your GitHub Token
      webhookSecret: "test"
      webhookId: "565459826"
      replayFailedDeliverOnStartup: true
    retryPolicy:
      maxJobInQueuedInMinutes: 6
      maxTimeBtwRetriesInMinutes: 6
      maxRetries: 3

  additionalSettings: { }

  deployment:
    environmentVariables:
      - name: AWS_ACCESS_KEY_ID
        value: ""
      - name: AWS_SECRET_ACCESS_KEY
        value: ""
      - name: JAVA_TOOL_OPTIONS
        value: "-XX:MaxRAMPercentage=90.0 -XX:+UseContainerSupport"
    resources:
      requests:
        memory: "4Gi"
        cpu: "2"
      limits:
        memory: "4Gi"
        cpu: "2"

  serviceAccount:
    create: true
    annotations: { }
    labels: { }

  service:
    annotations: { }
    port: 8080
    type: LoadBalancer

  configMap:
    nimbusRun:
      keyName: config.yaml
```

---

### 3. Install the Chart

Once your overrides file is ready, install the chart:

```bash
helm install nimbus-run nimbus-run/nimbus-run -f overrides.yaml # Add --version <latest_version> if needed
```

---

✅ That’s it! Your NimbusRun autoscaler should now be deployed and ready to scale runners on AWS (or GCP if configured).


---

## ⚙️ Values Overview  

### Compute Configuration  

Controls how NimbusRun provisions compute for your GitHub Actions runners. Supports **AWS** and **GCP**.  

| Name | Description | Example | Default |
|------|-------------|---------|---------|
| `compute.computeType` | Which compute backend to use. Options: `aws`, `gcp`, `none` | `aws` | `none` |

---

#### AWS Settings (`compute.aws`)  

| Name                                                 | Description                                                                                                                                     | Example        |
|------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| `compute.aws.defaultSettings.idleScaleDownInMinutes` | Minutes of inactivity before scaling down (accounts for boot + runner startup).                                                                 | `10`           |
| `compute.aws.defaultSettings.region`                 | AWS region for provisioning.                                                                                                                    | `us-east-1`    |
| `compute.aws.defaultSettings.subnet`                 | Subnet ID for networking.                                                                                                                       | `subnet-1234`  |
| `compute.aws.defaultSettings.securityGroup`          | Security group ID for firewall rules.                                                                                                           | `sg-1234`      |
| `compute.aws.defaultSettings.credentialsProfile`     | AWS credentials profile name (or uses [default chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html)). | `myProfile`    |
| `compute.aws.defaultSettings.diskSettings.type`      | EBS volume type. Supported: `gp3`, `gp2`, `io2`, `io1`, `st1`.                                                                                  | `gp3`          |
| `compute.aws.defaultSettings.diskSettings.size`      | Disk size in GiB.                                                                                                                               | `20`           |
| `compute.aws.defaultSettings.instanceType`           | EC2 instance type for runners.                                                                                                                  | `t3.medium`    |
| `compute.aws.defaultSettings.maxInstanceCount`       | Max instances allowed (0 = unlimited).                                                                                                          | `10`           |
| `compute.aws.defaultSettings.os`                     | The operating system to be used. See compatibility matrix to see supported version                                                              | `ubuntu22.04`  |
| `compute.aws.defaultSettings.architecture`           | The Central Processor Unit Architecture. Either x64 or ARM64. See compatibility matrix to see supported version.                                | `x64`          |
| `compute.aws.defaultSettings.keyPairName`            | EC2 key pair name for SSH.                                                                                                                      | `myKeyPair`    |
| `compute.aws.defaultActionPool`                      | Default action pool (inherits from defaults).                                                                                                   | `default-pool` |
| `compute.aws.actionPools`                            | List of action pool objects. Each pool can override defaults.                                                                                   | See below      |

Example AWS action pool:  

```yaml
actionPools:
  - name: burstable
    instanceType: t3a.xlarge
    maxInstanceCount: 9
    subnet: subnet-1234
    securityGroup: sg-1234
    diskSettings:
      type: gp2
      size: 4
```

---

#### GCP Settings (`compute.gcp`)  

| Name                                                 | Description                                                                                                      | Example                                |
|------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| `compute.gcp.defaultSettings.idleScaleDownInMinutes` | Minutes of inactivity before scaling down.                                                                       | `10`                                   |
| `compute.gcp.defaultSettings.projectId`              | GCP project ID.                                                                                                  | `massive-dynamo-342018`                |
| `compute.gcp.defaultSettings.region`                 | GCP region for provisioning.                                                                                     | `us-east1`                             |
| `compute.gcp.defaultSettings.subnet`                 | Subnet path.                                                                                                     | `regions/us-east1/subnetworks/default` |
| `compute.gcp.defaultSettings.vpc`                    | VPC path.                                                                                                        | `global/networks/default`              |
| `compute.gcp.defaultSettings.zones`                  | List of zones for placement.                                                                                     | `[us-east1-b, us-east1-c, us-east1-d]` |
| `compute.gcp.defaultSettings.serviceAccountPath`     | Path to service account JSON (or uses default provider chain).                                                   | `/path/to/service-account.json`        |
| `compute.gcp.defaultSettings.diskSettings.size`      | Disk size in GiB.                                                                                                | `20`                                   |
| `compute.gcp.defaultSettings.instanceType`           | GCE machine type.                                                                                                | `e2-highcpu-4`                         |
| `compute.aws.defaultSettings.os`                     | The operating system to be used. See compatibility matrix to see supported version                               | `ubuntu22.04`                          |
| `compute.aws.defaultSettings.architecture`           | The Central Processor Unit Architecture. Either x64 or ARM64. See compatibility matrix to see supported version. | `x64`                                  |
| `compute.gcp.defaultSettings.maxInstanceCount`       | Max instances allowed (0 = unlimited).                                                                           | `10`                                   |
| `compute.gcp.defaultActionPool`                      | Default action pool. Inherits from default settings.                                                             | `default-pool`                         |
| `compute.gcp.actionPools`                            | List of action pool objects. Each pool can override defaults.                                                    | See below                              |

Example GCP action pool:  

```yaml
actionPools:
  - name: test
    instanceType: n2d-standard-2
    maxInstanceCount: 5
    diskSettings:
      size: 15
```

---

### Autoscaler Settings (`autoscaler.settings`)  

Controls how NimbusRun integrates with GitHub and applies retry logic.  

| Name | Description | Example |
|------|-------------|---------|
| `autoscaler.settings.name` | Application name (used in metrics). | `aws-1-autoscaler` |
| `autoscaler.settings.logLevel` | Logging level: `info`, `warn`, `debug`, `verbose`. | `info` |
| `autoscaler.settings.github.groupName` | GitHub runner group name. | `prod` |
| `autoscaler.settings.github.organizationName` | GitHub organization name. | `my-org` |
| `autoscaler.settings.github.token` | GitHub token (resolve from env var recommended). | `${GITHUB_TOKEN}` |
| `autoscaler.settings.github.webhookSecret` | Webhook secret for verifying requests. | `${WEBHOOK_SECRET}` |
| `autoscaler.settings.github.webhookId` | Webhook ID (optional). | `123456` |
| `autoscaler.settings.github.replayFailedDeliverOnStartup` | Replay failed webhook deliveries on startup. | `true` |
| `autoscaler.settings.retryPolicy.maxJobInQueuedInMinutes` | Max minutes a job can stay queued before retry. | `7` |
| `autoscaler.settings.retryPolicy.maxTimeBtwRetriesInMinutes` | Min minutes between retries for a job. | `5` |
| `autoscaler.settings.retryPolicy.maxRetries` | Maximum retry attempts per job. | `3` |


---
### Autoscaler Additional Settings

| Name                | Description | Example |
|---------------------|-------------|-------|
| `autoscaler.additionalSettings` | This is a springboot app. Any additional settings set here will be injected in the application.yaml file that is generated. | {}    |
---

### Deployment Settings (`autoscaler.deployment`)  

| Name                                         | Description                  | Example                               | Default |
|----------------------------------------------|------------------------------|---------------------------------------|---------|
| `autoscaler.deployment.image`                | Docker image to deploy.      | `bourgeoisiehacker/autoscaler:latest` |         |
| `autoscaler.deployment.resources.requests`   | Resource requests.           | `cpu: 2`, `memory: 4Gi`               |         |
| `autoscaler.deployment.resources.limits`     | Resource limits.             | `cpu: 2`, `memory: 4Gi`               |         |
| `autoscaler.deployment.environmentVariables` | Extra environment variables. | See below                             | `[]`    |
| `autoscaler.deployment.volumes`              | Additional volumes.          |                                       | `[]`    |
| `autoscaler.deployment.volumeMounts`         | Volume mounts.               |                                       | `[]`    |
| `autoscaler.deployment.ports.http.port`      | HTTP port.                   | `8080`                                | `8080`  |
| `autoscaler.deployment.ports.http.portName`  | HTTP port name.              | `http`                                | `http`  |

---

### Service Account & Service  

| Name | Description | Example | Default |
|------|-------------|---------|---------|
| `autoscaler.serviceAccount.create` | Whether to create a service account. | `true` | `true` |
| `autoscaler.serviceAccount.annotations` | Annotations for the service account. | `{}` | `{}` |
| `autoscaler.service.port` | Service port. | `8080` | `8080` |
| `autoscaler.service.type` | Kubernetes service type. | `ClusterIP` | `ClusterIP` |
| `autoscaler.service.annotations` | Annotations for the service. | `{}` | `{}` |

---

### ConfigMap  

| Name | Description | Example | Default |
|------|-------------|---------|---------|
| `autoscaler.configMap.nimbusRun.keyName` | Key name of the NimbusRun config file inside the ConfigMap. | `config.yaml` | `config.yaml` |

---

## 🧩 Example Installation  

```bash
helm install nimbusrun ./charts/nimbusrun   --namespace nimbus   --create-namespace
```

Customize values with your own `values.yaml`:  

```bash
helm install nimbusrun ./charts/nimbusrun -f my-values.yaml
```

---

⚡ **NimbusRun**: Scale your GitHub runners on VMs like a pro. Because idle instances are just cloud tax.  
