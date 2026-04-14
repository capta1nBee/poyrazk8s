export interface ValidatingAdmissionPolicyBinding {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    policyName?: string
    paramRef?: any
    matchResources?: any
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
