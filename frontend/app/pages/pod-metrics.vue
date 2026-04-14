<script setup lang="ts">
import * as d3 from 'd3'

definePageMeta({ layout: 'default' })

const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('PodMetric')

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

// ── State ───────────────────────────────────────────────────────────────────
const loading = ref(false)
const namespaces = ref<string[]>([])
const pods = ref<string[]>([])
const selectedPod = ref<string>('')
const timeRange = ref('1h')
const topSortBy = ref('cpu')

const summary = ref<any[]>([])
const topPods = ref<any[]>([])
const history = ref<any[]>([])
const nsHistory = ref<any[]>([])

const cpuChartEl = ref<HTMLDivElement | null>(null)
const memChartEl = ref<HTMLDivElement | null>(null)
const nsCpuChartEl = ref<HTMLDivElement | null>(null)
const nsMemChartEl = ref<HTMLDivElement | null>(null)

const timeRanges = [
  { label: '15m', value: '15m' },
  { label: '30m', value: '30m' },
  { label: '1h', value: '1h' },
  { label: '6h', value: '6h' },
  { label: '24h', value: '24h' },
  { label: '7d', value: '7d' },
]

// ── Computed ────────────────────────────────────────────────────────────────
const metricsApiAvailable = ref<boolean | null>(null)
const metricsApiMessage = ref('')

const totalCpu = computed(() => summary.value.reduce((s, n) => s + (n.totalCpuMillicores ?? 0), 0))
const totalMem = computed(() => summary.value.reduce((s, n) => s + (n.totalMemoryMi ?? 0), 0))
const totalPodCount = computed(() => summary.value.reduce((s, n) => s + (n.podCount ?? 0), 0))

// ── API Calls ───────────────────────────────────────────────────────────────
const clusterUid = computed(() => selectedCluster.value?.uid)

async function fetchMetricsApiStatus() {
  if (!clusterUid.value) return
  try {
    const res = await $api.get<{ available: boolean; message: string }>(`/k8s/${clusterUid.value}/pod-metrics/status`)
    metricsApiAvailable.value = res.data?.available ?? false
    metricsApiMessage.value = res.data?.message ?? ''
  } catch {
    metricsApiAvailable.value = false
    metricsApiMessage.value = 'Failed to check Metrics API status'
  }
}

async function fetchNamespaces() {
  if (!clusterUid.value) return
  try {
    const res = await $api.get<string[]>(`/k8s/${clusterUid.value}/pod-metrics/namespaces`)
    namespaces.value = res.data ?? []
  } catch { namespaces.value = [] }
}

async function fetchPods() {
  if (!clusterUid.value || !selectedNamespace.value) { pods.value = []; return }
  try {
    const res = await $api.get<string[]>(`/k8s/${clusterUid.value}/pod-metrics/pods`, {
      params: { namespace: selectedNamespace.value }
    })
    pods.value = res.data ?? []
    if (pods.value.length && !pods.value.includes(selectedPod.value)) {
      selectedPod.value = pods.value[0]
    }
  } catch { pods.value = [] }
}

async function fetchSummary() {
  if (!clusterUid.value) return
  try {
    const res = await $api.get<any[]>(`/k8s/${clusterUid.value}/pod-metrics/summary`)
    summary.value = res.data ?? []
  } catch { summary.value = [] }
}

async function fetchTopPods() {
  if (!clusterUid.value) return
  try {
    const ns = selectedNamespace.value || undefined
    const res = await $api.get<any>(`/k8s/${clusterUid.value}/pod-metrics/top`, {
      params: { namespace: ns, sortBy: topSortBy.value, limit: 15 }
    })
    topPods.value = res.data?.pods ?? []
  } catch { topPods.value = [] }
}

async function fetchPodHistory() {
  if (!clusterUid.value || !selectedNamespace.value || !selectedPod.value) { history.value = []; return }
  try {
    const res = await $api.get<any>(`/k8s/${clusterUid.value}/pod-metrics/history`, {
      params: { namespace: selectedNamespace.value, podName: selectedPod.value, range: timeRange.value }
    })
    history.value = res.data?.series ?? []
  } catch { history.value = [] }
}

