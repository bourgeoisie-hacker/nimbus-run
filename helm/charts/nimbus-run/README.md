# NimbusRun Helm Chart  

Deploy **NimbusRun Autoscaler** on Kubernetes using Helm. This chart provisions everything needed to run the autoscaler and connect it to your GitHub organization and cloud compute backend.  

---

## ⚙️ Values Overview  

### Compute Configuration  

Controls how NimbusRun provisions compute for your GitHub Actions runners. Supports **AWS** and **GCP**.  

| Name | Description | Example | Default |
|------|-------------|---------|---------|
| `compute.computeType` | Which compute backend to use. Options: `aws`, `gcp`, `none` | `aws` | `none` |

---

#### AWS Settings (`compute.aws`)  

| Name | Description | Example | Default |
|------|-------------|---------|---------|
| `compute.aws.defaultSettings.idleScaleDownInMinutes` | Minutes of inactivity before scaling down (accounts for boot + runner startup). | `10` |  |
| `compute.aws.defaultSettings.region` | AWS region for provisioning. | `us-east-1` |  |
| `compute.aws.defaultSettings.subnet` | Subnet ID for networking. | `subnet-1234` |  |
| `compute.aws.defaultSettings.securityGroup` | Security group ID for firewall rules. | `sg-1234` |  |
| `compute.aws.defaultSettings.credentialsProfile` | AWS credentials profile name (or uses [default chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html)). | `myProfile` |  |
| `compute.aws.defaultSettings.diskSettings.type` | EBS volume type. Supported: `gp3`, `gp2`, `io2`, `io1`, `st1`. | `gp3` |  |
| `compute.aws.defaultSettings.diskSettings.size` | Disk size in GiB. | `20` |  |
| `compute.aws.defaultSettings.instanceType` | EC2 instance type for runners. | `t3.medium` |  |
| `compute.aws.defaultSettings.maxInstanceCount` | Max instances allowed (0 = unlimited). | `10` |  |
| `compute.aws.defaultSettings.keyPairName` | EC2 key pair name for SSH. | `myKeyPair` |  |
| `compute.aws.defaultActionPool.name` | Default action pool name (inherits all defaults). | `default-pool` |  |
| `compute.aws.actionPools` | List of action pool objects. Each pool can override defaults. | See below |  |

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

| Name | Description | Example | Default |
|------|-------------|---------|---------|
| `compute.gcp.defaultSettings.idleScaleDownInMinutes` | Minutes of inactivity before scaling down. | `10` |  |
| `compute.gcp.defaultSettings.projectId` | GCP project ID. | `massive-dynamo-342018` |  |
| `compute.gcp.defaultSettings.region` | GCP region for provisioning. | `us-east1` |  |
| `compute.gcp.defaultSettings.subnet` | Subnet path. | `regions/us-east1/subnetworks/default` |  |
| `compute.gcp.defaultSettings.vpc` | VPC path. | `global/networks/default` |  |
| `compute.gcp.defaultSettings.zones` | List of zones for placement. | `[us-east1-b, us-east1-c, us-east1-d]` | `[]` |
| `compute.gcp.defaultSettings.serviceAccountPath` | Path to service account JSON (or uses default provider chain). | `/path/to/service-account.json` |  |
| `compute.gcp.defaultSettings.diskSettings.size` | Disk size in GiB. | `20` |  |
| `compute.gcp.defaultSettings.instanceType` | GCE machine type. | `e2-highcpu-4` |  |
| `compute.gcp.defaultSettings.maxInstanceCount` | Max instances allowed (0 = unlimited). | `10` |  |
| `compute.gcp.defaultActionPool.name` | Default action pool name. | `default-pool` |  |
| `compute.gcp.actionPools` | List of action pool objects. Each pool can override defaults. | See below |  |

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

| Name | Description | Example | Default |
|------|-------------|---------|---------|
| `autoscaler.settings.name` | Application name (used in metrics). | `aws-1-autoscaler` |  |
| `autoscaler.settings.logLevel` | Logging level: `info`, `warn`, `debug`, `verbose`. | `info` |  |
| `autoscaler.settings.github.groupName` | GitHub runner group name. | `prod` |  |
| `autoscaler.settings.github.organizationName` | GitHub organization name. | `my-org` |  |
| `autoscaler.settings.github.token` | GitHub token (resolve from env var recommended). | `${GITHUB_TOKEN}` |  |
| `autoscaler.settings.github.webhookSecret` | Webhook secret for verifying requests. | `${WEBHOOK_SECRET}` |  |
| `autoscaler.settings.github.webhookId` | Webhook ID (optional). | `123456` |  |
| `autoscaler.settings.github.replayFailedDeliverOnStartup` | Replay failed webhook deliveries on startup. | `true` | `true` |
| `autoscaler.settings.retryPolicy.maxJobInQueuedInMinutes` | Max minutes a job can stay queued before retry. | `7` |  |
| `autoscaler.settings.retryPolicy.maxTimeBtwRetriesInMinutes` | Min minutes between retries for a job. | `5` |  |
| `autoscaler.settings.retryPolicy.maxRetries` | Maximum retry attempts per job. | `3` |  |

---

### Deployment Settings (`autoscaler.deployment`)  

| Name | Description | Example | Default |
|------|-------------|---------|---------|
| `autoscaler.deployment.image` | Docker image to deploy. | `bourgeoisiehacker/autoscaler:0.2.0` |  |
| `autoscaler.deployment.resources.requests` | Resource requests. | `cpu: 2`, `memory: 4Gi` |  |
| `autoscaler.deployment.resources.limits` | Resource limits. | `cpu: 2`, `memory: 4Gi` |  |
| `autoscaler.deployment.environmentVariables` | Extra environment variables. | See below | `[]` |
| `autoscaler.deployment.volumes` | Additional volumes. |  | `[]` |
| `autoscaler.deployment.volumeMounts` | Volume mounts. |  | `[]` |
| `autoscaler.deployment.ports.http.port` | HTTP port. | `8080` | `8080` |
| `autoscaler.deployment.ports.http.portName` | HTTP port name. | `http` | `http` |

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
