<script setup lang="ts">
import type { EndpointSlice } from '~/types/endpointslice'

const clusterStore = useClusterStore()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('EndpointSlice')

const endpointSlices = ref<EndpointSlice[]>([])
const filteredEndpointSlices = computed(() => filterByName(endpointSlices.value))
const loading = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'serviceName', key: 'serviceName', label: 'Service' },
  { id: 'addressType', key: 'addressType', label: 'Address Type' },
  { id: 'endpoints', key: 'endpoints', label: 'Endpoints' },
  { id: 'ports', key: 'ports', label: 'Ports' },
  { id: 'age', key: 'age', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchEndpointSlices = async () => {
  if (!selectedCluster.value || !hasPermission('view')) return

  loading.value = true
  try {
    const endpoint = selectedNamespace.value 
      ? `/api/clusters/${selectedCluster.value.uid}/namespaces/${selectedNamespace.value}/endpointslices`
      : `/api/clusters/${selectedCluster.value.uid}/endpointslices`
    
    const { data } = await useFetch(endpoint)
    endpointSlices.value = (data.value || []).map((es: any) => ({
      ...es,
      name: es.metadata?.name || es.name,
      namespace: es.metadata?.namespace || es.namespace,
      serviceName: es.metadata?.labels?.['kubernetes.io/service-name'] || '-',
      addressType: es.addressType || 'IPv4',
      endpoints: es.endpoints?.length || 0,
      ports: es.ports?.length || 0,
      age: formatAge(es.metadata?.creationTimestamp || es.createdAt || '')
    }))
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch endpoint slices',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
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

watch([selectedCluster, selectedNamespace], () => {
  if (!permissionsLoading.value) {
    fetchEndpointSlices()
  }
}, { immediate: true })

// Reload data when permissions are loaded
watch(permissionsLoading, (loading) => {
  if (!loading && hasPermission('view')) {
    fetchEndpointSlices()
  }
})
</script>

<template>
  <UDashboardPanel id="endpointslices">
    <template #header>
      <UDashboardNavbar title="Endpoint Slices">
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
            @click="fetchEndpointSlices"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar v-if="hasPermission('view')">
        <template #left>
          <NamespaceSelector />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <template v-if="permissionsLoading">
        <div class="flex items-center justify-center h-full">
          <ULoader />
        </div>
      </template>
      <template v-else-if="!hasPermission('view')">
        <div class="flex flex-col items-center justify-center h-full space-y-4">
          <UIcon name="i-lucide-shield-off" class="w-12 h-12 text-gray-400" />
          <p class="text-gray-500">You do not have permission to view Endpoint Slices.</p>
        </div>
      </template>
      <template v-else>
        <div v-if="loading" class="flex items-center justify-center h-64">
          <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-primary" />
        </div>

        <div v-else-if="!selectedCluster" class="flex flex-col items-center justify-center h-64 text-center">
          <UIcon name="i-lucide-alert-circle" class="w-16 h-16 text-gray-400 mb-4" />
          <p class="text-gray-500">Please select a cluster first</p>
        </div>

        <div v-else-if="endpointSlices.length === 0" class="flex flex-col items-center justify-center h-64 text-center">
          <UIcon name="i-lucide-network" class="w-16 h-16 text-gray-400 mb-4" />
          <p class="text-gray-500">No endpoint slices found</p>
        </div>

        <LegacyTable v-else :columns="columns" :rows="filteredEndpointSlices">
          <template #name-data="{ row }">
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-network" class="w-4 h-4 text-purple-500" />
              <span class="font-medium">{{ row.name }}</span>
            </div>
          </template>

          <template #serviceName-data="{ row }">
            <UBadge v-if="row.serviceName !== '-'" color="blue" variant="subtle">
              {{ row.serviceName }}
            </UBadge>
            <span v-else class="text-gray-400">-</span>
          </template>

          <template #addressType-data="{ row }">
            <UBadge color="gray" variant="subtle" size="xs">
              {{ row.addressType }}
            </UBadge>
          </template>

          <template #endpoints-data="{ row }">
            <div class="flex items-center gap-1">
              <UIcon name="i-lucide-circle" class="w-3 h-3 text-green-500" />
              <span class="text-sm">{{ row.endpoints }}</span>
            </div>
          </template>

          <template #ports-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ row.ports }}</span>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="EndpointSlice" @refresh="fetchEndpointSlices" />
          </template>
        </LegacyTable>
      </template>
    </template>
  </UDashboardPanel>
</template>
