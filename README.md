# NimbusRun

<p align="center">
  <img src="images/nimbusrun.png" width="400" alt="NimbusRun Logo"/>
</p>

<!-- TOC -->
* [NimbusRun](#nimbusrun)
  * [🚀 What is NimbusRun?](#-what-is-nimbusrun)
  * [Helm Chart](#helm-chart)
  * [Getting Started](#getting-started)
    * [Requirements](#requirements)
    * [Step 1: Setup a Webhook](#step-1-setup-a-webhook)
    * [Step 2: Configure a GitHub Action Workflow](#step-2-configure-a-github-action-workflow)
    * [Step 3: Create an Autoscaler Configuration File](#step-3-create-an-autoscaler-configuration-file)
    * [Step 4: Define Docker Compose](#step-4-define-docker-compose)
  * [Make sure your config.yaml lives in the same directory as your compose file.](#make-sure-your-configyaml-lives-in-the-same-directory-as-your-compose-file)
    * [Step 5: Setup the Webhook](#step-5-setup-the-webhook)
    * [🌍 Required Environment Variable](#-required-environment-variable)
  * [⚙️ Configuration Properties](#-configuration-properties)
    * [Main application configurations](#main-application-configurations)
    * [AWS Compute Configurations](#aws-compute-configurations)
    * [GCP Compute Configurations](#gcp-compute-configurations)
    * [🧩 Full Config Examples](#-full-config-examples)
  * [Metrics](#metrics)
  * [Supported Operating Systems Compatibility Matrix](#supported-operating-systems-compatibility-matrix)
  * [Supported CPU Architecture](#supported-cpu-architecture)
<!-- TOC -->


## 🚀 What is NimbusRun?

NimbusRun is an **autoscaler for GitHub self-hosted runners** running on **VMs** (or technically *any compute backend if
you’re brave enough to contribute 😏*).

You may ask: *“Why another autoscaler?”*

- Most alternatives focus exclusively on Kubernetes.
- But what about VMs? What if your builds needs to build a container or needs system-level isolation?

That’s where NimbusRun shines:

- ✅ Run GitHub Actions jobs directly on VMs (no more “containers inside containers inside containers” inception
  nightmares).
- ✅ Scale to **zero** — because cloud providers don’t need your charity when instances are idle.
- ✅ Pay only for what you use while keeping the flexibility of full VMs.

Traditional VM-based runners usually mean a fixed number of servers sitting idle (a.k.a. *burning
cash*). NimbusRun fixes that with **on-demand scaling** and cloud-friendly efficiency.


## Helm Chart
[Head over to the helm charts directory to see the helm chart](helm/charts)


## Getting Started

### Requirements
Before you can unleash NimbusRun, make sure you have the following in place:

- A [GitHub organization](https://docs.github.com/en/organizations/collaborating-with-groups-in-organizations/creating-a-new-organization-from-scratch)
- A [runner group for that organization](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/manage-access)
- An AWS or GCP account (or, if you’re adventurous, roll your own compute engine by extending [Compute.java](autoscaler/compute/ComputeApi/src/main/java/com/nimbusrun/compute/Compute.java) 😏)
- A [GitHub token](https://github.com/settings/tokens) with the right permissions
- At least a passing knowledge of [GitHub Actions](https://docs.github.com/en/actions)
- The service account needs permissions to create/delete/list compute instances

---

### Step 1: Setup a Webhook
Follow the [official documentation](https://docs.github.com/en/webhooks/using-webhooks/creating-webhooks) to create a webhook.

---

### Step 2: Configure a GitHub Action Workflow

Here’s a minimal example:

```yaml
name: test
on:
  push:
    branches:
      - master # or any branch you like
jobs:
  test:
    runs-on:
      group: prod
      labels:
        - action-group=prod
        - action-pool=pool-name-1
    steps:
      - name: test
        run: echo "test"
```
Key details:

- The runs-on.group field must match your runner group.
- You must also add a action-group=<group-name> label that matches runs-on.group.
- Specify which action pool the workflow should run on with action-pool=<pool-name>. This must match a pool defined in your config file.

If you’ve defined a default-pool, you can skip the action pool label. But honestly, it’s best practice to explicitly pick a pool (so you don’t wonder later why everything went to “default”).

---

### Step 3: Create an Autoscaler Configuration File
Create a config.yaml and fill in your values:

```yaml
name: aws-1-autoscaler
computeType: aws
logLevel: "info" # info | warn | debug | verbose

github:
  groupName: ""            # name of your runner group
  organizationName: ""     # name of your organization
  token: ""                # GitHub token with required permissions

server:
  port: 8080

compute:
  defaultSettings:
    idleScaleDownInMinutes: 10 # should be at least 5 (runners need a moment to start up)
    region: ""                 # e.g. us-east-2
    subnet: ""                 # subnet ID (e.g. subnet-1234)
    securityGroups: [""]          # security group ID (e.g. sg-1234)
    diskSettings:
      type: "gp3"              # gp3 | gp2 | io2 | io1 | st1
      size: "20"               # disk size in GiB
    instanceType: ""           # e.g. t3.medium
    maxInstanceCount: 10

  defaultActionPool:
    name: default-pool

  actionPools:
    - name: pool-name-1
      instanceType: t3.medium
    - name: big-compute
      instanceType: c4.2xlarge
```
In this example, we’ve defined three action pools:
- pool-name-1
- big-compute
- default-pool

The default-pool acts as a fallback if no pool is explicitly specified in your workflow.

---

### Step 4: Define Docker Compose
```yaml
version: "3.9"

services:
  autoscaler:
    image: bourgeoisiehacker/autoscaler:latest
    environment:
      AWS_ACCESS_KEY_ID: <SETUP>         # or rely on IAM roles/instance profiles
      AWS_SECRET_ACCESS_KEY: <SETUP>
      NIMBUS_RUN_CONFIGURATION_FILE: /opt/config.yaml
      SERVER_PORT: "8080"
    ports:
      - "8080:8080"
    volumes:
      - "./config.yaml:/opt/config.yaml"
networks:
  default:
    name: nimbus_run

```
Now, run Docker Compose:
```bash
docker compose -f compose.yaml up

```
Make sure your config.yaml lives in the same directory as your compose file.
---

### Step 5: Setup the Webhook
If you’re running on a VM with a public IP, configure your GitHub webhook to point at:
```html
http://<your-ip>:8080/webhook
```
Be sure to include /webhook at the end, set the content type to application/json, and select workflow jobs and workflow runs events. TLS is highly recommended in production — my demo turned off SSL, but you should definitely add HTTPS in front of the webhook or autoscaler.

<p align="center"> <img src="images/setup-webhook.gif" width="1000" alt="NimbusRun Webhook Setup"/> </p>

Once configured, push a commit to your repo. You should see a VM instance spin up, run the job, and then terminate — the full autoscaling magic at work.

---

## WebPage
Once you have the autoscaler running you can go to http://localhost:8080 to see the UI

---

### 🌍 Required Environment Variable

NimbusRun requires the environment variable:

- `NIMBUS_RUN_CONFIGURATION_FILE` → the path to your configuration file.

Without this, NimbusRun has no idea what to do. Think of it as NimbusRun’s GPS: without directions, it just wanders aimlessly in the cloud.

--- 

## ⚙️ Configuration Properties

**Note** You can override any value with an environment variable in `${ENVIRONMENT_VAR_NAME}` format

Legend for `Required`:

| Symbol | Meaning                                                       |
|--------|---------------------------------------------------------------|
| ✅      | property required                                             |
| ❌      | property not required                                         |
| ~      | property is required if not specified in the default settings |


### Main application configurations

| Name                                     | Required | Description                                                                                                          | Example               | Default Value |
|------------------------------------------|----------|----------------------------------------------------------------------------------------------------------------------|-----------------------|---------------|
| `name`                                   | ✅        | Application name for metrics reporting.                                                                              | `aws-1-autoscaler`    |               |
| `computeType`                            | ✅        | Compute backend to use (`aws`, `gcp`, `azure`).                                                                      | `aws`                 |               |
| `logLevel`                               | ❌        | Application log level. Options: `info`, `warn`, `debug`, `fatal`. Can also be overridden via Spring Boot properties. | `debug`               | `info`        |
| `retryPolicy.maxJobInQueuedInMinutes`    | ❌        | Max minutes a GitHub Actions job can remain queued before being retried.                                             | `6`                   | `6`           |
| `retryPolicy.maxTimeBtwRetriesInMinutes` | ❌        | Minimum time between subsequent retries for the same job.                                                            | `6`                   | `6`           |
| `retryPolicy.maxRetries`                 | ❌        | Maximum retry attempts for a single job.                                                                             | `3`                   | `3`           |
| `github.groupName`                       | ✅        | GitHub runner group/environment name.                                                                                | `prod`                | -             |
| `github.organizationName`                | ✅        | GitHub organization name.                                                                                            | `bourgeoisie-whacker` | -             |
| `github.token`                           | ✅        | GitHub Token. Must allow: <br>• Create self-hosted runner tokens <br>• List org runners.  Do not use PAT.            | `${GITHUB_TOKEN}`     | -             |
| `github.webhookSecret`                   | ❌        | Webhook secret to verify GitHub requests. Only required in standalone webhook mode.                                  | `test`                | -             |
| `github.webhookId`                       | ❌        | Webhook ID used to re-deliver failed events. Must be string (otherwise risk of scientific notation).                 | `"565459826"`         | -             |
| `github.replayFailedDeliverOnStartup`    | ❌        | If true, resends all failed webhook deliveries from **today** on startup (requires `webhookId`).                     | `true`                | false         |
| `server.port`                            | ❌        | Port for both metrics and webhook server.                                                                            | `8080`                | `8080`        |

### AWS Compute Configurations

*Default Settings*

| Name                                             | Required | Description                                                                                                                                                                  | Example                  | Default Value  |
|--------------------------------------------------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|----------------|
| `compute.defaultSettings.idleScaleDownInMinutes` | ❌        | Minutes of idle time before scaling down. Should be > 5 to allow runner startup.                                                                                             | `3`                      | `10`           |
| `compute.defaultSettings.region`                 | ~        | AWS region for provisioning.                                                                                                                                                 | `us-east-1`              | -              |
| `compute.defaultSettings.subnet`                 | ~        | AWS subnet ID.                                                                                                                                                               | `subnet-257dbf7d`        | -              |
| `compute.defaultSettings.securityGroups`         | ~        | List of AWS security group ID.                                                                                                                                               | `[sg-0189c3298c7be64ca]` | -              |
| `compute.defaultSettings.credentialsProfile`     | ~        | AWS credentials profile to use. Defaults to the [AWS SDK default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html). | -                        |                |
| `compute.defaultSettings.diskSettings.type`      | ❌        | Disk type. Supported: `gp3`, `gp2`, `io2`, `io1`, `st1`.                                                                                                                     | `gp3`                    | `gp3`          |
| `compute.defaultSettings.diskSettings.size`      | ❌        | Disk size in GiB.                                                                                                                                                            | `20`                     | `20`           |
| `compute.defaultSettings.diskSettings.iops`      | ❌        | Number of IOPS for the disk.                                                                                                                                                 | `20`                     | `20`           |
| `compute.defaultSettings.instanceType`           | ~        | Instance type for runners.                                                                                                                                                   | `t3.medium`              | -              |
| `compute.defaultSettings.os`                     | ❌        | The operating system to be used. See compatibility matrix to see supported version                                                                                           | `ubuntu20.04`            | `ubuntu20.04`  |
| `compute.defaultSettings.architecture`           | ❌        | The Central Processor Unit Architecture. Either x64 or ARM64. See compatibility matrix to see supported version.                                                             | `x64`                    | `x64`          |
| `compute.defaultSettings.maxInstanceCount`       | ❌        | Maximum instance count (0 = unlimited).                                                                                                                                      | `10`                     | `10`           |
| `compute.defaultSettings.keyPairName`            | ❌        | EC2 key pair name for SSH access.                                                                                                                                            | `my-keypair`             | -              |
| `compute.defaultActionPool`                      | ❌        | This is one instance of an action pool. If a github workflow doesn't specify an action-pool                                                                                  | `default-pool`           | `default-pool` |
| `compute.actionPools`                            | ❌        | List of aws action pools                                                                                                                                                     | `c4.2xlarge`             |                |

*AWS action pool configurations*

| Name                     | Required | Description                                                                                                                                                                  | Example                | Default Value          |
|--------------------------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------|------------------------|
| `name`                   | ✅        | Application name for metrics reporting.                                                                                                                                      | `3`                    | `3`                    |
| `idleScaleDownInMinutes` | ~        | Minutes of idle time before scaling down. Should be > 5 to allow runner startup.                                                                                             | `3`                    | `3`                    |
| `region`                 | ~        | AWS region for provisioning.                                                                                                                                                 | `us-east-1`            | `us-east-1`            |
| `subnet`                 | ~        | AWS subnet ID.                                                                                                                                                               | `subnet-257dbf7d`      | `subnet-257dbf7d`      |
| `securityGroups`         | ~        | List of AWS security group ID.                                                                                                                                               | `sg-0189c3298c7be64ca` | `sg-0189c3298c7be64ca` |
| `credentialsProfile`     | ❌        | AWS credentials profile to use. Defaults to the [AWS SDK default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html). | `my-profile`           |                        |
| `diskSettings.type`      | ❌        | Disk type. Supported: `gp3`, `gp2`, `io2`, `io1`, `st1`.                                                                                                                     | `gp3`                  | `gp3`                  |
| `diskSettings.size`      | ❌        | Disk size in GiB.                                                                                                                                                            | `20`                   | `20`                   |
| `instanceType`           | ~        | Instance type for runners.                                                                                                                                                   | `t3.medium`            | `t3.medium`            |
| `os`                     | ❌        | The operating system to be used. See compatibility matrix to see supported version                                                                                           | `ubuntu20.04`          | `ubuntu20.04`          |
| `architecture`           | ❌        | The Central Processor Unit Architecture. Either x64 or ARM64. See compatibility matrix to see supported version.                                                             | `x64`                  | `x64`                  |
| `maxInstanceCount`       | ~        | Maximum instance count (0 = unlimited).                                                                                                                                      | `10`                   | `10`                   |
| `keyPairName`            | ~        | EC2 key pair name for SSH access.                                                                                                                                            | `keypair`              | `keypair`              |

### GCP Compute Configurations

*Default Configuration*

| Name                                           | Required | Description                                                                                         | Example                                  | Default |
|------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------|------------------------------------------|---------|
| **compute.defaultSettings.idleScaleDownInMinutes** | ❌        | Minutes of inactivity before scaling down an instance (accounts for boot + runner warmup).          | `10`                                     | `10`    |
| **compute.defaultSettings.projectId**          | ~        | GCP project identifier for provisioning resources.                                                  | `massive-fasdf-342018`                   | -       |
| **compute.defaultSettings.region**             | ~        | GCP region for provisioning instances.                                                              | `us-east1`                               | -       |
| **compute.defaultSettings.subnet**             | ~        | Full path to GCP subnet for networking.                                                             | `regions/us-east1/subnetworks/default`   | -       |
| **compute.defaultSettings.vpc**                | ~        | Full path to GCP VPC for networking.                                                                | `global/networks/default`                | -       |
| **compute.defaultSettings.zones**              | ~        | List of zones for instance placement (load balanced across zones).                                  | `us-east1-b`, `us-east1-c`, `us-east1-d` | -       |
| **compute.defaultSettings.serviceAccountPath** | ~        | Path to service account JSON for authenticating to GCP APIs. If unset, uses default provider chain. | `/path/to/service-account-file.json`     | -       |
| **compute.defaultSettings.diskSettings.size**  | ❌        | Disk size in GiB.                                                                                   | `20`                                     | `20`    |
| **compute.defaultSettings.instanceType**       | ~        | GCE machine type for GitHub runners.                                                                | `e2-highcpu-4`                           | -       |
| **compute.defaultSettings.architecture**         | ❌        | The Central Processor Unit Architecture. Either x64 or ARM64. See compatibility matrix to see supported version.                                                             | `x64`                  | `x64`          |
| **compute.defaultSettings.maxInstanceCount**     | ❌        | Maximum instance count (0 = unlimited).                                                                                                                                      | `10`                   | `10`           |
| **compute.defaultSettings.maxInstanceCount**   | ❌        | Maximum number of instances (0 = unlimited).                                                        | `10`                                     | `10`    |
| **compute.defaultActionPool**                  | ❌        | Name of the default action pool (inherits all fields from `defaultSettings`).                       | `default-pool`                           | -       |
| **compute.actionPools**                        | ❌        | List of action pools(inherits all fields from `defaultSettings`).                                   | -                                        | -       |

*GCP Action Pool Configuration*

Action pool inherits from the default settings(except `name`). You can override the default Settings by specifying the
property in the action pool.

| Name                       | Required | Description                                                                                                      | Example                                  | Default       |
|----------------------------|----------|------------------------------------------------------------------------------------------------------------------|------------------------------------------|---------------|
| **name**                   | ✅        | Name of a specific action pool.                                                                                  | `n2d-standard-2 `                        | -             |
| **idleScaleDownInMinutes** | ❌        | Minutes of inactivity before scaling down an instance (accounts for boot + runner warmup).                       | `10`                                     | `10`          |
| **projectId**              | ~        | GCP project identifier for provisioning resources.                                                               | `massive-dynamo-342018`                  | -             |
| **region**                 | ~        | GCP region for provisioning instances.                                                                           | `us-east1`                               | -             |
| **subnet**                 | ~        | Full path to GCP subnet for networking.                                                                          | `regions/us-east1/subnetworks/default`   | -             |
| **vpc**                    | ~        | Full path to GCP VPC for networking.                                                                             | `global/networks/default`                | -             |
| **publicIp**               | ~        | determines whether or not the instances are assigned emphermal public ip addresses                               | `false`                                  | true          |
| **zones**                  | ~        | List of zones for instance placement (load balanced across zones).                                               | `us-east1-b`, `us-east1-c`, `us-east1-d` | -             |
| **serviceAccountPath**     | ❌        | Path to service account JSON for authenticating to GCP APIs. If unset, uses default provider chain.              | `/path/to/service-account-file.json`     | -             |
| **diskSettings.size**      | ❌        | Disk size in GiB.                                                                                                | `20`                                     | `20`          |
| **instanceType**           | ~        | GCE machine type for GitHub runners.                                                                             | `e2-highcpu-4`                           | -             |
| **maxInstanceCount**       | ❌        | Maximum number of instances (0 = unlimited).                                                                     | `10`                                     | `10`          |
| **os**                     | ❌        | The operating system to be used. See compatibility matrix to see supported version                               | `ubuntu20.04`                            | `ubuntu20.04` |
| **architecture**           | ❌        | The Central Processor Unit Architecture. Either x64 or ARM64. See compatibility matrix to see supported version. | `x64`                                    | `x64`         |

---


### 🧩 Full Config Examples

See the full YAML examples in the sections above for both **AWS** and **GCP** autoscalers.

- [AWS](config_examples/config-aws.yaml) 
- [GCP](config_examples/config-gcp.yaml)

---

## Metrics

Metrics exposed at `/metrics` endpoint. Because this is a springboot app additional metrics are exposed via micrometer. Use this with Prometheus or Opentelemetry.

| Metric Name                        | Type    | Tags                                                                 | Description |
|------------------------------------|---------|----------------------------------------------------------------------|-------------|
| `instance_operations_total`        | Counter | `pool_name`, `type` (`create` / `delete`), `result` (`success` / `failure`) | Increments whenever a compute instance is created or deleted, labeled by operation type and result. |
| `instance_count`                   | Gauge   | `pool_name`                                                          | Tracks the current number of running instances in an action pool. |
| `instance_create_retries_total`    | Counter | `pool_name`, `type` (`full` / `failed`)                              | Increments when a job retries because the action pool is full or instance creation failed (e.g., misconfiguration or permissions). |
| `invalid_action_pool_total`        | Counter | `pool_name`, `repository_name`, `workflow_name`                      | Increments when a workflow references an invalid action pool within a valid action group. |
| `invalid_workflow_job_label_total` | Counter | `repository_name`, `workflow_name`, `workflow_job_name`              | Increments when a workflow job has invalid labels, preventing Nimbus-run self-hosted runners from executing it. |
| `repository_upscale_total`         | Counter | `pool_name`, `repository_name`                                       | Tracks the number of times a repository has requested an action pool to process a workflow job. |
| `action_pool_process_time_total`   | Counter | `pool_name`                                                          | Accumulates the total processing time (in arbitrary units passed to the counter) for an action pool, useful for cost and utilization reporting. |

### PromQL Examples

> Adjust job/namespace labels as needed for your Prometheus setup. Replace `5m`/`1h` with your preferred windows.

 Instance operations (create/delete)

**Overall success rate (all pools, all ops):**

```promql
sum(rate(instance_operations_total{result="success"}[5m]))
/
sum(rate(instance_operations_total[5m]))
```

**Success rate by pool and operation type:**

```promql
sum by (pool_name, type) (rate(instance_operations_total{result="success"}[5m]))
/
sum by (pool_name, type) (rate(instance_operations_total[5m]))

```
**Creates vs deletes per pool (ops/sec):**

```promql
sum by (pool_name, type) (rate(instance_operations_total[5m]))
```

**Failures per pool (alerts feed):**

```promql
sum by (pool_name, type) (rate(instance_operations_total{result="failure"}[5m]))
```

**High retry alarm idea**

Indicates you may want to increment your max instance count

```promql
sum by (pool_name) (increase(instance_create_retries_total[15m])) > 50

```

**Invalid action pool references (top offenders in 1h):**

```promql
topk(10, sum by (repository_name, workflow_name, pool_name) (
  increase(invalid_action_pool_total[1h])
))
```

**Invalid workflow job labels (last 1h):**

```promql
sum by (repository_name, workflow_name, workflow_job_name) (
  increase(invalid_workflow_job_label_total[1h])
)
```

**Upscale requests by repo and pool (rate):**

```promql
sum by (repository_name, pool_name) (rate(repository_upscale_total[5m]))
```

**Top repos requesting capacity (last 24h):**

```promql
topk(10, sum by (repository_name) (increase(repository_upscale_total[24h])))
```

**Cost hotspot hint (pools with most processed time in 24h):**

```promql
topk(5, sum by (pool_name) (increase(action_pool_process_time_total[24h])))
```

## Supported Operating Systems Compatibility Matrix

|             | Configuration Name | AWS | GCP |
|-------------|--------------------|-----|-----|
| Ubuntu20.04 | ubuntu20.04        | ✅   | ✅   |
| Ubuntu22.04 | ubuntu22.04        | ✅   | ✅   |
| Ubuntu23.04 | ubuntu23.04        | ✅   | ❌   |
| Ubuntu24.04 | ubuntu24.04        | ✅   | ✅   |
| Ubuntu25.04 | ubuntu25.04        | ✅   | ✅   |
| Debian11    | debian11           | ✅   | ✅   |
| Debian12    | debian12           | ✅   | ✅   |
| Debian13    | debian13           | ❌   | ❌   |

## Supported CPU Architecture

|       | AWS | GCP |
|-------|-----|-----|
| X64   | ✅   | ✅   |
| ARM64 | ✅   | ✅   |

Please note that if you choose to use arm64 ensure that the instance type is compatible. Please see official documentation below

*AWS* 
- https://docs.aws.amazon.com/ec2/latest/instancetypes/ec2-instance-type-specifications.html

*GCP*
- https://cloud.google.com/compute/docs/instances/arm-on-compute

⚡ **NimbusRun**: because idle VMs should not be your cloud provider’s side hustle.  
