export interface User {
  id?: number
  username: string
  email: string
  isSuperadmin: boolean
  roles: string[]
  authType?: string
  isActive?: boolean
}

export interface LoginRequest {
  username: string
  password: string
}

export interface AuthResponse {
  token?: string
  username: string
  email: string
  isSuperadmin: boolean
  roles: string[]
}

export interface Permission {
  clusterId: number
  clusterName: string
  namespaces: string[]
  permissions: string[]
}

