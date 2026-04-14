<script setup lang="ts">
import type { Pod } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('Pod')

const pods = ref<Pod[]>([])
const filteredPods = computed(() => filterByName(pods.value))
const loading = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)
const includeDeleted = ref(false)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'phase', key: 'phase', label: 'Status', sortable: true },
  { id: 'nodeName', key: 'nodeName', label: 'Node', sortable: true },
  { id: 'owner', key: 'owner', label: 'Owner', sortable: true },
  { id: 'restartCount', key: 'restartCount', label: 'Restarts', sortable: true },
  { id: 'k8sCreatedAt', key: 'k8sCreatedAt', label: 'Created At', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchPods = async () => {
  if (!selectedCluster.value) {
    return
  }
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    const result = await k8s.fetchPods(undefined, includeDeleted.value)
    pods.value = result || []
  } catch (error: any) {
    console.error('Fetch pods error:', error)
    toast.add({
      title: 'Failed to fetch pods',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

// Actions handled by ResourceActionMenu

const getStatusColor = (status: string) => {
  const statusMap: Record<string, string> = {
    Running: 'green',
    Pending: 'yellow',
    Succeeded: 'blue',
    Failed: 'red',
    Unknown: 'gray'
  }
  return statusMap[status] || 'gray'
}

const formatDate = (timestamp: string) => {
  if (!timestamp) return '-'
  try {
    const date = new Date(timestamp)
    return date.toLocaleString()
  } catch {
    return timestamp
  }
}

watch([selectedCluster, selectedNamespace, includeDeleted, permissionsLoading], () => {
  fetchPods()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="pods">
    <template #header>
      <UDashboardNavbar title="Pods">
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
            @click="fetchPods"
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
          <UBadge color="neutral" variant="subtle">
            {{ pods.length }} pods
          </UBadge>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="permissionsLoading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>
      
      <template v-else-if="hasPermission('view')">
	      <LegacyTable
          :rows="filteredPods"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #name-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }">
              {{ row.name }}
            </span>
          </template>

          <template #phase-data="{ row }">
            <UBadge :color="getStatusColor(row.phase)" variant="subtle" :class="{ 'opacity-50': row.isDeleted }">
              {{ row.phase }}
            </UBadge>
          </template>

          <template #nodeName-data="{ row }">
            <span class="text-sm" :class="{ 'opacity-50': row.isDeleted }">
              {{ row.nodeName || '-' }}
            </span>
          </template>

          <template #owner-data="{ row }">
            <span class="text-sm" :class="{ 'opacity-50': row.isDeleted }">
              {{ row.owner || '-' }}
            </span>
          </template>

          <template #k8sCreatedAt-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400" :class="{ 'opacity-50': row.isDeleted }">
              {{ formatDate(row.k8sCreatedAt) }}
            </span>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="Pod" @refresh="fetchPods" />
          </template>
	      </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Pods in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>

  <!-- YAML Modal -->