async function fetchNsHistory() {
  if (!clusterUid.value || !selectedNamespace.value) { nsHistory.value = []; return }
  try {
    const res = await $api.get<any>(`/k8s/${clusterUid.value}/pod-metrics/namespace-history`, {
      params: { namespace: selectedNamespace.value, range: timeRange.value }
    })
    nsHistory.value = res.data?.series ?? []
  } catch { nsHistory.value = [] }
}

async function refreshAll() {
  if (!hasPermission('view')) return
  loading.value = true
  try {
    await fetchMetricsApiStatus()
    await Promise.all([fetchNamespaces(), fetchSummary()])
    await Promise.all([fetchPods(), fetchTopPods()])
    await Promise.all([fetchPodHistory(), fetchNsHistory()])
  } finally { loading.value = false }
}

// ── Chart Rendering ─────────────────────────────────────────────────────────
function formatCpu(m: number) { return m >= 1000 ? (m / 1000).toFixed(2) + ' cores' : m + 'm' }
function formatMem(mi: number) { return mi >= 1024 ? (mi / 1024).toFixed(2) + ' GiB' : mi.toFixed(1) + ' MiB' }

function renderAreaChart(el: HTMLDivElement | null, data: any[], yKey: string, color: string, label: string, formatter: (v: number) => string) {
  if (!el || !data.length) { if (el) el.innerHTML = ''; return }
  el.innerHTML = ''

  const margin = { top: 20, right: 20, bottom: 30, left: 60 }
  const width = el.clientWidth - margin.left - margin.right
  const height = 200 - margin.top - margin.bottom

  const svg = d3.select(el).append('svg')
    .attr('width', width + margin.left + margin.right)
    .attr('height', height + margin.top + margin.bottom)
    .append('g').attr('transform', `translate(${margin.left},${margin.top})`)

  const parseTime = (t: string) => new Date(t)
  const x = d3.scaleTime().domain(d3.extent(data, d => parseTime(d.time)) as [Date, Date]).range([0, width])
  const y = d3.scaleLinear().domain([0, d3.max(data, d => d[yKey]) as number * 1.15 || 1]).range([height, 0])

  // Grid
  svg.append('g').attr('class', 'grid').attr('transform', `translate(0,${height})`).call(d3.axisBottom(x).ticks(6).tickSize(-height).tickFormat(() => '')).select('.domain').remove()
  svg.selectAll('.grid line').attr('stroke', '#e5e7eb').attr('stroke-dasharray', '2,2')

  // Area
  const area = d3.area<any>().x(d => x(parseTime(d.time))).y0(height).y1(d => y(d[yKey])).curve(d3.curveMonotoneX)
  svg.append('path').datum(data).attr('fill', color).attr('fill-opacity', 0.15).attr('d', area)

  // Line
  const line = d3.line<any>().x(d => x(parseTime(d.time))).y(d => y(d[yKey])).curve(d3.curveMonotoneX)
  svg.append('path').datum(data).attr('fill', 'none').attr('stroke', color).attr('stroke-width', 2).attr('d', line)

  // Axes
  svg.append('g').attr('transform', `translate(0,${height})`).call(d3.axisBottom(x).ticks(6).tickFormat(d3.timeFormat('%H:%M') as any)).selectAll('text').attr('fill', '#6b7280').style('font-size', '10px')
  svg.append('g').call(d3.axisLeft(y).ticks(5).tickFormat(d => formatter(d as number))).selectAll('text').attr('fill', '#6b7280').style('font-size', '10px')

  // Dots with tooltip
  svg.selectAll('.dot').data(data).enter().append('circle')
    .attr('cx', d => x(parseTime(d.time))).attr('cy', d => y(d[yKey])).attr('r', 2.5).attr('fill', color)
    .append('title').text(d => `${new Date(d.time).toLocaleTimeString()}: ${formatter(d[yKey])}`)

  // Label
  svg.append('text').attr('x', 4).attr('y', -6).attr('fill', color).style('font-size', '12px').style('font-weight', '600').text(label)
}

