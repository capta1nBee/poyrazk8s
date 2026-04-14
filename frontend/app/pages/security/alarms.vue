<script setup lang="ts">
interface SecurityAlert {
  id: number
  clusterUid: string
  eventType: string
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  ruleName: string
  output: string
  namespaceName: string
  podName: string
  isAcknowledged: boolean
  acknowledgedBy: string
  acknowledgedAt: string
  resolved: boolean
  resolvedBy: string
  resolvedAt: string
  createdAt: string
}

const clusterStore = useClusterStore()
const { $api } = useNuxtApp()
const toast = useToast()
const { hasPermission, loading: permissionsLoading } = usePagePermissions('SecurityAlert')

const alerts = ref<SecurityAlert[]>([])
const loading = ref(false)
const selectedAlert = ref<SecurityAlert | null>(null)
const isDetailModalOpen = ref(false)
const isAcknowledgeModalOpen = ref(false)
const isResolveModalOpen = ref(false)

// Filters
const filterStatus = ref<'all' | 'pending' | 'acknowledged' | 'resolved'>('all')
const filterPriority = ref<string | null>(null)
const filterNamespace = ref<string | null>(null)
const filterPod = ref<string | null>(null)
const searchTerm = ref('')

const selectedCluster = computed(() => clusterStore.selectedCluster)

