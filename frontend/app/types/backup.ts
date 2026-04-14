export interface Backup {
    id: number
    clusterId: number
    clusterName: string
    clusterUid: string
    status: 'RUNNING' | 'COMPLETED' | 'FAILED'
    backupPath?: string
    totalResources?: number
    totalNamespaces?: number
    sizeBytes?: number
    errorMessage?: string
    startedAt?: string
    completedAt?: string
    createdAt: string
    triggeredBy: 'SCHEDULED' | 'MANUAL'
}

export interface BackupStats {
    totalBackups: number
    completedBackups: number
    totalSize: number
    lastWeekBackups: number
}

export interface BackupFileItem {
    name: string
    isDirectory: boolean
    path: string
    size?: number
}
