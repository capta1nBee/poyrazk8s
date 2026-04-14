export interface NetworkPolicy {
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
    spec?: {
        podSelector?: {
            matchLabels?: Record<string, string>
            matchExpressions?: Array<{
                key: string
                operator: string
                values?: string[]
            }>
        }
        policyTypes?: string[]
        ingress?: Array<{
            from?: Array<{
                podSelector?: any
                namespaceSelector?: any
                ipBlock?: any
            }>
            ports?: Array<{
                protocol?: string
                port?: number | string
            }>
        }>
        egress?: Array<{
            to?: Array<{
                podSelector?: any
                namespaceSelector?: any
                ipBlock?: any
            }>
            ports?: Array<{
                protocol?: string
                port?: number | string
            }>
        }>
    }
    podSelector?: string
    policyTypes?: string
    age?: string
    createdAt?: string
}
