<script setup lang="ts">
const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('StorageClass')

const resources = ref<any[]>([])
const filteredResources = computed(() => filterByName(resources.value))
const loading = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'provisioner', key: 'provisioner', label: 'Provisioner', sortable: true },
  { id: 'reclaimPolicy', key: 'reclaimPolicy', label: 'Reclaim Policy', sortable: true },
  { id: 'volumeBindingMode', key: 'volumeBindingMode', label: 'Binding Mode', sortable: true },
  { id: 'allowVolumeExpansion', key: 'allowVolumeExpansion', label: 'Expandable', sortable: false },
  { id: 'age', key: 'k8sCreatedAt', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchData = async () => {
  if (!selectedCluster.value || !hasPermission('view')) return

  loading.value = true
  try {
    const result = await k8s.fetchResources<any>('StorageClass')
    resources.value = result
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch StorageClasses',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

watch([selectedCluster], () => {
  fetchData()
}, { immediate: true })

watch(permissionsLoading, (isLoading) => {
  if (!isLoading && hasPermission('view')) {
    fetchData()
  }
})
</script>

<template>
  <UDashboardPanel id="storageclasses">
    <template #header>
      <UDashboardNavbar title="Storage Classes">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" square :loading="loading" @click="fetchData" />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar v-if="hasPermission('view')">
        <template #left>
          <UBadge color="neutral" variant="subtle">{{ resources.length }} classes</UBadge>
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
          <p class="text-gray-500">You do not have permission to view Storage Classes.</p>
        </div>
      </template>
      <template v-else>
        <LegacyTable :rows="filteredResources" :columns="columns" :loading="loading" class="w-full">
          <template #provisioner-data="{ row }">
            <span class="font-mono text-xs">{{ row.provisioner }}</span>
          </template>
          <template #reclaimPolicy-data="{ row }">
            <UBadge :color="row.reclaimPolicy === 'Retain' ? 'green' : 'neutral'" variant="subtle" size="sm">
              {{ row.reclaimPolicy || 'Delete' }}
            </UBadge>
          </template>
          <template #volumeBindingMode-data="{ row }">
            <UBadge color="blue" variant="subtle" size="sm">{{ row.volumeBindingMode || 'Immediate' }}</UBadge>
          </template>
          <template #allowVolumeExpansion-data="{ row }">
            <UIcon
              :name="row.allowVolumeExpansion ? 'i-lucide-check-circle' : 'i-lucide-x-circle'"
              :class="row.allowVolumeExpansion ? 'text-green-500' : 'text-gray-400'"
              class="w-5 h-5"
            />
          </template>
          <template #age-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ useTimeAgo(row.k8sCreatedAt).value }}</span>
          </template>
          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="StorageClass" @refresh="fetchData" />
          </template>
        </LegacyTable>
      </template>
    </template>
  </UDashboardPanel>
</template>
