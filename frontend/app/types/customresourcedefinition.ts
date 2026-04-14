export interface CustomResourceDefinition {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    groupName?: string
    versions?: any[]
    scope?: string
    names?: any
    conditions?: any[]
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
