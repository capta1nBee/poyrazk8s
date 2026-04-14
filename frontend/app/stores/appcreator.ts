import { defineStore } from 'pinia'
import { useClusterStore } from '~/stores/cluster'

export interface EnvVar { name: string; value: string }
export interface PortDef { name: string; containerPort: number; protocol: string }
export interface ResourceSpec { cpu: string; memory: string }

export interface ProbeConfig {
  enabled: boolean
  type: 'http' | 'tcp' | 'command'
  path: string
  port: number
  command: string
  initialDelay: number
  period: number
  failureThreshold: number
}

export interface VolumeMount { name: string; mountPath: string; readOnly: boolean; subPath: string }
export interface Volume {
  name: string
  type: 'pvc' | 'configMap' | 'secret' | 'emptyDir' | 'hostPath'
  claimName?: string
  configMapName?: string
  secretName?: string
  hostPath?: string
}

export interface HpaConfig { enabled: boolean; minReplicas: number; maxReplicas: number; targetCPU: number; targetMemory: number }
export interface ServiceConfig { type: 'ClusterIP' | 'LoadBalancer' | 'NodePort'; ports: { port: number; targetPort: number; name: string }[] }
export interface IngressConfig { host: string; path: string; pathType: 'Prefix' | 'Exact' | 'ImplementationSpecific'; ingressClass: string; tlsEnabled: boolean; tlsSecret: string; annotations: Record<string, string> }
export interface ConfigMapEntry { name: string; data: Record<string, string> }
export interface SecretEntry { name: string; type: string; data: Record<string, string> }

export interface WizardState {
  // Step 1 – Basic Info
  name: string
  description: string
  namespace: string
  workloadType: string
  // Step 2 – Container
  image: string
  imageTag: string
  imagePullPolicy: string
  replicas: number
  // Step 3 – Ports & Env
  ports: PortDef[]
  envVars: EnvVar[]
  // Step 4 – Resources
  resources: { requests: ResourceSpec; limits: ResourceSpec }
  probes: { liveness: ProbeConfig; readiness: ProbeConfig }
  // Step 5 – Kind-Specific
  deploymentStrategy: 'RollingUpdate' | 'Recreate'
  maxSurge: string
  maxUnavailable: string
  statefulSetServiceName: string
  statefulSetPodManagementPolicy: 'OrderedReady' | 'Parallel'
  cronJobSchedule: string
  cronJobConcurrencyPolicy: 'Allow' | 'Forbid' | 'Replace'
  cronJobSuccessHistory: number
  cronJobFailedHistory: number
  daemonSetNodeSelector: Record<string, string>
  // Step 6 – Add-ons
  createService: boolean
  serviceConfig: ServiceConfig
  createIngress: boolean
  ingressConfig: IngressConfig
  configMaps: ConfigMapEntry[]
  secrets: SecretEntry[]
  hpa: HpaConfig
  // Legacy fields kept for backward compat
  ingressHost: string
  ingressPath: string
  ingressClass: string
  configMap: Record<string, string>
  secret: Record<string, string>
  // Step 7 – Volumes
  volumes: Volume[]
  volumeMounts: VolumeMount[]
  // Template
  templateId: string | null
  // Step 2 – Build from Git
  buildMode: 'image' | 'git'
  buildGitConnectionId: string
  buildRepoPath: string
  buildBranch: string
  buildDockerfilePath: string
  buildRegistryConnectionId: string
}

const defaultProbe = (): ProbeConfig => ({
  enabled: false, type: 'http', path: '/', port: 80, command: '', initialDelay: 10, period: 10, failureThreshold: 3
})

