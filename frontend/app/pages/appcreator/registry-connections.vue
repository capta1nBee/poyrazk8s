<script setup lang="ts">
const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const toast = useToast()
const { hasPermission, loading: permissionsLoading } = usePagePermissions('AppCreatorRegistry')

interface RegistryConnection {
  id: string
  registryType: 'dockerhub' | 'gitlab' | 'github' | 'custom'
  name: string
  serverUrl: string | null
  username: string
  imagePrefix: string | null
  isDefault: boolean
  createdAt: string
}

const connections = ref<RegistryConnection[]>([])
const loading = ref(false)
const showModal = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)

const emptyForm = () => ({
  registryType: 'dockerhub' as string,
  name: '',
  serverUrl: '',
  username: '',
  passwordToken: '',
  imagePrefix: '',
  isDefault: false
})

const form = ref(emptyForm())

const registryTypeOptions = [
  { label: 'Docker Hub', value: 'dockerhub', icon: 'i-simple-icons-docker' },
  { label: 'GitHub Container Registry', value: 'github', icon: 'i-simple-icons-github' },
  { label: 'GitLab Registry', value: 'gitlab', icon: 'i-simple-icons-gitlab' },
  { label: 'Custom / Private', value: 'custom', icon: 'i-lucide-server' }
]

const registryIcon = (t: string) => {
  const m: Record<string, string> = { dockerhub: 'i-simple-icons-docker', github: 'i-simple-icons-github', gitlab: 'i-simple-icons-gitlab', custom: 'i-lucide-server' }
  return m[t] ?? 'i-lucide-container'
}

const registryColor = (t: string) => {
  const m: Record<string, string> = { dockerhub: 'info', github: 'neutral', gitlab: 'warning', custom: 'primary' }
  return m[t] ?? 'neutral'
}

const showServerUrl = computed(() => form.value.registryType === 'custom' || form.value.registryType === 'gitlab' || form.value.registryType === 'github')
const modalTitle = computed(() => editingId.value ? 'Edit Registry Connection' : 'Add Registry Connection')

const fetchConnections = async () => {
  if (!clusterStore.selectedCluster) return
  loading.value = true
  try {
    const res = await $api.get(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/registry-connections`)
    connections.value = res.data
  } catch (e: any) {
    toast.add({ title: 'Failed to load registry connections', description: e.message, color: 'error' })
  } finally {
    loading.value = false
  }
}

const openAddModal = () => {
  editingId.value = null
  form.value = emptyForm()
  showModal.value = true
}

const openEditModal = (conn: RegistryConnection) => {
  editingId.value = conn.id
  form.value = {
    registryType: conn.registryType,
    name: conn.name,
    serverUrl: conn.serverUrl ?? '',
    username: conn.username,
    passwordToken: '',
    imagePrefix: conn.imagePrefix ?? '',
    isDefault: conn.isDefault
  }
  showModal.value = true
}

const saveConnection = async () => {
  if (!clusterStore.selectedCluster) return
  if (!form.value.name.trim() || !form.value.username.trim()) {
    toast.add({ title: 'Name and username are required', color: 'warning' })
    return
  }
  if (!editingId.value && !form.value.passwordToken.trim()) {
    toast.add({ title: 'Password / Token is required for new connections', color: 'warning' })
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form.value,
      serverUrl: form.value.serverUrl.trim() || null,
      imagePrefix: form.value.imagePrefix.trim() || null
    }
    if (editingId.value) {
      await $api.put(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/registry-connections/${editingId.value}`, payload)
      toast.add({ title: 'Registry connection updated', color: 'success' })
    } else {
      await $api.post(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/registry-connections`, payload)
      toast.add({ title: 'Registry connection added', color: 'success' })
    }
    showModal.value = false
    await fetchConnections()
  } catch (e: any) {
    toast.add({ title: 'Failed to save connection', description: e.message, color: 'error' })
  } finally {
    saving.value = false
  }
}

const deleteConnection = async (conn: RegistryConnection) => {
  if (!clusterStore.selectedCluster) return
  if (!confirm(`Delete connection "${conn.name}"?`)) return
  try {
    await $api.delete(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/registry-connections/${conn.id}`)
    toast.add({ title: 'Connection removed', color: 'success' })
    await fetchConnections()
  } catch (e: any) {
    toast.add({ title: 'Failed to delete connection', description: e.message, color: 'error' })
  }
}

watch(() => clusterStore.selectedCluster, (c) => { if (c) fetchConnections() }, { immediate: true })
</script>

