<script setup lang="ts">
import type { ClusterRole } from '~/types/clusterrole'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('ClusterRole')

const resources = ref<ClusterRole[]>([])
const filteredResources = computed(() => filterByName(resources.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'age', key: 'k8sCreatedAt', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchData = async () => {
  if (!selectedCluster.value || !hasPermission('view')) return
  
  loading.value = true
  try {
    const result = await k8s.fetchResources<ClusterRole>('ClusterRole', undefined, includeDeleted.value)
    resources.value = result
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch ClusterRoles',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

watch([selectedCluster, includeDeleted], () => {
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
  <UDashboardPanel id="clusterroles">
    <template #header>
      <UDashboardNavbar title="Cluster Roles">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" square :loading="loading" @click="fetchData" />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar v-if="hasPermission('view')">
        <template #left>
          <UCheckbox v-model="includeDeleted" label="Include Deleted" color="neutral" class="mr-4" />
          <UBadge color="neutral" variant="subtle">{{ resources.length }} cluster roles</UBadge>
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
          <p class="text-gray-500">You do not have permission to view Cluster Roles.</p>
        </div>
      </template>
      <template v-else>
        <LegacyTable :rows="filteredResources" :columns="columns" :loading="loading" class="w-full">
          <template #age-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ useTimeAgo(row.k8sCreatedAt).value }}</span>
          </template>
          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="ClusterRole" @refresh="fetchData" />
          </template>
        </LegacyTable>
      </template>
    </template>
  </UDashboardPanel>
</template>
