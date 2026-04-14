export interface ClusterRole {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    rules?: any[]
    aggregationRule?: any
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
