<script setup lang="ts">
import type { Backup, BackupStats, BackupFileItem } from '~/types/backup'

definePageMeta({
  layout: 'default',
  requiredPermission: 'backups'
})

const { $api } = useNuxtApp()
const toast = useToast()
const clusterStore = useClusterStore()

// State
const backups = ref<Backup[]>([])
const stats = ref<BackupStats | null>(null)
const loading = ref(true)
const triggeringBackup = ref(false)

// File browser state
const selectedBackup = ref<Backup | null>(null)
const isFileBrowserOpen = ref(false)
const currentPath = ref('')
const fileContents = ref<BackupFileItem[]>([])
const loadingFiles = ref(false)

// YAML viewer state
const isYamlViewerOpen = ref(false)
const yamlContent = ref('')
const yamlFilePath = ref('')
const loadingYaml = ref(false)

// Fetch data
const fetchBackups = async () => {
  loading.value = true
  try {
    const params = { clusterUid: clusterStore.selectedClusterUid || undefined }
    const [backupsRes, statsRes] = await Promise.all([
      $api.get<Backup[]>('/backups', { params }),
      $api.get<BackupStats>('/backups/stats', { params })
    ])
    backups.value = backupsRes.data || []
    stats.value = statsRes.data || null
  } catch (e: any) {
    toast.add({ title: 'Failed to load backups', description: e.message, color: 'error' })
  } finally {
    loading.value = false
  }
}

// Watch for cluster changes
watch(() => clusterStore.selectedClusterUid, () => {
  fetchBackups()
})

// Trigger backup
const triggerBackup = async (clusterUid?: string) => {
  triggeringBackup.value = true
  try {
    if (clusterUid) {
      await $api.post(`/backups/trigger/${clusterUid}`)
      toast.add({ title: 'Backup started', description: `Backup triggered for cluster`, color: 'success' })
    } else {
      await $api.post('/backups/trigger-all')
      toast.add({ title: 'Backup started', description: 'Backup triggered for all clusters', color: 'success' })
    }
    await fetchBackups()
  } catch (e: any) {
    toast.add({ title: 'Failed to trigger backup', description: e.message, color: 'error' })
  } finally {
    triggeringBackup.value = false
  }
}

// Open file browser
const openFileBrowser = async (backup: Backup) => {
  selectedBackup.value = backup
  currentPath.value = ''
  isFileBrowserOpen.value = true
  await loadFileContents('')
}

// Load file contents
const loadFileContents = async (path: string) => {
  if (!selectedBackup.value) return
  loadingFiles.value = true
  try {
    const res = await $api.get<BackupFileItem[]>(`/backups/${selectedBackup.value.id}/contents`, {
      params: { 
        path,
        clusterUid: clusterStore.selectedClusterUid
      }
    })
    fileContents.value = res.data
    currentPath.value = path
  } catch (e: any) {
    toast.add({ title: 'Failed to load files', description: e.message, color: 'error' })
  } finally {
    loadingFiles.value = false
  }
}

// Navigate to directory or view file
const browseItem = async (item: BackupFileItem) => {
  if (item.isDirectory) {
    await loadFileContents(item.path)
  } else {
    // View YAML file
    await viewYamlFile(item.path)
  }
}

// Go up one directory
const goUp = async () => {
  const parts = currentPath.value.split('/').filter(Boolean)
  parts.pop()
  await loadFileContents(parts.join('/'))
}

// View YAML file
const viewYamlFile = async (path: string) => {
  if (!selectedBackup.value) return
  loadingYaml.value = true
  isYamlViewerOpen.value = true
  yamlFilePath.value = path
  try {
    const res = await $api.get<{ content: string; path: string }>(`/backups/${selectedBackup.value.id}/file`, {
      params: { 
        path,
        clusterUid: clusterStore.selectedClusterUid
      }
    })
    yamlContent.value = res.data.content
  } catch (e: any) {
    toast.add({ title: 'Failed to load file', description: e.message, color: 'error' })
    yamlContent.value = ''
  } finally {
    loadingYaml.value = false
  }
}

