<script setup lang="ts">
import * as XLSX from 'xlsx'

definePageMeta({ layout: 'default' })

const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
// ⚠️ composable returns 'loading', rename to avoid clash with our api loading ref
const { hasPermission, loading: permissionsLoading } = usePagePermissions('Report')
const toast = useToast()

const loading = ref(true)   // start true → no flash of empty state before first fetch
const report  = ref<any>(null)
const apiError = ref<string>('')
const generatedAt = ref<string>('')

const clusterUid = computed(() => clusterStore.selectedCluster?.uid)

async function generateReport() {
  if (!clusterUid.value) {
    loading.value = false
    return
  }
  loading.value = true
  report.value  = null
  apiError.value = ''
  try {
    const res = await $api.get(`/k8s/${clusterUid.value}/reports/summary`)
    report.value    = res.data
    generatedAt.value = new Date().toLocaleString('en-US')
  } catch (e: any) {
    const msg = e.response?.data?.error
      || e.response?.data?.message
      || e.message
      || 'Report could not be retrieved from server'
    apiError.value = msg
    toast.add({ title: 'Error', description: msg, color: 'red' })
  } finally {
    loading.value = false
  }
}

function printReport() { window.print() }

onMounted(() => generateReport())
watch(clusterUid, () => generateReport())

// ── Helpers ──────────────────────────────────────────────────────────────────
const totalPods = computed(() => {
  if (!report.value) return 0
  const h = report.value.podHealth
  return (h.running ?? 0) + (h.pending ?? 0) + (h.failed ?? 0) + (h.completed ?? 0) + (h.unknown ?? 0)
})
const podHealthPercent = computed(() => {
  if (!totalPods.value) return 0
  const h = report.value.podHealth
  // Running + Completed (Succeeded) pods are both healthy
  const healthy = (h.running ?? 0) + (h.completed ?? 0)
  return Math.round((healthy / totalPods.value) * 100)
})
const backupSuccessColor = computed(() => {
  const r = report.value?.backupStatus?.successRate ?? 100
  if (r >= 90) return 'text-green-600 dark:text-green-400'
  if (r >= 70) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})
function phaseColor(phase: string) {
  return { Running: 'green', Pending: 'yellow', Failed: 'red', Completed: 'blue', Unknown: 'gray' }[phase] ?? 'gray'
}
function nodeColor(key: string) { return key === 'ready' ? 'green' : 'red' }
function formatTs(ts: string) { try { return new Date(ts).toLocaleString('en-US') } catch { return ts } }

// ── Recent Events Pagination ──────────────────────────────────────────────────
const eventsPage = ref(1)
const eventsPageSize = 10
const pagedEvents = computed(() => {
  const logs = report.value?.auditSummary?.recentLogs ?? []
  const start = (eventsPage.value - 1) * eventsPageSize
  return logs.slice(start, start + eventsPageSize)
})
const eventsTotalPages = computed(() =>
  Math.ceil((report.value?.auditSummary?.recentLogs?.length ?? 0) / eventsPageSize)
)
watch(() => report.value?.auditSummary?.recentLogs, () => { eventsPage.value = 1 })

// ── Excel Export ─────────────────────────────────────────────────────────────
const exporting = ref(false)

