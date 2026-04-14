<script setup lang="ts">
import type { 
    NetworkPolicyRule, 
    GeneratedNetworkPolicy, 
    PolicyConflict,
    PolicyMigration,
    GeneratePolicyRequest,
    PolicyLabel
} from '~/types/networkpolicygenerator'
import { 
    formatBytes, 
    formatFlowCount, 
    getProtocolColor, 
    getPolicyStatusColor,
    getMigrationActionColor,
    getMigrationActionIcon,
    getConflictSeverityColor
} from '~/types/networkpolicygenerator'

const clusterStore = useClusterStore()
const toast = useToast()

const {
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
} = useNetworkPolicyGenerator()

const confirmDialog = useConfirm()

const selectedCluster = computed(() => clusterStore.selectedCluster)

// Tab state
const activeTab = ref<'rules' | 'policies'>('rules')

// Filter state
const selectedNamespace = ref('')
const selectedLabelValue = ref('')
const direction = ref<'ingress' | 'egress'>('ingress')

// Computed items for select menus
const directionOptions = [
    { label: 'Ingress (incoming traffic)', value: 'ingress' },
    { label: 'Egress (outgoing traffic)', value: 'egress' }
]

const namespaceOptions = computed(() => 
    namespaces.value.map(ns => ({ label: ns, value: ns }))
)

const labelSelectOptions = computed(() => 
    workloadLabels.value.map(l => ({ 
        label: `${l.displayName}${l.fromService ? ' (svc: ' + l.serviceName + ')' : ''} - ${l.flowCount} flows`, 
        value: l.displayName
    }))
)

// Get selected label info
const selectedLabelInfo = computed(() => {
    if (!selectedLabelValue.value) return null
    return workloadLabels.value.find(l => l.displayName === selectedLabelValue.value)
})

// Get selected label object for API calls
const selectedLabel = computed(() => selectedLabelInfo.value)

// Generate policy modal state
const showGenerateModal = ref(false)
const policyName = ref('')
const policyDescription = ref('')

// Preview modal state
const showPreviewModal = ref(false)

// Conflict modal state
const showConflictModal = ref(false)

// Migration modal state
const showMigrationModal = ref(false)

// Delete confirmation modal state
const showDeleteModal = ref(false)
const policyToDelete = ref<GeneratedNetworkPolicy | null>(null)

// YAML edit modal state
const showYamlEditModal = ref(false)
const editingYaml = ref('')

// Watch for cluster changes
watch(selectedCluster, () => {
    if (selectedCluster.value?.uid) {
        clearState()
        fetchNamespaces()
        fetchPolicies()
        fetchLabelKeys()
    }
}, { immediate: true })

// Watch namespace changes
watch(selectedNamespace, async (ns) => {
    selectedLabelValue.value = ''
    rules.value = []
    if (ns) {
        await fetchLabels(ns, direction.value)
    }
})

// Watch direction changes
watch(direction, async () => {
    selectedLabelValue.value = ''
    rules.value = []
    if (selectedNamespace.value) {
        await fetchLabels(selectedNamespace.value, direction.value)
    }
})

// Load rules when filter changes
async function loadRules() {
    if (!selectedNamespace.value) {
        toast.add({
            title: 'Please select a namespace',
            color: 'warning'
        })
        return
    }
    if (!selectedLabelInfo.value) {
        toast.add({
            title: 'Please select a workload label',
            description: 'Select a label (e.g., app=frontend) to identify the target workload',
            color: 'warning'
        })
        return
    }
    await fetchRules(
        selectedNamespace.value, 
        selectedLabelInfo.value.labelKey, 
        selectedLabelInfo.value.labelValue, 
        direction.value
    )
}

// Get pod selector from selected label
function getPodSelector(): Record<string, string> {
    if (!selectedLabelInfo.value) return {}
    return {
        [selectedLabelInfo.value.labelKey]: selectedLabelInfo.value.labelValue
    }
}

