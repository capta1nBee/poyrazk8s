<script setup lang="ts">
import type { DaemonSet } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('DaemonSet')

const daemonSets = ref<DaemonSet[]>([])
const filteredDaemonsets = computed(() => filterByName(daemonSets.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'desiredPods', key: 'desiredNumberScheduled', label: 'Desired', sortable: true },
  { id: 'currentPods', key: 'currentNumberScheduled', label: 'Current', sortable: true },
  { id: 'readyPods', key: 'numberReady', label: 'Ready', sortable: true },
  { id: 'numberAvailable', key: 'numberAvailable', label: 'Available', sortable: true },
  { id: 'k8sCreatedAt', key: 'k8sCreatedAt', label: 'Created At', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchDaemonSets = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    daemonSets.value = await k8s.fetchDaemonSets(undefined, includeDeleted.value)
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch daemonsets',
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
  fetchDaemonSets()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="daemonsets">
    <template #header>
      <UDashboardNavbar title="DaemonSets">
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
            @click="fetchDaemonSets"
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
            {{ daemonSets.length }} daemonsets
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
          :rows="filteredDaemonsets"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #name-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }">
              {{ row.name }}
            </span>
          </template>

          <template #k8sCreatedAt-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400" :class="{ 'opacity-50': row.isDeleted }">
              {{ formatDate(row.k8sCreatedAt) }}
            </span>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="DaemonSet" @refresh="fetchDaemonSets" />
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view DaemonSets in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>
