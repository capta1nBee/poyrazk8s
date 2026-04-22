export interface CrdNames {
    kind?: string
    plural?: string
    singular?: string
    shortNames?: string[]
    listKind?: string
    categories?: string[]
}

export interface CustomResourceDefinition {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    groupName?: string
    versions?: any[]
    scope?: string
    names?: string | CrdNames
    conditions?: any[]
    labels?: Record<string, string> | string
    annotations?: Record<string, string> | string
    age?: string
    k8sCreatedAt?: string
    createdAt?: string
}