// Generate policy
async function handleGeneratePolicy() {
    if (selectedRules.value.length === 0) {
        toast.add({
            title: 'Please select at least one rule',
            color: 'warning'
        })
        return
    }
    
    if (!selectedLabelInfo.value) {
        toast.add({
            title: 'No workload label selected',
            color: 'warning'
        })
        return
    }
    
    const podSelector = getPodSelector()
    
    const request: GeneratePolicyRequest = {
        namespace: selectedNamespace.value,
        name: policyName.value || undefined,
        policyType: direction.value,
        podSelector,
        selectedRules: selectedRules.value,
        description: policyDescription.value || undefined,
        autoApply: false
    }
    
    const policy = await generatePolicy(request)
    if (policy) {
        showGenerateModal.value = false
        selectedPolicy.value = policy
        showPreviewModal.value = true
        
        // Reset form
        policyName.value = ''
        policyDescription.value = ''
    }
}

// Check conflicts and show modal
async function handleCheckConflicts() {
    if (!selectedPolicy.value) return
    
    const foundConflicts = await checkConflicts(selectedPolicy.value)
    if (foundConflicts.length > 0) {
        showConflictModal.value = true
    } else {
        toast.add({
            title: 'No conflicts found',
            description: 'Policy can be safely applied',
            color: 'green'
        })
    }
}

// Apply policy
async function handleApplyPolicy() {
    if (!selectedPolicy.value?.id) return
    
    showConflictModal.value = false
    await applyPolicy(selectedPolicy.value.id)
}

// View policy details
async function viewPolicy(policy: GeneratedNetworkPolicy) {
    if (policy.id) {
        await fetchPolicy(policy.id)
        showPreviewModal.value = true
    }
}

// View migration history
async function viewMigrationHistory(policy: GeneratedNetworkPolicy) {
    if (policy.id) {
        selectedPolicy.value = policy
        await fetchMigrations(policy.id)
        showMigrationModal.value = true
    }
}

// Handle rollback
async function handleRollback(migration: PolicyMigration) {
    if (!selectedPolicy.value?.id) return
    
    if (!await confirmDialog.open({
        title: 'Rollback Policy',
        description: `Are you sure you want to rollback to version ${migration.version}?`,
        confirmLabel: 'Rollback',
        color: 'orange'
    })) return
    
    await rollbackPolicy(selectedPolicy.value.id, migration.version, false)
}

// Open delete confirmation
function confirmDelete(policy: GeneratedNetworkPolicy) {
    policyToDelete.value = policy
    showDeleteModal.value = true
}

// Handle delete
async function handleDelete() {
    if (!policyToDelete.value?.id) return
    
    await deletePolicy(policyToDelete.value.id, true)
    showDeleteModal.value = false
    policyToDelete.value = null
}

// Edit YAML
function openYamlEditor(policy: GeneratedNetworkPolicy) {
    selectedPolicy.value = policy
    editingYaml.value = policy.yamlContent || ''
    showYamlEditModal.value = true
}

// Save edited YAML
async function saveYamlEdits() {
    if (!selectedPolicy.value?.id) return
    
    await updatePolicy(selectedPolicy.value.id, editingYaml.value, 'Manual YAML edit')
    showYamlEditModal.value = false
}

// Copy YAML to clipboard
async function copyYaml() {
    if (selectedPolicy.value?.yamlContent) {
        await navigator.clipboard.writeText(selectedPolicy.value.yamlContent)
        toast.add({
            title: 'YAML copied to clipboard',
            color: 'green'
        })
    }
}
</script>

