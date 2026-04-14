export interface ClusterRoleBinding {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    roleRef?: any
    subjects?: any[]
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
