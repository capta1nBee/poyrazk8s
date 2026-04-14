<script setup lang="ts">
const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const toast = useToast()
const { hasPermission, loading: permissionsLoading } = usePagePermissions('AppCreatorGit')

interface GitConnection {
  id: string
  provider: 'github' | 'gitlab'
  name: string
  baseUrl: string | null
  isDefault: boolean
  createdAt: string
}

const connections = ref<GitConnection[]>([])
const loading = ref(false)
const showModal = ref(false)

const form = ref({
  provider: 'github' as 'github' | 'gitlab',
  name: '',
  accessToken: '',
  baseUrl: '',
  isDefault: false
})

const saving = ref(false)

const providerOptions = [
  { label: 'GitHub', value: 'github', icon: 'i-simple-icons-github' },
  { label: 'GitLab', value: 'gitlab', icon: 'i-simple-icons-gitlab' }
]

const providerIcon = (p: string) =>
  p === 'github' ? 'i-simple-icons-github' : 'i-simple-icons-gitlab'

const providerColor = (p: string) =>
  p === 'github' ? 'neutral' : 'warning'

const fetchConnections = async () => {
  if (!clusterStore.selectedCluster) return
  loading.value = true
  try {
    const res = await $api.get(
      `/k8s/${clusterStore.selectedCluster.uid}/appcreator/git-connections`
    )
    connections.value = res.data
  } catch (e: any) {
    toast.add({ title: 'Failed to load connections', description: e.message, color: 'error' })
  } finally {
    loading.value = false
  }
}

const openAddModal = () => {
  form.value = { provider: 'github', name: '', accessToken: '', baseUrl: '', isDefault: false }
  showModal.value = true
}

const saveConnection = async () => {
  if (!clusterStore.selectedCluster) return
  if (!form.value.name.trim() || !form.value.accessToken.trim()) {
    toast.add({ title: 'Name and Access Token are required', color: 'warning' })
    return
  }
  saving.value = true
  try {
    await $api.post(
      `/k8s/${clusterStore.selectedCluster.uid}/appcreator/git-connections`,
      {
        ...form.value,
        baseUrl: form.value.baseUrl.trim() || null
      }
    )
    toast.add({ title: 'Git connection added', color: 'success' })
    showModal.value = false
    await fetchConnections()
  } catch (e: any) {
    toast.add({ title: 'Failed to save connection', description: e.message, color: 'error' })
  } finally {
    saving.value = false
  }
}

const deleteConnection = async (conn: GitConnection) => {
  if (!clusterStore.selectedCluster) return
  if (!confirm(`Delete connection "${conn.name}"?`)) return
  try {
    await $api.delete(
      `/k8s/${clusterStore.selectedCluster.uid}/appcreator/git-connections/${conn.id}`
    )
    toast.add({ title: 'Connection deleted', color: 'success' })
    await fetchConnections()
  } catch (e: any) {
    toast.add({ title: 'Failed to delete', description: e.message, color: 'error' })
  }
}

watch(() => clusterStore.selectedCluster, (c) => { if (c) fetchConnections() }, { immediate: true })
</script>

<template>
  <UDashboardPanel id="appcreator-git">
    <template #header>
      <UDashboardNavbar title="Git Connections">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="hasPermission('create')"
            icon="i-lucide-plus"
            color="primary"
            label="Add Connection"
            @click="openAddModal"
          />
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="ghost"
            :loading="loading"
            @click="fetchConnections"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div v-if="permissionsLoading || loading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>

      <div v-else-if="!hasPermission('view')" class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium">Access Denied</h3>
      </div>

      <div v-else-if="connections.length === 0" class="flex flex-col items-center justify-center h-64 gap-4">
        <UIcon name="i-lucide-git-branch" class="w-16 h-16 text-gray-200 dark:text-gray-700" />
        <div class="text-center">
          <h3 class="text-base font-semibold text-gray-700 dark:text-gray-300">No Git connections yet</h3>
          <p class="text-sm text-gray-500 mt-1">Add a GitHub or GitLab Personal Access Token to push YAML via GitOps</p>
        </div>
        <UButton v-if="hasPermission('create')" icon="i-lucide-plus" color="primary" @click="openAddModal">
          Add Connection
        </UButton>
      </div>

      <div v-else class="p-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        <UCard
          v-for="conn in connections"
          :key="conn.id"
          class="relative"
        >
          <template #header>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <UIcon :name="providerIcon(conn.provider)" class="w-5 h-5" />
                <span class="font-semibold">{{ conn.name }}</span>
                <UBadge v-if="conn.isDefault" size="xs" color="primary" variant="soft">Default</UBadge>
              </div>
              <UButton
                v-if="hasPermission('delete')"
                icon="i-lucide-trash-2"
                color="error"
                variant="ghost"
                size="xs"
                @click="deleteConnection(conn)"
              />
            </div>
          </template>
          <div class="space-y-1 text-sm text-gray-500 dark:text-gray-400">
            <div class="flex items-center gap-1">
              <UBadge :color="providerColor(conn.provider)" variant="subtle" size="xs">
                {{ conn.provider.toUpperCase() }}
              </UBadge>
            </div>
            <div v-if="conn.baseUrl" class="text-xs truncate">{{ conn.baseUrl }}</div>
            <div class="text-xs">Added {{ new Date(conn.createdAt).toLocaleDateString() }}</div>
          </div>
        </UCard>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Add Connection Modal -->
  <UModal v-model:open="showModal" title="Add Git Connection">
    <template #body>
      <div class="space-y-4">
        <UFormField label="Provider" name="provider">
          <USelectMenu
            v-model="form.provider"
            :items="providerOptions"
            value-key="value"
            class="w-full"
          />
        </UFormField>
        <UFormField label="Name" name="name" required>
          <UInput v-model="form.name" placeholder="My GitHub Token" />
        </UFormField>
        <UFormField label="Personal Access Token" name="accessToken" required>
          <UInput v-model="form.accessToken" type="password" placeholder="ghp_..." />
        </UFormField>
        <UFormField
          v-if="form.provider === 'gitlab'"
          label="Self-hosted GitLab URL (optional)"
          name="baseUrl"
        >
          <UInput v-model="form.baseUrl" placeholder="https://gitlab.mycompany.com" />
        </UFormField>
        <UCheckbox v-model="form.isDefault" label="Set as default connection" />
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="outline" @click="showModal = false">Cancel</UButton>
        <UButton color="primary" :loading="saving" @click="saveConnection">Save</UButton>
      </div>
    </template>
  </UModal>
</template>

