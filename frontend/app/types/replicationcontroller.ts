export interface ReplicationController {
    id?: number
    name: string
    namespace: string
    uid?: string
    resourceVersion?: string
    desiredReplicas?: number
    currentReplicas?: number
    readyReplicas?: number
    availableReplicas?: number
    selector?: Record<string, string>
    template?: any
    conditions?: any[]
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
