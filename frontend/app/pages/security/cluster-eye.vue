<script setup lang="ts">
definePageMeta({ layout: 'default' })

const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const authStore    = useAuthStore()
const toast        = useToast()

const { hasPermission, loading: permissionsLoading } = usePagePermissions('ClusterEyeResult')
const isUserLoaded = computed(() => !!authStore.user || !authStore.isAuthenticated)

// ── State ─────────────────────────────────────────────────────────────────────
const summary  = ref<any>(null)
const results  = ref<any[]>([])
const loading  = ref(false)
const scanning = ref(false)

// Filters (namespace driven by the global NS selector like pods.vue)
const filterKind     = ref('')
const filterSeverity = ref('ALL')
const searchText     = ref('')

// Pagination
const page     = ref(1)
const pageSize = ref(20)

// Detail modal
const selectedRow    = ref<any>(null)
const showDetail     = ref(false)
const parsedFindings = computed<string[]>(() => {
  if (!selectedRow.value?.findings) return []
  try { return JSON.parse(selectedRow.value.findings) } catch { return [] }
})
const criticalFindings = computed(() => parsedFindings.value.filter(f => f.startsWith('[CRITICAL]')))
const highFindings     = computed(() => parsedFindings.value.filter(f => f.startsWith('[HIGH]')))
const mediumFindings   = computed(() => parsedFindings.value.filter(f => f.startsWith('[MEDIUM]')))

const clusterUid       = computed(() => clusterStore.selectedCluster?.uid)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

// ── Filtered + paginated results ──────────────────────────────────────────────
const filteredResults = computed(() => {
  let list = results.value
  if (searchText.value) {
    const q = searchText.value.toLowerCase()
    list = list.filter(r =>
      r.workloadName?.toLowerCase().includes(q) ||
      r.namespace?.toLowerCase().includes(q)
    )
  }
  return list
})
const totalPages  = computed(() => Math.max(1, Math.ceil(filteredResults.value.length / pageSize.value)))
const pagedResults = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredResults.value.slice(start, start + pageSize.value)
})
watch(filteredResults, () => { page.value = 1 })

// ── Fetch ─────────────────────────────────────────────────────────────────────
async function fetchSummary() {
  if (!clusterUid.value) return
  try {
    const res = await $api.get(`/k8s/${clusterUid.value}/security/eye/summary`)
    summary.value = res.data
  } catch { summary.value = null }
}

async function fetchResults() {
  if (!clusterUid.value) return
  loading.value = true
  page.value = 1
  try {
    const params: any = { minSeverity: filterSeverity.value }
    const ns = selectedNamespace.value
    if (ns && ns !== 'all' && ns !== '') params.namespace = ns
    if (filterKind.value) params.kind = filterKind.value
    const res = await $api.get(`/k8s/${clusterUid.value}/security/eye/results`, { params })
    results.value = res.data ?? []
  } catch {
    toast.add({ title: 'Error', description: 'Failed to load scan results', color: 'error' })
  } finally { loading.value = false }
}

async function triggerScan() {
  if (!clusterUid.value || scanning.value) return
  scanning.value = true
  try {
    const res = await $api.post(`/k8s/${clusterUid.value}/security/eye/scan`)
    toast.add({ title: 'Scan complete', description: res.data.message, color: 'success' })
    await Promise.all([fetchSummary(), fetchResults()])
  } catch (e: any) {
    toast.add({ title: 'Scan failed', description: e.message, color: 'error' })
  } finally { scanning.value = false }
}

function openDetail(row: any) { selectedRow.value = row; showDetail.value = true }

function findingColor(f: string) {
  if (f.startsWith('[CRITICAL]')) return 'text-red-600 dark:text-red-400'
  if (f.startsWith('[HIGH]'))     return 'text-orange-500 dark:text-orange-400'
  return 'text-yellow-600 dark:text-yellow-400'
}
function findingBg(f: string) {
  if (f.startsWith('[CRITICAL]')) return 'bg-red-50 dark:bg-red-950/40 border-red-200 dark:border-red-800'
  if (f.startsWith('[HIGH]'))     return 'bg-orange-50 dark:bg-orange-950/40 border-orange-200 dark:border-orange-800'
  return 'bg-yellow-50 dark:bg-yellow-950/40 border-yellow-200 dark:border-yellow-800'
}
function findingIcon(f: string) {
  if (f.startsWith('[CRITICAL]')) return 'i-lucide-shield-x'
  if (f.startsWith('[HIGH]'))     return 'i-lucide-shield-alert'
  return 'i-lucide-alert-triangle'
}
function workloadIcon(kind: string) {
  return ({ Deployment:'i-lucide-boxes', StatefulSet:'i-lucide-database', DaemonSet:'i-lucide-server', CronJob:'i-lucide-clock', Pod:'i-lucide-circle-dot' } as any)[kind] ?? 'i-lucide-box'
}
function workloadColor(kind: string) {
  return ({ Deployment:'primary', StatefulSet:'violet', DaemonSet:'amber', CronJob:'cyan', Pod:'green' } as any)[kind] ?? 'neutral'
}

