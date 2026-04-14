<script setup lang="ts">
import type { NetworkFlow, NetworkFlowFilter, FlowType } from '~/types/networkflow'
import { FLOW_TYPE_CONFIG, formatBytes, getFlowTypeLabel, getFlowTypeColor, getFlowTypeBgColor } from '~/types/networkflow'

const clusterStore = useClusterStore()
const toast = useToast()

const {
  loading,
  error,
  flows,
  totalFlows,
  totalPages,
  currentPage,
  stats,
  filterOptions,
  fetchFlows,
  fetchStats,
  fetchFilterOptions,
  exportFlows,
  downloadFlowsAsJson
} = useNetworkFlows()

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

// Global search
const globalSearch = ref('')

// Filter state
const showAdvancedFilters = ref(false)
const filter = reactive<NetworkFlowFilter>({
  flowTypes: [],
  sourceNamespaces: [],
  destinationNamespaces: [],
  sourcePodName: '',
  destinationPodName: '',
  protocols: [],
  sourceIp: '',
  destinationIp: '',
  sourcePort: undefined,
  destinationPort: undefined,
  startTime: '',
  endTime: '',
  l7Method: '',
  l7Path: '',
  serviceName: '',
  page: 0,
  pageSize: 50,
  sortBy: 'timestamp',
  sortDesc: true
})

// Time range presets
const timeRangePresets = [
  { label: 'Last 5 minutes', value: 5 },
  { label: 'Last 15 minutes', value: 15 },
  { label: 'Last 30 minutes', value: 30 },
  { label: 'Last 1 hour', value: 60 },
  { label: 'Last 3 hours', value: 180 },
  { label: 'Last 6 hours', value: 360 },
  { label: 'Last 12 hours', value: 720 },
  { label: 'Last 24 hours', value: 1440 },
  { label: 'Last 7 days', value: 10080 }
]
const selectedTimeRange = ref(60)

// Page size options
const pageSizeOptions = [
  { label: '25 rows', value: 25 },
  { label: '50 rows', value: 50 },
  { label: '100 rows', value: 100 },
  { label: '200 rows', value: 200 }
]

// Flow type options for filter
const flowTypeOptions = [
  { label: 'Pod → Pod', value: 'pod-to-pod' },
  { label: 'Pod → Service', value: 'pod-to-service' },
  { label: 'Pod → External', value: 'pod-to-external' },
  { label: 'Pod → NodePort', value: 'pod-to-nodeport' },
  { label: 'Pod → Node', value: 'pod-to-node' },
  { label: 'External → Pod', value: 'external-to-pod' },
  { label: 'External → Node', value: 'external-to-node' },
  { label: 'Node → Pod', value: 'node-to-pod' },
  { label: 'Node → External', value: 'node-to-external' }
]

// Protocol options
const protocolOptions = [
  { label: 'TCP', value: 'TCP' },
  { label: 'UDP', value: 'UDP' },
  { label: 'ICMP', value: 'ICMP' }
]

// L7 Method options
const l7MethodOptions = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
  { label: 'PATCH', value: 'PATCH' },
  { label: 'HEAD', value: 'HEAD' },
  { label: 'OPTIONS', value: 'OPTIONS' }
]

// Column visibility
const visibleColumns = ref([
  'timestamp', 'flowType', 'source', 'destination', 'network', 'l7', 'service', 'node'
])

const columnOptions = [
  { label: 'Timestamp', value: 'timestamp' },
  { label: 'Flow Type', value: 'flowType' },
  { label: 'Source', value: 'source' },
  { label: 'Destination', value: 'destination' },
  { label: 'Network', value: 'network' },
  { label: 'L7 Info', value: 'l7' },
  { label: 'Service', value: 'service' },
  { label: 'Node', value: 'node' }
]

// Flow details modal
const showDetailsModal = ref(false)
const selectedFlow = ref<NetworkFlow | null>(null)

// Computed filtered flows (client-side global search)
const filteredFlows = computed(() => {
  if (!globalSearch.value.trim()) return flows.value
  
  const search = globalSearch.value.toLowerCase()
  return flows.value.filter(flow => {
    return (
      flow.flowType?.toLowerCase().includes(search) ||
      flow.source?.podName?.toLowerCase().includes(search) ||
      flow.source?.namespace?.toLowerCase().includes(search) ||
      flow.source?.ip?.toLowerCase().includes(search) ||
      flow.destination?.podName?.toLowerCase().includes(search) ||
      flow.destination?.namespace?.toLowerCase().includes(search) ||
      flow.destination?.ip?.toLowerCase().includes(search) ||
      flow.network?.protocol?.toLowerCase().includes(search) ||
      flow.l7?.method?.toLowerCase().includes(search) ||
      flow.l7?.path?.toLowerCase().includes(search) ||
      flow.l7?.host?.toLowerCase().includes(search) ||
      flow.nodeName?.toLowerCase().includes(search)
    )
  })
})

// Active filters count
const activeFiltersCount = computed(() => {
  let count = 0
  if (filter.flowTypes?.length) count++
  if (filter.protocols?.length) count++
  if (filter.sourcePodName) count++
  if (filter.destinationPodName) count++
  if (filter.sourceIp) count++
  if (filter.destinationIp) count++
  if (filter.sourcePort) count++
  if (filter.destinationPort) count++
  if (filter.l7Method) count++
  if (filter.l7Path) count++
  if (filter.serviceName) count++
  return count
})

