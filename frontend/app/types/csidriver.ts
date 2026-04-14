export interface CSIDriver {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    attachRequired?: boolean
    podInfoOnMount?: boolean
    storageCapacity?: boolean
    tokenRequests?: any[]
    requiresRepublish?: boolean
    volumeLifecycleModes?: string[]
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