<template>
  <UDashboardPanel id="appcreator-registry">
    <template #header>
      <UDashboardNavbar title="Registry Connections">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="hasPermission('create')"
            icon="i-lucide-plus"
            color="primary"
            label="Add Registry"
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
        <UIcon name="i-lucide-container" class="w-16 h-16 text-gray-200 dark:text-gray-700" />
        <div class="text-center">
          <h3 class="text-base font-semibold text-gray-700 dark:text-gray-300">No registry connections yet</h3>
          <p class="text-sm text-gray-500 mt-1">Add Docker Hub, GHCR, GitLab or a custom registry to push built images</p>
        </div>
        <UButton v-if="hasPermission('create')" icon="i-lucide-plus" color="primary" @click="openAddModal">
          Add Registry
        </UButton>
      </div>

      <div v-else class="p-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        <UCard v-for="conn in connections" :key="conn.id" class="relative">
          <template #header>
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <UIcon :name="registryIcon(conn.registryType)" class="w-5 h-5" />
                <span class="font-semibold">{{ conn.name }}</span>
                <UBadge v-if="conn.isDefault" size="xs" color="primary" variant="soft">Default</UBadge>
              </div>
              <div class="flex gap-1">
                <UButton
                  v-if="hasPermission('update')"
                  icon="i-lucide-pencil"
                  color="neutral"
                  variant="ghost"
                  size="xs"
                  @click="openEditModal(conn)"
                />
                <UButton
                  v-if="hasPermission('delete')"
                  icon="i-lucide-trash-2"
                  color="error"
                  variant="ghost"
                  size="xs"
                  @click="deleteConnection(conn)"
                />
              </div>
            </div>
          </template>
          <div class="space-y-1.5 text-sm text-gray-500 dark:text-gray-400">
            <div class="flex items-center gap-2">
              <UBadge :color="registryColor(conn.registryType)" variant="subtle" size="xs">
                {{ conn.registryType.toUpperCase() }}
              </UBadge>
              <span class="text-xs">{{ conn.username }}</span>
            </div>
            <div v-if="conn.serverUrl" class="text-xs font-mono truncate">{{ conn.serverUrl }}</div>
            <div v-if="conn.imagePrefix" class="text-xs">
              Prefix: <code class="bg-gray-100 dark:bg-gray-800 px-1 rounded">{{ conn.imagePrefix }}</code>
            </div>
            <div class="text-xs">Added {{ new Date(conn.createdAt).toLocaleDateString() }}</div>
          </div>
        </UCard>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Add / Edit Modal -->
  <UModal v-model:open="showModal" :title="modalTitle">
    <template #body>
      <div class="space-y-4">
        <UFormField label="Registry Type" name="registryType" required>
          <USelectMenu
            v-model="form.registryType"
            :items="registryTypeOptions"
            value-key="value"
            class="w-full"
          />
        </UFormField>
        <UFormField label="Connection Name" name="name" required>
          <UInput v-model="form.name" placeholder="My Docker Hub" icon="i-lucide-tag" />
        </UFormField>
        <UFormField v-if="showServerUrl" label="Registry Server URL" name="serverUrl">
          <UInput v-model="form.serverUrl" placeholder="registry.example.com:5000" icon="i-lucide-server" />
        </UFormField>
        <UFormField label="Username" name="username" required>
          <UInput v-model="form.username" placeholder="yourusername" icon="i-lucide-user" />
        </UFormField>
        <UFormField
          :label="editingId ? 'Password / Token (leave blank to keep current)' : 'Password / Access Token'"
          name="passwordToken"
          :required="!editingId"
        >
          <UInput v-model="form.passwordToken" type="password" placeholder="dckr_pat_…" icon="i-lucide-key" />
        </UFormField>
        <UFormField label="Image Prefix (namespace / org)" name="imagePrefix">
          <UInput v-model="form.imagePrefix" placeholder="myorg or mygroup/myproject" icon="i-lucide-layers" />
          <template #hint>
            <span class="text-xs text-gray-400">Images pushed as <code class="bg-gray-100 dark:bg-gray-800 px-1 rounded">prefix/appname_date</code></span>
          </template>
        </UFormField>
        <UCheckbox v-model="form.isDefault" label="Set as default registry" />
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="outline" @click="showModal = false">Cancel</UButton>
        <UButton color="primary" :loading="saving" @click="saveConnection">
          {{ editingId ? 'Update' : 'Save' }}
        </UButton>
      </div>
    </template>
  </UModal>
</template>

