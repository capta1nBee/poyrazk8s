<script setup lang="ts">
import type { PersistentVolumeClaim } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('PersistentVolumeClaim')

const pvcs = ref<PersistentVolumeClaim[]>([])
const filteredPVCs = computed(() => filterByName(pvcs.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'status', key: 'status', label: 'Status', sortable: true },
  { id: 'storageClass', key: 'storageClass', label: 'Storage Class', sortable: true },
  { id: 'requestedSize', key: 'requestedSize', label: 'Requested Size', sortable: true },
  { id: 'boundVolume', key: 'boundVolume', label: 'Bound Volume', sortable: true },
  { id: 'accessModes', key: 'accessModes', label: 'Access Modes', sortable: true },
  { id: 'k8sCreatedAt', key: 'k8sCreatedAt', label: 'Created At', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchPersistentVolumeClaims = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    pvcs.value = await k8s.fetchPersistentVolumeClaims(undefined, includeDeleted.value)
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch PersistentVolumeClaims',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

// Actions handled by ResourceActionMenu

const handleRefresh = async () => {
  await fetchPersistentVolumeClaims()
  toast.add({
    title: 'PersistentVolumeClaims refreshed',
    color: 'green'
  })
}

const getStatusColor = (status: string) => {
  const statusMap: Record<string, string> = {
    Bound: 'green',
    Pending: 'yellow',
    Lost: 'red'
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
  fetchPersistentVolumeClaims()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="persistentvolumeclaims">
    <template #header>
      <UDashboardNavbar title="Persistent Volume Claims">
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
            @click="fetchPersistentVolumeClaims"
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
          <UBadge color="neutral" variant="subtle" v-if="hasPermission('view')">
            {{ pvcs.length }} claims
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
          :rows="filteredPVCs"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #status-data="{ row }">
            <UBadge :color="getStatusColor(row.status)" variant="subtle">
              {{ row.status }}
            </UBadge>
          </template>

          <template #name-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }">
              {{ row.name }}
            </span>
          </template>

          <template #boundVolume-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }">
              {{ row.boundVolume || '-' }}
            </span>
          </template>

          <template #requestedSize-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }">
              {{ row.requestedSize || '-' }}
            </span>
          </template>

          <template #accessModes-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }">
              {{ typeof row.accessModes === 'string' ? row.accessModes : (row.accessModes?.join(', ') || '-') }}
            </span>
          </template>

          <template #storageClass-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }">
              {{ row.storageClass || '-' }}
            </span>
          </template>

          <template #k8sCreatedAt-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400" :class="{ 'opacity-50': row.isDeleted }">
              {{ formatDate(row.k8sCreatedAt) }}
            </span>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="PersistentVolumeClaim" @refresh="fetchPersistentVolumeClaims" />
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Persistent Volume Claims in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>

