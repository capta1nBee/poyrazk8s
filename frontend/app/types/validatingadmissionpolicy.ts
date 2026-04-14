export interface ValidatingAdmissionPolicy {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    failurePolicy?: string
    matchConstraints?: any
    validations?: any[]
    paramKind?: any
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
