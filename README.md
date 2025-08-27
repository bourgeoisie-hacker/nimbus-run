# NimbusRun

<p align="center">
  <img src="images/nimbusrun.png" width="400" alt="NimbusRun Logo"/>
</p>
<!-- TOC start  -->

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
---

## Getting Started

*Requirements*

- [Github organization](https://docs.github.com/en/organizations/collaborating-with-groups-in-organizations/creating-a-new-organization-from-scratch)
- [Runner group for the organization](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/manage-access)
- either an AWS or GCP cloud account( or create a new implementation
  for [Compute](autoscaler/compute/ComputeApi/src/main/java/com/nimbusrun/compute/Compute.java) and choose your own
  compute engine :D )
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
        - action-pool=pool-name-1
    steps:
      - name: test
        run: echo "test"
```

You need to specify the runner group name for this field `runs-on.group`.

You have to specify the runner group name under the `labels` as

```yaml
      ...
      labels:
        - action-group=prod
```

The action-group label has to match the `runs-on.group` field

You next need to specify which action pool you want this to run on. Ensure that the action-pool name matches an action
pool name inside of your configuration file.

```yaml
      ...
      labels:
        - action-pool=pool-name-1
```

**Note**: You can skip adding an action pool name if you have a defaultPool setup. Its good practice to use an actual
action-pool

* Specify Autoscaler Configuration File *

Create configuration file called `config.yaml`. Replace all of the empty configurations with the correct values.

```yaml
name: aws-1-autoscaler
computeType: aws
logLevel: "info" # info | warn | debug | verbose. verbose option means debug mode but also dependencies are in debug mode. Of course  as a java springboot developer you can override these settings easily

github:
  groupName: "" # name of your runner group
  organizationName: "" # name of your organization
  token: "" # This is the github token with permissions to interact with github actions 

server:
  port: 8080

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
    - name: pool-name-1
      instanceType: t3.medium
    - name: big-compute
      instanceType: c4.2xlarge
```

We have three action pools specified. Their names are

- pool-name-1
- big-compute
- default-pool

The default-pool is special in that if you don't specify an action pool name in your GitHub workflow it will attempt to
assign it to the default action pool.

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

If you running this on a vm with a public ip address setup the webhook. Example `10.34.62.102:8080/webhook`. You have to
specify `/webhook` at the end. Also make sure the content type you chosen is `application/json`. I have SSL verification
turned off for demo purposes but, you should take the time to setup tls infront of either webhook or autoscaler. Choose
`Send me everything` and select `workflow jobs` and `workflow runs` events.
<p align="center">
  <img src="images/setup-webhook.gif" width="1000" alt="NimbusRun Logo"/>
</p>

Now you can trigger a workflow job in your repository by pushing a change you should see an instance get made. You
should see an instance be created and shortly be terminated.

## 🌍 Required Environment Variable

Nimbus Run requires the `NIMBUS_RUN_CONFIGURATION_FILE` environment variable. This variable tells the component where to
find its configuration file. Without it, NimbusRun has no idea how to behave and is essentially just a dreamer floating
in the cloud.

## ⚙️ Configuration Properties

**Note** You can override any value with an environment variable in `${ENVIRONMENT_VAR_NAME}` format

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
| `github.token`                           | ✅        | GitHub Token. Must allow: <br>• Create self-hosted runner tokens <br>• List org runners.                             | `${GITHUB_TOKEN}`     | -             |
| `github.webhookSecret`                   | ❌        | Webhook secret to verify GitHub requests. Only required in standalone webhook mode.                                  | `test`                | -             |
| `github.webhookId`                       | ❌        | Webhook ID used to re-deliver failed events. Must be string (otherwise risk of scientific notation).                 | `"565459826"`         | -             |
| `github.replayFailedDeliverOnStartup`    | ❌        | If true, resends all failed webhook deliveries from **today** on startup (requires `webhookId`).                     | `true`                | false         |
| `server.port`                            | ❌        | Port for both metrics and webhook server.                                                                            | `8080`                | `8080`        |

### AWS Compute Configurations

*Default Settings*

| Name                                             | Required | Description                                                                                                                                                                  | Example                | Default Value  |
|--------------------------------------------------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------|----------------|
| `compute.defaultSettings.idleScaleDownInMinutes` | ❌        | Minutes of idle time before scaling down. Should be > 5 to allow runner startup.                                                                                             | `3`                    | `10`           |
| `compute.defaultSettings.region`                 | ~        | AWS region for provisioning.                                                                                                                                                 | `us-east-1`            | -              |
| `compute.defaultSettings.subnet`                 | ~        | AWS subnet ID.                                                                                                                                                               | `subnet-257dbf7d`      | -              |
| `compute.defaultSettings.securityGroup`          | ~        | AWS security group ID.                                                                                                                                                       | `sg-0189c3298c7be64ca` | -              |
| `compute.defaultSettings.credentialsProfile`     | ~        | AWS credentials profile to use. Defaults to the [AWS SDK default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html). | -                      |                |
| `compute.defaultSettings.diskSettings.type`      | ❌        | Disk type. Supported: `gp3`, `gp2`, `io2`, `io1`, `st1`.                                                                                                                     | `gp3`                  | `gp3`          |
| `compute.defaultSettings.diskSettings.size`      | ❌        | Disk size in GiB.                                                                                                                                                            | `20`                   | `20`           |
| `compute.defaultSettings.instanceType`           | ~        | Instance type for runners.                                                                                                                                                   | `t3.medium`            | -              |
| `compute.defaultSettings.maxInstanceCount`       | ❌        | Maximum instance count (0 = unlimited).                                                                                                                                      | `10`                   | `10`           |
| `compute.defaultSettings.keyPairName`            | ❌        | EC2 key pair name for SSH access.                                                                                                                                            | `my-keypair`           | -              |
| `compute.defaultActionPool`                      | ❌        | This is one instance of an action pool. If a github workflow doesn't specif                                                                                                  | `default-pool`         | `default-pool` |
| `compute.actionPools`                            | ❌        | List of aws action pools                                                                                                                                                     | `c4.2xlarge`           |                |

*AWS action pool configurations*

| Name                     | Required | Description                                                                                                                                                                  | Example                | Default Value          |
|--------------------------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------|------------------------|
| `name`                   | ✅        | Application name for metrics reporting.                                                                                                                                      | `3`                    | `3`                    |
| `idleScaleDownInMinutes` | ~        | Minutes of idle time before scaling down. Should be > 5 to allow runner startup.                                                                                             | `3`                    | `3`                    |
| `region`                 | ~        | AWS region for provisioning.                                                                                                                                                 | `us-east-1`            | `us-east-1`            |
| `subnet`                 | ~        | AWS subnet ID.                                                                                                                                                               | `subnet-257dbf7d`      | `subnet-257dbf7d`      |
| `securityGroup`          | ~        | AWS security group ID.                                                                                                                                                       | `sg-0189c3298c7be64ca` | `sg-0189c3298c7be64ca` |
| `credentialsProfile`     | ~        | AWS credentials profile to use. Defaults to the [AWS SDK default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html). | `my-profile`           |                        |
| `diskSettings.type`      | ~        | Disk type. Supported: `gp3`, `gp2`, `io2`, `io1`, `st1`.                                                                                                                     | `gp3`                  | `gp3`                  |
| `diskSettings.size`      | ~        | Disk size in GiB.                                                                                                                                                            | `20`                   | `20`                   |
| `instanceType`           | ~        | Instance type for runners.                                                                                                                                                   | `t3.medium`            | `t3.medium`            |
| `maxInstanceCount`       | ~        | Maximum instance count (0 = unlimited).                                                                                                                                      | `10`                   | `10`                   |
| `keyPairName`            | ~        | EC2 key pair name for SSH access.                                                                                                                                            | `Testers`              | `Testers`              |

### GCP Compute Configurations

*Default Configuration*

| Name                                               | Required | Description                                                                                         | Example                                  | Default |
|----------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------|------------------------------------------|---------|
| **compute.defaultSettings.idleScaleDownInMinutes** | ❌        | Minutes of inactivity before scaling down an instance (accounts for boot + runner warmup).          | `10`                                     | `10`    |
| **compute.defaultSettings.projectId**              | ~        | GCP project identifier for provisioning resources.                                                  | `massive-fasdf-342018`                   | -       |
| **compute.defaultSettings.region**                 | ~        | GCP region for provisioning instances.                                                              | `us-east1`                               | -       |
| **compute.defaultSettings.subnet**                 | ~        | Full path to GCP subnet for networking.                                                             | `regions/us-east1/subnetworks/default`   | -       |
| **compute.defaultSettings.vpc**                    | ~        | Full path to GCP VPC for networking.                                                                | `global/networks/default`                | -       |
| **compute.defaultSettings.zones**                  | ~        | List of zones for instance placement (load balanced across zones).                                  | `us-east1-b`, `us-east1-c`, `us-east1-d` | -       |
| **compute.defaultSettings.serviceAccountPath**     | ~        | Path to service account JSON for authenticating to GCP APIs. If unset, uses default provider chain. | `/path/to/service-account-file.json`     | -       |
| **compute.defaultSettings.diskSettings.size**      | ❌        | Disk size in GiB.                                                                                   | `20`                                     | `20`    |
| **compute.defaultSettings.instanceType**           | ~        | GCE machine type for GitHub runners.                                                                | `e2-highcpu-4`                           | -       |
| **compute.defaultSettings.maxInstanceCount**       | ❌        | Maximum number of instances (0 = unlimited).                                                        | `10`                                     | `10`    |
| **compute.defaultActionPool**                      | ❌        | Name of the default action pool (inherits all fields from `defaultSettings`).                       | `default-pool`                           | -       |
| **compute.actionPools**                            | ❌        | List of action pools(inherits all fields from `defaultSettings`).                                   | -                                        | -       |

*GCP Action Pool Configuration*

Action pool inherits from the default settings(except `name`). You can override the default Settings by specifying the
property in the action pool.

| Name                       | Required | Description                                                                                         | Example                                  | Default |
|----------------------------|----------|-----------------------------------------------------------------------------------------------------|------------------------------------------|---------|
| **name**                   | ✅        | Name of a specific action pool.                                                                     | `n2d-standard-2 `                        | -       |
| **idleScaleDownInMinutes** | ❌        | Minutes of inactivity before scaling down an instance (accounts for boot + runner warmup).          | `10`                                     | `10`    |
| **projectId**              | ~        | GCP project identifier for provisioning resources.                                                  | `massive-dynamo-342018`                  | -       |
| **region**                 | ~        | GCP region for provisioning instances.                                                              | `us-east1`                               | -       |
| **subnet**                 | ~        | Full path to GCP subnet for networking.                                                             | `regions/us-east1/subnetworks/default`   | -       |
| **vpc**                    | ~        | Full path to GCP VPC for networking.                                                                | `global/networks/default`                | -       |
| **zones**                  | ~        | List of zones for instance placement (load balanced across zones).                                  | `us-east1-b`, `us-east1-c`, `us-east1-d` | -       |
| **serviceAccountPath**     | ❌        | Path to service account JSON for authenticating to GCP APIs. If unset, uses default provider chain. | `/path/to/service-account-file.json`     | -       |
| **diskSettings.size**      | ❌        | Disk size in GiB.                                                                                   | `20`                                     | `20`    |
| **instanceType**           | ~        | GCE machine type for GitHub runners.                                                                | `e2-highcpu-4`                           | -       |
| **maxInstanceCount**       | ❌        | Maximum number of instances (0 = unlimited).                                                        | `10`                                     | `10`    |

---



### 🧩 Examples

See the full YAML examples in the sections above for both **AWS** and **GCP** autoscalers.

---


## Helm Chart
[Head over to the helm charts directory to see the helm chart](helm/nimbus-run)
⚡ **NimbusRun**: because idle VMs should not be your cloud provider’s side hustle.  
