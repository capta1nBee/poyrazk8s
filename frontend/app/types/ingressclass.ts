export interface IngressClass {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    controller?: string
    parameters?: any
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
