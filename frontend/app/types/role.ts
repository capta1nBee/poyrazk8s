export interface Role {
    id?: number
    name: string
    namespace: string
    uid?: string
    resourceVersion?: string
    rules?: any[]
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
