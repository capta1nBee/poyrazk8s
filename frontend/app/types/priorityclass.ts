export interface PriorityClass {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    value?: number
    globalDefault?: boolean
    description?: string
    preemptionPolicy?: string
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
