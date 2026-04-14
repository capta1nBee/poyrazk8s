<script setup lang="ts">
import type { Pod } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()

const pods = ref<Pod[]>([])
const loading = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)
const includeDeleted = ref(false)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'phase', key: 'phase', label: 'Status', sortable: true },
  { id: 'restartCount', key: 'restartCount', label: 'Restarts', sortable: true },
  { id: 'createdAt', key: 'createdAt', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchPods = async () => {
  if (!selectedCluster.value) {
    return
  }
  
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

watch([selectedCluster, selectedNamespace, includeDeleted], () => {
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
	      <LegacyTable
        :rows="pods"
        :columns="columns"
        :loading="loading"
        class="w-full"
      >
        <template #phase-data="{ row }">
          <UBadge :color="getStatusColor(row.phase)" variant="subtle">
            {{ row.phase }}
          </UBadge>
        </template>

        <template #createdAt-data="{ row }">
          <span class="text-sm text-gray-600 dark:text-gray-400">
            {{ formatAge(row.createdAt) }}
          </span>
        </template>

       <template #actions-data="{ row }">
          <ResourceActionMenu :resource="row" kind="Pod" @refresh="fetchPods" />
       </template>
	      </LegacyTable>
    </template>
  </UDashboardPanel>

  <!-- YAML Modal -->

</template>

