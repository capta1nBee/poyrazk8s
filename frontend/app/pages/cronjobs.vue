<script setup lang="ts">
import type { CronJob } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('CronJob')

const cronJobs = ref<CronJob[]>([])
const filteredCronJobs = computed(() => filterByName(cronJobs.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'schedule', key: 'schedule', label: 'Schedule', sortable: true },
  { id: 'suspend', key: 'suspend', label: 'Suspended', sortable: true },
  { id: 'lastScheduleTime', key: 'lastScheduleTime', label: 'Last Schedule', sortable: true },
  { id: 'k8sCreatedAt', key: 'k8sCreatedAt', label: 'Created At', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchCronJobs = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    cronJobs.value = await k8s.fetchCronJobs(undefined, includeDeleted.value)
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch cronjobs',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
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
  fetchCronJobs()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="cronjobs">
    <template #header>
      <UDashboardNavbar title="CronJobs">
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
            @click="fetchCronJobs"
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
            {{ cronJobs.length }} cronjobs
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
          :rows="filteredCronJobs"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #name-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }">
              {{ row.name }}
            </span>
          </template>

          <template #suspend-data="{ row }">
            <UBadge :color="row.suspend ? 'yellow' : 'green'" variant="subtle" :class="{ 'opacity-50': row.isDeleted }">
              {{ row.suspend ? 'Yes' : 'No' }}
            </UBadge>
          </template>

          <template #lastScheduleTime-data="{ row }">
            <span class="text-sm" :class="{ 'opacity-50': row.isDeleted }">
              {{ formatDate(row.lastScheduleTime) }}
            </span>
          </template>

          <template #k8sCreatedAt-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400" :class="{ 'opacity-50': row.isDeleted }">
              {{ formatDate(row.k8sCreatedAt) }}
            </span>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="CronJob" @refresh="fetchCronJobs" />
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view CronJobs in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>
