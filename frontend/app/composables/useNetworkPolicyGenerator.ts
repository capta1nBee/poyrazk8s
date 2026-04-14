import type {
    NetworkPolicyRule,
    GeneratedNetworkPolicy,
    PolicyConflict,
    PolicyMigration,
    GeneratePolicyRequest,
    PolicyLabel
} from '~/types/networkpolicygenerator'

export function useNetworkPolicyGenerator() {
    const config = useRuntimeConfig()
    const baseUrl = config.public.apiBase || 'http://localhost:8080'
    const clusterStore = useClusterStore()
    const toast = useToast()
    
    const clusterUid = computed(() => clusterStore.selectedCluster?.uid)
    
    const loading = ref(false)
    const error = ref<string | null>(null)
    
    // State
    const rules = ref<NetworkPolicyRule[]>([])
    const policies = ref<GeneratedNetworkPolicy[]>([])
    const selectedPolicy = ref<GeneratedNetworkPolicy | null>(null)
    const conflicts = ref<PolicyConflict[]>([])
    const migrations = ref<PolicyMigration[]>([])
    const namespaces = ref<string[]>([])
    const workloadLabels = ref<PolicyLabel[]>([])
    const labelKeys = ref<string[]>([])
    
    // Get auth token (check cookie first, then localStorage)
    function getAuthHeader() {
        let token = useCookie('token').value
        if (!token && import.meta.client) {
            token = localStorage.getItem('token')
        }
        return token ? { Authorization: `Bearer ${token}` } : {}
    }
    
    /**
     * Extract distinct traffic rules from network flows by label
     */
    async function fetchRules(
        namespace: string,
        labelKey: string,
        labelValue: string,
        direction: 'ingress' | 'egress' = 'ingress'
    ) {
        if (!clusterUid.value) return
        
        loading.value = true
        error.value = null
        
        try {
            const params = new URLSearchParams()
            params.append('namespace', namespace)
            params.append('labelKey', labelKey)
            params.append('labelValue', labelValue)
            params.append('direction', direction)
            
            const response = await $fetch<{ data: NetworkPolicyRule[] }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/rules?${params.toString()}`,
                {
                    headers: getAuthHeader()
                }
            )
            
            if (response?.data) {
                rules.value = response.data.map((rule: NetworkPolicyRule) => ({
                    ...rule,
                    selected: false
                }))
            }
        } catch (e: any) {
            error.value = e.message || 'Failed to fetch rules'
            toast.add({
                title: 'Failed to fetch rules',
                description: e.message,
                color: 'red'
            })
        } finally {
            loading.value = false
        }
    }
    
    /**
     * Generate a network policy from selected rules
     */
    async function generatePolicy(request: GeneratePolicyRequest): Promise<GeneratedNetworkPolicy | null> {
        if (!clusterUid.value) return null
        
        loading.value = true
        error.value = null
        
        try {
            const response = await $fetch<{ data: GeneratedNetworkPolicy }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/generate`,
                {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        ...getAuthHeader()
                    },
                    body: request
                }
            )
            
            if (response?.data) {
                const policy = response.data
                policies.value.unshift(policy)
                selectedPolicy.value = policy
                
                toast.add({
                    title: 'Policy generated',
                    description: `${policy.policyType} policy "${policy.name}" created successfully`,
                    color: 'green'
                })
                
                return policy
            }
            return null
        } catch (e: any) {
            error.value = e.message || 'Failed to generate policy'
            toast.add({
                title: 'Failed to generate policy',
                description: e.message,
                color: 'red'
            })
            return null
        } finally {
            loading.value = false
        }
    }
    
    /**
     * Fetch all generated policies
     */
    async function fetchPolicies(namespace?: string, status?: string) {
        if (!clusterUid.value) return
        
        loading.value = true
        error.value = null
        
        try {
            const params = new URLSearchParams()
            if (namespace) params.append('namespace', namespace)
            if (status) params.append('status', status)
            
            const response = await $fetch<{ data: GeneratedNetworkPolicy[] }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/policies?${params.toString()}`,
                {
                    headers: getAuthHeader()
                }
            )
            
            if (response?.data) {
                policies.value = response.data
            }
        } catch (e: any) {
            error.value = e.message || 'Failed to fetch policies'
            toast.add({
                title: 'Failed to fetch policies',
                description: e.message,
                color: 'red'
            })
        } finally {
            loading.value = false
        }
    }
    
    /**
     * Get a single policy by ID
     */
    async function fetchPolicy(policyId: number): Promise<GeneratedNetworkPolicy | null> {
        if (!clusterUid.value) return null
        
        loading.value = true
        error.value = null
        
        try {
            const response = await $fetch<{ data: GeneratedNetworkPolicy }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/policies/${policyId}`,
                {
                    headers: getAuthHeader()
                }
            )
            
            if (response?.data) {
                selectedPolicy.value = response.data
                return response.data
            }
            return null
        } catch (e: any) {
            error.value = e.message || 'Failed to fetch policy'
            toast.add({
                title: 'Failed to fetch policy',
                description: e.message,
                color: 'red'
            })
            return null
        } finally {
            loading.value = false
        }
    }
    
    /**
     * Update a policy's YAML content
     */
    async function updatePolicy(
        policyId: number,
        yamlContent: string,
        description?: string
    ): Promise<GeneratedNetworkPolicy | null> {
        if (!clusterUid.value) return null
        
        loading.value = true
        error.value = null
        
        try {
            const response = await $fetch<{ data: GeneratedNetworkPolicy }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/policies/${policyId}`,
                {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json',
                        ...getAuthHeader()
                    },
                    body: { yamlContent, description }
                }
            )
            
            if (response?.data) {
                const updated = response.data
                
                // Update in list
                const index = policies.value.findIndex(p => p.id === policyId)
                if (index !== -1) {
                    policies.value[index] = updated
                }
                
                selectedPolicy.value = updated
                
                toast.add({
                    title: 'Policy updated',
                    description: 'Policy content has been updated',
                    color: 'green'
                })
                
                return updated
            }
            return null
        } catch (e: any) {
            error.value = e.message || 'Failed to update policy'
            toast.add({
                title: 'Failed to update policy',
                description: e.message,
                color: 'red'
            })
            return null
        } finally {
            loading.value = false
        }
    }
    
    /**
     * Check for conflicts before applying
     */
    async function checkConflicts(policy: GeneratedNetworkPolicy): Promise<PolicyConflict[]> {
        if (!clusterUid.value) return []
        
        loading.value = true
        error.value = null
        
        try {
            const response = await $fetch<{ data: PolicyConflict[] }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/check-conflicts`,
                {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        ...getAuthHeader()
                    },
                    body: policy
                }
            )
            
            if (response?.data) {
                conflicts.value = response.data
                return response.data
            }
            return []
        } catch (e: any) {
            error.value = e.message || 'Failed to check conflicts'
            toast.add({
                title: 'Failed to check conflicts',
                description: e.message,
                color: 'red'
            })
            return []
        } finally {
            loading.value = false
        }
    }
    
    /**
     * Apply a policy to the Kubernetes cluster
     */
    async function applyPolicy(policyId: number): Promise<GeneratedNetworkPolicy | null> {
        if (!clusterUid.value) return null
        
        loading.value = true
        error.value = null
        
        try {
            const response = await $fetch<{ data: GeneratedNetworkPolicy }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/policies/${policyId}/apply`,
                {
                    method: 'POST',
                    headers: getAuthHeader()
                }
            )
            
            if (response?.data) {
                const applied = response.data
                
                // Update in list
                const index = policies.value.findIndex(p => p.id === policyId)
                if (index !== -1) {
                    policies.value[index] = applied
                }
                
                selectedPolicy.value = applied
                
                toast.add({
                    title: 'Policy applied',
                    description: `Policy "${applied.name}" has been applied to the cluster`,
                    color: 'green'
                })
                
                return applied
            }
            return null
        } catch (e: any) {
            error.value = e.message || 'Failed to apply policy'
            toast.add({
                title: 'Failed to apply policy',
                description: e.message,
                color: 'red'
            })
            return null
        } finally {
            loading.value = false
        }
    }
    
    /**
     * Delete a policy
     */
    async function deletePolicy(policyId: number, deleteFromCluster: boolean = true): Promise<boolean> {
        if (!clusterUid.value) return false
        
        loading.value = true
        error.value = null
        
        try {
            await $fetch(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/policies/${policyId}?deleteFromCluster=${deleteFromCluster}`,
                {
                    method: 'DELETE',
                    headers: getAuthHeader()
                }
            )
            
            // Remove from list
            policies.value = policies.value.filter(p => p.id !== policyId)
            
            if (selectedPolicy.value?.id === policyId) {
                selectedPolicy.value = null
            }
            
            toast.add({
                title: 'Policy deleted',
                description: 'Policy has been deleted successfully',
                color: 'green'
            })
            
            return true
        } catch (e: any) {
            error.value = e.message || 'Failed to delete policy'
            toast.add({
                title: 'Failed to delete policy',
                description: e.message,
                color: 'red'
            })
            return false
        } finally {
            loading.value = false
        }
    }
    
    /**
     * Fetch migration history for a policy
     */
    async function fetchMigrations(policyId: number) {
        if (!clusterUid.value) return
        
        loading.value = true
        error.value = null
        
        try {
            const response = await $fetch<{ data: PolicyMigration[] }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/policies/${policyId}/migrations`,
                {
                    headers: getAuthHeader()
                }
            )
            
            if (response?.data) {
                migrations.value = response.data
            }
        } catch (e: any) {
            error.value = e.message || 'Failed to fetch migration history'
            toast.add({
                title: 'Failed to fetch migration history',
                description: e.message,
                color: 'red'
            })
        } finally {
            loading.value = false
        }
    }
    
    /**
     * Rollback a policy to a specific version
     */
    async function rollbackPolicy(
        policyId: number,
        targetVersion: number,
        applyToCluster: boolean = false
    ): Promise<PolicyMigration | null> {
        if (!clusterUid.value) return null
        
        loading.value = true
        error.value = null
        
        try {
            const params = new URLSearchParams()
            params.append('targetVersion', targetVersion.toString())
            params.append('applyToCluster', applyToCluster.toString())
            
            const response = await $fetch<{ data: PolicyMigration }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/policies/${policyId}/rollback?${params.toString()}`,
                {
                    method: 'POST',
                    headers: getAuthHeader()
                }
            )
            
            if (response?.data) {
                const migration = response.data
                
                // Refresh policy and migrations
                await fetchPolicy(policyId)
                await fetchMigrations(policyId)
                
                toast.add({
                    title: 'Rollback successful',
                    description: `Policy rolled back to version ${targetVersion}`,
                    color: 'green'
                })
                
                return migration
            }
            return null
        } catch (e: any) {
            error.value = e.message || 'Failed to rollback policy'
            toast.add({
                title: 'Failed to rollback policy',
                description: e.message,
                color: 'red'
            })
            return null
        } finally {
            loading.value = false
        }
    }
    
    /**
     * Fetch namespaces with network flows
     */
    async function fetchNamespaces() {
        if (!clusterUid.value) return
        
        try {
            const response = await $fetch<{ data: string[] }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/namespaces`,
                {
                    headers: getAuthHeader()
                }
            )
            
            if (response?.data) {
                namespaces.value = response.data
            }
        } catch (e: any) {
            console.error('Failed to fetch namespaces:', e)
        }
    }
    
    /**
     * Fetch workload labels in a namespace
     */
    async function fetchLabels(namespace: string, direction: 'ingress' | 'egress' = 'ingress') {
        if (!clusterUid.value) return
        
        try {
            const params = new URLSearchParams()
            params.append('namespace', namespace)
            params.append('direction', direction)
            
            const response = await $fetch<{ data: PolicyLabel[] }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/labels?${params.toString()}`,
                {
                    headers: getAuthHeader()
                }
            )
            
            if (response?.data) {
                workloadLabels.value = response.data
            }
        } catch (e: any) {
            console.error('Failed to fetch labels:', e)
        }
    }

    /**
     * Fetch configured policy label keys
     */
    async function fetchLabelKeys() {
        if (!clusterUid.value) return
        
        try {
            const response = await $fetch<{ data: string[] }>(
                `${baseUrl}/k8s/${clusterUid.value}/network-policy-generator/label-keys`,
                {
                    headers: getAuthHeader()
                }
            )
            
            if (response?.data) {
                labelKeys.value = response.data
            }
        } catch (e: any) {
            console.error('Failed to fetch label keys:', e)
        }
    }
    
    /**
     * Toggle rule selection
     */
    function toggleRuleSelection(ruleId: string) {
        const rule = rules.value.find(r => r.ruleId === ruleId)
        if (rule) {
            rule.selected = !rule.selected
        }
    }
    
    /**
     * Select all rules
     */
    function selectAllRules() {
        rules.value.forEach(r => r.selected = true)
    }
    
    /**
     * Deselect all rules
     */
    function deselectAllRules() {
        rules.value.forEach(r => r.selected = false)
    }
    
    /**
     * Get selected rules
     */
    const selectedRules = computed(() => rules.value.filter(r => r.selected))
    
    /**
     * Clear state
     */
    function clearState() {
        rules.value = []
        policies.value = []
        selectedPolicy.value = null
        conflicts.value = []
        migrations.value = []
        workloadLabels.value = []
        error.value = null
    }
    
    return {
        // State
        loading,
        error,
        rules,
        policies,
        selectedPolicy,
        conflicts,
        migrations,
        namespaces,
        workloadLabels,
        labelKeys,
        selectedRules,
        
        // Actions
        fetchRules,
        generatePolicy,
        fetchPolicies,
        fetchPolicy,
        updatePolicy,
        checkConflicts,
        applyPolicy,
        deletePolicy,
        fetchMigrations,
        rollbackPolicy,
        fetchNamespaces,
        fetchLabels,
        fetchLabelKeys,
        toggleRuleSelection,
        selectAllRules,
        deselectAllRules,
        clearState
    }
}