async function exportToExcel() {
  if (!report.value || !clusterUid.value) return
  const r = report.value
  exporting.value = true
  try {
    const wb = XLSX.utils.book_new()

    // Sheet 1 — Overview
    const overviewData = [
      ['Cluster Health Report'],
      ['Cluster', r.clusterName],
      ['Generated At', generatedAt.value],
      ['Pod Health %', `${podHealthPercent.value}%`],
      ['Terminal Sessions', r.terminalSessions],
      [],
      ['RESOURCE OVERVIEW'],
      ['Nodes', r.resources?.nodes ?? ''],
      ['Namespaces', r.resources?.namespaces ?? ''],
      ['Pods', r.resources?.pods ?? ''],
      ['Deployments', r.resources?.deployments ?? ''],
      ['Services', r.resources?.services ?? ''],
      [],
      ['POD HEALTH'],
      ['Running',   r.podHealth?.running   ?? 0],
      ['Pending',   r.podHealth?.pending   ?? 0],
      ['Failed',    r.podHealth?.failed    ?? 0],
      ['Completed', r.podHealth?.completed ?? 0],
      ['Unknown',   r.podHealth?.unknown   ?? 0],
      ['Total', totalPods.value],
      [],
      ['NODE HEALTH'],
      ['Ready', r.nodeHealth?.ready ?? 0],
      ['Not Ready', r.nodeHealth?.notReady ?? 0],
      [],
      ['BACKUP STATUS'],
      ['Total Backups', r.backupStatus?.total ?? 0],
      ['Successful', r.backupStatus?.completed ?? 0],
      ['Failed', r.backupStatus?.failed ?? 0],
      ['Success Rate', `${r.backupStatus?.successRate ?? 0}%`],
      ['Last Backup', r.backupStatus?.lastTime ? formatTs(r.backupStatus.lastTime) : 'N/A'],
      ['Last Status', r.backupStatus?.lastStatus ?? 'N/A'],
    ]
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(overviewData), 'Overview')

    // Sheet 2 — Top Users
    if (r.auditSummary?.topUsers?.length) {
      const usersData = [['Rank', 'Username', 'Action Count'], ...r.auditSummary.topUsers.map((u: any, i: number) => [i + 1, u.username, u.count])]
      XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(usersData), 'Top Users (30d)')
    }

    // Sheet 3 — Top Actions
    if (r.auditSummary?.topActions?.length) {
      const actionsData = [['Rank', 'Action', 'Count'], ...r.auditSummary.topActions.map((a: any, i: number) => [i + 1, a.action, a.count])]
      XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(actionsData), 'Top Actions (30d)')
    }

    // Sheet 4 — Recent Audit Logs
    if (r.auditSummary?.recentLogs?.length) {
      const logsData = [
        ['Username', 'Action', 'Details', 'Timestamp'],
        ...r.auditSummary.recentLogs.map((l: any) => [l.username, l.action, l.details, formatTs(l.timestamp)])
      ]
      XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(logsData), 'Recent Audit Logs')
    }

    // Sheets 5-9 — Resource detail lists from DB
    try {
      const detailRes = await $api.get(`/k8s/${clusterUid.value}/reports/resource-details`)
      const d = detailRes.data

      if (d.nodes?.length) {
        const rows = [['Name', 'Status', 'Roles', 'CPU', 'Memory', 'Created At'],
          ...d.nodes.map((n: any) => [n.name, n.status, n.roles, n.cpu, n.memory, n.createdAt])]
        XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(rows), 'Nodes')
      }

      if (d.namespaces?.length) {
        const rows = [['Name', 'Status', 'Created At'],
          ...d.namespaces.map((n: any) => [n.name, n.status, n.createdAt])]
        XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(rows), 'Namespaces')
      }

      if (d.pods?.length) {
        const rows = [['Name', 'Namespace', 'Phase', 'Node', 'Created At'],
          ...d.pods.map((p: any) => [p.name, p.namespace, p.phase, p.nodeName, p.createdAt])]
        XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(rows), 'Pods')
      }

      if (d.deployments?.length) {
        const rows = [['Name', 'Namespace', 'Replicas', 'Available', 'Created At'],
          ...d.deployments.map((dep: any) => [dep.name, dep.namespace, dep.replicas, dep.availableReplicas, dep.createdAt])]
        XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(rows), 'Deployments')
      }

      if (d.services?.length) {
        const rows = [['Name', 'Namespace', 'Type', 'Cluster IP', 'Ports', 'Created At'],
          ...d.services.map((s: any) => [s.name, s.namespace, s.type, s.clusterIp, s.ports, s.createdAt])]
        XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(rows), 'Services')
      }
    } catch (e) {
      // resource detail fetch failure should not block the export
      toast.add({ title: 'Warning', description: 'Resource detail sheets could not be loaded', color: 'yellow' })
    }

    const filename = `cluster-report-${r.clusterName}-${new Date().toISOString().slice(0, 10)}.xlsx`
    XLSX.writeFile(wb, filename)
    toast.add({ title: 'Excel exported', description: filename, color: 'green' })
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <UDashboardPanel id="reports">
    <template #header>
      <UDashboardNavbar title="Cluster Report">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton v-if="report" icon="i-lucide-file-spreadsheet" color="success" variant="soft" label="Export Excel" :loading="exporting" @click="exportToExcel" />
          <UButton v-if="report" icon="i-lucide-printer" color="neutral" variant="ghost" label="Print" @click="printReport" />
          <UButton icon="i-lucide-refresh-cw" :loading="loading" color="primary" label="Refresh Report" @click="generateReport" />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div v-if="permissionsLoading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>

      <template v-else-if="hasPermission('view')">
        <!-- loading skeleton -->
        <div v-if="loading" class="p-6 space-y-6">
          <USkeleton class="h-24 rounded-2xl" />
          <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
            <USkeleton v-for="i in 5" :key="i" class="h-28 rounded-xl" />
          </div>
          <div class="grid md:grid-cols-2 gap-4">
            <USkeleton class="h-48 rounded-xl" /><USkeleton class="h-48 rounded-xl" />
          </div>
        </div>

        <!-- ── API Error banner ──────────────────────────────────────────── -->
        <div v-else-if="apiError" class="m-6 flex items-start gap-3 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 p-5 text-red-700 dark:text-red-300">
          <UIcon name="i-lucide-circle-x" class="w-6 h-6 flex-shrink-0 mt-0.5" />
          <div class="flex-1">
            <p class="font-semibold text-red-800 dark:text-red-200">Report could not be loaded</p>
            <p class="text-sm mt-1 text-red-600 dark:text-red-300 font-mono break-all">{{ apiError }}</p>
          </div>
          <UButton size="sm" color="red" variant="soft" icon="i-lucide-refresh-cw" label="Try Again" @click="generateReport" />
        </div>

        <!-- report content -->
        <div v-else-if="report" class="p-6 space-y-6 print:p-4 print:space-y-4">

          <!-- ── Header banner ──────────────────────────────────────────── -->
          <div class="relative overflow-hidden rounded-2xl bg-gradient-to-br from-indigo-600 via-purple-600 to-violet-700 p-6 text-white shadow-xl print:shadow-none">
            <div class="absolute inset-0 opacity-10 bg-[radial-gradient(circle_at_80%_20%,white,transparent)]" />
            <div class="relative flex flex-col md:flex-row md:items-center md:justify-between gap-3">
              <div>
                <p class="text-xs uppercase tracking-widest text-indigo-200 mb-1">Cluster Health Report</p>
                <h1 class="text-2xl font-bold">{{ report.clusterName }}</h1>
                <p class="text-sm text-indigo-200 mt-1">Report generated: {{ generatedAt }}</p>
              </div>
              <div class="flex flex-col items-end gap-2">
                <div class="flex items-center gap-2 bg-white/20 rounded-full px-4 py-1.5 text-sm font-medium">
                  <span class="inline-block w-2 h-2 rounded-full" :class="podHealthPercent >= 90 ? 'bg-green-300' : podHealthPercent >= 70 ? 'bg-yellow-300' : 'bg-red-300'" />
                  {{ podHealthPercent }}% Pods Healthy
                </div>
                <div class="text-xs text-indigo-200">{{ report.terminalSessions }} terminal session{{ report.terminalSessions !== 1 ? 's' : '' }}</div>
              </div>
            </div>
          </div>

          <!-- ── Resource overview cards ─────────────────────────────────── -->
          <div>
            <h2 class="text-xs font-semibold uppercase tracking-widest text-gray-500 dark:text-gray-400 mb-3">Resource Overview</h2>
            <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-4">
              <div v-for="[key, icon, label, color] in [
                ['nodes','i-lucide-server','Node','blue'],
                ['namespaces','i-lucide-folder','Namespace','violet'],
                ['pods','i-lucide-box','Pod','emerald'],
                ['deployments','i-lucide-layers','Deployment','amber'],
                ['services','i-lucide-waypoints','Service','rose']
              ]" :key="key"
                class="relative overflow-hidden rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm hover:shadow-md transition-shadow">
                <div class="absolute top-0 left-0 w-1 h-full rounded-l-xl" :class="`bg-${color}-500`" />
                <UIcon :name="icon as string" :class="`w-6 h-6 text-${color}-500 mb-2`" />
                <p class="text-3xl font-bold text-gray-900 dark:text-white">{{ report.resources[key as string] }}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ label }}</p>
              </div>
            </div>
          </div>

          <!-- ── Pod Health + Node Health ────────────────────────────────── -->
          <div class="grid md:grid-cols-2 gap-4">
            <!-- Pod Health -->
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-5 shadow-sm">
              <div class="flex items-center gap-2 mb-4">
                <UIcon name="i-lucide-activity" class="w-5 h-5 text-indigo-500" />
                <h3 class="font-semibold text-gray-800 dark:text-gray-100">Pod Health</h3>
              </div>
              <!-- progress bar -->
              <div class="w-full h-3 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden flex mb-4">
                <div class="h-full bg-green-500 transition-all" :style="`width:${totalPods ? Math.round(((report.podHealth.running??0)/totalPods)*100) : 0}%`" />
                <div class="h-full bg-yellow-400 transition-all" :style="`width:${totalPods ? Math.round(((report.podHealth.pending??0)/totalPods)*100) : 0}%`" />
                <div class="h-full bg-red-500 transition-all"    :style="`width:${totalPods ? Math.round(((report.podHealth.failed??0)/totalPods)*100) : 0}%`" />
                <div class="h-full bg-blue-400 transition-all"   :style="`width:${totalPods ? Math.round(((report.podHealth.completed??0)/totalPods)*100) : 0}%`" />
                <div class="h-full bg-gray-400 transition-all"   :style="`width:${totalPods ? Math.round(((report.podHealth.unknown??0)/totalPods)*100) : 0}%`" />
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div v-for="[phase, val] in [
                    ['Running',   report.podHealth.running   ?? 0],
                    ['Pending',   report.podHealth.pending   ?? 0],
                    ['Failed',    report.podHealth.failed    ?? 0],
                    ['Completed', report.podHealth.completed ?? 0],
                    ['Unknown',   report.podHealth.unknown   ?? 0]
                  ]"
                  :key="phase" class="flex items-center justify-between rounded-lg px-3 py-2 bg-gray-50 dark:bg-gray-800">
                  <div class="flex items-center gap-2">
                    <span class="inline-block w-2.5 h-2.5 rounded-full" :class="{
                      'bg-green-500':  phase==='Running',
                      'bg-yellow-400': phase==='Pending',
                      'bg-red-500':    phase==='Failed',
                      'bg-blue-400':   phase==='Completed',
                      'bg-gray-400':   phase==='Unknown'
                    }" />
                    <span class="text-sm text-gray-600 dark:text-gray-300">{{ phase }}</span>
                  </div>
                  <span class="font-bold text-gray-900 dark:text-white text-sm">{{ val }}</span>
                </div>
              </div>
            </div>

            <!-- Node Health -->
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-5 shadow-sm">
              <div class="flex items-center gap-2 mb-4">
                <UIcon name="i-lucide-server" class="w-5 h-5 text-blue-500" />
                <h3 class="font-semibold text-gray-800 dark:text-gray-100">Node Status</h3>
              </div>
              <div class="flex items-center justify-center gap-8 py-4">
                <div class="text-center">
                  <p class="text-4xl font-bold text-green-600 dark:text-green-400">{{ report.nodeHealth.ready }}</p>
                  <p class="text-sm text-gray-500 mt-1">Ready</p>
                </div>
                <div class="w-px h-16 bg-gray-200 dark:bg-gray-700" />
                <div class="text-center">
                  <p class="text-4xl font-bold text-red-600 dark:text-red-400">{{ report.nodeHealth.notReady }}</p>
                  <p class="text-sm text-gray-500 mt-1">Not Ready</p>
                </div>
              </div>
              <div v-if="report.nodeHealth.notReady > 0" class="mt-2 flex items-center gap-2 rounded-lg bg-red-50 dark:bg-red-900/20 p-3 text-sm text-red-700 dark:text-red-300">
                <UIcon name="i-lucide-alert-triangle" class="w-4 h-4 flex-shrink-0" />
                {{ report.nodeHealth.notReady }} node{{ report.nodeHealth.notReady > 1 ? 's' : '' }} not Ready — urgent attention required!
              </div>
            </div>
          </div>

          <!-- ── Backup Status ───────────────────────────────────────────── -->
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-5 shadow-sm">
            <div class="flex items-center gap-2 mb-4">
              <UIcon name="i-lucide-archive" class="w-5 h-5 text-amber-500" />
              <h3 class="font-semibold text-gray-800 dark:text-gray-100">Backup Status</h3>
            </div>
            <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div class="text-center p-3 rounded-lg bg-gray-50 dark:bg-gray-800">
                <p class="text-2xl font-bold text-gray-900 dark:text-white">{{ report.backupStatus.total }}</p>
                <p class="text-xs text-gray-500 mt-1">Total Backups</p>
              </div>
              <div class="text-center p-3 rounded-lg bg-green-50 dark:bg-green-900/20">
                <p class="text-2xl font-bold text-green-600 dark:text-green-400">{{ report.backupStatus.completed }}</p>
                <p class="text-xs text-gray-500 mt-1">Successful</p>
              </div>
              <div class="text-center p-3 rounded-lg bg-red-50 dark:bg-red-900/20">
                <p class="text-2xl font-bold text-red-600 dark:text-red-400">{{ report.backupStatus.failed }}</p>
                <p class="text-xs text-gray-500 mt-1">Failed</p>
              </div>
              <div class="text-center p-3 rounded-lg bg-gray-50 dark:bg-gray-800">
                <p class="text-2xl font-bold" :class="backupSuccessColor">{{ report.backupStatus.successRate }}%</p>
                <p class="text-xs text-gray-500 mt-1">Success Rate</p>
              </div>
            </div>
            <div v-if="report.backupStatus.lastTime" class="mt-3 flex items-center gap-2 text-sm text-gray-500">
              <UIcon name="i-lucide-clock" class="w-4 h-4" />
              Last backup: <span class="font-medium text-gray-700 dark:text-gray-300">{{ formatTs(report.backupStatus.lastTime) }}</span>
              <UBadge :color="report.backupStatus.lastStatus === 'COMPLETED' ? 'green' : 'red'" variant="subtle" size="sm">{{ report.backupStatus.lastStatus }}</UBadge>
            </div>
            <div v-else class="mt-3 text-sm text-gray-400 italic">No backups yet.</div>
          </div>

          <!-- ── Audit Activity ──────────────────────────────────────────── -->
          <div class="grid md:grid-cols-2 gap-4">
            <!-- Top Users -->
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-5 shadow-sm">
              <div class="flex items-center justify-between mb-4">
                <div class="flex items-center gap-2">
                  <UIcon name="i-lucide-users" class="w-5 h-5 text-violet-500" />
                  <h3 class="font-semibold text-gray-800 dark:text-gray-100">Most Active Users</h3>
                </div>
                <UBadge color="neutral" variant="subtle" size="sm">Last 30 days</UBadge>
              </div>
              <div v-if="report.auditSummary.topUsers.length" class="space-y-2">
                <div v-for="(u, i) in report.auditSummary.topUsers" :key="u.username"
                  class="flex items-center gap-3">
                  <span class="w-5 h-5 rounded-full text-xs font-bold flex items-center justify-center"
                    :class="i===0 ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 dark:bg-gray-800 text-gray-500'">
                    {{ i + 1 }}
                  </span>
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center justify-between mb-0.5">
                      <span class="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">{{ u.username }}</span>
                      <span class="text-xs text-gray-500">{{ u.count }}</span>
                    </div>
                    <div class="w-full h-1.5 rounded-full bg-gray-100 dark:bg-gray-800">
                      <div class="h-full rounded-full bg-violet-500 transition-all"
                        :style="`width:${Math.round((u.count / report.auditSummary.topUsers[0].count) * 100)}%`" />
                    </div>
                  </div>
                </div>
              </div>
              <p v-else class="text-sm text-gray-400 italic">No activity in the last 30 days.</p>
              <div class="mt-4 pt-3 border-t border-gray-100 dark:border-gray-800 flex items-center justify-between text-xs text-gray-400">
                <span>Total platform actions</span>
                <span class="font-semibold text-gray-700 dark:text-gray-300">{{ report.auditSummary.totalEvents.toLocaleString() }}</span>
              </div>
            </div>

            <!-- Top Actions -->
            <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-5 shadow-sm">
              <div class="flex items-center justify-between mb-4">
                <div class="flex items-center gap-2">
                  <UIcon name="i-lucide-zap" class="w-5 h-5 text-rose-500" />
                  <h3 class="font-semibold text-gray-800 dark:text-gray-100">Most Frequent Operations</h3>
                </div>
                <UBadge color="neutral" variant="subtle" size="sm">Last 30 days</UBadge>
              </div>
              <div v-if="report.auditSummary.topActions.length" class="space-y-2">
                <div v-for="(a, i) in report.auditSummary.topActions" :key="a.action"
                  class="flex items-center gap-3">
                  <span class="w-5 h-5 rounded-full text-xs font-bold flex items-center justify-center"
                    :class="i===0 ? 'bg-rose-100 text-rose-700' : 'bg-gray-100 dark:bg-gray-800 text-gray-500'">
                    {{ i + 1 }}
                  </span>
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center justify-between mb-0.5">
                      <span class="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">{{ a.action }}</span>
                      <span class="text-xs text-gray-500">{{ a.count }}</span>
                    </div>
                    <div class="w-full h-1.5 rounded-full bg-gray-100 dark:bg-gray-800">
                      <div class="h-full rounded-full bg-rose-500 transition-all"
                        :style="`width:${Math.round((a.count / report.auditSummary.topActions[0].count) * 100)}%`" />
                    </div>
                  </div>
                </div>
              </div>
              <p v-else class="text-sm text-gray-400 italic">No activity in the last 30 days.</p>
            </div>
          </div>

          <!-- ── Recent Audit Logs ───────────────────────────────────────── -->
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-5 shadow-sm">
            <div class="flex items-center justify-between mb-4">
              <div class="flex items-center gap-2">
                <UIcon name="i-lucide-scroll-text" class="w-5 h-5 text-teal-500" />
                <h3 class="font-semibold text-gray-800 dark:text-gray-100">Recent Platform Events</h3>
              </div>
              <span class="text-xs text-gray-400">{{ report.auditSummary.recentLogs.length }} events</span>
            </div>
            <div v-if="report.auditSummary.recentLogs.length" class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-gray-100 dark:border-gray-800">
                    <th class="text-left py-2 px-3 font-medium text-gray-500 text-xs">User</th>
                    <th class="text-left py-2 px-3 font-medium text-gray-500 text-xs">Action</th>
                    <th class="text-left py-2 px-3 font-medium text-gray-500 text-xs">Details</th>
                    <th class="text-left py-2 px-3 font-medium text-gray-500 text-xs">Time</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="log in pagedEvents" :key="log.timestamp + log.action"
                    class="border-b border-gray-50 dark:border-gray-800/50 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                    <td class="py-2 px-3">
                      <span class="inline-flex items-center gap-1.5 font-medium text-gray-800 dark:text-gray-100">
                        <span class="w-2 h-2 rounded-full bg-teal-400 flex-shrink-0" />{{ log.username }}
                      </span>
                    </td>
                    <td class="py-2 px-3"><UBadge color="neutral" variant="subtle" size="sm">{{ log.action }}</UBadge></td>
                    <td class="py-2 px-3 text-gray-500 max-w-xs truncate">{{ log.details }}</td>
                    <td class="py-2 px-3 text-gray-400 text-xs whitespace-nowrap">{{ formatTs(log.timestamp) }}</td>
                  </tr>
                </tbody>
              </table>
              <!-- Pagination -->
              <div v-if="eventsTotalPages > 1" class="flex items-center justify-between mt-3 pt-3 border-t border-gray-100 dark:border-gray-800">
                <span class="text-xs text-gray-400">
                  Page {{ eventsPage }} of {{ eventsTotalPages }}
                  ({{ (eventsPage - 1) * eventsPageSize + 1 }}–{{ Math.min(eventsPage * eventsPageSize, report.auditSummary.recentLogs.length) }})
                </span>
                <div class="flex items-center gap-1">
                  <UButton size="xs" variant="ghost" color="neutral" icon="i-lucide-chevron-first" :disabled="eventsPage === 1" @click="eventsPage = 1" />
                  <UButton size="xs" variant="ghost" color="neutral" icon="i-lucide-chevron-left" :disabled="eventsPage === 1" @click="eventsPage--" />
                  <UButton size="xs" variant="ghost" color="neutral" icon="i-lucide-chevron-right" :disabled="eventsPage === eventsTotalPages" @click="eventsPage++" />
                  <UButton size="xs" variant="ghost" color="neutral" icon="i-lucide-chevron-last" :disabled="eventsPage === eventsTotalPages" @click="eventsPage = eventsTotalPages" />
                </div>
              </div>
            </div>
            <p v-else class="text-sm text-gray-400 italic">No events recorded for this cluster.</p>
          </div>

        </div>

        <!-- no cluster selected -->
        <div v-else class="flex flex-col items-center justify-center h-64 gap-3 text-gray-400">
          <UIcon name="i-lucide-monitor-off" class="w-12 h-12" />
          <p class="font-medium text-gray-500">Please select a cluster from the left menu.</p>
        </div>

      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 gap-3 text-gray-500">
        <UIcon name="i-lucide-lock" class="w-12 h-12" />
        <p class="text-lg font-semibold">You do not have permission to view this page.</p>
      </div>
    </template>
  </UDashboardPanel>
</template>

<style scoped>
@media print {
  .print\:shadow-none { box-shadow: none !important; }
  .print\:p-4 { padding: 1rem !important; }
  .print\:space-y-4 > * + * { margin-top: 1rem !important; }
}
</style>
