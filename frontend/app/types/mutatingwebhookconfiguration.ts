export interface MutatingWebhookConfiguration {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    webhooks?: any[]
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