const columns = [
  { id: 'priority', key: 'priority', label: 'Priority', sortable: true },
  { id: 'ruleName', key: 'ruleName', label: 'Rule' },
  { id: 'output', key: 'output', label: 'Alert' },
  { id: 'podName', key: 'podName', label: 'Pod' },
  { id: 'namespaceName', key: 'namespaceName', label: 'Namespace' },
  { id: 'eventType', key: 'eventType', label: 'Event Type' },
  { id: 'status', key: 'status', label: 'Status' },
  { id: 'createdAt', key: 'createdAt', label: 'Time' },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const statuses = ['all', 'pending', 'acknowledged', 'resolved']

const priorityBadgeColor = (priority: string) => {
  const colors = {
    'CRITICAL': 'red',
    'HIGH': 'orange',
    'MEDIUM': 'yellow',
    'LOW': 'green'
  }
  return colors[priority] || 'gray'
}

const getAlertStatus = (alert: SecurityAlert) => {
  if (alert.resolved) return 'Resolved'
  if (alert.isAcknowledged) return 'Acknowledged'
  return 'Pending'
}

const getStatusColor = (alert: SecurityAlert) => {
  if (alert.resolved) return 'green'
  if (alert.isAcknowledged) return 'blue'
  return 'red'
}

const fetchAlerts = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return

  loading.value = true
  try {
    const params: any = {
      clusterUid: selectedCluster.value.uid
    }

    if (filterStatus.value === 'pending') {
      params.acknowledged = false
      params.resolved = false
    } else if (filterStatus.value === 'acknowledged') {
      params.acknowledged = true
      params.resolved = false
    } else if (filterStatus.value === 'resolved') {
      params.resolved = true
    }

    if (filterPriority.value) {
      params.priority = filterPriority.value
    }
    if (filterNamespace.value) {
      params.namespaceName = filterNamespace.value
    }
    if (filterPod.value) {
      params.podName = filterPod.value
    }
    if (searchTerm.value) {
      params.search = searchTerm.value
    }

    const response = await $api.get<any>('/security/alerts/search', {
      params
    })

    alerts.value = Array.isArray(response.data) ? response.data : response.data.content || []
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch alerts',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const acknowledgeAlert = async (note: string) => {
  if (!selectedAlert.value) return

  try {
    await $api.patch(`/security/alerts/${selectedAlert.value.id}/acknowledge`, { note })

    toast.add({
      title: 'Success',
      description: 'Alert acknowledged',
      color: 'green'
    })

    isAcknowledgeModalOpen.value = false
    selectedAlert.value = null
    fetchAlerts()
  } catch (error: any) {
    toast.add({
      title: 'Failed to acknowledge alert',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  }
}

const resolveAlert = async (note: string) => {
  if (!selectedAlert.value) return

  try {
    await $api.patch(`/security/alerts/${selectedAlert.value.id}/resolve`, { note })

    toast.add({
      title: 'Success',
      description: 'Alert resolved',
      color: 'green'
    })

    isResolveModalOpen.value = false
    selectedAlert.value = null
    fetchAlerts()
  } catch (error: any) {
    toast.add({
      title: 'Failed to resolve alert',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  }
}

const totalAlerts = computed(() => alerts.value.length)
const pendingCount = computed(() => {
  return alerts.value.filter(a => !a.isAcknowledged && !a.resolved).length
})

const criticalCount = computed(() => {
  return alerts.value.filter(a => a.priority === 'CRITICAL').length
})

watch([selectedCluster, permissionsLoading], () => {
  fetchAlerts()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="security-alarms">
    <template #header>
      <UDashboardNavbar title="Security Alarms">
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
            @click="fetchAlerts"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div v-if="permissionsLoading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>

      <template v-else-if="hasPermission('view')">
        <!-- Stats Cards -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6 p-4">
          <div class="border rounded-lg p-4 dark:border-gray-700 bg-red-50 dark:bg-red-900/20">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-gray-600 dark:text-gray-400">Pending Alerts</p>
                <p class="text-3xl font-bold text-red-600">{{ pendingCount }}</p>
              </div>
              <UIcon name="i-lucide-alert-circle" class="w-8 h-8 text-red-600" />
            </div>
          </div>

          <div class="border rounded-lg p-4 dark:border-gray-700 bg-orange-50 dark:bg-orange-900/20">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-gray-600 dark:text-gray-400">Critical Alerts</p>
                <p class="text-3xl font-bold text-orange-600">{{ criticalCount }}</p>
              </div>
              <UIcon name="i-lucide-zap" class="w-8 h-8 text-orange-600" />
            </div>
          </div>

          <div class="border rounded-lg p-4 dark:border-gray-700 bg-blue-50 dark:bg-blue-900/20">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm text-gray-600 dark:text-gray-400">Total Alerts</p>
                <p class="text-3xl font-bold text-blue-600">{{ totalAlerts }}</p>
              </div>
              <UIcon name="i-lucide-list" class="w-8 h-8 text-blue-600" />
            </div>
          </div>
        </div>

        <!-- Filters -->
        <UDashboardToolbar>
          <template #left>
            <UInput
              v-model="searchTerm"
              placeholder="Search..."
              icon="i-lucide-search"
              size="sm"
              :ui="{ base: 'min-w-[200px]' }"
              @update:model-value="fetchAlerts"
            />
            <div class="border-l border-gray-200 dark:border-gray-700 mx-2 h-6" />
            <UDropdownMenu
              :items="[[...statuses.map(s => ({
                label: s.charAt(0).toUpperCase() + s.slice(1),
                icon: filterStatus === s ? 'i-lucide-check' : undefined,
                onSelect: () => { filterStatus = s; fetchAlerts() }
              }))]]"
              :popper="{ placement: 'bottom-start' }"
            >
              <UButton
                :label="filterStatus.charAt(0).toUpperCase() + filterStatus.slice(1)"
                icon="i-lucide-filter"
                trailing-icon="i-lucide-chevron-down"
                color="neutral"
                variant="ghost"
                size="sm"
              />
            </UDropdownMenu>
            <UDropdownMenu
              :items="[['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map(p => ({
                label: p,
                icon: filterPriority === p ? 'i-lucide-check' : undefined,
                onSelect: () => { filterPriority = p; fetchAlerts() }
              })), [{ label: 'Clear', icon: 'i-lucide-x', onSelect: () => { filterPriority = null; fetchAlerts() } }]]"
              :popper="{ placement: 'bottom-start' }"
            >
              <UButton
                :label="filterPriority || 'All Priorities'"
                icon="i-lucide-filter"
                trailing-icon="i-lucide-chevron-down"
                color="neutral"
                variant="ghost"
                size="sm"
              />
            </UDropdownMenu>
          </template>
        </UDashboardToolbar>

        <!-- Alerts Table -->
        <LegacyTable
          :rows="alerts"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #priority-data="{ row }">
            <UBadge :color="priorityBadgeColor(row.priority)" variant="subtle">
              {{ row.priority }}
            </UBadge>
          </template>

          <template #output-data="{ row }">
            <p class="text-sm truncate max-w-xs" :title="row.output">
              {{ row.output }}
            </p>
          </template>

          <template #status-data="{ row }">
            <UBadge :color="getStatusColor(row)" variant="subtle">
              {{ getAlertStatus(row) }}
            </UBadge>
          </template>

          <template #createdAt-data="{ row }">
            <span class="text-xs text-gray-600 dark:text-gray-400">
              {{ new Date(row.createdAt).toLocaleString() }}
            </span>
          </template>

          <template #actions-data="{ row }">
            <div class="flex gap-2">
              <UButton
                icon="i-lucide-info"
                color="gray"
                variant="ghost"
                size="xs"
                @click="selectedAlert = row; isDetailModalOpen = true"
              />

              <UButton
                v-if="hasPermission('acknowledge') && !row.isAcknowledged"
                icon="i-lucide-check"
                color="blue"
                variant="ghost"
                size="xs"
                @click="selectedAlert = row; isAcknowledgeModalOpen = true"
              />

              <UButton
                v-if="hasPermission('resolve') && !row.resolved"
                icon="i-lucide-check-circle"
                color="green"
                variant="ghost"
                size="xs"
                @click="selectedAlert = row; isResolveModalOpen = true"
              />
            </div>
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Security Alerts.
        </p>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Alert Detail Modal -->
  <AlertDetailModal
    v-if="selectedAlert"
    v-model="isDetailModalOpen"
    :alert="selectedAlert"
  />

  <!-- Acknowledge Modal -->
  <AcknowledgeAlertModal
    v-if="selectedAlert"
    v-model="isAcknowledgeModalOpen"
    :alert="selectedAlert"
    @acknowledge="acknowledgeAlert"
  />

  <!-- Resolve Modal -->
  <ResolveAlertModal
    v-if="selectedAlert"
    v-model="isResolveModalOpen"
    :alert="selectedAlert"
    @resolve="resolveAlert"
  />
</template>
