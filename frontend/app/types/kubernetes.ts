export interface Pod {
  id: number
  kind: string
  clusterId: number
  name: string
  namespace: string
  phase: string
  nodeName?: string
  owner?: string
  restartCount: number
  qosClass?: string
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  containers?: string
  ownerReferences?: string
  labels?: string
  annotations?: string
}

export interface Container {
  name: string
  image: string
  ready: boolean
  restartCount: number
  state?: string
}

export interface Deployment {
  id: number
  kind: string
  clusterId: number
  name: string
  namespace: string
  replicasDesired: number
  replicasAvailable: number
  replicasReady: number
  replicas?: number
  updatedReplicas?: number
  strategy?: string
  paused?: boolean
  owner?: string
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  containers?: string
  ownerReferences?: string
  labels?: string
  annotations?: string
}

export interface StatefulSet {
  id: number
  kind: string
  clusterId: number
  name: string
  namespace: string
  replicasDesired: number
  replicasReady: number
  replicas?: number
  currentReplicas?: number
  updatedReplicas?: number
  serviceName?: string
  updateStrategy?: string
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  labels?: string
  annotations?: string
}

export interface DaemonSet {
  id: number
  kind: string
  clusterId: number
  name: string
  namespace: string
  desiredPods: number
  currentPods: number
  readyPods: number
  numberAvailable?: number
  nodeSelector?: string
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  labels?: string
  annotations?: string
}

export interface Service {
  id: number
  kind: string
  clusterId: number
  name: string
  namespace: string
  type: string
  clusterIP: string
  externalIP?: string
  ports?: string | ServicePort[]
  selector?: string
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  labels?: string
  annotations?: string
}

export interface ServicePort {
  name?: string
  port: number
  targetPort: number | string
  protocol: string
  nodePort?: number
}

export interface Job {
  name: string
  namespace: string
  completions: string
  duration: string
  age: string
  status: string
}

export interface CronJob {
  id: number
  kind: string
  clusterId: number
  name: string
  namespace: string
  schedule: string
  concurrencyPolicy?: string
  lastScheduleTime?: string
  suspend: boolean
  activeJobs?: string
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  labels?: string
  annotations?: string
}

export interface Node {
  id: number
  kind: string
  clusterId: number
  name: string
  status: string
  unschedulable?: boolean
  roles?: string
  cpuCapacity?: string
  memoryCapacity?: string
  allocatableCpu?: string
  allocatableMemory?: string
  kubeletVersion?: string
  os?: string
  kernel?: string
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  nodeIP?: string
  capacity?: string
  allocatable?: string
  ownerReferences?: string
  labels?: string
  annotations?: string
}

export interface NodeResources {
  cpu: string
  memory: string
  pods: string
}

export interface NodeCondition {
  type: string
  status: string
  reason?: string
  message?: string
}

export interface Namespace {
  id: number
  kind: string
  clusterId: number
  name: string
  status: string
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  uid?: string
  labels?: string
  annotations?: string
}

export interface ConfigMap {
  id: number
  kind: string
  clusterId: number
  name: string
  namespace: string
  dataCount?: number
  immutable?: boolean
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  labels?: string
  annotations?: string
  uid?: string
}

export interface Secret {
  id: number
  kind: string
  clusterId: number
  name: string
  namespace: string
  type?: string
  dataCount?: number
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  labels?: string
  annotations?: string
  uid?: string
}

export interface PersistentVolume {
  name: string
  capacity: string
  accessModes: string[]
  reclaimPolicy: string
  status: string
  claim?: string
  storageClass?: string
  k8sCreatedAt?: string
  isDeleted?: boolean
}

export interface PersistentVolumeClaim {
  id: number
  kind: string
  clusterId: number
  name: string
  namespace: string
  status: string
  storageClass?: string
  requestedSize?: string
  boundVolume?: string
  accessModes?: string
  k8sCreatedAt?: string
  updatedAt?: string
  isDeleted?: boolean
  labels?: string
  annotations?: string
  uid?: string
  resourceVersion?: string
}

