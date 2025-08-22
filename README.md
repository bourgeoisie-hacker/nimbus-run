# NimbusRun
<p align="center">
  <img src="images/nimbusrun.png" width="400" alt="NimbusRun Logo"/>
</p>
<!-- TOC start  -->




## 🚀 What is NimbusRun?
NimbusRun is an **autoscaler for GitHub self-hosted runners** running on **VMs** (or technically *any compute backend if you’re brave enough to contribute 😏*).

You may ask: *“Why another autoscaler?”*
- Most alternatives focus exclusively on Kubernetes.
- But what about VMs? What if your builds needs to build a container or needs system-level isolation?

That’s where NimbusRun shines:
- ✅ Run GitHub Actions jobs directly on VMs (no more “containers inside containers inside containers” inception nightmares).
- ✅ Scale to **zero** — because cloud providers don’t need your charity when instances are idle.
- ✅ Pay only for what you use while keeping the flexibility of full VMs.

Traditional VM-based runners usually mean a fixed number of servers sitting idle (a.k.a. *burning cash*). NimbusRun fixes that with **on-demand scaling** and cloud-friendly efficiency.
---

## Getting Started

*Requirements*

- [Github organization](https://docs.github.com/en/organizations/collaborating-with-groups-in-organizations/creating-a-new-organization-from-scratch)
- [Runner group for the organization](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/manage-access)
- either an AWS or GCP cloud account( or create a new implementation for [Compute](autoscaler/compute/ComputeApi/src/main/java/com/nimbusrun/compute/Compute.java) and choose your own compute engine :D )
- [Github Token](https://github.com/settings/tokens)
- [Knowledge of what github actions is](https://docs.github.com/en/actions)

*Setup A Webhook*
Follow along with the [official documentation](https://docs.github.com/en/webhooks/using-webhooks/creating-webhooks)

* Configure A Github Action Workflow *

```yaml
name: test
on:
  push:
    branches:
      - master #<------ or whatever branch that exists
jobs:
  test:
    runs-on:
      group: prod
      labels:
        - action-group=prod
        - action-pool=t3.medium
    steps:
      - name: test
        run: echo "test"
```
You need to specify the runner group name for this field `runs-on.group`. 

You have to specify the runner group name under the labels as 
```yaml
      ...
      labels:
        - action-group=prod
```
The action-group label has to match the `runs-on.group` field

You next need to specify which action pool you want this to run on. Ensure that the action-pool name matches an action pool name inside of your configuration file.
```yaml
      ...
      labels:
        - action-pool=pool-name-1
```
**Note**: You can skip adding an action pool name if you have a defaultPool setup. Its good practice to use an actual action-pool

* Specify Autoscaler Configuration File *

Create configuration file called `config.yaml`
```yaml
name: aws-1-autoscaler
computeType: aws
logLevel: "info" # info | warn | debug | verbose. verbose option means debug mode but also dependencies are in debug mode. Of course  as a java springboot developer you can override these settings easily
standalone: true # "default false". turns off kafka queue listener

github:
  groupName: "prod"
  organizationName: "bourgeoisie-whacker"
  token: "" # This is the github token with permissions to interact with github actions 

server:
  port: 8081

compute:
  defaultSettings:
    idleScaleDownInMinutes: 10 # This number should higher than 5 atleast. It takes a second for runner service to start
    region: "" # i.e us-east-2
    subnet: "" # subnet-id i.e subnet-1234
    securityGroup: "" # security group id i.e sg-1234
    diskSettings:
      type: "gp3" #Possible values gp3 | gp2 | io2 | io1| st1
      size: "20" # in gigs
    instanceType: "" # i.e t3.medium
    maxInstanceCount: 10 #
  defaultActionPool:
    name: default-pool
  actionPools:
    - name: t3.medium
      instanceType: t3.medium  
    - name: big-compute
      instanceType: c4.2xlarge
```

We have three action pools specified. Their names are 
- t3.medium
- big-compute
- default-pool

The default-pool is special in that if you don't specify an action pool name in your GitHub workflow it will attempt to assign it to the default action pool.

*Define Docker Compose*
```yaml
version: "3.9"

services:
  autoscaler:
    image: bourgeoisiehacker/autoscaler:latest
    environment:
      AWS_ACCESS_KEY_ID: <SETUP> # You can explicitly set AWS environment variables for authentication to AWS
      AWS_SECRET_ACCESS_KEY: <SETUP> # You can explicitly set AWS environment variables for authentication to AWS
      NIMBUS_RUN_CONFIGURATION_FILE: /opt/config.yaml
      SERVER_PORT: "8080"
    ports:
      - "8080:8080"
    volumes:
      - "./config.yaml:/opt/config.yaml" 
    depends_on:
      init-topics:
        condition: service_completed_successfully

networks:
  default:
    name: nimbus_run
```
Now run docker compose. Make sure the config file you made is in the same directory as your compose file

```bash
docker compose -f compose.yaml up
```




If you running this on a vm with a public ip address setup the webhook. Example `10.34.62.102:8080/webhook`. You have to specify `/webhook` at the end. Also make sure the content type you chosen is `application/json`. I have SSL verification turned off for demo purposes but, you should take the time to setup tls infront of either webhook or autoscaler. Choose  `Send me everything` and select `workflow jobs` and `workflow runs` events.
<p align="center">
  <img src="images/setup-webhook.gif" width="1000" alt="NimbusRun Logo"/>
</p>

Now you can trigger a workflow job in your repository by pushing a change you should see an instance get made

## 🔧 Components


### Autoscaler
The Autoscaler is the muscle of NimbusRun. It spins up new instances whenever a `workflow_job` webhook event is received and manages the full lifecycle of GitHub Actions runners — from creating instances to tearing them down and cleaning up any orphaned runners left behind. It listens on two Kafka queues: the **webhook queue**, which receives new jobs from GitHub, and the **retry queue**, which holds jobs that have been stuck in a “queued” state for too long. For simpler setups, the Autoscaler can also run in standalone mode without Kafka. In this mode, it exposes a `/webhook` endpoint that accepts webhooks directly from GitHub.

### ActionTracker
The ActionTracker plays the role of supervisor for jobs in the webhook queue. When it notices a job has been “queued” longer than allowed, it republishes the job to the retry topic so the Autoscaler can attempt to process it again. Care should be taken when setting values for `maxJobInQueuedInMinutes` and `maxTimeBtwRetriesInMinutes`. If these numbers are too low, the Autoscaler may scale up runners more quickly than jobs actually arrive, which leads to wasted compute and — even worse — wasted cash. In other words, you’ll be making your cloud provider very happy.

### Webhook
The Webhook component acts like a lightweight version of the Autoscaler running in standalone mode. Instead of scaling, its main responsibility is to accept incoming traffic and push valid `workflow_job` payloads into the webhook Kafka topic. Before doing so, it performs basic validation to ensure only the correct payload types get through.

### TL;DR
NimbusRun is made up of three core parts: the **Autoscaler**, the **ActionTracker**, and the **Webhook**.

- The **Autoscaler** is in charge of spinning runners up and down.
- The **ActionTracker** makes sure jobs don’t get stuck in queue limbo.
- The **Webhook** is the lightweight front door, passing valid GitHub job events into the system.

Together, they keep your GitHub Actions humming, your runners lean, and your cloud bill a little less terrifying.
## 🌍 Required Environment Variable
Each component — Autoscaler, ActionTracker, and Webhook — requires the `NIMBUS_RUN_CONFIGURATION_FILE` environment variable. This variable tells the component where to find its configuration file. Without it, NimbusRun has no idea how to behave and is essentially just a dreamer floating in the cloud.



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
