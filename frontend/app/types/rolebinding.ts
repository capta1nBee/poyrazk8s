export interface RoleBinding {
    id?: number
    name: string
    namespace: string
    uid?: string
    resourceVersion?: string
    roleRef?: any
    subjects?: any[]
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