// Apply global namespace filter
watch(selectedNamespace, (ns) => {
  if (ns && ns !== 'all') {
    filter.sourceNamespaces = [ns]
    filter.destinationNamespaces = [ns]
  } else {
    filter.sourceNamespaces = []
    filter.destinationNamespaces = []
  }
  loadData()
})

// Fetch data
const loadData = async () => {
  if (!selectedCluster.value?.uid) return
  
  const now = new Date()
  const start = new Date(now.getTime() - selectedTimeRange.value * 60 * 1000)
  filter.startTime = start.toISOString()
  filter.endTime = now.toISOString()
  
  try {
    await Promise.all([
      fetchFlows(filter),
      fetchStats(filter.startTime, filter.endTime),
      fetchFilterOptions()
    ])
  } catch (e: any) {
    toast.add({
      title: 'Failed to load network flows',
      description: e.message,
      color: 'red'
    })
  }
}

// Reset filters
const resetFilters = () => {
  filter.flowTypes = []
  filter.protocols = []
  filter.sourcePodName = ''
  filter.destinationPodName = ''
  filter.sourceIp = ''
  filter.destinationIp = ''
  filter.sourcePort = undefined
  filter.destinationPort = undefined
  filter.l7Method = ''
  filter.l7Path = ''
  filter.serviceName = ''
  globalSearch.value = ''
  loadData()
}

// Pagination
const changePage = (page: number) => {
  filter.page = page
  loadData()
}

const changePageSize = (size: number) => {
  filter.pageSize = size
  filter.page = 0
  loadData()
}

// Sorting
const sortBy = (column: string) => {
  if (filter.sortBy === column) {
    filter.sortDesc = !filter.sortDesc
  } else {
    filter.sortBy = column
    filter.sortDesc = true
  }
  loadData()
}

