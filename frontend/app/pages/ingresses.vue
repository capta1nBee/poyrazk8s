<script setup lang="ts">
import type { Ingress } from '~/types/ingress'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('Ingress')

const ingresses = ref<Ingress[]>([])
const filteredIngresses = computed(() => filterByName(ingresses.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'ingressClass', key: 'ingressClass', label: 'Class', sortable: true },
  { id: 'hosts', key: 'hosts', label: 'Hosts' },
  { id: 'address', key: 'address', label: 'Address' },
  { id: 'age', key: 'age', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchIngresses = async () => {
  if (!selectedCluster.value) {
    return
  }
  if (permissionsLoading.value) {
    return
  }
  if (!hasPermission('view')) {
    return
  }

  loading.value = true
  try {
    const result = await k8s.fetchIngresses(undefined, includeDeleted.value)

    ingresses.value = (result || []).map((ing: Ingress) => ({
      ...ing,
      age: formatAge(ing.createdAt || ing.age || ''),
      // Ensure hosts and address are strings for rendering
      hosts: Array.isArray(ing.hosts) ? ing.hosts.join(', ') : (ing.hosts || '-'),
      address: formatAddress(ing.address)
    }))
  } catch (error: any) {
    console.error('Fetch ingresses error:', error)
    toast.add({
      title: 'Failed to fetch ingresses',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const formatAddress = (address: any) => {
  if (!address) return 'Pending'
  if (Array.isArray(address)) {
    return address.map(a => a.ip || a.hostname || JSON.stringify(a)).join(', ')
  }
  return typeof address === 'object' ? JSON.stringify(address) : address
}

const formatAge = (createdAt: string) => {
  if (!createdAt) return '-'
  const now = new Date()
  const created = new Date(createdAt)
  
  // Basic validation
  if (isNaN(created.getTime())) return createdAt

  const diff = now.getTime() - created.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
  
  if (days > 0) return `${days}d`
  if (hours > 0) return `${hours}h`
  return '<1h'
}

watch([selectedCluster, selectedNamespace, includeDeleted, permissionsLoading], () => {
  fetchIngresses()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="ingresses">
    <template #header>
      <UDashboardNavbar title="Ingresses">
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
            @click="fetchIngresses"
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
      <div v-if="permissionsLoading || loading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-primary" />
      </div>

      <template v-else-if="hasPermission('view')">
        <div v-if="!selectedCluster" class="flex flex-col items-center justify-center h-64 text-center">
          <UIcon name="i-lucide-alert-circle" class="w-16 h-16 text-gray-400 mb-4" />
          <p class="text-gray-500">Please select a cluster first</p>
        </div>

        <div v-else-if="ingresses.length === 0" class="flex flex-col items-center justify-center h-64 text-center">
          <UIcon name="i-lucide-globe" class="w-16 h-16 text-gray-400 mb-4" />
          <p class="text-gray-500">No ingresses found</p>
        </div>

        <LegacyTable v-else :columns="columns" :rows="filteredIngresses">
          <template #name-data="{ row }">
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-globe" class="w-4 h-4 text-blue-500" />
              <span class="font-medium">{{ row.name }}</span>
            </div>
          </template>

          <template #ingressClass-data="{ row }">
            <UBadge v-if="row.ingressClass" color="blue" variant="subtle">
              {{ row.ingressClass }}
            </UBadge>
            <span v-else class="text-gray-400">-</span>
          </template>

          <template #hosts-data="{ row }">
            <span class="text-sm">{{ row.hosts || '-' }}</span>
          </template>

          <template #address-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ row.address || 'Pending' }}</span>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="Ingress" @refresh="fetchIngresses" />
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Ingresses in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>
