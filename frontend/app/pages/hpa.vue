<script setup lang="ts">
const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('HorizontalPodAutoscaler')

const hpas = ref<any[]>([])
const filteredHpas = computed(() => filterByName(hpas.value))
const loading = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'targetKind', key: 'targetKind', label: 'Target Kind', sortable: true },
  { id: 'targetName', key: 'targetName', label: 'Target Name', sortable: true },
  { id: 'minReplicas', key: 'minReplicas', label: 'Min', sortable: true },
  { id: 'maxReplicas', key: 'maxReplicas', label: 'Max', sortable: true },
  { id: 'currentReplicas', key: 'currentReplicas', label: 'Current', sortable: true },
  { id: 'desiredReplicas', key: 'desiredReplicas', label: 'Desired', sortable: true },
  { id: 'k8sCreatedAt', key: 'k8sCreatedAt', label: 'Created At', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchHpas = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return

  loading.value = true
  try {
    const result = await k8s.fetchHpas()
    hpas.value = result || []
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch HPAs',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

watch([selectedCluster, selectedNamespace, permissionsLoading], () => {
  fetchHpas()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="hpa">
    <template #header>
      <UDashboardNavbar title="Horizontal Pod Autoscalers">
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
            @click="fetchHpas"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <NamespaceSelector />
          <div class="border-l border-gray-200 dark:border-gray-700 mx-2" />
          <UBadge color="neutral" variant="subtle" v-if="hasPermission('view')">
            {{ hpas.length }} HPAs
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
          :rows="filteredHpas"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #targetKind-data="{ row }">
            <UBadge color="primary" variant="subtle" size="sm">
              {{ row.targetKind || '-' }}
            </UBadge>
          </template>

          <template #targetName-data="{ row }">
            <span class="text-sm font-medium">{{ row.targetName || '-' }}</span>
          </template>

          <template #currentReplicas-data="{ row }">
            <span :class="row.currentReplicas !== row.desiredReplicas ? 'text-amber-500' : 'text-green-600 dark:text-green-400'">
              {{ row.currentReplicas ?? '-' }}
            </span>
          </template>

          <template #desiredReplicas-data="{ row }">
            <span class="text-sm">{{ row.desiredReplicas ?? '-' }}</span>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="HorizontalPodAutoscaler" @refresh="fetchHpas" />
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view HPAs in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>
