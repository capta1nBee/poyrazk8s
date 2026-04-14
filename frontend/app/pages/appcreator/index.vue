<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'
import type { AppCreatorApp } from '~/stores/appcreator'

const store = useAppCreatorStore()
const clusterStore = useClusterStore()
const toast = useToast()
const router = useRouter()
const { $api } = useNuxtApp()
const { hasPermission, loading: permissionsLoading } = usePagePermissions('AppCreator')

const selectedCluster = computed(() => clusterStore.selectedCluster)

const statusColor = (status: string) => {
  const map: Record<string, string> = {
    DEPLOYED: 'green', DRAFT: 'yellow', FAILED: 'red', PENDING: 'blue', NOT_FOUND: 'orange'
  }
  return map[status] || 'gray'
}

const statusLabel = (status: string) => {
  if (status === 'NOT_FOUND') return 'Deleted'
  return status
}

const deployStatusColor = (status: string) => {
  const map: Record<string, string> = {
    SUCCESS: 'success', FAILED: 'error', PENDING: 'warning'
  }
  return map[status] || 'neutral'
}

// ── Delete ────────────────────────────────────────────────────────────────────
const confirmDelete = ref(false)
const targetApp = ref<AppCreatorApp | null>(null)
const deleting = ref(false)

// K8s resource deletion
const deleteK8sResources = ref(false)
interface K8sResource { kind: string; name: string; namespace: string; exists: boolean }
const k8sResources = ref<K8sResource[]>([])
const loadingK8sResources = ref(false)

const openDeleteModal = (app: AppCreatorApp) => {
  targetApp.value = app
  deleteK8sResources.value = false
  k8sResources.value = []
  confirmDelete.value = true
}

const fetchK8sResources = async () => {
  if (!targetApp.value || !selectedCluster.value) return
  loadingK8sResources.value = true
  try {
    const res = await $api.get(`/k8s/${selectedCluster.value.uid}/appcreator/apps/${targetApp.value.id}/k8s-resources`)
    k8sResources.value = res.data
  } catch (e: any) {
    k8sResources.value = []
  } finally {
    loadingK8sResources.value = false
  }
}

watch(deleteK8sResources, (val) => {
  if (val) fetchK8sResources()
  else k8sResources.value = []
})

const deleteApp = async () => {
  if (!targetApp.value || !selectedCluster.value) return
  deleting.value = true
  try {
    await $api.delete(`/k8s/${selectedCluster.value.uid}/appcreator/apps/${targetApp.value.id}`, {
      params: { deleteK8sResources: deleteK8sResources.value }
    })
    store.apps = store.apps.filter(a => a.id !== targetApp.value?.id)
    toast.add({ title: 'App deleted successfully', color: 'success' })
    confirmDelete.value = false
  } catch (e: any) {
    toast.add({ title: 'Delete failed', description: e?.response?.data?.message || e.message, color: 'red' })
  } finally {
    deleting.value = false
  }
}

// ── Deploy History ────────────────────────────────────────────────────────────
interface DeployHistoryItem {
  historyId: string
  status: string
  deployType: string
  gitPrUrl: string | null
  gitCommitSha: string | null
  resourceCount: number
  errorMessage: string | null
  createdAt: string
}

const showHistory = ref(false)
const historyApp = ref<AppCreatorApp | null>(null)
const historyItems = ref<DeployHistoryItem[]>([])
const historyLoading = ref(false)

const openHistory = async (app: AppCreatorApp) => {
  historyApp.value = app
  showHistory.value = true
  historyItems.value = []
  if (!selectedCluster.value) return
  historyLoading.value = true
  try {
    const res = await $api.get(`/k8s/${selectedCluster.value.uid}/appcreator/apps/${app.id}/history`)
    historyItems.value = res.data
  } catch (e: any) {
    toast.add({ title: 'Failed to load history', color: 'error' })
  } finally {
    historyLoading.value = false
  }
}

// ── Wizard ────────────────────────────────────────────────────────────────────
const openCreateWizard = () => {
  store.resetWizard()
  router.push('/appcreator/create')
}

