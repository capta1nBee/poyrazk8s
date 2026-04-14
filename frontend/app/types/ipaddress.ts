export interface IPAddress {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    parentRef?: any
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