const defaultWizardState = (): WizardState => ({
  name: '',
  description: '',
  namespace: 'default',
  workloadType: 'Deployment',
  image: '',
  imageTag: 'latest',
  imagePullPolicy: 'IfNotPresent',
  replicas: 1,
  ports: [],
  envVars: [],
  resources: {
    requests: { cpu: '100m', memory: '128Mi' },
    limits: { cpu: '500m', memory: '512Mi' }
  },
  probes: { liveness: defaultProbe(), readiness: { ...defaultProbe(), path: '/ready', initialDelay: 5 } },
  // Kind-Specific
  deploymentStrategy: 'RollingUpdate',
  maxSurge: '25%',
  maxUnavailable: '25%',
  statefulSetServiceName: '',
  statefulSetPodManagementPolicy: 'OrderedReady',
  cronJobSchedule: '*/5 * * * *',
  cronJobConcurrencyPolicy: 'Allow',
  cronJobSuccessHistory: 3,
  cronJobFailedHistory: 1,
  daemonSetNodeSelector: {},
  // Add-ons
  createService: true,
  serviceConfig: { type: 'ClusterIP', ports: [{ port: 80, targetPort: 80, name: 'http' }] },
  createIngress: false,
  ingressConfig: { host: '', path: '/', pathType: 'Prefix', ingressClass: 'nginx', tlsEnabled: false, tlsSecret: '', annotations: {} },
  configMaps: [],
  secrets: [],
  hpa: { enabled: false, minReplicas: 1, maxReplicas: 5, targetCPU: 80, targetMemory: 0 },
  // Legacy fields
  ingressHost: '',
  ingressPath: '/',
  ingressClass: 'nginx',
  configMap: {},
  secret: {},
  // Volumes
  volumes: [],
  volumeMounts: [],
  templateId: null,
  // Build from Git
  buildMode: 'image',
  buildGitConnectionId: '',
  buildRepoPath: '',
  buildBranch: '',
  buildDockerfilePath: 'Dockerfile',
  buildRegistryConnectionId: ''
})

export interface AppCreatorApp {
  id: string
  clusterUid: string
  name: string
  description: string
  namespace: string
  workloadType: string
  config: string
  status: string
  templateId: string | null
  createdAt: string
  updatedAt: string
}

export interface AppCreatorTemplate {
  id: string
  name: string
  description: string
  category: string
  icon: string
  config: string
  isPublic: boolean
}

export interface GitConnection {
  id: string
  provider: 'github' | 'gitlab'
  name: string
  baseUrl: string | null
  isDefault: boolean
  createdAt: string
}

export interface RegistryConnection {
  id: string
  registryType: 'dockerhub' | 'gitlab' | 'github' | 'custom'
  name: string
  serverUrl: string | null
  username: string
  imagePrefix: string | null
  isDefault: boolean
  createdAt: string
}

export interface ContainerBuildJob {
  jobId: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'NOT_FOUND'
  imageRef: string | null
  logs: string | null
  errorMessage: string | null
}

