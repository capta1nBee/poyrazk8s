<script setup lang="ts">
import type { PersistentVolume } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('PersistentVolume')

const pvs = ref<PersistentVolume[]>([])
const filteredPVs = computed(() => filterByName(pvs.value))
const loading = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'capacity', key: 'capacity.storage', label: 'Capacity', sortable: true },
  { id: 'accessModes', key: 'accessModes', label: 'Access Modes', sortable: true },
  { id: 'reclaimPolicy', key: 'persistentVolumeReclaimPolicy', label: 'Reclaim Policy', sortable: true },
  { id: 'status', key: 'phase', label: 'Status', sortable: true },
  { id: 'claim', key: 'claimRef', label: 'Claim', sortable: true },
  { id: 'storageClass', key: 'storageClassName', label: 'Storage Class', sortable: true },
  { id: 'age', key: 'k8sCreatedAt', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchPersistentVolumes = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    pvs.value = await k8s.fetchPersistentVolumes()
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch PersistentVolumes',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

// Actions handled by ResourceActionMenu

const handleRefresh = async () => {
  await fetchPersistentVolumes()
  toast.add({
    title: 'PersistentVolumes refreshed',
    color: 'green'
  })
}

const getStatusColor = (status: string) => {
  const statusMap: Record<string, string> = {
    Available: 'green',
    Bound: 'blue',
    Released: 'yellow',
    Failed: 'red'
  }
  return statusMap[status] || 'gray'
}

const formatAge = (timestamp: string) => {
  if (!timestamp) return '-'
  const now = new Date()
  const created = new Date(timestamp)
  const diffMs = now.getTime() - created.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMins / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffDays > 0) return `${diffDays}d`
  if (diffHours > 0) return `${diffHours}h`
  if (diffMins > 0) return `${diffMins}m`
  return 'Just now'
}

watch([selectedCluster, permissionsLoading], () => {
  fetchPersistentVolumes()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="persistentvolumes">
    <template #header>
      <UDashboardNavbar title="Persistent Volumes">
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
            @click="fetchPersistentVolumes"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UBadge color="neutral" variant="subtle" v-if="hasPermission('view')">
            {{ pvs.length }} volumes
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
          :rows="filteredPVs"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #capacity-data="{ row }">
            {{ row.capacity?.storage || '-' }}
          </template>

          <template #accessModes-data="{ row }">
            {{ Array.isArray(row.accessModes) ? row.accessModes.join(', ') : (row.accessModes || '-') }}
          </template>

          <template #status-data="{ row }">
            <UBadge :color="getStatusColor(row.phase)" variant="subtle">
              {{ row.phase }}
            </UBadge>
          </template>

          <template #claim-data="{ row }">
            {{ row.claimRef?.name || '-' }}
          </template>

          <template #storageClass-data="{ row }">
            {{ row.storageClassName || '-' }}
          </template>

          <template #age-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">
              {{ formatAge(row.k8sCreatedAt) }}
            </span>
          </template>

          <template #actions-data="{ row }">
             <ResourceActionMenu :resource="row" kind="PersistentVolume" @refresh="fetchPersistentVolumes" />
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Persistent Volumes in this cluster.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>
