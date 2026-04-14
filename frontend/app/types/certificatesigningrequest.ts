export interface CertificateSigningRequest {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    request?: string
    signerName?: string
    usages?: string[]
    conditions?: any[]
    certificate?: string
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
