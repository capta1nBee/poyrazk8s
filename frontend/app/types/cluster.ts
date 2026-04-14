export interface Cluster {
  id: number
  uid: string
  name: string
  description?: string
  apiServerUrl: string
  isActive: boolean
  vulnScanEnabled: boolean
  backupEnabled: boolean
  privateRegistryUser?: string
  privateRegistryPassword?: string
  createdAt?: string
  updatedAt?: string
  cpu?: string
  memory?: string
  version?: string
  nodes?: number
}

export interface ClusterStats {
  totalNodes: number
  totalPods: number
  totalDeployments: number
  totalServices: number
  totalNamespaces: number
}

export interface K8sEvent {
  id: number
  kind: string
  clusterId: number
  namespace: string
  involvedObjectKind: string
  involvedObjectName: string
  type: string
  reason: string
  message: string
  count: number | null
  lastSeen: string | null
  createdAt: string
  updatedAt: string
  uid: string
  source: string
}

