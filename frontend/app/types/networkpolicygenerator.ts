// Network Policy Rule extracted from flows
export interface NetworkPolicyRule {
    ruleId: string
    
    // Source information
    sourceNamespace?: string
    sourcePodName?: string
    sourcePodLabels?: Record<string, string>
    sourceIp?: string
    sourcePort?: number
    sourceKind?: string
    
    // Destination information
    destinationNamespace?: string
    destinationPodName?: string
    destinationPodLabels?: Record<string, string>
    destinationIp?: string
    destinationPort?: number
    destinationKind?: string
    
    // Service information
    serviceName?: string
    serviceNamespace?: string
    serviceSelector?: Record<string, string>
    backendPodLabels?: Record<string, string>
    
    // Network information
    protocol?: string
    flowType?: string
    
    // Statistics
    flowCount?: number
    totalBytes?: number
    
    // UI state
    selected?: boolean
}

// Generated Network Policy
export interface GeneratedNetworkPolicy {
    id?: number
    clusterUid?: string
    namespace: string
    name: string
    policyType: 'ingress' | 'egress'
    
    podSelector?: Record<string, string>
    rules?: NetworkPolicyRule[]
    
    yamlContent?: string
    status?: 'draft' | 'applied' | 'deleted'
    
    description?: string
    createdBy?: string
    appliedAt?: string
    createdAt?: string
    updatedAt?: string
    
    currentVersion?: number
    totalVersions?: number
    
    spec?: PolicySpec
}

export interface PolicySpec {
    podSelector?: Record<string, string>
    policyTypes?: string[]
    ingress?: IngressRule[]
    egress?: EgressRule[]
}

export interface IngressRule {
    from?: NetworkPolicyPeer[]
    ports?: NetworkPolicyPort[]
}

export interface EgressRule {
    to?: NetworkPolicyPeer[]
    ports?: NetworkPolicyPort[]
}

export interface NetworkPolicyPeer {
    podSelector?: Record<string, string>
    namespaceSelector?: Record<string, string>
    ipBlock?: IpBlock
}

export interface IpBlock {
    cidr: string
    except?: string[]
}

export interface NetworkPolicyPort {
    protocol?: string
    port?: number | string
}

// Conflict detection
export interface PolicyConflict {
    existingPolicyName?: string
    existingPolicyNamespace?: string
    existingPolicyType?: string
    conflictType: ConflictType
    severity: 'info' | 'warning' | 'error'
    description: string
    details?: ConflictDetail[]
    autoResolvable?: boolean
    suggestedResolution?: string
}

export type ConflictType = 
    | 'DUPLICATE_SELECTOR'
    | 'OVERLAPPING_RULES'
    | 'CONFLICTING_PORTS'
    | 'NAMESPACE_ISOLATION'
    | 'DEFAULT_DENY_OVERRIDE'
    | 'REDUNDANT_RULE'

export interface ConflictDetail {
    field: string
    existingValue?: string
    newValue?: string
    explanation?: string
}

// Migration/Version tracking
export interface PolicyMigration {
    id: number
    policyId: number
    policyName?: string
    policyNamespace?: string
    version: number
    action: 'create' | 'update' | 'delete' | 'rollback' | 'apply'
    previousYaml?: string
    newYaml?: string
    changeDescription?: string
    appliedBy?: string
    appliedAt?: string
    rollbackAt?: string
    rolledBackBy?: string
    createdAt?: string
    canRollback?: boolean
    diffSummary?: string
}

// Request DTOs
export interface GeneratePolicyRequest {
    namespace: string
    name?: string
    policyType: 'ingress' | 'egress'
    podSelector?: Record<string, string>
    selectedRules: NetworkPolicyRule[]
    description?: string
    autoApply?: boolean
}

// Policy Label for workload targeting
export interface PolicyLabel {
    labelKey: string
    labelValue: string
    displayName: string
    flowCount: number
    podNames?: string[]
    fromService: boolean
    serviceName?: string
}

// Filter/Search state
export interface PolicyGeneratorFilter {
    namespace?: string
    labelKey?: string
    labelValue?: string
    direction: 'ingress' | 'egress'
}

// Formatting helpers
export function formatBytes(bytes?: number): string {
    if (!bytes || bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

export function formatFlowCount(count?: number): string {
    if (!count) return '0'
    if (count >= 1000000) return (count / 1000000).toFixed(1) + 'M'
    if (count >= 1000) return (count / 1000).toFixed(1) + 'K'
    return count.toString()
}

export function getProtocolColor(protocol?: string): string {
    const colors: Record<string, string> = {
        'TCP': 'info',
        'UDP': 'success',
        'ICMP': 'warning'
    }
    return colors[protocol?.toUpperCase() || ''] || 'neutral'
}

export function getPolicyStatusColor(status?: string): string {
    const colors: Record<string, string> = {
        'draft': 'warning',
        'applied': 'success',
        'deleted': 'error'
    }
    return colors[status || ''] || 'neutral'
}

export function getMigrationActionColor(action?: string): string {
    const colors: Record<string, string> = {
        'create': 'success',
        'update': 'info',
        'apply': 'primary',
        'delete': 'error',
        'rollback': 'warning'
    }
    return colors[action || ''] || 'neutral'
}

export function getMigrationActionIcon(action?: string): string {
    const icons: Record<string, string> = {
        'create': 'i-lucide-plus-circle',
        'update': 'i-lucide-edit',
        'apply': 'i-lucide-check-circle',
        'delete': 'i-lucide-trash-2',
        'rollback': 'i-lucide-undo'
    }
    return icons[action || ''] || 'i-lucide-circle'
}

export function getConflictSeverityColor(severity?: string): string {
    const colors: Record<string, string> = {
        'info': 'info',
        'warning': 'warning',
        'error': 'error'
    }
    return colors[severity || ''] || 'neutral'
}
