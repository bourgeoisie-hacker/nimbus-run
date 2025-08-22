{{/*
Expand the name of the chart.
*/}}
{{- define "nimbus-run.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "autoscaler.fullname" -}}
    {{- printf "%s-%s" .Release.Name "autoscaler" | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- define "webhook.fullname" -}}
    {{- printf "%s-%s" .Release.Name "webhook" | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- define "actionTracker.fullname" -}}
    {{- printf "%s-%s" .Release.Name "action-tracker" | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- define "kafka.fullname" -}}
    {{- printf "%s-%s" .Release.Name "kafka" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "nimbus-run.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "autoscaler.labels" }}
{{- with .Values.autoscaler.deployment.labels }}
{{- toYaml . }}
{{- end }}
{{- "application: nimbus-run"}}
{{ "nimbus-run-component: autoscaler"}}
{{- end }}

{{- define "webhook.labels" -}}
{{- with .Values.webhook.deployment.labels }}
{{ toYaml . }}
{{- end }}
{{- "application: nimbus-run"}}
{{ "nimbus-run-component: webhook"}}
{{- end }}

{{- define "actionTracker.labels" -}}
{{- with .Values.actionTracker.deployment.labels }}
{{ toYaml . }}
{{- end }}
{{- "application: nimbus-run"}}
{{ "nimbus-run-component: actionTracker"}}
{{- end }}

{{- define "kafka.labels" -}}
{{- with .Values.kafka.deployment.labels }}
{{ toYaml . }}
{{- end }}
{{- "application: nimbus-run"}}
{{ "nimbus-run-component: kafka"}}
{{- end }}

{{- define "autoscale-pullpolicy"}}
    {{- if .Values.autoscaler.deployment.pullPolicy}}
    {{- .Values.autoscaler.deployment.pullPolicy}}
    {{- else }}
    {{- "Always"}}
    {{- end }}
{{- end }}
{{- define "webhook-pullpolicy"}}
    {{- if .Values.autoscaler.deployment.pullPolicy}}
    {{- .Values.autoscaler.deployment.pullPolicy}}
    {{- else }}
    {{- "Always"}}
    {{- end }}
{{- end }}
{{- define "actionTracker-pullpolicy"}}
    {{- if .Values.autoscaler.deployment.pullPolicy}}
    {{- .Values.autoscaler.deployment.pullPolicy}}
    {{- else }}
    {{- "Always"}}
    {{- end }}
{{- end }}

{{- define "kafka-pullpolicy"}}
    {{- if .Values.kafka.deployment.pullPolicy}}
    {{- .Values.kafka.deployment.pullPolicy}}
    {{- else }}
    {{- "Always"}}
    {{- end }}
{{- end }}

{{- define "autoscaler-service-loadbalancer-type"}}
    {{- if .Values.autoscaler.service.type}}
    {{- .Values.autoscaler.service.type}}
    {{- else }}
    {{- "ClusterIP"}}
    {{- end }}
{{- end }}
{{- define "autoscaler-service-loadbalancer-port"}}
    {{- if .Values.autoscaler.service.port}}
    {{- .Values.autoscaler.service.port}}
    {{- else }}
    {{- "8080"}}
    {{- end }}
{{- end }}
{{- define "webhook-service-loadbalancer-type"}}
    {{- if .Values.webhook.service.type}}
    {{- .Values.webhook.service.type}}
    {{- else }}
    {{- "ClusterIP"}}
    {{- end }}
{{- end }}
{{- define "webhook-service-loadbalancer-port"}}
    {{- if .Values.webhook.service.port}}
    {{- .Values.webhook.service.port}}
    {{- else }}
    {{- "8080"}}
    {{- end }}
{{- end }}
{{- define "actionTracker-service-loadbalancer-type"}}
    {{- if .Values.actionTracker.service.type}}
    {{- .Values.actionTracker.service.type}}
    {{- else }}
    {{- "ClusterIP"}}
    {{- end }}
{{- end }}
{{- define "actionTracker-service-loadbalancer-port"}}
    {{- if .Values.actionTracker.service.port}}
    {{- .Values.actionTracker.service.port}}
    {{- else }}
    {{- "8080"}}
    {{- end }}
{{- end }}

{{- define "kafka-service-loadbalancer-type"}}
    {{- if .Values.kafka.service.type}}
    {{- .Values.kafka.service.type}}
    {{- else }}
    {{- "ClusterIP"}}
    {{- end }}
{{- end }}

{{- define "kafka-broker-url"}}
{{- if .Values.kafka.brokerOverride}}
{{- .Values.kafka.brokerOverride}}
{{- else }}
{{- include "kafka.fullname" . }}.{{.Release.Namespace}}.svc.cluster.local:{{.Values.kafka.deployment.ports.client.port}}
{{- end }}
{{- end }}



{{define "autoscaler.computeSettings"}}
    {{- if eq .Values.compute.computeType "aws" }}
          {{- with .Values.compute.aws}}
          {{- toYaml . }}
          {{- end }}
      {{- else if eq .Values.compute.computeType "gcp" }}
          {{- with .Values.compute.gcp}}
          {{- toYaml . }}
          {{- end }}
      {{ else }}
      {{fail "incorrect computeType. Valid values: aws, gcp"}}
    {{- end }}
{{- end }}