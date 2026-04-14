export interface Ingress {
  id?: string
  kind: string
  clusterId: number
  namespace: string
  name: string
  ingressClass?: string
  hosts?: string
  paths?: string
  tlsEnabled: boolean
  address?: string
  createdAt: string
  updatedAt: string
  // Additional UI fields
  ingressClassName?: string
  rules?: string
  tls?: string
  status?: string
  labels?: string
  annotations?: string
  age?: string
}