watch(selectedCluster, (c) => {
  if (c) store.fetchApps()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="appcreator">
    <template #header>
      <UDashboardNavbar title="App Creator">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="hasPermission('create')"
            icon="i-lucide-plus"
            color="primary"
            label="New Application"
            @click="openCreateWizard"
          />
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" :loading="store.loading" @click="store.fetchApps()" />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div v-if="permissionsLoading || store.loading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>

      <div v-else-if="!hasPermission('view')" class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium">Access Denied</h3>
        <p class="text-sm text-gray-500 mt-1">You don't have permission to view applications.</p>
      </div>

      <div v-else-if="store.apps.length === 0" class="flex flex-col items-center justify-center h-64 gap-4">
        <UIcon name="i-lucide-rocket" class="w-16 h-16 text-gray-200 dark:text-gray-700" />
        <div class="text-center">
          <h3 class="text-base font-semibold text-gray-700 dark:text-gray-300">No applications yet</h3>
          <p class="text-sm text-gray-500 mt-1">Build and deploy your first Kubernetes application</p>
        </div>
        <UButton icon="i-lucide-plus" color="primary" @click="openCreateWizard">Create Application</UButton>
      </div>

      <div v-else class="p-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
        <div
          v-for="app in store.apps"
          :key="app.id"
          class="group bg-white dark:bg-gray-900 rounded-xl border border-gray-100 dark:border-gray-800 hover:border-primary-500/50 hover:shadow-xl transition-all duration-200 flex flex-col overflow-hidden"
        >
          <div class="p-4 flex-1 space-y-3">
            <div class="flex items-start justify-between gap-2">
              <div class="w-10 h-10 bg-primary-50 dark:bg-primary-900/20 rounded-lg flex items-center justify-center border border-primary-100 dark:border-primary-800">
                <UIcon name="i-lucide-layers" class="w-5 h-5 text-primary-500" />
              </div>
              <UBadge :color="statusColor(app.status)" variant="subtle" size="xs" class="uppercase">{{ statusLabel(app.status) }}</UBadge>
            </div>
            <div>
              <h4 class="font-bold text-gray-900 dark:text-white truncate">{{ app.name }}</h4>
              <p class="text-xs text-gray-400 truncate mt-0.5">{{ app.description || 'No description' }}</p>
            </div>
            <div class="grid grid-cols-2 gap-2 text-xs">
              <div class="bg-gray-50 dark:bg-gray-800/50 rounded-md p-2">
                <span class="text-gray-400 block text-[10px] mb-0.5">Namespace</span>
                <span class="font-medium truncate block">{{ app.namespace }}</span>
              </div>
              <div class="bg-gray-50 dark:bg-gray-800/50 rounded-md p-2">
                <span class="text-gray-400 block text-[10px] mb-0.5">Type</span>
                <span class="font-medium truncate block">{{ app.workloadType }}</span>
              </div>
            </div>
          </div>
          <div class="flex gap-2 px-4 py-3 border-t border-gray-100 dark:border-gray-800">
            <UButton
              size="xs" color="primary" variant="soft" icon="i-lucide-edit" class="flex-1 justify-center"
              @click="router.push(`/appcreator/create?appId=${app.id}`)"
            >Edit</UButton>
            <UButton
              size="xs" color="neutral" variant="ghost" icon="i-lucide-history"
              @click="openHistory(app)"
            />
            <UButton
              size="xs" color="red" variant="soft" icon="i-lucide-trash-2"
              @click="openDeleteModal(app)"
            />
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>

  <UModal v-model:open="confirmDelete" title="Delete Application" description="This action cannot be undone.">
    <template #body>
      <p class="text-sm text-gray-600 dark:text-gray-400 mb-4">
        Are you sure you want to delete <strong>{{ targetApp?.name }}</strong>? This will remove all associated records from the database.
      </p>

      <!-- K8s Resources Checkbox -->
      <div class="flex items-center gap-3 mb-4 p-3 rounded-lg bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800">
        <USwitch v-model="deleteK8sResources" color="error" />
        <div>
          <p class="text-sm font-medium text-orange-800 dark:text-orange-200">Also delete Kubernetes resources</p>
          <p class="text-xs text-orange-600 dark:text-orange-400">Permanently removes Deployments, Services, Ingresses, HPAs, etc. from the cluster</p>
        </div>
      </div>

      <!-- Resource List -->
      <div v-if="deleteK8sResources" class="mb-4">
        <div v-if="loadingK8sResources" class="flex items-center gap-2 text-sm text-gray-500 py-2">
          <UIcon name="i-lucide-loader-2" class="w-4 h-4 animate-spin" />
          <span>Fetching Kubernetes resources...</span>
        </div>
        <div v-else-if="k8sResources.length === 0" class="text-sm text-gray-500 py-2 text-center">
          No Kubernetes resources found for this application.
        </div>
        <div v-else class="space-y-1.5">
          <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Resources to be deleted:</p>
          <div
            v-for="res in k8sResources"
            :key="`${res.kind}-${res.name}`"
            class="flex items-center justify-between rounded-md px-3 py-2 text-sm"
            :class="res.exists
              ? 'bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800'
              : 'bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700 opacity-50'"
          >
            <div class="flex items-center gap-2">
              <UIcon
                :name="res.exists ? 'i-lucide-trash-2' : 'i-lucide-minus-circle'"
                class="w-3.5 h-3.5"
                :class="res.exists ? 'text-red-500' : 'text-gray-400'"
              />
              <span class="font-mono font-medium">{{ res.name }}</span>
              <UBadge size="xs" variant="subtle" color="neutral">{{ res.kind }}</UBadge>
            </div>
            <span class="text-xs" :class="res.exists ? 'text-red-500' : 'text-gray-400'">
              {{ res.exists ? 'will be deleted' : 'not found' }}
            </span>
          </div>
        </div>
      </div>

      <div class="flex justify-end gap-3">
        <UButton color="neutral" variant="outline" @click="confirmDelete = false">Cancel</UButton>
        <UButton color="error" :loading="deleting" @click="deleteApp">Delete</UButton>
      </div>
    </template>
  </UModal>

  <!-- Deploy History Modal -->
  <UModal v-model:open="showHistory" :title="`Deploy History — ${historyApp?.name}`" size="xl">
    <template #body>
      <div v-if="historyLoading" class="flex items-center justify-center py-12">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>
      <div v-else-if="historyItems.length === 0" class="flex flex-col items-center justify-center py-12 gap-2 text-gray-400">
        <UIcon name="i-lucide-history" class="w-10 h-10 opacity-30" />
        <p class="text-sm">No deployments yet</p>
      </div>
      <div v-else class="space-y-3">
        <div
          v-for="item in historyItems"
          :key="item.historyId"
          class="border border-gray-100 dark:border-gray-800 rounded-xl p-4 space-y-2"
        >
          <div class="flex items-center justify-between gap-2">
            <div class="flex items-center gap-2">
              <UBadge :color="deployStatusColor(item.status)" variant="subtle" size="xs">{{ item.status }}</UBadge>
              <UBadge color="neutral" variant="outline" size="xs">
                <UIcon :name="item.deployType === 'git' ? 'i-lucide-git-pull-request' : 'i-lucide-rocket'" class="w-3 h-3 mr-1" />
                {{ item.deployType === 'git' ? 'GitOps' : 'Direct' }}
              </UBadge>
              <span v-if="item.resourceCount" class="text-xs text-gray-400">{{ item.resourceCount }} resources</span>
            </div>
            <span class="text-xs text-gray-400">{{ new Date(item.createdAt).toLocaleString() }}</span>
          </div>

          <!-- PR URL -->
          <div v-if="item.gitPrUrl" class="flex items-center gap-2 text-sm">
            <UIcon name="i-lucide-git-pull-request" class="w-4 h-4 text-primary-500 flex-shrink-0" />
            <a
              :href="item.gitPrUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="text-primary-500 hover:text-primary-600 hover:underline truncate"
            >{{ item.gitPrUrl }}</a>
          </div>

          <!-- Commit SHA -->
          <div v-if="item.gitCommitSha" class="flex items-center gap-2 text-xs text-gray-400">
            <UIcon name="i-lucide-git-commit-horizontal" class="w-3.5 h-3.5" />
            <code class="font-mono">{{ item.gitCommitSha.slice(0, 8) }}</code>
          </div>

          <!-- Error -->
          <div v-if="item.errorMessage" class="text-xs text-red-500 bg-red-50 dark:bg-red-900/20 rounded-lg p-2">
            {{ item.errorMessage }}
          </div>
        </div>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end">
        <UButton color="neutral" variant="outline" @click="showHistory = false">Close</UButton>
      </div>
    </template>
  </UModal>
</template>

