<script setup lang="ts">
import type { K8sEvent } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('Event')

const events = ref<K8sEvent[]>([])
const filteredEvents = computed(() => filterByName(events.value))
const loading = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

/* =========================
   TABLE COLUMNS
========================= */
const columns = [
  { id: 'lastSeen', key: 'lastSeen', label: 'Last Seen', sortable: true },
  { id: 'type', key: 'type', label: 'Type', sortable: true },
  { id: 'reason', key: 'reason', label: 'Reason', sortable: true },
  { id: 'object', key: 'involvedObjectKind', label: 'Object', sortable: true },
  { id: 'message', key: 'message', label: 'Message' },
  { id: 'count', key: 'count', label: 'Count', sortable: true }
]

/* =========================
   FETCH EVENTS
========================= */
const fetchEvents = async () => {
  if (!selectedCluster.value || !hasPermission('view')) return

  loading.value = true
  try {
    const result = await k8s.fetchEvents(undefined)
    events.value = result || []
  } catch (error: any) {
    console.error('Fetch events error:', error)
    toast.add({
      title: 'Failed to fetch Events',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

/* =========================
   HELPERS
========================= */
const formatAge = (timestamp: string) => {
  if (!timestamp) return '-'

  const now = Date.now()
  const seen = new Date(timestamp).getTime()
  const diff = Math.floor((now - seen) / 1000)

  if (diff < 60) return `${diff}s`
  if (diff < 3600) return `${Math.floor(diff / 60)}m`
  if (diff < 86400) return `${Math.floor(diff / 3600)}h`
  return `${Math.floor(diff / 86400)}d`
}

const eventTypeColor = (type: string) => {
  switch (type) {
    case 'Warning':
      return 'red'
    case 'Normal':
      return 'green'
    default:
      return 'gray'
  }
}

/* =========================
   WATCHERS
========================= */
watch([selectedCluster, selectedNamespace], () => {
  if (!permissionsLoading.value) {
    fetchEvents()
  }
}, { immediate: true })

// Reload data when permissions are loaded
watch(permissionsLoading, (loading) => {
  if (!loading && hasPermission('view')) {
    fetchEvents()
  }
})
</script>

<template>
  <UDashboardPanel id="events">
    <!-- ================= HEADER ================= -->
    <template #header>
      <UDashboardNavbar title="Events">
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
            @click="fetchEvents"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar v-if="hasPermission('view')">
        <template #left>
          <NamespaceSelector />
          <div class="border-l border-gray-200 dark:border-gray-700 mx-2" />
          <UBadge color="neutral" variant="subtle">
            {{ events.length }} events
          </UBadge>
        </template>
      </UDashboardToolbar>
    </template>

    <!-- ================= BODY ================= -->
    <template #body>
      <template v-if="permissionsLoading">
        <div class="flex items-center justify-center h-full">
          <ULoader />
        </div>
      </template>
      <template v-else-if="!hasPermission('view')">
        <div class="flex flex-col items-center justify-center h-full space-y-4">
          <UIcon name="i-lucide-shield-off" class="w-12 h-12 text-gray-400" />
          <p class="text-gray-500">You do not have permission to view Events.</p>
        </div>
      </template>
      <template v-else>
        <LegacyTable
          :rows="filteredEvents"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <!-- Last Seen -->
          <template #lastSeen-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">
              {{ formatAge(row.lastSeen) }}
            </span>
          </template>

          <!-- Type -->
          <template #type-data="{ row }">
            <UBadge
              :color="eventTypeColor(row.type)"
              variant="soft"
              class="uppercase"
            >
              {{ row.type }}
            </UBadge>
          </template>

          <!-- Object -->
          <template #object-data="{ row }">
            <div class="flex flex-col">
              <span class="font-medium">
                {{ row.involvedObjectKind }}
              </span>
              <span class="text-xs text-gray-500 truncate max-w-[320px]">
                {{ row.involvedObjectName }}
              </span>
            </div>
          </template>

          <!-- Message -->
          <template #message-data="{ row }">
            <span
              class="text-sm text-gray-700 dark:text-gray-300 line-clamp-2"
            >
              {{ row.message }}
            </span>
          </template>

          <!-- Count -->
          <template #count-data="{ row }">
            <UBadge color="neutral" variant="subtle">
              {{ row.count }}
            </UBadge>
          </template>
        </LegacyTable>
      </template>
    </template>
  </UDashboardPanel>
</template>
