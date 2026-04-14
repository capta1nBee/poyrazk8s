<script setup lang="ts">
import type { ReplicaSet } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('ReplicaSet')

const replicaSets = ref<ReplicaSet[]>([])
const filteredReplicaSets = computed(() => filterByName(replicaSets.value))
const loading = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)
const includeDeleted = ref(false)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'desired', key: 'replicas', label: 'Desired', sortable: true },
  { id: 'current', key: 'readyReplicas', label: 'Current', sortable: true },
  { id: 'k8sCreatedAt', key: 'k8sCreatedAt', label: 'Created At', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchReplicaSets = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    const result = await k8s.fetchReplicaSets(undefined, includeDeleted.value)
    replicaSets.value = result || []
  } catch (error: any) {
    console.error('Fetch replicasets error:', error)
    toast.add({
      title: 'Failed to fetch ReplicaSets',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
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

watch([selectedCluster, selectedNamespace, includeDeleted, permissionsLoading], () => {
  fetchReplicaSets()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="replicasets">
    <template #header>
      <UDashboardNavbar title="ReplicaSets">
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
            @click="fetchReplicaSets"
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
            {{ replicaSets.length }} replicasets
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
          :rows="filteredReplicaSets"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #name-data="{ row }">
             <span class="font-medium text-gray-900 dark:text-white">{{ row.name }}</span>
          </template>

          <template #current-data="{ row }">
             <span>{{ row.readyReplicas || 0 }}</span>
          </template>
          
          <template #age-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">
              {{ formatAge(row.k8sCreatedAt) }}
            </span>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="ReplicaSet" @refresh="fetchReplicaSets" />
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view ReplicaSets in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>