<template>
  <UDashboardPanel id="network-policy-generator">
    <template #header>
      <UDashboardNavbar title="Network Policy Generator">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="soft"
            :loading="loading"
            @click="fetchPolicies()"
          >
            Refresh
          </UButton>
        </template>
      </UDashboardNavbar>

      <!-- Tabs -->
      <div class="border-b border-gray-200 dark:border-gray-800">
        <nav class="flex px-4" aria-label="Tabs">
          <button
            :class="[
              'py-3 px-4 text-sm font-medium border-b-2 -mb-px',
              activeTab === 'rules' 
                ? 'border-primary-500 text-primary-600 dark:text-primary-400' 
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'
            ]"
            @click="activeTab = 'rules'"
          >
            <UIcon name="i-lucide-filter" class="mr-2" />
            Rule Extraction
          </button>
          <button
            :class="[
              'py-3 px-4 text-sm font-medium border-b-2 -mb-px',
              activeTab === 'policies' 
                ? 'border-primary-500 text-primary-600 dark:text-primary-400' 
                : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'
            ]"
            @click="activeTab = 'policies'"
          >
            <UIcon name="i-lucide-shield" class="mr-2" />
            Generated Policies
            <UBadge v-if="policies.length > 0" color="primary" size="xs" class="ml-2">
              {{ policies.length }}
            </UBadge>
          </button>
        </nav>
      </div>
    </template>

    <template #body>
      <div class="flex flex-col h-full">
      <!-- No Cluster Selected -->
      <div v-if="!selectedCluster" class="flex flex-col items-center justify-center h-96">
        <UIcon name="i-lucide-alert-circle" class="w-16 h-16 text-gray-400 mb-4" />
        <p class="text-lg text-gray-500">Please select a cluster first</p>
      </div>

      <!-- Rule Extraction Tab -->
      <div v-else-if="activeTab === 'rules'" class="p-6">
        <!-- Filters -->
        <div class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 mb-6">
          <h3 class="text-lg font-semibold mb-4 flex items-center gap-2">
            <UIcon name="i-lucide-filter" class="text-primary" />
            Extract Traffic Rules
          </h3>
          
          <div class="grid grid-cols-1 md:grid-cols-5 gap-4">
            <!-- Direction -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Policy Type</label>
              <USelect
                v-model="direction"
                :items="directionOptions"
                value-key="value"
              />
            </div>
            
            <!-- Namespace -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Namespace</label>
              <USelect
                v-model="selectedNamespace"
                :items="namespaceOptions"
                value-key="value"
                placeholder="Select namespace"
              />
            </div>
            
            <!-- Workload Label (Required) -->
            <div class="col-span-2">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                Target Workload <span class="text-red-500">*</span>
                <span class="text-xs text-gray-400 ml-1">(label-based)</span>
              </label>
              <USelect
                v-model="selectedLabelValue"
                :items="labelSelectOptions"
                value-key="value"
                placeholder="Select workload label"
                :disabled="!selectedNamespace || workloadLabels.length === 0"
              />
              <p v-if="selectedLabelInfo" class="text-xs text-blue-600 dark:text-blue-400 mt-1">
                {{ selectedLabelInfo.flowCount }} flows
                <span v-if="selectedLabelInfo.fromService">
                  · Service: {{ selectedLabelInfo.serviceName }}
                </span>
                <span v-if="selectedLabelInfo.podNames?.length">
                  · Pods: {{ selectedLabelInfo.podNames.slice(0, 2).join(', ') }}{{ selectedLabelInfo.podNames.length > 2 ? '...' : '' }}
                </span>
              </p>
              <p v-else class="text-xs text-gray-500 mt-1">
                Labels: {{ labelKeys.join(', ') }}
              </p>
            </div>
            
            <!-- Load Button -->
            <div class="flex items-end">
              <UButton
                icon="i-lucide-search"
                color="primary"
                :loading="loading"
                :disabled="!selectedNamespace || !selectedLabelValue"
                @click="loadRules"
              >
                Extract Rules
              </UButton>
            </div>
          </div>
        </div>

        <!-- Rules Table -->
        <div v-if="rules.length > 0" class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700">
          <div class="p-4 border-b border-gray-200 dark:border-gray-700 flex justify-between items-center">
            <div class="flex items-center gap-4">
              <h3 class="text-lg font-semibold">Discovered Traffic Rules</h3>
              <UBadge color="info">{{ rules.length }} rules</UBadge>
              <UBadge v-if="selectedRules.length > 0" color="success">
                {{ selectedRules.length }} selected
              </UBadge>
            </div>
            <div class="flex items-center gap-2">
              <UButton size="sm" variant="ghost" @click="selectAllRules">Select All</UButton>
              <UButton size="sm" variant="ghost" @click="deselectAllRules">Deselect All</UButton>
              <UButton
                icon="i-lucide-wand-2"
                color="primary"
                :disabled="selectedRules.length === 0"
                @click="showGenerateModal = true"
              >
                Generate Policy
              </UButton>
            </div>
          </div>
          
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead class="bg-gray-50 dark:bg-gray-900">
                <tr>
                  <th class="px-4 py-3 text-left w-12">
                    <UCheckbox 
                      :model-value="selectedRules.length === rules.length && rules.length > 0"
                      :indeterminate="selectedRules.length > 0 && selectedRules.length < rules.length"
                      @update:model-value="(v: boolean) => v ? selectAllRules() : deselectAllRules()"
                    />
                  </th>
                  <th class="px-4 py-3 text-left font-semibold">Source</th>
                  <th class="px-4 py-3 text-left font-semibold">Target (Labels)</th>
                  <th class="px-4 py-3 text-left font-semibold">Port/Protocol</th>
                  <th class="px-4 py-3 text-left font-semibold">Service Backend</th>
                  <th class="px-4 py-3 text-right font-semibold">Flows</th>
                  <th class="px-4 py-3 text-right font-semibold">Data</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
                <tr 
                  v-for="rule in rules" 
                  :key="rule.ruleId"
                  class="hover:bg-gray-50 dark:hover:bg-gray-800/50 cursor-pointer"
                  @click="toggleRuleSelection(rule.ruleId)"
                >
                  <td class="px-4 py-3">
                    <UCheckbox :model-value="rule.selected" @click.stop />
                  </td>
                  <td class="px-4 py-3">
                    <div class="flex flex-col">
                      <span class="font-medium">{{ rule.sourcePodName || rule.sourceIp || 'External' }}</span>
                      <span v-if="rule.sourceNamespace" class="text-xs text-gray-500">
                        ns: {{ rule.sourceNamespace }}
                      </span>
                      <!-- Source labels -->
                      <div v-if="rule.sourcePodLabels" class="flex flex-wrap gap-1 mt-1">
                        <UBadge 
                          v-for="(value, key) in rule.sourcePodLabels" 
                          :key="key"
                          color="neutral" 
                          variant="subtle" 
                          size="xs"
                        >
                          {{ key }}={{ value }}
                        </UBadge>
                      </div>
                    </div>
                  </td>
                  <td class="px-4 py-3">
                    <div class="flex flex-col">
                      <span class="font-medium">{{ rule.destinationPodName || rule.destinationIp || 'External' }}</span>
                      <span v-if="rule.destinationNamespace" class="text-xs text-gray-500">
                        ns: {{ rule.destinationNamespace }}
                      </span>
                      <!-- Destination/Backend labels -->
                      <div v-if="rule.backendPodLabels && Object.keys(rule.backendPodLabels).length > 0" class="flex flex-wrap gap-1 mt-1">
                        <UBadge 
                          v-for="(value, key) in rule.backendPodLabels" 
                          :key="key"
                          color="blue" 
                          variant="subtle" 
                          size="xs"
                        >
                          {{ key }}={{ value }}
                        </UBadge>
                      </div>
                      <div v-else-if="rule.destinationPodLabels" class="flex flex-wrap gap-1 mt-1">
                        <UBadge 
                          v-for="(value, key) in rule.destinationPodLabels" 
                          :key="key"
                          color="neutral" 
                          variant="subtle" 
                          size="xs"
                        >
                          {{ key }}={{ value }}
                        </UBadge>
                      </div>
                    </div>
                  </td>
                  <td class="px-4 py-3">
                    <div class="flex items-center gap-2">
                      <UBadge :color="getProtocolColor(rule.protocol)" size="xs">
                        {{ rule.protocol }}
                      </UBadge>
                      <span class="font-mono">:{{ rule.destinationPort }}</span>
                    </div>
                  </td>
                  <td class="px-4 py-3">
                    <div v-if="rule.serviceName" class="flex flex-col">
                      <span class="text-sm font-medium text-blue-600 dark:text-blue-400">
                        <UIcon name="i-lucide-cloud" class="w-3 h-3 inline mr-1" />
                        {{ rule.serviceName }}
                      </span>
                      <span v-if="rule.backendPodLabels" class="text-xs text-gray-500">
                        Backend labels used for policy
                      </span>
                    </div>
                    <span v-else class="text-gray-400">Direct pod traffic</span>
                  </td>
                  <td class="px-4 py-3 text-right font-mono">
                    {{ formatFlowCount(rule.flowCount) }}
                  </td>
                  <td class="px-4 py-3 text-right font-mono text-gray-500">
                    {{ formatBytes(rule.totalBytes) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Empty State -->
        <div v-else-if="!loading" class="flex flex-col items-center justify-center h-64 text-center">
          <UIcon name="i-lucide-network" class="w-16 h-16 text-gray-400 mb-4" />
          <p class="text-lg text-gray-500 mb-2">No rules extracted yet</p>
          <p class="text-sm text-gray-400 mb-2">
            Select a namespace and a workload label to discover traffic patterns
          </p>
          <p class="text-xs text-gray-400">
            Workloads are identified by labels: <span class="font-mono">{{ labelKeys.join(', ') }}</span>
          </p>
        </div>
      </div>

      <!-- Policies Tab -->
      <div v-else-if="activeTab === 'policies'" class="p-6">
        <div v-if="policies.length > 0" class="grid gap-4">
          <div 
            v-for="policy in policies" 
            :key="policy.id"
            class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5"
          >
            <div class="flex items-start justify-between">
              <div class="flex items-start gap-4">
                <div class="p-3 bg-primary-100 dark:bg-primary-900/30 rounded-lg">
                  <UIcon 
                    :name="policy.policyType === 'ingress' ? 'i-lucide-arrow-down-to-line' : 'i-lucide-arrow-up-from-line'" 
                    class="h-6 w-6 text-primary-600 dark:text-primary-400" 
                  />
                </div>
                <div>
                  <div class="flex items-center gap-3 mb-1">
                    <h4 class="text-lg font-semibold">{{ policy.name }}</h4>
                    <UBadge :color="getPolicyStatusColor(policy.status)" size="sm">
                      {{ policy.status }}
                    </UBadge>
                    <UBadge color="neutral" variant="subtle" size="sm">
                      {{ policy.policyType }}
                    </UBadge>
                  </div>
                  <p class="text-sm text-gray-500">
                    Namespace: <span class="font-medium">{{ policy.namespace }}</span>
                  </p>
                  <p v-if="policy.description" class="text-sm text-gray-400 mt-1">
                    {{ policy.description }}
                  </p>
                  <div class="flex items-center gap-4 mt-2 text-xs text-gray-400">
                    <span v-if="policy.createdBy">Created by {{ policy.createdBy }}</span>
                    <span v-if="policy.currentVersion">Version {{ policy.currentVersion }}</span>
                    <span v-if="policy.appliedAt">Applied {{ new Date(policy.appliedAt).toLocaleString() }}</span>
                  </div>
                </div>
              </div>
              <div class="flex items-center gap-2">
                <UButton
                  icon="i-lucide-eye"
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  @click="viewPolicy(policy)"
                />
                <UButton
                  icon="i-lucide-edit"
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  @click="openYamlEditor(policy)"
                />
                <UButton
                  icon="i-lucide-history"
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  @click="viewMigrationHistory(policy)"
                />
                <UButton
                  v-if="policy.status === 'draft'"
                  icon="i-lucide-check-circle"
                  color="success"
                  variant="soft"
                  size="sm"
                  @click="() => { selectedPolicy = policy; handleCheckConflicts(); }"
                >
                  Apply
                </UButton>
                <UButton
                  icon="i-lucide-trash-2"
                  color="error"
                  variant="ghost"
                  size="sm"
                  @click="confirmDelete(policy)"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div v-else class="flex flex-col items-center justify-center h-64 text-center">
          <UIcon name="i-lucide-shield-off" class="w-16 h-16 text-gray-400 mb-4" />
          <p class="text-lg text-gray-500 mb-2">No policies generated yet</p>
          <p class="text-sm text-gray-400 mb-4">Extract rules from traffic flows and generate policies</p>
          <UButton color="primary" @click="activeTab = 'rules'">
            Go to Rule Extraction
          </UButton>
        </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Generate Policy Modal -->
  <UModal v-model:open="showGenerateModal">
    <template #content>
      <div class="p-6">
        <h3 class="text-lg font-semibold mb-4">Generate Network Policy</h3>
        
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium mb-2">Policy Name (optional)</label>
            <UInput v-model="policyName" placeholder="auto-generated if empty" />
          </div>
          
          <!-- Pod Selector from Selected Label -->
          <div class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4">
            <label class="block text-sm font-medium text-blue-800 dark:text-blue-200 mb-2">
              Pod Selector (from selected workload)
            </label>
            <div v-if="selectedLabelInfo" class="flex items-center gap-2">
              <UBadge color="primary" size="lg">
                {{ selectedLabelInfo.labelKey }}={{ selectedLabelInfo.labelValue }}
              </UBadge>
              <span v-if="selectedLabelInfo.fromService" class="text-xs text-blue-600 dark:text-blue-400">
                Backend of Service: {{ selectedLabelInfo.serviceName }}
              </span>
            </div>
            <p class="text-xs text-blue-600 dark:text-blue-400 mt-2">
              This label identifies which pods this policy will apply to.
            </p>
          </div>
          
          <div>
            <label class="block text-sm font-medium mb-2">Description (optional)</label>
            <UTextarea v-model="policyDescription" placeholder="Describe the purpose of this policy" rows="2" />
          </div>
          
          <div class="bg-gray-50 dark:bg-gray-900 rounded-lg p-4">
            <p class="text-sm text-gray-600 dark:text-gray-400">
              <strong>{{ selectedRules.length }}</strong> rules selected for 
              <strong>{{ direction }}</strong> policy in namespace 
              <strong>{{ selectedNamespace }}</strong>
            </p>
          </div>
        </div>
        
        <div class="flex justify-end gap-3 mt-6">
          <UButton color="neutral" variant="ghost" @click="showGenerateModal = false">Cancel</UButton>
          <UButton color="primary" :loading="loading" @click="handleGeneratePolicy">
            Generate Policy
          </UButton>
        </div>
      </div>
    </template>
  </UModal>

  <!-- Preview Modal -->
  <UModal v-model:open="showPreviewModal" :ui="{ width: 'max-w-4xl' }">
    <template #content>
      <div v-if="selectedPolicy" class="p-6">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold">{{ selectedPolicy.name }}</h3>
          <div class="flex items-center gap-2">
            <UBadge :color="getPolicyStatusColor(selectedPolicy.status)">
              {{ selectedPolicy.status }}
            </UBadge>
          </div>
        </div>
        
        <div class="bg-gray-900 rounded-lg p-4 overflow-auto max-h-[60vh]">
          <pre class="text-sm text-gray-100 font-mono whitespace-pre">{{ selectedPolicy.yamlContent }}</pre>
        </div>
        
        <div class="flex justify-between mt-6">
          <UButton icon="i-lucide-copy" color="neutral" variant="ghost" @click="copyYaml">
            Copy YAML
          </UButton>
          <div class="flex gap-3">
            <UButton color="neutral" variant="ghost" @click="showPreviewModal = false">Close</UButton>
            <UButton 
              v-if="selectedPolicy.status === 'draft'"
              color="primary"
              icon="i-lucide-check-circle"
              :loading="loading"
              @click="handleCheckConflicts"
            >
              Check Conflicts & Apply
            </UButton>
          </div>
        </div>
      </div>
    </template>
  </UModal>

  <!-- Conflict Modal -->
  <UModal v-model:open="showConflictModal">
    <template #content>
      <div class="p-6">
        <div class="flex items-center gap-3 mb-4">
          <div class="p-2 bg-yellow-100 dark:bg-yellow-900/30 rounded-lg">
            <UIcon name="i-lucide-alert-triangle" class="h-6 w-6 text-yellow-600" />
          </div>
          <h3 class="text-lg font-semibold">Potential Conflicts Detected</h3>
        </div>
        
        <div class="space-y-3 mb-6">
          <div 
            v-for="(conflict, index) in conflicts" 
            :key="index"
            class="p-4 rounded-lg border"
            :class="{
              'border-yellow-200 bg-yellow-50 dark:bg-yellow-900/20': conflict.severity === 'warning',
              'border-red-200 bg-red-50 dark:bg-red-900/20': conflict.severity === 'error',
              'border-blue-200 bg-blue-50 dark:bg-blue-900/20': conflict.severity === 'info'
            }"
          >
            <div class="flex items-start gap-3">
              <UBadge :color="getConflictSeverityColor(conflict.severity)" size="sm">
                {{ conflict.conflictType.replace(/_/g, ' ') }}
              </UBadge>
              <div>
                <p class="text-sm font-medium">{{ conflict.existingPolicyName }}</p>
                <p class="text-sm text-gray-600 dark:text-gray-400">{{ conflict.description }}</p>
                <p v-if="conflict.suggestedResolution" class="text-xs text-gray-500 mt-1">
                  <strong>Suggestion:</strong> {{ conflict.suggestedResolution }}
                </p>
              </div>
            </div>
          </div>
        </div>
        
        <div class="flex justify-end gap-3">
          <UButton color="neutral" variant="ghost" @click="showConflictModal = false">Cancel</UButton>
          <UButton color="warning" @click="handleApplyPolicy">
            Apply Anyway
          </UButton>
        </div>
      </div>
    </template>
  </UModal>

  <!-- Migration History Modal -->
  <UModal v-model:open="showMigrationModal" :ui="{ width: 'max-w-3xl' }">
    <template #content>
      <div class="p-6">
        <h3 class="text-lg font-semibold mb-4">Version History</h3>
        
        <div class="space-y-3 max-h-[60vh] overflow-y-auto">
          <div 
            v-for="migration in migrations" 
            :key="migration.id"
            class="p-4 bg-gray-50 dark:bg-gray-900 rounded-lg"
          >
            <div class="flex items-start justify-between">
              <div class="flex items-start gap-3">
                <div class="p-2 rounded-lg" :class="`bg-${getMigrationActionColor(migration.action)}-100 dark:bg-${getMigrationActionColor(migration.action)}-900/30`">
                  <UIcon :name="getMigrationActionIcon(migration.action)" class="h-4 w-4" />
                </div>
                <div>
                  <div class="flex items-center gap-2">
                    <span class="font-medium">Version {{ migration.version }}</span>
                    <UBadge :color="getMigrationActionColor(migration.action)" size="xs">
                      {{ migration.action }}
                    </UBadge>
                    <span v-if="migration.rollbackAt" class="text-xs text-gray-400">(rolled back)</span>
                  </div>
                  <p class="text-sm text-gray-600 dark:text-gray-400">{{ migration.changeDescription }}</p>
                  <p class="text-xs text-gray-400 mt-1">
                    {{ migration.appliedBy }} - {{ new Date(migration.appliedAt || '').toLocaleString() }}
                  </p>
                  <p v-if="migration.diffSummary" class="text-xs text-gray-500 mt-1">
                    {{ migration.diffSummary }}
                  </p>
                </div>
              </div>
              <UButton
                v-if="migration.canRollback"
                icon="i-lucide-undo"
                color="neutral"
                variant="ghost"
                size="xs"
                @click="handleRollback(migration)"
              >
                Rollback
              </UButton>
            </div>
          </div>
        </div>
        
        <div class="flex justify-end mt-6">
          <UButton color="neutral" @click="showMigrationModal = false">Close</UButton>
        </div>
      </div>
    </template>
  </UModal>

  <!-- Delete Confirmation Modal -->
  <UModal v-model:open="showDeleteModal">
    <template #content>
      <div class="p-6">
        <div class="flex items-center gap-3 mb-4">
          <div class="p-2 bg-red-100 dark:bg-red-900/30 rounded-lg">
            <UIcon name="i-lucide-trash-2" class="h-6 w-6 text-red-600" />
          </div>
          <h3 class="text-lg font-semibold">Delete Policy</h3>
        </div>
        
        <p class="text-gray-600 dark:text-gray-400 mb-6">
          Are you sure you want to delete <strong>{{ policyToDelete?.name }}</strong>?
          This will also remove the policy from the Kubernetes cluster if it was applied.
        </p>
        
        <div class="flex justify-end gap-3">
          <UButton color="neutral" variant="ghost" @click="showDeleteModal = false">Cancel</UButton>
          <UButton color="error" :loading="loading" @click="handleDelete">
            Delete Policy
          </UButton>
        </div>
      </div>
    </template>
  </UModal>

  <!-- YAML Edit Modal -->
  <UModal v-model:open="showYamlEditModal" :ui="{ width: 'max-w-4xl' }">
    <template #content>
      <div class="p-6">
        <h3 class="text-lg font-semibold mb-4">Edit Policy YAML</h3>
        
        <UTextarea
          v-model="editingYaml"
          rows="20"
          class="font-mono text-sm"
          :ui="{ base: 'font-mono' }"
        />
        
        <div class="flex justify-end gap-3 mt-6">
          <UButton color="neutral" variant="ghost" @click="showYamlEditModal = false">Cancel</UButton>
          <UButton color="primary" :loading="loading" @click="saveYamlEdits">
            Save Changes
          </UButton>
        </div>
      </div>
    </template>
  </UModal>
</template>
