export interface ServiceAccount {
    id?: number
    name: string
    namespace: string
    uid?: string
    resourceVersion?: string
    secrets?: any[]
    imagePullSecrets?: any[]
    automountServiceAccountToken?: boolean
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