// ── Watch & Render ──────────────────────────────────────────────────────────
watch([selectedCluster, permissionsLoading], () => { if (!permissionsLoading.value) refreshAll() }, { immediate: true })
watch(selectedNamespace, () => { fetchPods(); fetchTopPods(); fetchNsHistory() })
watch(selectedPod, () => fetchPodHistory())
watch(timeRange, () => { fetchPodHistory(); fetchNsHistory() })
watch(topSortBy, () => fetchTopPods())

watch(history, () => {
  nextTick(() => {
    renderAreaChart(cpuChartEl.value, history.value, 'cpuMillicores', '#3b82f6', 'CPU (millicores)', formatCpu)
    renderAreaChart(memChartEl.value, history.value, 'memoryMi', '#8b5cf6', 'Memory (MiB)', formatMem)
  })
}, { deep: true })

watch(nsHistory, () => {
  nextTick(() => {
    renderAreaChart(nsCpuChartEl.value, nsHistory.value, 'cpuMillicores', '#059669', 'Namespace CPU Total', formatCpu)
    renderAreaChart(nsMemChartEl.value, nsHistory.value, 'memoryMi', '#d97706', 'Namespace Memory Total', formatMem)
  })
}, { deep: true })

// Auto-refresh every 30s
let interval: ReturnType<typeof setInterval> | null = null
onMounted(() => { interval = setInterval(refreshAll, 30000) })
onUnmounted(() => { if (interval) clearInterval(interval) })
</script>

