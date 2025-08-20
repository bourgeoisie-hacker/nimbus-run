# NimbusRun
<p align="center">
  <img src="images/nimbusrun.png" width="400" alt="NimbusRun Logo"/>
</p>
<!-- TOC start  -->

- [NimbusRun](#nimbusrun)
    * [🚀 What is NimbusRun?](#-what-is-nimbusrun)
    * [Components ](#components)
        + [Autoscaler](#autoscaler)
        + [ActionTracker](#actiontracker)
        + [Webhook](#webhook)
    * [Required Environment Variable](#required-environment-variable)
    * [⚙️ Configuration Properties](#-configuration-properties)
        + [AutoScaler](#autoscaler-1)
            - [Kafka Settings](#kafka-settings)
            - [GitHub Settings](#github-settings)
        + [🖥️ AWS Compute Settings](#-aws-compute-settings)
            - [Default Settings](#default-settings)
            - [Default Action Pool](#default-action-pool)
            - [AWS Action Pools (`compute.actionPools`)](#aws-action-pools-computeactionpools)
        + [☁️ GCP Compute Settings](#-gcp-compute-settings)
            - [Default Settings](#default-settings-1)
            - [GCP Action Pools](#gcp-action-pools)
        + [🎯 Action Tracker](#-action-tracker)
        + [🔔 Webhook Service](#-webhook-service)
            - [Kafka](#kafka)
            - [GitHub](#github)
            - [Server](#server)
        + [🧩 Examples](#-examples)

<!-- TOC end -->

## 🚀 What is NimbusRun?
NimbusRun is an **autoscaler for GitHub self-hosted runners** running on **VMs** (or technically *any compute backend if you’re brave enough to contribute 😏*).

You may ask: *“Why another autoscaler?”*
- Most alternatives focus exclusively on Kubernetes.
- But what about VMs? What if your builds need Docker-in-Docker or system-level isolation?

That’s where NimbusRun shines:
- ✅ Run GitHub Actions jobs directly on VMs (no more “containers inside containers inside containers” inception nightmares).
- ✅ Scale to **zero** — because cloud providers don’t need your charity when instances are idle.
- ✅ Pay only for what you use while keeping the flexibility of full VMs.

Traditional VM-based runners usually mean a fixed number of servers sitting idle (a.k.a. *burning cash*). NimbusRun fixes that with **on-demand scaling** and cloud-friendly efficiency.

---
## Components 
### Autoscaler
- This is responsible for scaling up instances when a workflow_job webhook event is triggered. 
- It handles the lifecycle of bringing instances up and down and
- It handles the removes github actions runners that are offline(orphaned)
- Reads from 2 kafka queues. Webhook and retry queues. The webhook queue are coming github and the retry queue for jobs that are being retried due to them being in a "queued" status for some period of time.
- It can be ran in standalone mode without the use of a kafka queue. It'll  have an endpoint /webhook that can accept webhooks from github

### ActionTracker
- This reads from the webhook queue. 
- If a job has been in a "queued" status for some period of time the actionTracker will write the job to the retry topic so that the autoscaler can retry processing that job again.
- You should be careful with having to short small of numbers for `maxJobInQueuedInMinutes` or `maxTimeBtwRetriesInMinutes` because this could instances being scaled with not enough jobs to process and thus wasting compute and cash.

### Webhook
- Similiar to Autoscaler in standalone mode except it accepts traffic and pushes the workflow_job payload to the webhook kafka topic.
- It does some validation to make sure its the correct payload type

## Required Environment Variable
Each one of the components(Autoscaler, Webhook, ActionTracker) requires the `NIMBUS_RUN_CONFIGURATION_FILE` environment variable to know where to look for its configuration file. 



## ⚙️ Configuration Properties
**Note** You can override any value with an environment variable
### AutoScaler

| Key | Required | Description | Default |
|-----|----------|-------------|---------|
| **name** | ✅ | Application name for metrics reporting. | `none` |
| **computeType** | ✅ | Backend provider (`aws`, `gcp`). | `none` |
| **logLevel** | ❌ | Logging level (`info`, `warn`, `debug`, `fatal`). Can be overridden with Spring Boot props. | `info` |
| **standalone** | ❌ | Runs in standalone mode: disables Kafka listener, enables `/webhook` endpoint. | `false` |

---

#### Kafka Settings

| Key | Required | Description | Example / Default |
|-----|----------|-------------|-------------------|
| **kafka.retryTopic** | ✅ | Topic for retrying failed/delayed jobs. | `none` |
| **kafka.webhookTopic** | ✅ | Topic for incoming GitHub webhook events. | `none` |
| **kafka.broker** | ✅ | Kafka broker connection string. | `none` |
| **kafka.consumerGroupId** | ✅ | Unique consumer group ID. Changing it may reprocess jobs (and possibly trigger surprise scaling events 🙃). | `none` |

---

#### GitHub Settings

| Key | Required | Description | Default |
|-----|----------|-------------|---------|
| **github.groupName** | ✅ | GitHub runner group/environment label. | `none` |
| **github.organizationName** | ✅ | GitHub organization name. | `none` |
| **github.token** | ✅ | Personal Access Token with runner management perms. | `none` |
| **github.webhookSecret** | ❌ | Optional webhook secret (mainly for standalone mode). | `none` |

---

### 🖥️ AWS Compute Settings

#### Default Settings

| Key | Required | Description | Example | Default |
|-----|----------|-------------|---------|---------|
| **compute.defaultSettings.idleScaleDownInMinutes** | ❌ | Idle minutes before scaling down (includes VM boot + runner warmup). | `10` | `10` |
| **compute.defaultSettings.region** | ✅ | AWS region for provisioning. | `us-east-1` | `none` |
| **compute.defaultSettings.subnet** | ✅ | Subnet ID for networking. | `subnet-257dbf7d` | `none` |
| **compute.defaultSettings.securityGroup** | ✅ | Security Group for firewall rules. | `sg-0189c3298c7be64ca` | `none` |
| **compute.defaultSettings.credentialsProfile** | ❌ | AWS credentials profile. Falls back to [default chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html). | `MyProfile` | `none` |
| **compute.defaultSettings.diskSettings.type** | ❌ | EBS volume type (`gp3`, `gp2`, `io2`, `io1`, `st1`). | `gp3` | `gp3` |
| **compute.defaultSettings.diskSettings.size** | ❌ | Disk size (GiB). | `20` | `20` |
| **compute.defaultSettings.instanceType** | ✅ | EC2 instance type for runners. | `t3.medium` | `none` |
| **compute.defaultSettings.maxInstanceCount** | ❌ | Max number of instances (0 = unlimited). | `10` | `10` |
| **compute.defaultSettings.keyPairName** | ❌ | EC2 key pair for SSH. | `Testers` | `none` |

---

#### Default Action Pool

| Key | Required | Description | Example |
|-----|----------|-------------|---------|
| **compute.defaultActionPool.name** | ✅ | Default pool name. Inherits all fields from `defaultSettings`. | `default-pool` |

---

#### AWS Action Pools (`compute.actionPools`)

| Key | Required | Description | Example | Default |
|-----|----------|-------------|---------|---------|
| **name** | ✅ | Pool name. | `meh` | `none` |
| **idleScaleDownInMinutes** | ❌ | Override idle shutdown minutes. | `10` | `10` |
| **region** | ❌ | Override AWS region. | `us-east-1` | `none` |
| **subnet** | ❌ | Override Subnet. | `subnet-1234` | `none` |
| **securityGroup** | ❌ | Override Security Group. | `sg-0189c3298c7be64ca` | `none` |
| **credentialsProfile** | ❌ | Override credentials profile. | `MyProfile` | `none` |
| **diskSettings.type** | ❌ | Override EBS type. | `gp2` | `gp3` |
| **diskSettings.size** | ❌ | Override disk size. | `4` | `20` |
| **instanceType** | ❌ | Override instance type. | `t3a.xlarge` | `none` |
| **maxInstanceCount** | ❌ | Override instance cap. | `3` | `10` |
| **keyPairName** | ❌ | Override key pair. | `Testers` | `none` |

---

### ☁️ GCP Compute Settings

#### Default Settings

| Key | Required | Description | Example | Default |
|-----|----------|-------------|---------|---------|
| **compute.defaultSettings.idleScaleDownInMinutes** | ❌ | Idle minutes before scale down. | `10` | `10` |
| **compute.defaultSettings.projectId** | ✅ | GCP project ID. | `massive-dynamo-342018` | `none` |
| **compute.defaultSettings.region** | ✅ | GCP region. | `us-east1` | `none` |
| **compute.defaultSettings.subnet** | ✅ | Subnet path. | `regions/us-east1/subnetworks/default` | `none` |
| **compute.defaultSettings.vpc** | ✅ | VPC path. | `global/networks/default` | `none` |
| **compute.defaultSettings.zones** | ✅ | Zones for placement. | `us-east1-b`, `us-east1-c`, `us-east1-d` | `none` |
| **compute.defaultSettings.serviceAccountPath** | ❌ | Service account JSON path (or default chain). | `/path/to/service-account.json` | `none` |
| **compute.defaultSettings.diskSettings.size** | ✅ | Disk size (GiB). | `20` | `20` |
| **compute.defaultSettings.instanceType** | ✅ | GCE machine type. | `e2-highcpu-4` | `none` |
| **compute.defaultSettings.maxInstanceCount** | ❌ | Max instances. | `10` | `none` |

---

#### GCP Action Pools

| Key | Required | Description | Example |
|-----|----------|-------------|---------|
| **name** | ✅ | Pool name. | `chief` |
| **instanceType** | ❌ | Override machine type. | `n2d-standard-2` |
| **maxInstanceCount** | ❌ | Override max instances. | `1` |
| **subnet** | ❌ | Override subnet. | `subnet-1234` |
| **diskSettings.size** | ❌ | Override disk size. | `12` |
| **isNvme** | ❌ | Use NVMe local storage. | `true` |

---

### 🎯 Action Tracker

| Key | Required | Description | Default |
|-----|----------|-------------|---------|
| **name** | ✅ | Name used in metrics. | `N/A` |
| **retryPolicy.maxJobInQueuedInMinutes** | ❌ | Max minutes a job can sit in queue before retry. | `7` |
| **retryPolicy.maxTimeBtwRetriesInMinutes** | ❌ | Min minutes between retries. | `7` |
| **retryPolicy.maxRetries** | ❌ | Max retry attempts per job. | `3` |
| **kafka.retryTopic** | ✅ | Kafka retry topic. | `none` |
| **kafka.webhookTopic** | ✅ | Kafka webhook topic. | `none` |
| **kafka.broker** | ✅ | Kafka broker string. | `none` |
| **kafka.consumerGroupId** | ✅ | Unique consumer group ID. | `none` |
| **github.groupName** | ✅ | GitHub runner group name (used to validate retries). | `none` |

---

### 🔔 Webhook Service

| Key | Required | Description | Example / Default |
|-----|----------|-------------|-------------------|
| **name** | ✅ | App name for metrics. | `webhook` |

#### Kafka

| Key | Required | Description | Example / Default |
|-----|----------|-------------|-------------------|
| **kafka.webhookTopic** | ✅ | Topic for incoming GitHub webhook messages. | `webhook` |
| **kafka.broker** | ✅ | Kafka broker address. | `localhost:9092` |

#### GitHub

| Key | Required | Description | Example / Default |
|-----|----------|-------------|-------------------|
| **github.webhookSecret** | ❌ | Optional secret for verifying incoming webhooks. | `test` |

#### Server

| Key | Required | Description | Example / Default |
|-----|----------|-------------|-------------------|
| **server.port** | ✅ | Port for metrics + webhook server. | `8082` |

---

### 🧩 Examples

See the full YAML examples in the sections above for both **AWS** and **GCP** autoscalers.

---

⚡ **NimbusRun**: because idle VMs should not be your cloud provider’s side hustle.  
