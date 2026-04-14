<script setup lang="ts">
import type { NetworkPolicy } from '~/types/networkpolicy'

const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('NetworkPolicy')

const networkPolicies = ref<NetworkPolicy[]>([])
const filteredNetworkPolicies = computed(() => filterByName(networkPolicies.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'podSelector', key: 'podSelector', label: 'Pod Selector' },
  { id: 'policyTypes', key: 'policyTypes', label: 'Policy Types' },
  { id: 'age', key: 'age', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchNetworkPolicies = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return

  loading.value = true
  try {
    const endpoint = selectedNamespace.value 
      ? `/k8s/${selectedCluster.value.uid}/namespaces/${selectedNamespace.value}/networkpolicies`
      : `/k8s/${selectedCluster.value.uid}/networkpolicies`
    
    const response = await $api.get(endpoint)
    const data = response.data || []
    networkPolicies.value = data.map((np: any) => ({
      ...np,
      name: np.metadata?.name || np.name,
      namespace: np.metadata?.namespace || np.namespace,
      podSelector: formatPodSelector(np.spec?.podSelector),
      policyTypes: np.spec?.policyTypes?.join(', ') || '-',
      age: formatAge(np.metadata?.creationTimestamp || np.createdAt || '')
    }))
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch network policies',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const formatPodSelector = (selector: any) => {
  if (!selector || Object.keys(selector.matchLabels || {}).length === 0) {
    return 'All pods'
  }
  const labels = Object.entries(selector.matchLabels || {})
    .map(([k, v]) => `${k}=${v}`)
    .join(', ')
  return labels || '-'
}

const formatAge = (createdAt: string) => {
  if (!createdAt) return '-'
  const now = new Date()
  const created = new Date(createdAt)
  const diff = now.getTime() - created.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
  
  if (days > 0) return `${days}d`
  if (hours > 0) return `${hours}h`
  return '<1h'
}

watch([selectedCluster, selectedNamespace, includeDeleted, permissionsLoading], () => {
  fetchNetworkPolicies()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="networkpolicies">
    <template #header>
      <UDashboardNavbar title="Network Policies">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="ghost"
            square
            :loading="loading"
            @click="fetchNetworkPolicies"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <NamespaceSelector />
          <div class="border-l border-gray-200 dark:border-gray-700 mx-2" />
          <UCheckbox 
            v-model="includeDeleted" 
            label="Include Deleted" 
            color="neutral"
            class="mr-4"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="permissionsLoading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>

      <template v-else-if="hasPermission('view')">
        <div v-if="!selectedCluster" class="flex flex-col items-center justify-center h-64 text-center">
          <UIcon name="i-lucide-alert-circle" class="w-16 h-16 text-gray-400 mb-4" />
          <p class="text-gray-500">Please select a cluster first</p>
        </div>

        <div v-else-if="networkPolicies.length === 0" class="flex flex-col items-center justify-center h-64 text-center">
          <UIcon name="i-lucide-shield" class="w-16 h-16 text-gray-400 mb-4" />
          <p class="text-gray-500">No network policies found</p>
        </div>

        <LegacyTable v-else :columns="columns" :rows="filteredNetworkPolicies">
          <template #name-data="{ row }">
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-shield" class="w-4 h-4 text-green-500" />
              <span class="font-medium">{{ row.name }}</span>
            </div>
          </template>

          <template #podSelector-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ row.podSelector }}</span>
          </template>

          <template #policyTypes-data="{ row }">
            <div class="flex gap-1">
              <UBadge 
                v-for="type in (row.policyTypes || '').split(', ').filter(Boolean)" 
                :key="type"
                color="blue" 
                variant="subtle"
                size="xs"
              >
                {{ type }}
              </UBadge>
              <span v-if="!row.policyTypes || row.policyTypes === '-'" class="text-gray-400">-</span>
            </div>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="NetworkPolicy" @refresh="fetchNetworkPolicies" />
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Network Policies in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>