// Format helpers
const formatTime = (timestamp: string) => {
  const date = new Date(timestamp)
  return date.toLocaleString('tr-TR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const formatShortTime = (timestamp: string) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

// View flow details
const viewFlowDetails = (flow: NetworkFlow) => {
  selectedFlow.value = flow
  showDetailsModal.value = true
}

// Copy flow JSON to clipboard
const copyFlowJson = async () => {
  if (selectedFlow.value) {
    try {
      await navigator.clipboard.writeText(JSON.stringify(selectedFlow.value, null, 2))
      // Could add a toast notification here
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }
}

// Export handler
const handleExport = async () => {
  try {
    const data = await exportFlows(filter.startTime, filter.endTime, 10000)
    if (data) {
      downloadFlowsAsJson(data, `network-flows-${new Date().toISOString()}.json`)
      toast.add({ title: 'Export successful', description: `${data.length} flows exported`, color: 'green' })
    }
  } catch (e: any) {
    toast.add({ title: 'Export failed', description: e.message, color: 'red' })
  }
}

// Watch for cluster change
watch([selectedCluster], () => {
  loadData()
}, { immediate: true })

// Auto-refresh
const autoRefresh = ref(false)
const refreshInterval = ref<ReturnType<typeof setInterval> | null>(null)

watch(autoRefresh, (val) => {
  if (val) {
    refreshInterval.value = setInterval(() => {
      loadData()
    }, 5000)
  } else if (refreshInterval.value) {
    clearInterval(refreshInterval.value)
    refreshInterval.value = null
  }
})

onUnmounted(() => {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value)
  }
})

// Get flow type badge color
const getFlowTypeBadgeColor = (flowType: string) => {
  const colors: Record<string, string> = {
    'pod-to-pod': 'info',
    'pod-to-service': 'success',
    'pod-to-external': 'warning',
    'pod-to-nodeport': 'primary',
    'pod-to-node': 'neutral',
    'external-to-pod': 'error',
    'external-to-node': 'error',
    'node-to-pod': 'neutral',
    'node-to-external': 'warning'
  }
  return colors[flowType] || 'neutral'
}

// Get protocol badge color
const getProtocolBadgeColor = (protocol: string) => {
  const colors: Record<string, string> = {
    'TCP': 'info',
    'UDP': 'success',
    'ICMP': 'warning',
    'HTTP': 'primary',
    'HTTPS': 'primary'
  }
  return colors[protocol] || 'neutral'
}
</script>

<template>
  <UDashboardPanel id="network-monitor" grow>
    <template #header>
      <UDashboardNavbar title="Network Flow Monitor">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <div class="flex items-center gap-3">
            <!-- Live indicator -->
            <div v-if="autoRefresh" class="flex items-center gap-2 text-sm text-green-600 dark:text-green-400">
              <span class="relative flex h-2 w-2">
                <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                <span class="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
              </span>
              Live
            </div>

            <UCheckbox v-model="autoRefresh" label="Auto-refresh (5s)" color="primary" />

            <UButton
              icon="i-lucide-download"
              color="neutral"
              variant="soft"
              @click="handleExport"
            >
              Export
            </UButton>

            <UButton
              icon="i-lucide-refresh-cw"
              color="primary"
              variant="soft"
              :loading="loading"
              @click="loadData"
            >
              Refresh
            </UButton>
          </div>
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar class="border-b border-gray-200 dark:border-gray-800">
        <template #left>
          <div class="flex items-center gap-3 flex-wrap">
            <!-- Namespace Selector (T3: show all namespaces unfiltered) -->
            <NamespaceSelector for-page />
            
            <div class="h-6 w-px bg-gray-200 dark:bg-gray-700" />
            
            <!-- Time Range -->
            <USelectMenu
              v-model="selectedTimeRange"
              :items="timeRangePresets"
              value-key="value"
              class="w-44"
              @update:model-value="loadData"
            >
              <template #leading>
                <UIcon name="i-lucide-clock" class="text-gray-400" />
              </template>
              <template #label>
                {{ timeRangePresets.find(t => t.value === selectedTimeRange)?.label }}
              </template>
            </USelectMenu>
            
            <div class="h-6 w-px bg-gray-200 dark:bg-gray-700" />
            
            <!-- Global Search -->
            <div class="relative">
              <UInput
                v-model="globalSearch"
                placeholder="Search flows..."
                icon="i-lucide-search"
                class="w-64"
                :ui="{ icon: { trailing: { pointer: '' } } }"
              >
                <template #trailing>
                  <UButton
                    v-if="globalSearch"
                    icon="i-lucide-x"
                    color="neutral"
                    variant="ghost"
                    size="2xs"
                    @click="globalSearch = ''"
                  />
                </template>
              </UInput>
            </div>
            
            <div class="h-6 w-px bg-gray-200 dark:bg-gray-700" />
            
            <!-- Advanced Filters Toggle -->
            <UButton
              :icon="showAdvancedFilters ? 'i-lucide-filter-x' : 'i-lucide-filter'"
              :color="activeFiltersCount > 0 ? 'primary' : 'neutral'"
              variant="soft"
              @click="showAdvancedFilters = !showAdvancedFilters"
            >
              Filters
              <UBadge v-if="activeFiltersCount > 0" color="primary" size="xs" class="ml-1">
                {{ activeFiltersCount }}
              </UBadge>
            </UButton>
            
            <UButton
              v-if="activeFiltersCount > 0"
              icon="i-lucide-x"
              color="neutral"
              variant="ghost"
              size="xs"
              @click="resetFilters"
            >
              Clear
            </UButton>
          </div>
        </template>
        
        <template #right>
          <div class="flex items-center gap-3">
            <!-- Stats Summary -->
            <div class="flex items-center gap-4 text-sm">
              <div class="flex items-center gap-1.5">
                <UIcon name="i-lucide-activity" class="text-primary" />
                <span class="font-semibold">{{ totalFlows.toLocaleString() }}</span>
                <span class="text-gray-500">flows</span>
              </div>
              <div v-if="stats" class="flex items-center gap-1.5">
                <UIcon name="i-lucide-hard-drive" class="text-green-500" />
                <span class="font-semibold">{{ formatBytes(stats.totalBytes) }}</span>
              </div>
            </div>
            
            <div class="h-6 w-px bg-gray-200 dark:bg-gray-700" />
            
            <!-- Column Visibility -->
            <UPopover>
              <UButton icon="i-lucide-columns-3" color="neutral" variant="ghost" />
              <template #content>
                <div class="p-3 space-y-2 w-48">
                  <p class="text-xs font-medium text-gray-500 uppercase">Visible Columns</p>
                  <div v-for="col in columnOptions" :key="col.value" class="flex items-center gap-2">
                    <UCheckbox
                      :model-value="visibleColumns.includes(col.value)"
                      :label="col.label"
                      @update:model-value="(v: boolean) => {
                        if (v) visibleColumns.push(col.value)
                        else visibleColumns = visibleColumns.filter(c => c !== col.value)
                      }"
                    />
                  </div>
                </div>
              </template>
            </UPopover>
            
            <!-- Page Size -->
            <USelectMenu
              v-model="filter.pageSize"
              :items="pageSizeOptions"
              value-key="value"
              class="w-28"
              @update:model-value="changePageSize"
            >
              <template #label>
                {{ filter.pageSize }} rows
              </template>
            </USelectMenu>
          </div>
        </template>
      </UDashboardToolbar>

      
      <!-- Advanced Filters Panel -->
      <Transition
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="transform -translate-y-2 opacity-0"
        enter-to-class="transform translate-y-0 opacity-100"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="transform translate-y-0 opacity-100"
        leave-to-class="transform -translate-y-2 opacity-0"
      >
        <div v-if="showAdvancedFilters" class="bg-gray-50 dark:bg-gray-900 border-b border-gray-200 dark:border-gray-800 p-4">
          <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
            <!-- Flow Types -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">Flow Type</label>
              <USelectMenu
                v-model="filter.flowTypes"
                :items="flowTypeOptions"
                value-key="value"
                multiple
                placeholder="All types"
                class="w-full"
                @update:model-value="loadData"
              />
            </div>
            
            <!-- Protocols -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">Protocol</label>
              <USelectMenu
                v-model="filter.protocols"
                :items="protocolOptions"
                value-key="value"
                multiple
                placeholder="All protocols"
                class="w-full"
                @update:model-value="loadData"
              />
            </div>
            
            <!-- Source Pod -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">Source Pod</label>
              <UInput
                v-model="filter.sourcePodName"
                placeholder="Pod name..."
                class="w-full"
                @change="loadData"
              />
            </div>
            
            <!-- Destination Pod -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">Destination Pod</label>
              <UInput
                v-model="filter.destinationPodName"
                placeholder="Pod name..."
                class="w-full"
                @change="loadData"
              />
            </div>
            
            <!-- Source IP -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">Source IP</label>
              <UInput
                v-model="filter.sourceIp"
                placeholder="IP address..."
                class="w-full"
                @change="loadData"
              />
            </div>
            
            <!-- Destination IP -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">Destination IP</label>
              <UInput
                v-model="filter.destinationIp"
                placeholder="IP address..."
                class="w-full"
                @change="loadData"
              />
            </div>
            
            <!-- Source Port -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">Source Port</label>
              <UInput
                v-model.number="filter.sourcePort"
                type="number"
                placeholder="Port..."
                class="w-full"
                @change="loadData"
              />
            </div>
            
            <!-- Destination Port -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">Destination Port</label>
              <UInput
                v-model.number="filter.destinationPort"
                type="number"
                placeholder="Port..."
                class="w-full"
                @change="loadData"
              />
            </div>
            
            <!-- L7 Method -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">HTTP Method</label>
              <USelectMenu
                v-model="filter.l7Method"
                :items="l7MethodOptions"
                value-key="value"
                placeholder="Any method"
                class="w-full"
                @update:model-value="loadData"
              />
            </div>
            
            <!-- L7 Path -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">HTTP Path (LIKE search)</label>
              <UInput
                v-model="filter.l7Path"
                placeholder="e.g. /api/users or health"
                class="w-full"
                @change="loadData"
              />
              <span class="text-xs text-gray-400 mt-0.5 block">Searches with %path% pattern</span>
            </div>
            
            <!-- Service Name -->
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">Service Name</label>
              <UInput
                v-model="filter.serviceName"
                placeholder="Service..."
                class="w-full"
                @change="loadData"
              />
            </div>
          </div>
        </div>
      </Transition>
    </template>

    <template #body>
      <div class="flex flex-col h-full overflow-hidden">
        <!-- Stats Cards -->
        <div v-if="stats" class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4 p-4 bg-gray-50 dark:bg-gray-900/50 border-b border-gray-200 dark:border-gray-800">

        <div class="bg-white dark:bg-gray-800 rounded-xl p-4 shadow-sm border border-gray-100 dark:border-gray-700">
          <div class="flex items-center gap-3">
            <div class="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
              <UIcon name="i-lucide-activity" class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <div class="text-xs text-gray-500">Total Flows</div>
              <div class="text-xl font-bold">{{ stats.totalFlows?.toLocaleString() || 0 }}</div>
            </div>
          </div>
        </div>
        
        <div class="bg-white dark:bg-gray-800 rounded-xl p-4 shadow-sm border border-gray-100 dark:border-gray-700">
          <div class="flex items-center gap-3">
            <div class="p-2 bg-green-100 dark:bg-green-900 rounded-lg">
              <UIcon name="i-lucide-hard-drive" class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <div class="text-xs text-gray-500">Total Data</div>
              <div class="text-xl font-bold">{{ formatBytes(stats.totalBytes) }}</div>
            </div>
          </div>
        </div>
        
        <!-- Flow Type Distribution -->
        <div v-for="item in (stats.byFlowType || []).slice(0, 4)" :key="item.key" 
             class="bg-white dark:bg-gray-800 rounded-xl p-4 shadow-sm border border-gray-100 dark:border-gray-700">
          <div class="flex items-center justify-between">
            <div>
              <div class="text-xs text-gray-500">{{ getFlowTypeLabel(item.key as FlowType) }}</div>
              <div class="text-lg font-bold">{{ item.count.toLocaleString() }}</div>
            </div>
            <div class="text-right">
              <div class="text-xs text-gray-400">{{ item.percentage.toFixed(1) }}%</div>
              <div class="text-sm text-gray-500">{{ formatBytes(item.bytes) }}</div>
            </div>
          </div>
          <div class="mt-2 h-1.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
            <div 
              class="h-full bg-gradient-to-r from-blue-500 to-blue-600 rounded-full"
              :style="{ width: `${item.percentage}%` }"
            />
          </div>
        </div>
      </div>

      <!-- Loading State -->
      <div v-if="loading && flows.length === 0" class="flex flex-col items-center justify-center py-20">
        <UIcon name="i-lucide-loader-2" class="animate-spin h-10 w-10 text-primary mb-4" />
        <p class="text-gray-500">Loading network flows...</p>
      </div>

      <!-- Flows Table -->
      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead class="bg-gray-50 dark:bg-gray-800/50 sticky top-0 z-10">
            <tr>
              <th v-if="visibleColumns.includes('timestamp')" 
                  class="px-4 py-3 text-left font-semibold text-gray-600 dark:text-gray-300 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                  @click="sortBy('timestamp')">
                <div class="flex items-center gap-1">
                  Time
                  <UIcon v-if="filter.sortBy === 'timestamp'" 
                         :name="filter.sortDesc ? 'i-lucide-chevron-down' : 'i-lucide-chevron-up'" 
                         class="h-4 w-4" />
                </div>
              </th>
              <th v-if="visibleColumns.includes('flowType')" class="px-4 py-3 text-left font-semibold text-gray-600 dark:text-gray-300">Type</th>
              <th v-if="visibleColumns.includes('source')" class="px-4 py-3 text-left font-semibold text-gray-600 dark:text-gray-300">Source</th>
              <th v-if="visibleColumns.includes('destination')" class="px-4 py-3 text-left font-semibold text-gray-600 dark:text-gray-300">Destination</th>
              <th v-if="visibleColumns.includes('network')" class="px-4 py-3 text-left font-semibold text-gray-600 dark:text-gray-300">Network</th>
              <th v-if="visibleColumns.includes('l7')" class="px-4 py-3 text-left font-semibold text-gray-600 dark:text-gray-300">L7 Info</th>
              <th v-if="visibleColumns.includes('service')" class="px-4 py-3 text-left font-semibold text-gray-600 dark:text-gray-300">Service</th>
              <th v-if="visibleColumns.includes('node')" class="px-4 py-3 text-left font-semibold text-gray-600 dark:text-gray-300">Node</th>
              <th class="px-4 py-3 text-center font-semibold text-gray-600 dark:text-gray-300 w-20">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
            <tr 
              v-for="flow in filteredFlows" 
              :key="flow.id"
              class="hover:bg-gray-50 dark:hover:bg-gray-800/50 cursor-pointer transition-colors"
              @click="viewFlowDetails(flow)"
            >
              <!-- Timestamp -->
              <td v-if="visibleColumns.includes('timestamp')" class="px-4 py-3 whitespace-nowrap">
                <div class="flex flex-col">
                  <span class="font-mono text-xs text-gray-600 dark:text-gray-400">
                    {{ formatShortTime(flow.timestamp) }}
                  </span>
                </div>
              </td>
              
              <!-- Flow Type -->
              <td v-if="visibleColumns.includes('flowType')" class="px-4 py-3">
                <UBadge :color="getFlowTypeBadgeColor(flow.flowType)" variant="subtle" size="xs">
                  {{ getFlowTypeLabel(flow.flowType as FlowType) }}
                </UBadge>
              </td>
              
              <!-- Source -->
              <td v-if="visibleColumns.includes('source')" class="px-4 py-3">
                <div class="flex flex-col gap-0.5">
                  <div class="flex items-center gap-1.5">
                    <UIcon name="i-lucide-box" class="h-3.5 w-3.5 text-gray-400" />
                    <span class="font-medium text-gray-900 dark:text-gray-100">
                      {{ flow.source?.podName || flow.source?.ip || '-' }}
                    </span>
                  </div>
                  <div class="flex items-center gap-2 text-xs text-gray-500">
                    <span v-if="flow.source?.namespace" class="bg-gray-100 dark:bg-gray-700 px-1.5 py-0.5 rounded">
                      {{ flow.source.namespace }}
                    </span>
                    <span class="font-mono">:{{ flow.source?.port || '-' }}</span>
                  </div>
                </div>
              </td>
              
              <!-- Destination -->
              <td v-if="visibleColumns.includes('destination')" class="px-4 py-3">
                <div class="flex flex-col gap-0.5">
                  <div class="flex items-center gap-1.5">
                    <UIcon :name="flow.destination?.kind === 'Service' ? 'i-lucide-server' : 'i-lucide-box'" 
                           class="h-3.5 w-3.5 text-gray-400" />
                    <span class="font-medium text-gray-900 dark:text-gray-100">
                      {{ flow.destination?.podName || flow.destination?.ip || '-' }}
                    </span>
                  </div>
                  <div class="flex items-center gap-2 text-xs text-gray-500">
                    <span v-if="flow.destination?.namespace" class="bg-gray-100 dark:bg-gray-700 px-1.5 py-0.5 rounded">
                      {{ flow.destination.namespace }}
                    </span>
                    <span class="font-mono">:{{ flow.destination?.port || '-' }}</span>
                  </div>
                </div>
              </td>
              
              <!-- Network -->
              <td v-if="visibleColumns.includes('network')" class="px-4 py-3">
                <div class="flex flex-col gap-1">
                  <UBadge :color="getProtocolBadgeColor(flow.network?.protocol || '')" variant="soft" size="xs">
                    {{ flow.network?.protocol || '-' }}
                  </UBadge>
                  <span class="text-xs text-gray-500 font-mono">{{ formatBytes(flow.network?.bytes) }}</span>
                </div>
              </td>
              
              <!-- L7 Info -->
              <td v-if="visibleColumns.includes('l7')" class="px-4 py-3">
                <div v-if="flow.l7" class="flex flex-col gap-0.5">
                  <div class="flex items-center gap-1.5">
                    <UBadge color="primary" variant="soft" size="xs">{{ flow.l7.method }}</UBadge>
                    <span class="text-xs font-mono text-gray-600 dark:text-gray-400 truncate max-w-32">
                      {{ flow.l7.path }}
                    </span>
                  </div>
                  <span class="text-xs text-gray-400 truncate max-w-40">{{ flow.l7.host }}</span>
                </div>
                <span v-else class="text-gray-300 dark:text-gray-600">—</span>
              </td>
              
              <!-- Service -->
              <td v-if="visibleColumns.includes('service')" class="px-4 py-3">
                <div v-if="flow.destination?.service" class="flex flex-col gap-0.5">
                  <div class="flex items-center gap-1.5">
                    <UIcon name="i-lucide-server" class="h-3.5 w-3.5 text-green-500" />
                    <span class="font-medium text-gray-900 dark:text-gray-100">
                      {{ flow.destination.service.name }}
                    </span>
                  </div>
                  <span class="text-xs text-gray-500">{{ flow.destination.service.namespace }}</span>
                </div>
                <span v-else class="text-gray-300 dark:text-gray-600">—</span>
              </td>
              
              <!-- Node -->
              <td v-if="visibleColumns.includes('node')" class="px-4 py-3">
                <div v-if="flow.nodeName" class="flex items-center gap-1.5">
                  <UIcon name="i-lucide-server" class="h-3.5 w-3.5 text-gray-400" />
                  <span class="text-sm text-gray-600 dark:text-gray-400">{{ flow.nodeName }}</span>
                </div>
                <span v-else class="text-gray-300 dark:text-gray-600">—</span>
              </td>
              
              <!-- Actions -->
              <td class="px-4 py-3 text-center">
                <UButton
                  icon="i-lucide-eye"
                  color="neutral"
                  variant="ghost"
                  size="xs"
                  @click.stop="viewFlowDetails(flow)"
                />
              </td>
            </tr>
            
            <!-- Empty State -->
            <tr v-if="filteredFlows.length === 0 && !loading">
              <td :colspan="visibleColumns.length + 1" class="px-4 py-16 text-center">
                <div class="flex flex-col items-center gap-3">
                  <div class="p-4 bg-gray-100 dark:bg-gray-800 rounded-full">
                    <UIcon name="i-lucide-network" class="h-8 w-8 text-gray-400" />
                  </div>
                  <div>
                    <p class="text-lg font-medium text-gray-900 dark:text-gray-100">No flows found</p>
                    <p class="text-sm text-gray-500">Try adjusting your filters or time range</p>
                  </div>
                  <UButton color="primary" variant="soft" @click="resetFilters">
                    Clear Filters
                  </UButton>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="flex items-center justify-between px-4 py-3 border-t border-gray-200 dark:border-gray-800 bg-gray-50 dark:bg-gray-900/50">
        <div class="text-sm text-gray-500">
          Showing {{ currentPage * (filter.pageSize || 50) + 1 }} - {{ Math.min((currentPage + 1) * (filter.pageSize || 50), totalFlows) }} of {{ totalFlows.toLocaleString() }} flows
        </div>
        <div class="flex items-center gap-2">
          <UButton
            icon="i-lucide-chevrons-left"
            color="neutral"
            variant="ghost"
            size="xs"
            :disabled="currentPage === 0"
            @click="changePage(0)"
          />
          <UButton
            icon="i-lucide-chevron-left"
            color="neutral"
            variant="ghost"
            size="xs"
            :disabled="currentPage === 0"
            @click="changePage(currentPage - 1)"
          />
          <div class="flex items-center gap-1 px-2">
            <template v-for="page in Math.min(5, totalPages)" :key="page">
              <UButton
                :color="currentPage === page - 1 ? 'primary' : 'neutral'"
                :variant="currentPage === page - 1 ? 'solid' : 'ghost'"
                size="xs"
                @click="changePage(page - 1)"
              >
                {{ page }}
              </UButton>
            </template>
            <span v-if="totalPages > 5" class="px-2 text-gray-400">...</span>
          </div>
          <UButton
            icon="i-lucide-chevron-right"
            color="neutral"
            variant="ghost"
            size="xs"
            :disabled="currentPage >= totalPages - 1"
            @click="changePage(currentPage + 1)"
          />
          <UButton
            icon="i-lucide-chevrons-right"
            color="neutral"
            variant="ghost"
            size="xs"
            :disabled="currentPage >= totalPages - 1"
            @click="changePage(totalPages - 1)"
          />
        </div>
      </div>
    </div>
  </template>
  </UDashboardPanel>

  <!-- Flow Details Modal -->
  <UModal v-model:open="showDetailsModal">
    <template #content>
      <div v-if="selectedFlow" class="bg-white dark:bg-gray-900 rounded-xl shadow-2xl w-full max-w-4xl max-h-[90vh] overflow-hidden">
        <!-- Header -->
        <div class="bg-gradient-to-r from-primary-500 to-primary-600 dark:from-primary-600 dark:to-primary-700 px-6 py-4">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-4">
              <div class="p-3 bg-white/20 rounded-xl">
                <UIcon name="i-lucide-activity" class="h-6 w-6 text-white" />
              </div>
              <div class="text-white">
                <h3 class="text-xl font-bold">Network Flow Details</h3>
                <p class="text-sm text-white/80">{{ formatTime(selectedFlow.timestamp) }}</p>
              </div>
            </div>
            <div class="flex items-center gap-3">
              <UBadge :color="getFlowTypeBadgeColor(selectedFlow.flowType)" size="lg" class="text-sm font-semibold">
                {{ getFlowTypeLabel(selectedFlow.flowType as FlowType) }}
              </UBadge>
              <UButton 
                icon="i-lucide-x" 
                color="white" 
                variant="ghost" 
                size="sm"
                @click="showDetailsModal = false" 
              />
            </div>
          </div>
        </div>

        <!-- Content -->
        <div class="p-6 overflow-y-auto max-h-[calc(90vh-180px)]">
          <!-- Flow Direction Visual -->
          <div class="mb-6 p-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl">
            <div class="flex items-center justify-between gap-4">
              <!-- Source -->
              <div class="flex-1 text-center">
                <div class="inline-flex flex-col items-center">
                  <div class="p-3 bg-blue-100 dark:bg-blue-900/50 rounded-full mb-2">
                    <UIcon name="i-lucide-box" class="h-6 w-6 text-blue-600 dark:text-blue-400" />
                  </div>
                  <span class="font-semibold text-gray-900 dark:text-white text-sm">
                    {{ selectedFlow.source?.podName || selectedFlow.source?.ip || 'Unknown' }}
                  </span>
                  <span class="text-xs text-gray-500">{{ selectedFlow.source?.namespace || 'External' }}</span>
                  <span class="text-xs font-mono text-gray-400 mt-1">:{{ selectedFlow.source?.port }}</span>
                </div>
              </div>
              
              <!-- Arrow -->
              <div class="flex-shrink-0 flex flex-col items-center">
                <div class="flex items-center gap-2">
                  <div class="w-16 h-0.5 bg-gradient-to-r from-blue-400 to-green-400"></div>
                  <UIcon name="i-lucide-arrow-right" class="h-5 w-5 text-gray-400" />
                  <div class="w-16 h-0.5 bg-gradient-to-r from-green-400 to-green-500"></div>
                </div>
                <div class="mt-2 flex items-center gap-2">
                  <UBadge :color="getProtocolBadgeColor(selectedFlow.network?.protocol || '')" size="xs">
                    {{ selectedFlow.network?.protocol }}
                  </UBadge>
                  <span class="text-xs text-gray-500">{{ formatBytes(selectedFlow.network?.bytes) }}</span>
                </div>
              </div>
              
              <!-- Destination -->
              <div class="flex-1 text-center">
                <div class="inline-flex flex-col items-center">
                  <div class="p-3 bg-green-100 dark:bg-green-900/50 rounded-full mb-2">
                    <UIcon :name="selectedFlow.destination?.kind === 'Service' ? 'i-lucide-server' : 'i-lucide-box'" 
                           class="h-6 w-6 text-green-600 dark:text-green-400" />
                  </div>
                  <span class="font-semibold text-gray-900 dark:text-white text-sm">
                    {{ selectedFlow.destination?.podName || selectedFlow.destination?.ip || 'Unknown' }}
                  </span>
                  <span class="text-xs text-gray-500">{{ selectedFlow.destination?.namespace || 'External' }}</span>
                  <span class="text-xs font-mono text-gray-400 mt-1">:{{ selectedFlow.destination?.port }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Details Grid -->
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <!-- Source Details -->
            <div class="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
              <div class="bg-blue-50 dark:bg-blue-900/30 px-4 py-2.5 border-b border-gray-200 dark:border-gray-700">
                <h4 class="font-semibold text-blue-700 dark:text-blue-300 flex items-center gap-2">
                  <UIcon name="i-lucide-arrow-up-right" class="h-4 w-4" />
                  Source
                </h4>
              </div>
              <div class="p-4">
                <table class="w-full text-sm">
                  <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
                    <tr>
                      <td class="py-2 text-gray-500 w-28">Pod Name</td>
                      <td class="py-2 font-medium text-right break-all">{{ selectedFlow.source?.podName || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Namespace</td>
                      <td class="py-2 text-right">
                        <UBadge v-if="selectedFlow.source?.namespace" color="neutral" variant="subtle" size="xs">
                          {{ selectedFlow.source.namespace }}
                        </UBadge>
                        <span v-else>-</span>
                      </td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">IP Address</td>
                      <td class="py-2 font-mono text-right">{{ selectedFlow.source?.ip || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Port</td>
                      <td class="py-2 font-mono text-right">{{ selectedFlow.source?.port || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Kind</td>
                      <td class="py-2 text-right">{{ selectedFlow.source?.kind || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Node</td>
                      <td class="py-2 text-right text-xs">{{ selectedFlow.source?.nodeName || '-' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Destination Details -->
            <div class="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
              <div class="bg-green-50 dark:bg-green-900/30 px-4 py-2.5 border-b border-gray-200 dark:border-gray-700">
                <h4 class="font-semibold text-green-700 dark:text-green-300 flex items-center gap-2">
                  <UIcon name="i-lucide-arrow-down-right" class="h-4 w-4" />
                  Destination
                </h4>
              </div>
              <div class="p-4">
                <table class="w-full text-sm">
                  <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
                    <tr>
                      <td class="py-2 text-gray-500 w-28">Pod Name</td>
                      <td class="py-2 font-medium text-right break-all">{{ selectedFlow.destination?.podName || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Namespace</td>
                      <td class="py-2 text-right">
                        <UBadge v-if="selectedFlow.destination?.namespace" color="neutral" variant="subtle" size="xs">
                          {{ selectedFlow.destination.namespace }}
                        </UBadge>
                        <span v-else>-</span>
                      </td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">IP Address</td>
                      <td class="py-2 font-mono text-right">{{ selectedFlow.destination?.ip || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Port</td>
                      <td class="py-2 font-mono text-right">{{ selectedFlow.destination?.port || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Kind</td>
                      <td class="py-2 text-right">{{ selectedFlow.destination?.kind || '-' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Network Details -->
            <div class="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
              <div class="bg-purple-50 dark:bg-purple-900/30 px-4 py-2.5 border-b border-gray-200 dark:border-gray-700">
                <h4 class="font-semibold text-purple-700 dark:text-purple-300 flex items-center gap-2">
                  <UIcon name="i-lucide-network" class="h-4 w-4" />
                  Network
                </h4>
              </div>
              <div class="p-4">
                <table class="w-full text-sm">
                  <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
                    <tr>
                      <td class="py-2 text-gray-500 w-28">Protocol</td>
                      <td class="py-2 text-right">
                        <UBadge :color="getProtocolBadgeColor(selectedFlow.network?.protocol || '')" size="xs">
                          {{ selectedFlow.network?.protocol || '-' }}
                        </UBadge>
                      </td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Bytes</td>
                      <td class="py-2 font-mono text-right">{{ formatBytes(selectedFlow.network?.bytes) }}</td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Interface</td>
                      <td class="py-2 font-mono text-right">{{ selectedFlow.network?.interfaceName || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">TCP Flags</td>
                      <td class="py-2 font-mono text-right">{{ selectedFlow.network?.tcpFlags || '-' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- L7 / HTTP Details -->
            <div v-if="selectedFlow.l7" class="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden">
              <div class="bg-orange-50 dark:bg-orange-900/30 px-4 py-2.5 border-b border-gray-200 dark:border-gray-700">
                <h4 class="font-semibold text-orange-700 dark:text-orange-300 flex items-center gap-2">
                  <UIcon name="i-lucide-globe" class="h-4 w-4" />
                  L7 / HTTP
                </h4>
              </div>
              <div class="p-4">
                <table class="w-full text-sm">
                  <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
                    <tr>
                      <td class="py-2 text-gray-500 w-28">Method</td>
                      <td class="py-2 text-right">
                        <UBadge color="primary" size="xs">{{ selectedFlow.l7.method || '-' }}</UBadge>
                      </td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Host</td>
                      <td class="py-2 font-mono text-xs text-right break-all">{{ selectedFlow.l7.host || '-' }}</td>
                    </tr>
                    <tr>
                      <td class="py-2 text-gray-500">Path</td>
                      <td class="py-2 font-mono text-xs text-right break-all">{{ selectedFlow.l7.path || '-' }}</td>
                    </tr>
                    <tr v-if="selectedFlow.l7.statusCode">
                      <td class="py-2 text-gray-500">Status</td>
                      <td class="py-2 text-right">
                        <UBadge :color="selectedFlow.l7.statusCode >= 400 ? 'error' : 'success'" size="xs">
                          {{ selectedFlow.l7.statusCode }}
                        </UBadge>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Service Details -->
            <div v-if="selectedFlow.destination?.service" class="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden lg:col-span-2">
              <div class="bg-cyan-50 dark:bg-cyan-900/30 px-4 py-2.5 border-b border-gray-200 dark:border-gray-700">
                <h4 class="font-semibold text-cyan-700 dark:text-cyan-300 flex items-center gap-2">
                  <UIcon name="i-lucide-server" class="h-4 w-4" />
                  Kubernetes Service
                </h4>
              </div>
              <div class="p-4">
                <div class="grid grid-cols-3 gap-4 text-sm">
                  <div>
                    <span class="text-gray-500 block mb-1">Service Name</span>
                    <span class="font-semibold">{{ selectedFlow.destination.service.name }}</span>
                  </div>
                  <div>
                    <span class="text-gray-500 block mb-1">Namespace</span>
                    <UBadge color="neutral" variant="subtle" size="xs">
                      {{ selectedFlow.destination.service.namespace }}
                    </UBadge>
                  </div>
                  <div v-if="selectedFlow.destination.service.backendPodName">
                    <span class="text-gray-500 block mb-1">Backend Pod</span>
                    <span class="font-medium text-sm">{{ selectedFlow.destination.service.backendPodName }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="px-6 py-4 bg-gray-50 dark:bg-gray-800/50 border-t border-gray-200 dark:border-gray-700 flex justify-between items-center">
          <div class="flex items-center gap-4">
            <span class="text-xs text-gray-400">
              <span class="text-gray-500">Flow ID:</span> 
              <span class="font-mono">{{ selectedFlow.flowId || selectedFlow.id }}</span>
            </span>
            <span v-if="selectedFlow.nodeName" class="text-xs text-gray-400">
              <span class="text-gray-500">Node:</span> 
              <span>{{ selectedFlow.nodeName }}</span>
            </span>
          </div>
          <div class="flex items-center gap-2">
            <UButton color="neutral" variant="ghost" size="sm" icon="i-lucide-copy" @click="copyFlowJson">
              Copy JSON
            </UButton>
            <UButton color="primary" @click="showDetailsModal = false">Close</UButton>
          </div>
        </div>
      </div>
    </template>
  </UModal>
</template>
