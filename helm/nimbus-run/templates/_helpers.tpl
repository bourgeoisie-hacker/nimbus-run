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
{{ toYaml . }}
{{- end }}
{{ "application: nimbus-run"}}
{{ "nimbus-run-component: autoscaler"}}
{{- end }}

{{- define "webhook.labels" -}}
    {{- with .Values.webhook.deployment.labels }}
    {{- toYaml . }}
    {{- end }}
    {{- "application: nimbus-run"}}
    {{- "nimbus-run-component: webhook"}}
{{- end }}

{{- define "actionTracker.labels" -}}
    {{- with .Values.actionTracker.deployment.labels }}
    {{- toYaml . }}
    {{- end }}
    {{- "application: nimbus-run"}}
    {{- "nimbus-run-component: actionTracker"}}
{{- end }}

{{- define "autoscale-pullpolicy"}}
    {{- if .Values.autoscaler.deployment.pullPolicy}}
    {{- .Values.autoscaler.deployment.pullPolicy}}
    {{- else }}
    {{- "always"}}
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