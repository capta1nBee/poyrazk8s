export interface VolumeAttachment {
    id?: number
    name: string
    uid?: string
    resourceVersion?: string
    attacher?: string
    persistentVolumeName?: string
    nodeName?: string
    attached?: boolean
    attachmentMetadata?: Record<string, string>
    labels?: Record<string, string>
    annotations?: Record<string, string>
    age?: string
    createdAt?: string
}
