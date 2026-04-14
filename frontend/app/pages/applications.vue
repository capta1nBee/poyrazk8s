<script setup lang="ts">
import type { Application } from '~/types/application'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading } = usePagePermissions('Application')

const resources = ref<Application[]>([])
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
  if (!selectedCluster.value || !hasPermission('view')) return
  
  loading.value = true
  try {
    const result = await k8s.fetchResources<Application>('Application', undefined, includeDeleted.value)
    resources.value = result
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch Applications',
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

// Reload data when permissions are loaded
watch(permissionsLoading, (loading) => {
  if (!loading && hasPermission('view')) {
    fetchData()
  }
})
</script>

<template>
  <UDashboardPanel id="applications">
    <template #header>
      <UDashboardNavbar title="Applications">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" square :loading="loading" @click="fetchData" />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar v-if="hasPermission('view')">
        <template #left>
          <NamespaceSelector />
          <div class="border-l border-gray-200 dark:border-gray-700 mx-2" />
          <UCheckbox v-model="includeDeleted" label="Include Deleted" color="neutral" class="mr-4" />
          <UBadge color="neutral" variant="subtle">{{ resources.length }} applications</UBadge>
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
          <p class="text-gray-500">You do not have permission to view Applications.</p>
        </div>
      </template>
      <template v-else>
        <LegacyTable :rows="resources" :columns="columns" :loading="loading" class="w-full">
          <template #age-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ useTimeAgo(row.k8sCreatedAt).value }}</span>
          </template>
          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="Application" @refresh="fetchData" />
          </template>
        </LegacyTable>
      </template>
    </template>
  </UDashboardPanel>
</template>
