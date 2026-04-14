<script setup lang="ts">
import type { ReplicationController } from '~/types/replicationcontroller'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('ReplicationController')

const resources = ref<ReplicationController[]>([])
const filteredResources = computed(() => filterByName(resources.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'age', key: 'k8sCreatedAt', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchData = async () => {
  if (!selectedCluster.value) return
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    const result = await k8s.fetchResources<ReplicationController>('ReplicationController', undefined, includeDeleted.value)
    resources.value = result
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch ReplicationControllers',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

watch([selectedCluster, selectedNamespace, includeDeleted], () => {
  fetchData()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="replicationcontrollers">
    <template #header>
      <UDashboardNavbar title="Replication Controllers">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" square :loading="loading" @click="fetchData" />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <NamespaceSelector />
          <div class="border-l border-gray-200 dark:border-gray-700 mx-2" />
          <UCheckbox v-model="includeDeleted" label="Include Deleted" color="neutral" class="mr-4" />
          <UBadge color="neutral" variant="subtle" v-if="hasPermission('view')">{{ resources.length }} replication controllers</UBadge>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="permissionsLoading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>

      <template v-else-if="hasPermission('view')">
        <LegacyTable :rows="filteredResources" :columns="columns" :loading="loading" class="w-full">
          <template #age-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ useTimeAgo(row.k8sCreatedAt).value }}</span>
          </template>
          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="ReplicationController" @refresh="fetchData" />
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view ReplicationControllers in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>
