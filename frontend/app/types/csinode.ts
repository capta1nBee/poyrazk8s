export interface CSINode {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    drivers?: any[]
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
