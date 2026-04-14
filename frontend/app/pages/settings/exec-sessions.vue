<script setup lang="ts">
const { $api } = useNuxtApp()
const toast = useToast()
const authStore = useAuthStore()

// Check superadmin access
const isSuperadmin = computed(() => authStore.user?.isSuperadmin === true)

const sessions = ref([])
const selectedSession = ref(null)
const loading = ref(false)
const playbackModalOpen = ref(false)

const fetchSessions = async () => {
  loading.value = true
  try {
    const response = await $api.get('/exec/sessions')
    sessions.value = response.data
  } catch (e) {
    toast.add({ title: 'Error', description: 'Failed to fetch sessions', color: 'red' })
  } finally {
    loading.value = false
  }
}

const viewSession = async (session: any) => {
  try {
    const response = await $api.get(`/exec/sessions/${session.sessionId}/recording`)
    selectedSession.value = {
      ...session,
      events: JSON.parse(response.data.eventData)
    }
    playbackModalOpen.value = true
  } catch (e) {
    toast.add({ title: 'Error', description: 'Failed to load recording', color: 'red' })
  }
}

// Initialize rrweb player when modal opens
watch(playbackModalOpen, async (isOpen) => {
  if (isOpen && selectedSession.value?.events) {
    await nextTick()
    initRrwebPlayer()
  }
})

const initRrwebPlayer = () => {
  // Wait a tick for DOM to fully layout
  setTimeout(() => {
    const playerContainer = document.getElementById('rrweb-player')
    if (!playerContainer || !selectedSession.value?.events) return

    // Clear previous player
    playerContainer.innerHTML = ''

    // Calculate height: parent's height minus info panel
    const parentEl = playerContainer.closest('.flex-1.flex.flex-col')
    const infoHeight = 100 // approximate info panel height
    const availableHeight = parentEl
      ? parentEl.clientHeight - infoHeight - 48 // subtract padding
      : Math.round(window.innerHeight * 0.55)
    const playerWidth = playerContainer.clientWidth || Math.round(window.innerWidth * 0.75)

    // Load rrweb player
    const script = document.createElement('script')
    script.src = '/lib/rrweb/rrweb-player.min.js'
    script.onload = () => {
      const link = document.createElement('link')
      link.rel = 'stylesheet'
      link.href = '/lib/rrweb/rrweb-player.min.css'
      document.head.appendChild(link)

      if (typeof (window as any).rrwebPlayer !== 'undefined') {
        new (window as any).rrwebPlayer({
          target: playerContainer,
          props: {
            events: selectedSession.value!.events,
            width: playerWidth,
            height: Math.max(availableHeight, 300),
          },
        })
      }
    }
    document.head.appendChild(script)
  }, 200) // wait for modal transition + layout
}

onMounted(() => {
  if (!isSuperadmin.value) {
    navigateTo('/settings')
  }
  fetchSessions()
})

const columns = [
  { key: 'sessionId', label: 'Session ID' },
  { key: 'username', label: 'User' },
  { key: 'podName', label: 'Pod' },
  { key: 'namespace', label: 'Namespace' },
  { key: 'status', label: 'Status' },
  { key: 'createdAt', label: 'Started' },
  { key: 'actions', label: 'Actions' }
]

const formatDate = (date: string) => {
  return new Date(date).toLocaleString()
}
</script>

<template>
  <div class="p-6 space-y-6">
    <div class="flex justify-between items-center">
      <div>
        <h1 class="text-2xl font-black uppercase tracking-tight">Exec Session Recordings</h1>
        <p class="text-sm text-gray-500">View and playback terminal session recordings (Superadmin only)</p>
      </div>
      <UButton icon="i-lucide-refresh-cw" label="Refresh" variant="outline" @click="fetchSessions" :loading="loading" />
    </div>

    <UCard :ui="{ body: { padding: 'p-0' } }">
      <LegacyTable 
        :rows="sessions" 
        :columns="columns" 
        :loading="loading"
        search-placeholder="Search sessions..."
      >
        <template #sessionId-data="{ row }">
          <code class="text-xs bg-gray-100 dark:bg-gray-800 p-1 rounded">{{ row.sessionId.substring(0, 8) }}...</code>
        </template>
        
        <template #username-data="{ row }">
          <div class="flex items-center gap-2">
            <UIcon name="i-lucide-user" class="text-gray-400" />
            <span>{{ row.username || 'Unknown' }}</span>
          </div>
        </template>

        <template #status-data="{ row }">
          <UBadge 
            :color="row.status === 'ACTIVE' ? 'green' : 'gray'" 
            variant="subtle"
          >
            {{ row.status }}
          </UBadge>
        </template>

        <template #createdAt-data="{ row }">
          <span class="text-xs text-gray-500">{{ formatDate(row.createdAt) }}</span>
        </template>

        <template #actions-data="{ row }">
          <UButton 
            icon="i-lucide-play" 
            size="xs" 
            color="primary"
            @click="viewSession(row)"
          >
            Play
          </UButton>
        </template>
      </LegacyTable>
    </UCard>

    <!-- Playback Modal — 80% of viewport -->
    <UModal v-model:open="playbackModalOpen" fullscreen :ui="{ background: 'bg-black/60' }">
      <template #content>
        <div class="fixed inset-0 flex items-center justify-center p-4">
          <div class="w-[80vw] h-[80vh] bg-white dark:bg-gray-950 rounded-xl shadow-2xl flex flex-col overflow-hidden border border-gray-200 dark:border-gray-800">

            <!-- Header -->
            <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between shrink-0">
              <h3 class="text-lg font-semibold">Session Playback</h3>
              <UButton icon="i-lucide-x" variant="ghost" color="neutral" size="xs" @click="playbackModalOpen = false" />
            </div>

            <!-- Body -->
            <div v-if="selectedSession" class="flex-1 flex flex-col overflow-hidden p-6 space-y-4">
              <div class="grid grid-cols-2 gap-4 text-sm bg-gray-50 dark:bg-gray-900 p-4 rounded-lg shrink-0">
                <div>
                  <span class="text-gray-500">User:</span>
                  <span class="ml-2 font-medium">{{ selectedSession.username }}</span>
                </div>
                <div>
                  <span class="text-gray-500">Pod:</span>
                  <span class="ml-2 font-medium">{{ selectedSession.podName }}</span>
                </div>
                <div>
                  <span class="text-gray-500">Namespace:</span>
                  <span class="ml-2 font-medium">{{ selectedSession.namespace }}</span>
                </div>
                <div>
                  <span class="text-gray-500">Started:</span>
                  <span class="ml-2 font-medium">{{ formatDate(selectedSession.createdAt) }}</span>
                </div>
              </div>

              <div class="flex-1 border rounded-lg p-4 bg-gray-50 dark:bg-gray-900 overflow-auto min-h-0">
                <div v-if="selectedSession.events && selectedSession.events.length > 0">
                  <div id="rrweb-player" class="w-full" style="min-height: 300px"></div>
                </div>
                <div v-else class="flex items-center justify-center h-full text-gray-500">
                  <div class="text-center">
                    <UIcon name="i-lucide-video-off" class="w-12 h-12 mx-auto mb-2" />
                    <p>No recording data available for this session</p>
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>
      </template>
    </UModal>
  </div>
</template>
