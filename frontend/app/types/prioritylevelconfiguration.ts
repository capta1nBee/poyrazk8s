export interface PriorityLevelConfiguration {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    type?: string
    nominalConcurrencyShares?: number
    queues?: number
    handSize?: number
    queueLengthLimit?: number
    conditions?: any[]
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
