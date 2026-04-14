export interface EndpointSlice {
    id?: number
    name: string
    namespace: string
    uid?: string
    metadata?: {
        name: string
        namespace: string
        creationTimestamp?: string
        labels?: Record<string, string>
        annotations?: Record<string, string>
    }
    addressType?: string
    endpoints?: Array<{
        addresses?: string[]
        conditions?: {
            ready?: boolean
            serving?: boolean
            terminating?: boolean
        }
        hostname?: string
        targetRef?: {
            kind?: string
            name?: string
            namespace?: string
            uid?: string
        }
    }>
    ports?: Array<{
        name?: string
        protocol?: string
        port?: number
        appProtocol?: string
    }>
    serviceName?: string
    age?: string
    createdAt?: string
}
