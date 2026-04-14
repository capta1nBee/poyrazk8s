<script setup lang="ts">
const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('Node')

const nodes = ref<any[]>([])
const filteredNodes = computed(() => filterByName(nodes.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'status', key: 'status', label: 'Status', sortable: true },
  { id: 'roles', key: 'roles', label: 'Roles', sortable: true },
  { id: 'version', key: 'kubeletVersion', label: 'Version', sortable: true },
  { id: 'age', key: 'k8sCreatedAt', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const getStatusColor = (status: string) => status === 'Ready' ? 'success' : 'error'

const fetchNodes = async () => {
  if (!selectedCluster.value || !hasPermission('view')) return
  loading.value = true
  try {
    const result = await k8s.fetchResources<any>('Node', undefined, includeDeleted.value)
    nodes.value = result.map((node: any) => ({
      ...node,
      roles: node.roles
        ? String(node.roles).split(',').map((r: string) => r.trim()).filter(Boolean)
        : ['<none>']
    }))
  } catch (error: any) {
    toast.add({ title: 'Failed to fetch Nodes', description: error.response?.data?.message || error.message, color: 'red' })
  } finally {
    loading.value = false
  }
}

watch([selectedCluster, includeDeleted, permissionsLoading], () => {
  fetchNodes()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="nodes">
    <template #header>
      <UDashboardNavbar title="Nodes">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" square :loading="loading" @click="fetchNodes" />
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar v-if="hasPermission('view')">
        <template #left>
          <UCheckbox v-model="includeDeleted" label="Include Deleted" color="neutral" class="mr-4" />
          <UBadge color="neutral" variant="subtle">{{ nodes.length }} nodes</UBadge>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="permissionsLoading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>
      <template v-else-if="hasPermission('view')">
        <LegacyTable :rows="filteredNodes" :columns="columns" :loading="loading" class="w-full">
          <template #status-data="{ row }">
            <UBadge :color="getStatusColor(row.status)" variant="subtle">{{ row.status }}</UBadge>
          </template>
          <template #roles-data="{ row }">
            <div class="flex flex-wrap gap-1">
              <UBadge v-for="role in (row.roles || []).slice(0, 3)" :key="role" color="neutral" variant="soft" size="xs">{{ role }}</UBadge>
            </div>
          </template>
          <template #version-data="{ row }">
            <span class="text-xs font-mono text-gray-500">{{ row.kubeletVersion }}</span>
          </template>
          <template #age-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ useTimeAgo(row.k8sCreatedAt).value }}</span>
          </template>
          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="Node" @refresh="fetchNodes" />
          </template>
        </LegacyTable>
      </template>
      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium">Access Denied</h3>
        <p class="text-sm text-gray-500 mt-1">You do not have permission to view Nodes.</p>
      </div>
    </template>
  </UDashboardPanel>
</template>
