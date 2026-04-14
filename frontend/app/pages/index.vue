<script setup lang="ts">
import type { ClusterStats, Cluster, K8sEvent } from '~/types/cluster'
import { formatDistanceToNow } from 'date-fns'
import { enUS } from 'date-fns/locale'

const clusterStore = useClusterStore()
const { $api } = useNuxtApp()
const authStore = useAuthStore()

const stats = ref<ClusterStats | null>(null)
const loading = ref(false)
const selectedCluster = computed(() => clusterStore.selectedCluster)
const events = ref<K8sEvent[]>([])
const clusterHealth = ref<{
  cpuPct: number; memPct: number
  cpuLabel: string; memLabel: string
  cpuTotalCores: number; cpuUsedCores: number
  memTotalGb: number; memUsedGb: number
}>({ cpuPct: 0, memPct: 0, cpuLabel: '0%', memLabel: '0%', cpuTotalCores: 0, cpuUsedCores: 0, memTotalGb: 0, memUsedGb: 0 })
const warningCount = ref(0)
const currentTime = ref(new Date())

let clockTimer: ReturnType<typeof setInterval>
onMounted(() => { clockTimer = setInterval(() => { currentTime.value = new Date() }, 60000) })
onUnmounted(() => clearInterval(clockTimer))

const timeGreeting = computed(() => {
  const h = currentTime.value.getHours()
  if (h < 12) return 'Good morning'
  if (h < 18) return 'Good afternoon'
  return 'Good evening'
})
const formattedTime = computed(() =>
  currentTime.value.toLocaleString('en-US', { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' })
)

const cpuPct  = computed(() => clusterHealth.value.cpuPct)
const memPct  = computed(() => clusterHealth.value.memPct)

function barColor(pct: number) {
  if (pct >= 85) return 'bg-red-500'
  if (pct >= 70) return 'bg-amber-500'
  return 'bg-emerald-500'
}

// ── Fetch ────────────────────────────────────────────────────────────────────
const fetchClusterData = async () => {
  if (!selectedCluster.value) return
  loading.value = true
  try {
    const [statsRes, healthRes, eventsRes] = await Promise.allSettled([
      $api.get<ClusterStats>(`/k8s/${selectedCluster.value.uid}/stats`),
      $api.get<any>(`/k8s/${selectedCluster.value.uid}/cluster-health`),
      $api.get<any>(`/k8s/${selectedCluster.value.uid}/namespaces/default/events`)
    ])

    if (statsRes.status === 'fulfilled') stats.value = statsRes.value.data
    if (healthRes.status === 'fulfilled') {
      const h = healthRes.value.data
      clusterHealth.value = {
        cpuPct: h.cpuPct ?? 0,
        memPct: h.memPct ?? 0,
        cpuLabel: h.cpuLabel ?? `${h.cpuPct ?? 0}%`,
        memLabel: h.memLabel ?? `${h.memPct ?? 0}%`,
        cpuTotalCores: h.cpuTotalCores ?? 0,
        cpuUsedCores:  h.cpuUsedCores ?? 0,
        memTotalGb:    h.memTotalGb ?? 0,
        memUsedGb:     h.memUsedGb ?? 0
      }
    }
    if (eventsRes.status === 'fulfilled') {
      const raw = eventsRes.value.data?.items ?? eventsRes.value.data
      const valid: K8sEvent[] = Array.isArray(raw) ? raw : []
      events.value = valid
        .sort((a: any, b: any) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
        .slice(0, 8)
      warningCount.value = valid.filter((e: any) => e.type === 'Warning').length
    }
  } catch (err) {
    console.error('Dashboard fetch error:', err)
  } finally {
    loading.value = false
  }
}

watch(selectedCluster, () => { fetchClusterData() }, { immediate: true })

// ── Stat cards ───────────────────────────────────────────────────────────────
const statCards = computed(() => [
  { label: 'Node',        value: stats.value?.totalNodes || 0,       icon: 'i-lucide-server',   accent: 'blue',   border: 'border-l-blue-500' },
  { label: 'Pod',         value: stats.value?.totalPods || 0,        icon: 'i-lucide-box',      accent: 'emerald',border: 'border-l-emerald-500' },
  { label: 'Deployment',  value: stats.value?.totalDeployments || 0, icon: 'i-lucide-layers',   accent: 'violet', border: 'border-l-violet-500' },
  { label: 'Service',     value: stats.value?.totalServices || 0,    icon: 'i-lucide-waypoints',accent: 'amber',  border: 'border-l-amber-500' },
  { label: 'Namespace',   value: stats.value?.totalNamespaces || 0,  icon: 'i-lucide-folder',   accent: 'rose',   border: 'border-l-rose-500' },
])

// ── Quick actions ─────────────────────────────────────────────────────────────
const quickActions = [
  { label: 'Pods',        icon: 'i-lucide-box',         to: '/pods',        color: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400' },
  { label: 'Deployments', icon: 'i-lucide-layers',      to: '/deployments', color: 'bg-violet-500/10 text-violet-600 dark:text-violet-400' },
  { label: 'Services',    icon: 'i-lucide-waypoints',   to: '/services',    color: 'bg-amber-500/10 text-amber-600 dark:text-amber-400' },
  { label: 'Nodes',       icon: 'i-lucide-server',      to: '/nodes',       color: 'bg-blue-500/10 text-blue-600 dark:text-blue-400' },
  { label: 'Namespaces',  icon: 'i-lucide-folder',      to: '/namespaces',  color: 'bg-rose-500/10 text-rose-600 dark:text-rose-400' },
  { label: 'Events',      icon: 'i-lucide-activity',    to: '/events',      color: 'bg-teal-500/10 text-teal-600 dark:text-teal-400' },
  { label: 'Backups',     icon: 'i-lucide-archive',     to: '/backups',     color: 'bg-orange-500/10 text-orange-600 dark:text-orange-400' },
  { label: 'Reports',     icon: 'i-lucide-bar-chart-2', to: '/reports',     color: 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400' },
]
</script>

<template>
  <UDashboardPanel id="dashboard">
    <template #header>
      <UDashboardNavbar title="Dashboard">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" size="sm" :loading="loading" @click="fetchClusterData">
            Refresh
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="p-5 space-y-5">

        <!-- ── Hero banner ───────────────────────────────────────────────── -->
        <div class="relative overflow-hidden rounded-2xl bg-gradient-to-br from-slate-800 via-slate-900 to-slate-950 dark:from-slate-900 dark:via-slate-950 dark:to-black p-6 text-white shadow-xl">
          <!-- subtle grid texture -->
          <div class="absolute inset-0 opacity-5" style="background-image:repeating-linear-gradient(0deg,transparent,transparent 39px,#fff 39px,#fff 40px),repeating-linear-gradient(90deg,transparent,transparent 39px,#fff 39px,#fff 40px)" />
          <!-- coloured glow blobs -->
          <div class="absolute -top-10 -right-10 w-48 h-48 rounded-full bg-indigo-500/20 blur-3xl" />
          <div class="absolute -bottom-8 -left-8 w-40 h-40 rounded-full bg-violet-500/20 blur-3xl" />

          <div class="relative flex flex-col md:flex-row md:items-center md:justify-between gap-4">
            <div>
              <p class="text-xs uppercase tracking-widest text-slate-400 mb-1">{{ formattedTime }}</p>
              <h1 class="text-2xl font-bold tracking-tight">
                {{ timeGreeting }}<span v-if="authStore.user">, {{ authStore.user.displayName || authStore.user.username }}</span> 👋
              </h1>
              <p class="text-sm text-slate-400 mt-1">Welcome to the Kubernetes management platform.</p>
            </div>

            <div class="flex flex-col items-start md:items-end gap-2">
              <!-- Cluster pill -->
              <div v-if="selectedCluster" class="flex items-center gap-2 rounded-full bg-white/10 border border-white/20 px-4 py-1.5 text-sm font-medium backdrop-blur-sm">
                <span class="w-2 h-2 rounded-full bg-green-400 animate-pulse" />
                {{ selectedCluster.name }}
              </div>
              <!-- Warning badge -->
              <div v-if="warningCount > 0" class="flex items-center gap-1.5 rounded-full bg-amber-500/20 border border-amber-500/40 px-3 py-1 text-xs text-amber-300">
                <UIcon name="i-lucide-alert-triangle" class="w-3.5 h-3.5" />
                {{ warningCount }} Warning Event{{ warningCount > 1 ? 's' : '' }}
              </div>
            </div>
          </div>
        </div>

        <!-- ── Stat cards ─────────────────────────────────────────────────── -->
        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
          <div
            v-for="(stat, i) in statCards" :key="stat.label"
            class="group relative overflow-hidden rounded-xl border border-gray-200 dark:border-gray-700/60 bg-white dark:bg-gray-900 p-4 shadow-sm hover:shadow-md transition-all duration-200 hover:-translate-y-0.5"
          >
            <!-- colored left accent -->
            <div class="absolute left-0 top-0 h-full w-1 rounded-l-xl transition-all duration-200 group-hover:w-1.5"
              :class="[stat.border.replace('border-l-','bg-')]" />
            <div class="flex items-start justify-between">
              <div>
                <p class="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-500">{{ stat.label }}</p>
                <p class="mt-1.5 text-3xl font-bold tracking-tight text-gray-900 dark:text-white">
                  <span v-if="loading"><USkeleton class="h-8 w-14 rounded" /></span>
                  <span v-else>{{ stat.value.toLocaleString() }}</span>
                </p>
              </div>
              <div class="rounded-lg p-2.5" :class="`bg-${stat.accent}-100 dark:bg-${stat.accent}-900/30`">
                <UIcon :name="stat.icon" class="w-5 h-5" :class="`text-${stat.accent}-600 dark:text-${stat.accent}-400`" />
              </div>
            </div>
          </div>
        </div>

        <!-- ── Main grid ───────────────────────────────────────────────────── -->
        <div class="grid lg:grid-cols-5 gap-5">

          <!-- LEFT: Cluster Health + Quick Actions  (3 cols) -->
          <div class="lg:col-span-3 space-y-5">

            <!-- Cluster Health -->
            <div class="rounded-xl border border-gray-200 dark:border-gray-700/60 bg-white dark:bg-gray-900 overflow-hidden shadow-sm">
              <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100 dark:border-gray-800">
                <div class="flex items-center gap-2.5">
                  <div class="p-1.5 rounded-lg bg-emerald-100 dark:bg-emerald-900/30">
                    <UIcon name="i-lucide-heart-pulse" class="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
                  </div>
                  <span class="font-semibold text-gray-800 dark:text-gray-100">Cluster Health</span>
                </div>
                <div class="flex items-center gap-1.5 text-xs font-medium"
                  :class="cpuPct >= 85 || memPct >= 85 ? 'text-red-600' : 'text-emerald-600 dark:text-emerald-400'">
                  <span class="w-2 h-2 rounded-full animate-pulse"
                    :class="cpuPct >= 85 || memPct >= 85 ? 'bg-red-500' : 'bg-emerald-500'" />
                  {{ cpuPct >= 85 || memPct >= 85 ? 'Attention Needed' : 'Healthy' }}
                </div>
              </div>
              <div class="p-5 space-y-5">
                <!-- CPU -->
                <div>
                  <div class="flex justify-between text-sm mb-1.5">
                    <span class="flex items-center gap-1.5 text-gray-600 dark:text-gray-400 font-medium">
                      <UIcon name="i-lucide-cpu" class="w-4 h-4" /> CPU Usage
                    </span>
                    <div class="text-right">
                      <span class="font-bold" :class="cpuPct>=85?'text-red-600':cpuPct>=70?'text-amber-600':'text-gray-900 dark:text-white'">
                        {{ clusterHealth.cpuLabel }}
                      </span>
                      <span class="ml-1.5 text-xs text-gray-400">({{ cpuPct }}%)</span>
                    </div>
                  </div>
                  <div class="h-2.5 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
                    <div class="h-full rounded-full transition-all duration-700" :class="barColor(cpuPct)" :style="`width:${cpuPct}%`" />
                  </div>
                </div>
                <!-- Memory -->
                <div>
                  <div class="flex justify-between text-sm mb-1.5">
                    <span class="flex items-center gap-1.5 text-gray-600 dark:text-gray-400 font-medium">
                      <UIcon name="i-lucide-memory-stick" class="w-4 h-4" /> Memory Usage
                    </span>
                    <div class="text-right">
                      <span class="font-bold" :class="memPct>=85?'text-red-600':memPct>=70?'text-amber-600':'text-gray-900 dark:text-white'">
                        {{ clusterHealth.memLabel }}
                      </span>
                      <span class="ml-1.5 text-xs text-gray-400">({{ memPct }}%)</span>
                    </div>
                  </div>
                  <div class="h-2.5 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
                    <div class="h-full rounded-full transition-all duration-700" :class="barColor(memPct)" :style="`width:${memPct}%`" />
                  </div>
                </div>
                <!-- Pods summary row -->
                <div class="flex items-center gap-3 pt-1 border-t border-gray-100 dark:border-gray-800">
                  <UIcon name="i-lucide-box" class="w-4 h-4 text-gray-400" />
                  <span class="text-sm text-gray-500">Total Pods</span>
                  <span class="text-sm font-bold text-gray-900 dark:text-white">{{ stats?.totalPods ?? '—' }}</span>
                  <span class="mx-2 text-gray-200 dark:text-gray-700">|</span>
                  <UIcon name="i-lucide-layers" class="w-4 h-4 text-gray-400" />
                  <span class="text-sm text-gray-500">Deployments</span>
                  <span class="text-sm font-bold text-gray-900 dark:text-white">{{ stats?.totalDeployments ?? '—' }}</span>
                </div>
              </div>
            </div>

            <!-- Quick Actions -->
            <div class="rounded-xl border border-gray-200 dark:border-gray-700/60 bg-white dark:bg-gray-900 overflow-hidden shadow-sm">
              <div class="flex items-center gap-2.5 px-5 py-4 border-b border-gray-100 dark:border-gray-800">
                <div class="p-1.5 rounded-lg bg-indigo-100 dark:bg-indigo-900/30">
                  <UIcon name="i-lucide-zap" class="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
                </div>
                <span class="font-semibold text-gray-800 dark:text-gray-100">Quick Access</span>
              </div>
              <div class="p-4 grid grid-cols-4 gap-2">
                <NuxtLink v-for="action in quickActions" :key="action.label" :to="action.to"
                  class="flex flex-col items-center gap-1.5 rounded-xl p-3 hover:bg-gray-50 dark:hover:bg-gray-800/60 transition-all duration-150 hover:-translate-y-0.5 group cursor-pointer">
                  <div class="rounded-xl p-2.5 transition-all group-hover:scale-110" :class="action.color.split(' ').slice(0,1)">
                    <UIcon :name="action.icon" class="w-5 h-5" :class="action.color.split(' ').slice(1).join(' ')" />
                  </div>
                  <span class="text-xs font-medium text-gray-600 dark:text-gray-400 text-center">{{ action.label }}</span>
                </NuxtLink>
              </div>
            </div>
          </div>

          <!-- RIGHT: Recent Events (2 cols) -->
          <div class="lg:col-span-2">
            <div class="rounded-xl border border-gray-200 dark:border-gray-700/60 bg-white dark:bg-gray-900 overflow-hidden shadow-sm h-full">
              <div class="flex items-center justify-between px-5 py-4 border-b border-gray-100 dark:border-gray-800">
                <div class="flex items-center gap-2.5">
                  <div class="p-1.5 rounded-lg bg-blue-100 dark:bg-blue-900/30">
                    <UIcon name="i-lucide-activity" class="w-4 h-4 text-blue-600 dark:text-blue-400" />
                  </div>
                  <span class="font-semibold text-gray-800 dark:text-gray-100">Recent Events</span>
                </div>
                <NuxtLink to="/events">
                  <UButton variant="ghost" color="neutral" size="xs" trailing-icon="i-lucide-arrow-right">View All</UButton>
                </NuxtLink>
              </div>

              <!-- loading skeleton -->
              <div v-if="loading" class="p-4 space-y-3">
                <USkeleton v-for="i in 5" :key="i" class="h-14 rounded-lg" />
              </div>

              <!-- empty state -->
              <div v-else-if="events.length === 0" class="flex flex-col items-center justify-center py-16 gap-3 text-gray-400">
                <UIcon name="i-lucide-inbox" class="w-10 h-10" />
                <p class="text-sm">No events found</p>
              </div>

              <!-- event list -->
              <div v-else class="divide-y divide-gray-50 dark:divide-gray-800/60 max-h-[480px] overflow-y-auto">
                <div v-for="event in events" :key="(event as any).uid || (event as any).id"
                  class="flex items-start gap-3 px-5 py-3.5 hover:bg-gray-50/70 dark:hover:bg-gray-800/40 transition-colors">
                  <!-- type icon -->
                  <div class="mt-0.5 flex-shrink-0 w-7 h-7 rounded-full flex items-center justify-center"
                    :class="event.type === 'Warning' ? 'bg-amber-100 dark:bg-amber-900/30' : 'bg-blue-100 dark:bg-blue-900/30'">
                    <UIcon
                      :name="event.type === 'Warning' ? 'i-lucide-alert-triangle' : 'i-lucide-info'"
                      class="w-3.5 h-3.5"
                      :class="event.type === 'Warning' ? 'text-amber-600 dark:text-amber-400' : 'text-blue-600 dark:text-blue-400'"
                    />
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="text-xs font-medium text-gray-800 dark:text-gray-100 line-clamp-2 leading-snug">{{ event.message }}</p>
                    <div class="flex items-center gap-1.5 mt-1">
                      <UBadge size="xs" :color="event.type === 'Warning' ? 'amber' : 'blue'" variant="subtle">{{ event.reason || '—' }}</UBadge>
                      <span class="text-[10px] text-gray-400 truncate">{{ event.involvedObjectKind }}/{{ event.involvedObjectName }}</span>
                    </div>
                  </div>
                  <span class="text-[10px] text-gray-400 whitespace-nowrap mt-0.5">
                    {{ formatDistanceToNow(new Date(event.createdAt), { addSuffix: true, locale: enUS }) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

      </div>
    </template>
  </UDashboardPanel>
</template>

<style scoped>
.line-clamp-2 { display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden; }
</style>