// Format bytes
const formatBytes = (bytes?: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`
}

// Format date
const formatDate = (date?: string) => {
  if (!date) return '-'
  return new Date(date).toLocaleString()
}

// Get status color
const getStatusColor = (status: string) => {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'RUNNING': return 'warning'
    case 'FAILED': return 'error'
    default: return 'neutral'
  }
}

// Get status icon
const getStatusIcon = (status: string) => {
  switch (status) {
    case 'COMPLETED': return 'i-lucide-check-circle'
    case 'RUNNING': return 'i-lucide-loader-2'
    case 'FAILED': return 'i-lucide-x-circle'
    default: return 'i-lucide-circle'
  }
}

// Table columns
const columns = [
  { key: 'clusterName', label: 'Cluster', sortable: true },
  { key: 'status', label: 'Status', sortable: true },
  { key: 'triggeredBy', label: 'Trigger', sortable: true },
  { key: 'totalResources', label: 'Resources', sortable: true },
  { key: 'totalNamespaces', label: 'Namespaces', sortable: true },
  { key: 'sizeBytes', label: 'Size', sortable: true },
  { key: 'startedAt', label: 'Started', sortable: true },
  { key: 'completedAt', label: 'Completed', sortable: true },
  { key: 'actions', label: '' }
]

// Backup path (platform dependent)
const backupPath = '/tmp/k8s-backup'

onMounted(() => {
  fetchBackups()
})
</script>

<template>
  <UDashboardPanel grow>
    <UDashboardNavbar title="Backups">
      <template #leading>
        <UDashboardSidebarCollapse />
      </template>
      <template #right>
        <UButton
          icon="i-lucide-refresh-cw"
          color="neutral"
          variant="ghost"
          :loading="loading"
          @click="fetchBackups"
        />
        <UButton
          icon="i-lucide-play"
          color="primary"
          :loading="triggeringBackup"
          :disabled="!clusterStore.selectedClusterUid"
          @click="triggerBackup(clusterStore.selectedClusterUid)"
        >
          Backup Cluster
        </UButton>
      </template>
    </UDashboardNavbar>

    <div class="p-6 space-y-6">
      <!-- Stats Cards -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div class="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="p-3 bg-emerald-100 dark:bg-emerald-500/20 rounded-lg">
              <UIcon name="i-lucide-archive" class="text-emerald-600 dark:text-emerald-400 text-xl" />
            </div>
            <div>
              <p class="text-2xl font-bold text-emerald-600 dark:text-emerald-400">{{ stats?.totalBackups || 0 }}</p>
              <p class="text-sm text-gray-500 dark:text-gray-400">Total Backups</p>
            </div>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="p-3 bg-blue-100 dark:bg-blue-500/20 rounded-lg">
              <UIcon name="i-lucide-check-circle" class="text-blue-600 dark:text-blue-400 text-xl" />
            </div>
            <div>
              <p class="text-2xl font-bold text-blue-600 dark:text-blue-400">{{ stats?.completedBackups || 0 }}</p>
              <p class="text-sm text-gray-500 dark:text-gray-400">Completed</p>
            </div>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="p-3 bg-purple-100 dark:bg-purple-500/20 rounded-lg">
              <UIcon name="i-lucide-hard-drive" class="text-purple-600 dark:text-purple-400 text-xl" />
            </div>
            <div>
              <p class="text-2xl font-bold text-purple-600 dark:text-purple-400">{{ formatBytes(stats?.totalSize) }}</p>
              <p class="text-sm text-gray-500 dark:text-gray-400">Total Size</p>
            </div>
          </div>
        </div>

        <div class="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl p-5 shadow-sm">
          <div class="flex items-center gap-3">
            <div class="p-3 bg-amber-100 dark:bg-amber-500/20 rounded-lg">
              <UIcon name="i-lucide-calendar" class="text-amber-600 dark:text-amber-400 text-xl" />
            </div>
            <div>
              <p class="text-2xl font-bold text-amber-600 dark:text-amber-400">{{ stats?.lastWeekBackups || 0 }}</p>
              <p class="text-sm text-gray-500 dark:text-gray-400">Last 7 Days</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Info Banner -->
      <div class="bg-indigo-50 dark:bg-indigo-950/30 border border-indigo-200 dark:border-indigo-500/20 rounded-xl p-4">
        <div class="flex items-center gap-3">
          <UIcon name="i-lucide-info" class="text-indigo-600 dark:text-indigo-400 text-lg" />
          <p class="text-sm text-gray-700 dark:text-gray-300">
            <span class="font-medium text-indigo-600 dark:text-indigo-400">Automatic backups</span> run every night at 2:00 AM for clusters with backup enabled. 
            Backups are stored in <code class="bg-gray-200 dark:bg-gray-800 px-1.5 py-0.5 rounded text-xs">{{ backupPath }}</code> organized by cluster and namespace.
          </p>
        </div>
      </div>

      <!-- Backups Table -->
      <LegacyTable
        :columns="columns"
        :rows="backups"
        :loading="loading"
      >
        <template #clusterName-data="{ row }">
          <div class="flex items-center gap-2">
            <UIcon name="i-lucide-server" class="text-gray-400" />
            <span class="font-medium">{{ row.clusterName }}</span>
          </div>
        </template>

        <template #status-data="{ row }">
          <UBadge
            :color="getStatusColor(row.status)"
            variant="subtle"
            class="gap-1"
          >
            <UIcon 
              :name="getStatusIcon(row.status)" 
              :class="{ 'animate-spin': row.status === 'RUNNING' }"
            />
            {{ row.status }}
          </UBadge>
        </template>

        <template #triggeredBy-data="{ row }">
          <UBadge 
            :color="row.triggeredBy === 'SCHEDULED' ? 'info' : 'neutral'"
            variant="soft"
          >
            <UIcon :name="row.triggeredBy === 'SCHEDULED' ? 'i-lucide-clock' : 'i-lucide-hand'" class="mr-1" />
            {{ row.triggeredBy }}
          </UBadge>
        </template>

        <template #totalResources-data="{ row }">
          <span class="font-mono text-sm">{{ row.totalResources || '-' }}</span>
        </template>

        <template #totalNamespaces-data="{ row }">
          <span class="font-mono text-sm">{{ row.totalNamespaces || '-' }}</span>
        </template>

        <template #sizeBytes-data="{ row }">
          <span class="font-mono text-sm text-emerald-600 dark:text-emerald-400">{{ formatBytes(row.sizeBytes) }}</span>
        </template>

        <template #startedAt-data="{ row }">
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ formatDate(row.startedAt) }}</span>
        </template>

        <template #completedAt-data="{ row }">
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ formatDate(row.completedAt) }}</span>
        </template>

        <template #actions-data="{ row }">
          <div class="flex items-center gap-1">
            <UTooltip text="Browse Files">
              <UButton
                icon="i-lucide-folder-open"
                color="neutral"
                variant="ghost"
                size="xs"
                :disabled="row.status !== 'COMPLETED'"
                @click="openFileBrowser(row)"
              />
            </UTooltip>
            <UTooltip text="Trigger New Backup">
              <UButton
                icon="i-lucide-refresh-cw"
                color="neutral"
                variant="ghost"
                size="xs"
                :loading="triggeringBackup"
                @click="triggerBackup(row.clusterUid)"
              />
            </UTooltip>
          </div>
        </template>
      </LegacyTable>
    </div>

    <!-- File Browser Modal -->
    <UModal v-model:open="isFileBrowserOpen" :ui="{ width: 'max-w-4xl' }">
      <template #content>
        <div class="p-6 bg-white dark:bg-gray-900">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h3 class="text-lg font-semibold flex items-center gap-2 text-gray-900 dark:text-white">
                <UIcon name="i-lucide-folder-open" class="text-indigo-600 dark:text-indigo-400" />
                Backup Files
              </h3>
              <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
                {{ selectedBackup?.clusterName }} - {{ formatDate(selectedBackup?.createdAt) }}
              </p>
            </div>
            <UButton
              icon="i-lucide-x"
              color="neutral"
              variant="ghost"
              @click="isFileBrowserOpen = false"
            />
          </div>

          <!-- Breadcrumb -->
          <div class="flex items-center gap-2 mb-4 p-2 bg-gray-100 dark:bg-gray-800/50 rounded-lg">
            <UButton
              icon="i-lucide-home"
              color="neutral"
              variant="ghost"
              size="xs"
              @click="loadFileContents('')"
            />
            <template v-if="currentPath">
              <UIcon name="i-lucide-chevron-right" class="text-gray-400 dark:text-gray-500" />
              <UButton
                icon="i-lucide-arrow-up"
                color="neutral"
                variant="ghost"
                size="xs"
                @click="goUp"
              >
                Back
              </UButton>
              <UIcon name="i-lucide-chevron-right" class="text-gray-400 dark:text-gray-500" />
              <span class="text-sm text-gray-700 dark:text-gray-300 font-mono">{{ currentPath }}</span>
            </template>
          </div>

          <!-- File List -->
          <div class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden max-h-96 overflow-y-auto">
            <div v-if="loadingFiles" class="p-8 text-center">
              <UIcon name="i-lucide-loader-2" class="animate-spin text-2xl text-gray-400" />
            </div>
            <div v-else-if="fileContents.length === 0" class="p-8 text-center text-gray-400">
              <UIcon name="i-lucide-folder-x" class="text-3xl mb-2" />
              <p>No files found</p>
            </div>
            <div v-else>
              <div
                v-for="item in fileContents"
                :key="item.path"
                class="flex items-center gap-3 p-3 hover:bg-gray-50 dark:hover:bg-gray-800/50 cursor-pointer border-b border-gray-100 dark:border-gray-800 last:border-b-0"
                @click="browseItem(item)"
              >
                <UIcon
                  :name="item.isDirectory ? 'i-lucide-folder' : 'i-lucide-file-code'"
                  :class="item.isDirectory ? 'text-amber-500 dark:text-amber-400' : 'text-blue-500 dark:text-blue-400'"
                />
                <span class="flex-1 font-mono text-sm text-gray-700 dark:text-gray-200">{{ item.name }}</span>
                <span v-if="!item.isDirectory" class="text-xs text-gray-500">
                  {{ formatBytes(item.size) }}
                </span>
                <UIcon
                  :name="item.isDirectory ? 'i-lucide-chevron-right' : 'i-lucide-eye'"
                  class="text-gray-400 dark:text-gray-500"
                />
              </div>
            </div>
          </div>
        </div>
      </template>
    </UModal>

    <!-- YAML Viewer Modal -->
    <UModal v-model:open="isYamlViewerOpen" fullscreen>
      <template #content>
        <div class="fixed inset-0 flex items-center justify-center bg-black/60">
          <div class="w-[80vw] h-[80vh] bg-white dark:bg-gray-900 rounded-xl shadow-2xl flex flex-col overflow-hidden border border-gray-200 dark:border-gray-700">
            <!-- Header -->
            <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between bg-gray-50 dark:bg-gray-900">
              <div>
                <h3 class="text-lg font-bold flex items-center gap-2 text-gray-900 dark:text-white">
                  <UIcon name="i-lucide-file-code" class="text-blue-500 dark:text-blue-400" />
                  View YAML
                </h3>
                <p class="text-xs text-gray-500 font-mono mt-1">{{ yamlFilePath }}</p>
              </div>
              <div class="flex items-center gap-2">
                <UButton
                  icon="i-lucide-copy"
                  color="neutral"
                  variant="ghost"
                  @click="navigator.clipboard.writeText(yamlContent); toast.add({ title: 'Copied!', color: 'success' })"
                />
                <UButton
                  icon="i-lucide-x"
                  color="neutral"
                  variant="ghost"
                  @click="isYamlViewerOpen = false"
                />
              </div>
            </div>

            <!-- Content -->
            <div class="flex-1 overflow-auto p-4 bg-gray-50 dark:bg-[#0d1117]">
              <div v-if="loadingYaml" class="flex items-center justify-center h-full">
                <UIcon name="i-lucide-loader-2" class="animate-spin text-3xl text-gray-400" />
              </div>
              <pre v-else class="text-sm font-mono text-gray-800 dark:text-gray-300 whitespace-pre-wrap">{{ yamlContent }}</pre>
            </div>
          </div>
        </div>
      </template>
    </UModal>
  </UDashboardPanel>
</template>

<style scoped>
code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}
</style>
