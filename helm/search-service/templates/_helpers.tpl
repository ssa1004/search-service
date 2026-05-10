{{/*
표준 helm helper — name / fullname / labels / selectorLabels / serviceAccountName.
helm create 가 만들어주는 형식 그대로. 우리만의 편의 helper 가 끝쪽에 추가.
*/}}

{{- define "search-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "search-service.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "search-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "search-service.labels" -}}
helm.sh/chart: {{ include "search-service.chart" . }}
{{ include "search-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "search-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "search-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "search-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{ default (include "search-service.fullname" .) .Values.serviceAccount.name }}
{{- else -}}
{{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
컨테이너 image 태그. values.image.tag 가 비어 있으면 .Chart.AppVersion 사용.
*/}}
{{- define "search-service.image" -}}
{{- $tag := default .Chart.AppVersion .Values.image.tag -}}
{{- printf "%s:%s" .Values.image.repository $tag -}}
{{- end -}}

{{/*
Postgres password 가 들어 있는 secret 의 이름.
- values.secret.create=true 인 경우 chart 가 만든 secret 이름 사용.
- false 인 경우 values.secret.name 그대로 사용 (외부 secret 참조).
*/}}
{{- define "search-service.postgresSecretName" -}}
{{- if .Values.secret.create -}}
{{- default (printf "%s-postgres" (include "search-service.fullname" .)) .Values.secret.name -}}
{{- else -}}
{{- .Values.secret.name -}}
{{- end -}}
{{- end -}}
