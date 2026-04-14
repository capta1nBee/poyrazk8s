<script setup lang="ts">
import type { Job } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('Job')

const jobs = ref<Job[]>([])
const filteredJobs = computed(() => filterByName(jobs.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'completions', key: 'completions', label: 'Completions', sortable: true },
  { id: 'duration', key: 'duration', label: 'Duration', sortable: true },
  { id: 'age', key: 'age', label: 'Age', sortable: true },
  { id: 'status', key: 'status', label: 'Status', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchJobs = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    const data = await k8s.fetchJobs(undefined, includeDeleted.value)
    jobs.value = data || []
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch jobs',
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
    Complete: 'green',
    Failed: 'red',
    Running: 'blue',
    Pending: 'yellow'
  }
  return statusMap[status] || 'gray'
}

watch([selectedCluster, selectedNamespace, includeDeleted, permissionsLoading], () => {
  fetchJobs()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="jobs">
    <template #header>
      <UDashboardNavbar title="Jobs">
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
            @click="fetchJobs"
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
            {{ jobs.length }} jobs
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
          :rows="filteredJobs"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #status-data="{ row }">
            <UBadge :color="getStatusColor(row.status)" variant="subtle">
              {{ row.status }}
            </UBadge>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="Job" @refresh="fetchJobs" />
          </template>
	      </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Jobs in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>

