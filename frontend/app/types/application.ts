export interface Application {
    id?: number
    name: string
    namespace: string
    uid?: string
    resourceVersion?: string
    descriptor?: any
    componentKinds?: any[]
    selector?: any
    info?: any[]
    assemblyPhase?: string
    conditions?: any[]
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