const kindOptions     = ['', 'Deployment', 'StatefulSet', 'DaemonSet', 'CronJob', 'Pod'].map(v => ({ label: v || 'All Kinds', value: v }))
const severityOptions = [['ALL','All'],['CRITICAL','Critical+'],['HIGH','High+'],['MEDIUM','Medium+']].map(([v,l]) => ({ label: l, value: v }))

watch([selectedNamespace, filterKind, filterSeverity], fetchResults)
watch(clusterUid, () => { fetchSummary(); fetchResults() })
onMounted(() => { fetchSummary(); fetchResults() })
</script>

<template>
  <UDashboardPanel id="cluster-eye" grow>

    <!-- ── Navbar ───────────────────────────────────────────────────────────── -->
    <template #header>
      <UDashboardNavbar title="ClusterEye Security">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <span v-if="summary?.lastScannedAt" class="text-xs text-gray-400 mr-1 hidden md:block">
            Last scan: {{ new Date(summary.lastScannedAt).toLocaleString() }}
          </span>
          <UButton
            v-if="hasPermission('scan')"
            icon="i-lucide-scan-eye"
            label="Scan Now"
            color="primary"
            size="sm"
            :loading="scanning"
            @click="triggerScan"
          />
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" square size="sm"
            :loading="loading" @click="() => { fetchSummary(); fetchResults() }" />
        </template>
      </UDashboardNavbar>

      <!-- ── Toolbar ─────────────────────────────────────────────────────── -->
      <UDashboardToolbar>
        <template #left>
          <NamespaceSelector />
          <USelectMenu
            v-model="filterKind"
            :items="kindOptions"
            value-key="value"
            label-key="label"
            size="sm"
            class="w-40"
            placeholder="All Kinds"
          />
          <USelectMenu
            v-model="filterSeverity"
            :items="severityOptions"
            value-key="value"
            label-key="label"
            size="sm"
            class="w-32"
          />
        </template>
        <template #right>
          <UInput v-model="searchText" icon="i-lucide-search" placeholder="Search workload..." size="sm" class="w-52" />
          <span class="text-xs text-gray-400">{{ filteredResults.length }} workload(s)</span>
        </template>
      </UDashboardToolbar>
    </template>

    <!-- ── Body ─────────────────────────────────────────────────────────────── -->
    <template #body>

      <!-- Permission / loading guards -->
      <div v-if="!isUserLoaded || permissionsLoading" class="flex items-center justify-center p-16">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-primary" />
      </div>
      <div v-else-if="!hasPermission('view')" class="flex flex-col items-center justify-center h-full p-12 text-center gap-4">
        <div class="p-5 rounded-full bg-red-50 dark:bg-red-900/10">
          <UIcon name="i-lucide-shield-off" class="w-12 h-12 text-red-500" />
        </div>
        <h2 class="text-xl font-bold">Access Restricted</h2>
        <p class="text-sm text-gray-500 max-w-sm">ClusterEye security data is visible only to authorized users.</p>
      </div>

      <div v-else class="flex flex-col h-full">

        <!-- Summary cards -->
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4 p-4 border-b border-gray-200 dark:border-gray-700">
          <div v-for="card in [
            { label: 'Critical',          value: summary?.critical  ?? 0, bg: 'bg-red-50 dark:bg-red-950/50 border-red-200 dark:border-red-800',       text: 'text-red-600 dark:text-red-400',       icon: 'i-lucide-shield-x' },
            { label: 'High',              value: summary?.high      ?? 0, bg: 'bg-orange-50 dark:bg-orange-950/50 border-orange-200 dark:border-orange-800', text: 'text-orange-500 dark:text-orange-400', icon: 'i-lucide-shield-alert' },
            { label: 'Medium',            value: summary?.medium    ?? 0, bg: 'bg-yellow-50 dark:bg-yellow-950/50 border-yellow-200 dark:border-yellow-800', text: 'text-yellow-600 dark:text-yellow-400', icon: 'i-lucide-alert-triangle' },
            { label: 'Workloads Scanned', value: summary?.workloads ?? 0, bg: 'bg-gray-50 dark:bg-gray-800/50 border-gray-200 dark:border-gray-700',    text: 'text-gray-700 dark:text-gray-300',      icon: 'i-lucide-boxes' },
          ]" :key="card.label" class="rounded-xl border p-4 flex items-center gap-3" :class="card.bg">
            <UIcon :name="card.icon" class="w-7 h-7 flex-shrink-0" :class="card.text" />
            <div>
              <div class="text-2xl font-bold leading-none" :class="card.text">{{ card.value }}</div>
              <div class="text-xs text-gray-500 mt-0.5">{{ card.label }}</div>
            </div>
          </div>
        </div>

        <!-- Table -->
        <div class="flex-1 overflow-auto">
          <div v-if="loading" class="flex items-center justify-center py-20">
            <UIcon name="i-lucide-loader-2" class="animate-spin w-8 h-8 text-gray-400" />
          </div>
          <table v-else-if="pagedResults.length" class="w-full text-sm">
            <thead class="sticky top-0 z-10">
              <tr class="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/80 backdrop-blur">
                <th class="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide w-1/4">Workload</th>
                <th class="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Namespace</th>
                <th class="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Kind</th>
                <th class="text-center px-4 py-3 text-xs font-semibold text-red-500 uppercase tracking-wide">Critical</th>
                <th class="text-center px-4 py-3 text-xs font-semibold text-orange-500 uppercase tracking-wide">High</th>
                <th class="text-center px-4 py-3 text-xs font-semibold text-yellow-500 uppercase tracking-wide">Medium</th>
                <th class="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide hidden lg:table-cell">Last Scanned</th>
                <th class="w-10 px-4 py-3"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in pagedResults" :key="r.id"
                class="border-b border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/40 transition-colors cursor-pointer"
                @click="openDetail(r)">
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <UIcon :name="workloadIcon(r.workloadKind)" class="w-4 h-4 text-gray-400 flex-shrink-0" />
                    <span class="font-mono font-medium text-gray-800 dark:text-gray-100 truncate max-w-[200px]">{{ r.workloadName }}</span>
                  </div>
                </td>
                <td class="px-4 py-3 text-gray-500 font-mono text-xs">{{ r.namespace }}</td>
                <td class="px-4 py-3">
                  <UBadge :color="workloadColor(r.workloadKind)" variant="soft" size="xs">{{ r.workloadKind }}</UBadge>
                </td>
                <td class="px-4 py-3 text-center">
                  <span v-if="r.criticalCount > 0" class="inline-flex items-center justify-center w-7 h-7 rounded-full bg-red-100 dark:bg-red-900/60 text-red-600 dark:text-red-400 text-xs font-bold">{{ r.criticalCount }}</span>
                  <span v-else class="text-gray-300 dark:text-gray-700 text-xs">—</span>
                </td>
                <td class="px-4 py-3 text-center">
                  <span v-if="r.highCount > 0" class="inline-flex items-center justify-center w-7 h-7 rounded-full bg-orange-100 dark:bg-orange-900/60 text-orange-600 dark:text-orange-400 text-xs font-bold">{{ r.highCount }}</span>
                  <span v-else class="text-gray-300 dark:text-gray-700 text-xs">—</span>
                </td>
                <td class="px-4 py-3 text-center">
                  <span v-if="r.mediumCount > 0" class="inline-flex items-center justify-center w-7 h-7 rounded-full bg-yellow-100 dark:bg-yellow-900/60 text-yellow-600 dark:text-yellow-400 text-xs font-bold">{{ r.mediumCount }}</span>
                  <span v-else class="text-gray-300 dark:text-gray-700 text-xs">—</span>
                </td>
                <td class="px-4 py-3 text-xs text-gray-400 whitespace-nowrap hidden lg:table-cell">
                  {{ r.lastScannedAt ? new Date(r.lastScannedAt).toLocaleString() : '—' }}
                </td>
                <td class="px-4 py-3">
                  <UIcon name="i-lucide-chevron-right" class="w-4 h-4 text-gray-400" />
                </td>
              </tr>
            </tbody>
          </table>

          <!-- Empty -->
          <div v-else class="flex flex-col items-center justify-center py-20 text-gray-400 gap-3">
            <UIcon name="i-lucide-shield-check" class="w-12 h-12" />
            <p class="text-sm font-medium">No findings match the current filters</p>
            <UButton v-if="hasPermission('scan')" size="sm" variant="soft" icon="i-lucide-scan-eye" :loading="scanning" @click="triggerScan">
              Run First Scan
            </UButton>
          </div>
        </div>

        <!-- Pagination -->
        <div v-if="totalPages > 1" class="flex items-center justify-between px-4 py-3 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900">
          <span class="text-xs text-gray-400">
            {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, filteredResults.length) }} of {{ filteredResults.length }}
          </span>
          <div class="flex items-center gap-1">
            <UButton size="xs" variant="ghost" color="neutral" icon="i-lucide-chevron-first" :disabled="page === 1" @click="page = 1" />
            <UButton size="xs" variant="ghost" color="neutral" icon="i-lucide-chevron-left"  :disabled="page === 1" @click="page--" />
            <span class="text-xs text-gray-500 px-2">{{ page }} / {{ totalPages }}</span>
            <UButton size="xs" variant="ghost" color="neutral" icon="i-lucide-chevron-right" :disabled="page === totalPages" @click="page++" />
            <UButton size="xs" variant="ghost" color="neutral" icon="i-lucide-chevron-last"  :disabled="page === totalPages" @click="page = totalPages" />
          </div>
        </div>

      </div>
    </template>
  </UDashboardPanel>

  <!-- ── Detail modal ────────────────────────────────────────────────────── -->
  <UModal v-model:open="showDetail" :ui="{ width: 'sm:max-w-2xl' }">
    <template #header>
      <div class="flex items-center gap-3 min-w-0">
        <div class="p-2 rounded-lg bg-red-50 dark:bg-red-900/20 flex-shrink-0">
          <UIcon :name="workloadIcon(selectedRow?.workloadKind)" class="w-5 h-5 text-red-500" />
        </div>
        <div class="min-w-0">
          <h3 class="font-semibold truncate">{{ selectedRow?.workloadName }}</h3>
          <div class="flex items-center gap-2 mt-0.5">
            <UBadge :color="workloadColor(selectedRow?.workloadKind)" variant="soft" size="xs">{{ selectedRow?.workloadKind }}</UBadge>
            <span class="text-xs text-gray-400 font-mono">{{ selectedRow?.namespace }}</span>
            <span class="text-xs text-gray-400">·</span>
            <span class="text-xs text-gray-400">{{ parsedFindings.length }} finding(s)</span>
          </div>
        </div>
      </div>
    </template>
    <template #body>
      <!-- Severity summary row -->
      <div class="flex gap-3 mb-4">
        <div v-for="[label, count, cls] in [
          ['Critical', selectedRow?.criticalCount ?? 0, 'bg-red-50 dark:bg-red-950/50 text-red-600 dark:text-red-400 border-red-200 dark:border-red-800'],
          ['High',     selectedRow?.highCount     ?? 0, 'bg-orange-50 dark:bg-orange-950/50 text-orange-500 dark:text-orange-400 border-orange-200 dark:border-orange-800'],
          ['Medium',   selectedRow?.mediumCount   ?? 0, 'bg-yellow-50 dark:bg-yellow-950/50 text-yellow-600 dark:text-yellow-400 border-yellow-200 dark:border-yellow-800'],
        ]" :key="label" class="flex-1 rounded-lg border px-3 py-2 text-center" :class="cls">
          <div class="text-xl font-bold">{{ count }}</div>
          <div class="text-xs opacity-70">{{ label }}</div>
        </div>
      </div>

      <!-- Finding groups -->
      <div class="space-y-4 max-h-[50vh] overflow-y-auto pr-1">
        <template v-for="[groupLabel, groupFindings, groupCls] in [
          ['Critical', criticalFindings, 'border-l-red-500'],
          ['High',     highFindings,     'border-l-orange-400'],
          ['Medium',   mediumFindings,   'border-l-yellow-400'],
        ]" :key="groupLabel">
          <div v-if="groupFindings.length">
            <div class="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-2">{{ groupLabel }}</div>
            <div class="space-y-1.5">
              <div v-for="(f, i) in groupFindings" :key="i"
                class="flex items-start gap-3 rounded-lg p-3 border border-l-4 bg-gray-50 dark:bg-gray-800/40 border-gray-100 dark:border-gray-700"
                :class="groupCls">
                <UIcon :name="findingIcon(f)" class="w-4 h-4 mt-0.5 flex-shrink-0" :class="findingColor(f)" />
                <span class="text-sm text-gray-700 dark:text-gray-300 leading-snug">
                  {{ f.replace(/^\[(CRITICAL|HIGH|MEDIUM)\]\s*/, '') }}
                </span>
              </div>
            </div>
          </div>
        </template>
        <div v-if="!parsedFindings.length" class="text-sm text-gray-400 italic text-center py-6">
          No findings recorded for this workload.
        </div>
      </div>

      <div class="flex items-center justify-between mt-4 pt-3 border-t border-gray-100 dark:border-gray-700">
        <span class="text-xs text-gray-400">
          Last scanned: {{ selectedRow?.lastScannedAt ? new Date(selectedRow.lastScannedAt).toLocaleString() : '—' }}
        </span>
        <UButton color="neutral" variant="ghost" label="Close" size="sm" @click="showDetail = false" />
      </div>
    </template>
  </UModal>
</template>