export const useAppCreatorStore = defineStore('appcreator', {
  state: () => ({
    apps: [] as AppCreatorApp[],
    templates: [] as AppCreatorTemplate[],
    gitConnections: [] as GitConnection[],
    registryConnections: [] as RegistryConnection[],
    activeBuildJob: null as ContainerBuildJob | null,
    currentApp: null as AppCreatorApp | null,
    wizard: defaultWizardState(),
    currentStep: 1,
    totalSteps: 8,
    draftId: null as string | null,
    yamlPreview: null as Record<string, string> | null,
    loading: false,
    deploying: false,
    error: null as string | null,
    // Undo/Redo
    history: [] as string[],
    historyIndex: -1,
    lastSavedAt: null as Date | null
  }),

  getters: {
    configJson: (state): string => {
      const w = state.wizard
      return JSON.stringify({
        name: w.name,
        description: w.description,
        namespace: w.namespace,
        workloadType: w.workloadType,
        image: `${w.image}:${w.imageTag}`,
        imagePullPolicy: w.imagePullPolicy,
        replicas: w.replicas,
        ports: w.ports,
        envVars: w.envVars,
        resources: w.resources,
        probes: w.probes,
        deploymentStrategy: w.deploymentStrategy,
        maxSurge: w.maxSurge,
        maxUnavailable: w.maxUnavailable,
        statefulSetServiceName: w.statefulSetServiceName,
        statefulSetPodManagementPolicy: w.statefulSetPodManagementPolicy,
        cronJobSchedule: w.cronJobSchedule,
        cronJobConcurrencyPolicy: w.cronJobConcurrencyPolicy,
        cronJobSuccessHistory: w.cronJobSuccessHistory,
        cronJobFailedHistory: w.cronJobFailedHistory,
        daemonSetNodeSelector: w.daemonSetNodeSelector,
        createService: w.createService,
        serviceConfig: w.serviceConfig,
        createIngress: w.createIngress,
        ingressConfig: w.ingressConfig,
        configMaps: w.configMaps,
        secrets: w.secrets,
        hpa: w.hpa,
        volumes: w.volumes,
        volumeMounts: w.volumeMounts,
        // legacy
        ingressHost: w.ingressConfig.host || w.ingressHost,
        ingressPath: w.ingressConfig.path || w.ingressPath,
        ingressClass: w.ingressConfig.ingressClass || w.ingressClass,
        configMap: w.configMap,
        secret: w.secret
      })
    },
    canUndo: (state): boolean => state.historyIndex > 0,
    canRedo: (state): boolean => state.historyIndex < state.history.length - 1
  },

  actions: {
    resetWizard() {
      this.wizard = defaultWizardState()
      this.currentStep = 1
      this.draftId = null
      this.yamlPreview = null
      this.error = null
      this.history = []
      this.historyIndex = -1
    },

    pushHistory() {
      const snapshot = JSON.stringify(this.wizard)
      // Remove any redo states
      this.history = this.history.slice(0, this.historyIndex + 1)
      this.history.push(snapshot)
      if (this.history.length > 50) this.history.shift()
      this.historyIndex = this.history.length - 1
    },

    undo() {
      if (this.historyIndex > 0) {
        this.historyIndex--
        this.wizard = JSON.parse(this.history[this.historyIndex])
      }
    },

    redo() {
      if (this.historyIndex < this.history.length - 1) {
        this.historyIndex++
        this.wizard = JSON.parse(this.history[this.historyIndex])
      }
    },

    async saveDraft() {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster) return
      try {
        const payload = { wizardState: this.configJson, currentStep: this.currentStep, appId: this.currentApp?.id }
        if (this.draftId) {
          await $api.put(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/drafts/${this.draftId}`, payload)
        } else {
          const res = await $api.post(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/drafts`, payload)
          this.draftId = res.data.id
        }
        this.lastSavedAt = new Date()
      } catch (e) {
        console.error('Draft save failed', e)
      }
    },

    async fetchApps() {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster) return
      this.loading = true
      try {
        const res = await $api.get(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/apps`)
        this.apps = res.data
      } catch (e: any) {
        this.error = e?.response?.data?.message || 'Failed to fetch apps'
      } finally {
        this.loading = false
      }
    },

    async fetchTemplates() {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster) return
      try {
        const res = await $api.get(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/templates`)
        this.templates = res.data
      } catch (e: any) {
        this.error = e?.response?.data?.message || 'Failed to fetch templates'
      }
    },

    async saveApp() {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster) throw new Error('No cluster selected')
      const uid = clusterStore.selectedCluster.uid
      const payload = {
        name: this.wizard.name,
        description: this.wizard.description,
        namespace: this.wizard.namespace,
        workloadType: this.wizard.workloadType,
        config: this.configJson,
        templateId: this.wizard.templateId
      }
      if (this.currentApp) {
        const res = await $api.put(`/k8s/${uid}/appcreator/apps/${this.currentApp.id}`, payload)
        this.currentApp = res.data
      } else {
        const res = await $api.post(`/k8s/${uid}/appcreator/apps`, payload)
        this.currentApp = res.data
        this.apps.unshift(res.data)
      }
      return this.currentApp
    },

    async refreshYamlPreview() {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster) return
      try {
        const res = await $api.post(
          `/k8s/${clusterStore.selectedCluster.uid}/appcreator/preview-yaml`,
          { config: this.configJson }
        )
        this.yamlPreview = res.data.files
      } catch (e: any) {
        this.error = 'YAML preview failed'
      }
    },

    async fetchGitConnections() {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster) return
      try {
        const res = await $api.get(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/git-connections`)
        this.gitConnections = res.data
      } catch (e: any) {
        console.error('Failed to fetch git connections', e)
      }
    },

    async deployApp(
      deployType: 'direct' | 'git' = 'direct',
      gitOptions?: { connectionId: string; repo: string; branch: string; gitPath: string }
    ) {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster || !this.currentApp) throw new Error('Missing context')
      this.deploying = true
      try {
        const payload: Record<string, any> = { deployType }
        if (deployType === 'git' && gitOptions) {
          payload.gitConnectionId = gitOptions.connectionId
          payload.gitRepo        = gitOptions.repo
          payload.gitBranch      = gitOptions.branch
          payload.gitPath        = gitOptions.gitPath
        }
        const res = await $api.post(
          `/k8s/${clusterStore.selectedCluster.uid}/appcreator/apps/${this.currentApp.id}/deploy`,
          payload
        )
        return res.data
      } finally {
        this.deploying = false
      }
    },

    applyTemplate(template: AppCreatorTemplate) {
      try {
        const config = JSON.parse(template.config)
        // Start from a clean default state so stale fields don't bleed through
        this.wizard = defaultWizardState()
        // Split stored image back into image + imageTag (stored as "image:tag")
        if (config.image && config.image.includes(':')) {
          const colonIdx = config.image.lastIndexOf(':')
          config.imageTag = config.image.slice(colonIdx + 1) || 'latest'
          config.image = config.image.slice(0, colonIdx)
        }
        Object.assign(this.wizard, config)
        this.wizard.templateId = template.id
        // Reset app-specific state so a new app is created (not an update of an old one)
        this.currentApp = null
        this.draftId = null
        this.currentStep = 1
        this.history = []
        this.historyIndex = -1
      } catch (e) {
        console.error('Failed to apply template', e)
      }
    },

    async saveAsTemplate(payload: {
      name: string
      description: string
      category: string
      icon: string
      isPublic: boolean
    }) {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster) throw new Error('No cluster selected')
      const uid = clusterStore.selectedCluster.uid
      const body = {
        name: payload.name,
        description: payload.description,
        category: payload.category,
        icon: payload.icon,
        isPublic: payload.isPublic,
        config: this.configJson
      }
      const res = await $api.post(`/k8s/${uid}/appcreator/templates`, body)
      this.templates.unshift(res.data)
      return res.data
    },

    async fetchRegistryConnections() {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster) return
      try {
        const res = await $api.get(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/registry-connections`)
        this.registryConnections = res.data
      } catch (e: any) {
        console.error('Failed to fetch registry connections', e)
      }
    },

    async startBuild() {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster) throw new Error('No cluster selected')
      const uid = clusterStore.selectedCluster.uid
      const w = this.wizard
      const payload = {
        gitConnectionId: w.buildGitConnectionId,
        repoPath: w.buildRepoPath,
        branch: w.buildBranch,
        dockerfilePath: w.buildDockerfilePath || 'Dockerfile',
        registryConnectionId: w.buildRegistryConnectionId,
        appName: w.name || 'app'
      }
      const res = await $api.post(`/k8s/${uid}/appcreator/build`, payload)
      this.activeBuildJob = res.data
      return res.data
    },

    async pollBuildJob(jobId: string) {
      const { $api } = useNuxtApp()
      const clusterStore = useClusterStore()
      if (!clusterStore.selectedCluster) return
      const uid = clusterStore.selectedCluster.uid
      const res = await $api.get(`/k8s/${uid}/appcreator/build/${jobId}`)
      this.activeBuildJob = res.data
      return res.data
    }
  }
})