<template>
  <UDashboardPanel id="pod-metrics">
    <template #header>
      <UDashboardNavbar title="Pod Metrics">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <div class="flex items-center gap-2">
            <!-- Metrics API Status Indicator -->
            <UTooltip v-if="metricsApiAvailable !== null" :text="metricsApiMessage">
              <div class="flex items-center gap-1.5 px-2 py-1 rounded-md" :class="metricsApiAvailable ? 'bg-green-50 dark:bg-green-900/20' : 'bg-red-50 dark:bg-red-900/20'">
                <span class="relative flex h-2.5 w-2.5">
                  <span v-if="metricsApiAvailable" class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75" />
                  <span class="relative inline-flex rounded-full h-2.5 w-2.5" :class="metricsApiAvailable ? 'bg-green-500' : 'bg-red-500'" />
                </span>
                <span class="text-xs font-medium" :class="metricsApiAvailable ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400'">
                  {{ metricsApiAvailable ? 'Metrics API' : 'Metrics Unavailable' }}
                </span>
              </div>
            </UTooltip>
            <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" square :loading="loading" @click="refreshAll" />
          </div>
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <div class="flex items-center gap-3 flex-wrap">
            <!-- Namespace selector -->
            <NamespaceSelector />

            <!-- Pod selector -->
            <USelectMenu
              v-model="selectedPod"
              :items="pods"
              placeholder="Select Pod"
              class="w-56"
              searchable
            />

            <div class="border-l border-gray-200 dark:border-gray-700 mx-1 h-5" />

            <!-- Time range -->
            <div class="flex items-center gap-1">
              <UButton
                v-for="tr in timeRanges" :key="tr.value"
                :label="tr.label"
                size="xs"
                :color="timeRange === tr.value ? 'primary' : 'neutral'"
                :variant="timeRange === tr.value ? 'solid' : 'ghost'"
                @click="timeRange = tr.value"
              />
            </div>
          </div>
        </template>
        <template #right>
          <div class="flex items-center gap-2 text-xs text-gray-500">
            <UIcon name="i-lucide-activity" class="w-3.5 h-3.5" />
            <span>Auto-refresh: 30s</span>
          </div>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <!-- Loading -->
      <div v-if="permissionsLoading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>

      <!-- Access Denied -->
      <div v-else-if="!hasPermission('view')" class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Restricted</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">Pod metrics data is visible only to authorized users.</p>
      </div>

      <!-- Content -->
      <div v-else class="p-4 space-y-6">

        <!-- ── Summary Cards ─────────────────────────────────────────────── -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div class="bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-lg bg-blue-50 dark:bg-blue-900/30 flex items-center justify-center">
                <UIcon name="i-lucide-cpu" class="w-5 h-5 text-blue-600 dark:text-blue-400" />
              </div>
              <div>
                <p class="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wider">Total CPU Usage</p>
                <p class="text-xl font-bold text-gray-900 dark:text-white">{{ formatCpu(totalCpu) }}</p>
              </div>
            </div>
          </div>
          <div class="bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-lg bg-purple-50 dark:bg-purple-900/30 flex items-center justify-center">
                <UIcon name="i-lucide-memory-stick" class="w-5 h-5 text-purple-600 dark:text-purple-400" />
              </div>
              <div>
                <p class="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wider">Total Memory Usage</p>
                <p class="text-xl font-bold text-gray-900 dark:text-white">{{ formatMem(totalMem) }}</p>
              </div>
            </div>
          </div>
          <div class="bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-lg bg-green-50 dark:bg-green-900/30 flex items-center justify-center">
                <UIcon name="i-lucide-box" class="w-5 h-5 text-green-600 dark:text-green-400" />
              </div>
              <div>
                <p class="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wider">Monitored Pods</p>
                <p class="text-xl font-bold text-gray-900 dark:text-white">{{ totalPodCount }}</p>
              </div>
            </div>
          </div>
        </div>


        <!-- ── Namespace Aggregate Charts ─────────────────────────────────── -->
        <div v-if="selectedNamespace" class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div class="bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
            <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
              <UIcon name="i-lucide-cpu" class="w-4 h-4 inline mr-1 text-emerald-500" />
              Namespace CPU — {{ selectedNamespace }}
            </h3>
            <div ref="nsCpuChartEl" class="w-full" style="min-height:220px" />
            <p v-if="!nsHistory.length" class="text-xs text-gray-400 text-center py-8">No data for selected time range</p>
          </div>
          <div class="bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
            <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
              <UIcon name="i-lucide-memory-stick" class="w-4 h-4 inline mr-1 text-amber-500" />
              Namespace Memory — {{ selectedNamespace }}
            </h3>
            <div ref="nsMemChartEl" class="w-full" style="min-height:220px" />
            <p v-if="!nsHistory.length" class="text-xs text-gray-400 text-center py-8">No data for selected time range</p>
          </div>
        </div>

        <!-- ── Pod-Level Charts ───────────────────────────────────────────── -->
        <div v-if="selectedPod" class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div class="bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
            <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
              <UIcon name="i-lucide-cpu" class="w-4 h-4 inline mr-1 text-blue-500" />
              Pod CPU — {{ selectedPod }}
            </h3>
            <div ref="cpuChartEl" class="w-full" style="min-height:220px" />
            <p v-if="!history.length" class="text-xs text-gray-400 text-center py-8">No data for selected time range</p>
          </div>
          <div class="bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
            <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
              <UIcon name="i-lucide-memory-stick" class="w-4 h-4 inline mr-1 text-violet-500" />
              Pod Memory — {{ selectedPod }}
            </h3>
            <div ref="memChartEl" class="w-full" style="min-height:220px" />
            <p v-if="!history.length" class="text-xs text-gray-400 text-center py-8">No data for selected time range</p>
          </div>
        </div>

        <!-- ── Top Pods Table ─────────────────────────────────────────────── -->
        <div class="bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-gray-700">
          <div class="flex items-center justify-between px-5 py-3 border-b border-gray-100 dark:border-gray-700">
            <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">
              <UIcon name="i-lucide-trophy" class="w-4 h-4 inline mr-1 text-amber-500" />
              Top Pods
            </h3>
            <div class="flex items-center gap-1">
              <UButton size="xs" :variant="topSortBy === 'cpu' ? 'solid' : 'ghost'" :color="topSortBy === 'cpu' ? 'primary' : 'neutral'" label="By CPU" @click="topSortBy = 'cpu'" />
              <UButton size="xs" :variant="topSortBy === 'memory' ? 'solid' : 'ghost'" :color="topSortBy === 'memory' ? 'primary' : 'neutral'" label="By Memory" @click="topSortBy = 'memory'" />
            </div>
          </div>
          <div class="overflow-x-auto">
            <table class="min-w-full text-sm">
              <thead class="bg-gray-50 dark:bg-neutral-900">
                <tr>
                  <th class="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">#</th>
                  <th class="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Namespace</th>
                  <th class="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Pod</th>
                  <th class="px-4 py-2.5 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">CPU</th>
                  <th class="px-4 py-2.5 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">Memory</th>
                  <th class="px-4 py-2.5 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">CPU Bar</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
                <tr v-for="(pod, i) in topPods" :key="pod.podName" class="hover:bg-gray-50 dark:hover:bg-neutral-900/50 transition-colors cursor-pointer" @click="selectedPod = pod.podName">
                  <td class="px-4 py-2 text-gray-500">{{ i + 1 }}</td>
                  <td class="px-4 py-2">
                    <UBadge variant="subtle" color="neutral" size="xs">{{ pod.namespace }}</UBadge>
                  </td>
                  <td class="px-4 py-2 font-mono text-xs text-gray-800 dark:text-gray-200">{{ pod.podName }}</td>
                  <td class="px-4 py-2 text-right font-mono text-xs">
                    <span class="text-blue-600 dark:text-blue-400">{{ formatCpu(pod.cpuMillicores) }}</span>
                  </td>
                  <td class="px-4 py-2 text-right font-mono text-xs">
                    <span class="text-purple-600 dark:text-purple-400">{{ formatMem(pod.memoryMi) }}</span>
                  </td>
                  <td class="px-4 py-2 w-40">
                    <div class="h-2 rounded-full bg-gray-200 dark:bg-gray-700 overflow-hidden">
                      <div class="h-full rounded-full transition-all duration-500"
                        :class="topSortBy === 'cpu' ? 'bg-blue-500' : 'bg-purple-500'"
                        :style="{ width: Math.min(100, (topSortBy === 'cpu' ? pod.cpuMillicores : pod.memoryMi) / (topPods[0]?.[topSortBy === 'cpu' ? 'cpuMillicores' : 'memoryMi'] || 1) * 100) + '%' }"
                      />
                    </div>
                  </td>
                </tr>
                <tr v-if="!topPods.length">
                  <td colspan="6" class="px-4 py-8 text-center text-gray-400">No pod metrics data available yet</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- ── Namespace Summary Table ────────────────────────────────────── -->
        <div class="bg-white dark:bg-neutral-800 rounded-xl border border-gray-200 dark:border-gray-700">
          <div class="px-5 py-3 border-b border-gray-100 dark:border-gray-700">
            <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">
              <UIcon name="i-lucide-layers" class="w-4 h-4 inline mr-1 text-green-500" />
              Namespace Summary
            </h3>
          </div>
          <div class="overflow-x-auto">
            <table class="min-w-full text-sm">
              <thead class="bg-gray-50 dark:bg-neutral-900">
                <tr>
                  <th class="px-4 py-2.5 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Namespace</th>
                  <th class="px-4 py-2.5 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Pods</th>
                  <th class="px-4 py-2.5 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">CPU</th>
                  <th class="px-4 py-2.5 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">Memory</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
                <tr v-for="ns in summary" :key="ns.namespace" class="hover:bg-gray-50 dark:hover:bg-neutral-900/50">
                  <td class="px-4 py-2 font-medium text-gray-800 dark:text-gray-200">{{ ns.namespace }}</td>
                  <td class="px-4 py-2 text-right text-gray-600 dark:text-gray-400">{{ ns.podCount }}</td>
                  <td class="px-4 py-2 text-right font-mono text-xs text-blue-600 dark:text-blue-400">{{ formatCpu(ns.totalCpuMillicores) }}</td>
                  <td class="px-4 py-2 text-right font-mono text-xs text-purple-600 dark:text-purple-400">{{ formatMem(ns.totalMemoryMi) }}</td>
                </tr>
                <tr v-if="!summary.length">
                  <td colspan="4" class="px-4 py-8 text-center text-gray-400">No namespace metrics data yet</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

      </div>
    </template>
  </UDashboardPanel>
</template>