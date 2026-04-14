<script setup lang="ts">
import type { Cluster } from '~/types/cluster'

const clusterStore = useClusterStore()
const authStore = useAuthStore()
const { $api } = useNuxtApp()
const toast = useToast()

const clusters = ref<Cluster[]>([])
const loading = ref(false)
const validated = ref(false)
const validationResult = ref<any>(null)
const confirmingDelete = ref(false)
const clusterToDelete = ref<any>(null)
const isDeleteModalOpen = ref(false)
const editingClusterId = ref<number | null>(null)
const showAddForm = ref(false)
const validating = ref(false)

// ── Registry Credentials ────────────────────────────────────────────────────
interface RegistryRow {
  id?: number
  registryUrl: string
  username: string
  password: string
  description: string
  _saving?: boolean
  _deleting?: boolean
  _dirty?: boolean
}
const registryCredentials = ref<RegistryRow[]>([])

function addNewRegistryRow() {
  registryCredentials.value.push({ registryUrl: '', username: '', password: '', description: '' })
}

async function loadRegistryCredentials(clusterUid: string) {
  try {
    const res = await $api.get<any[]>(`/k8s/${clusterUid}/registry-credentials`)
    registryCredentials.value = (res.data ?? []).map((r: any) => ({ ...r, _dirty: false }))
  } catch { registryCredentials.value = [] }
}

async function saveRegistryCredential(reg: RegistryRow) {
  if (!editingClusterId.value || !reg.registryUrl || !reg.username || !reg.password) return
  const cluster = clusters.value.find(c => c.id === editingClusterId.value)
  if (!cluster) return
  reg._saving = true
  try {
    const res = await $api.post(`/k8s/${cluster.uid}/registry-credentials`, {
      registryUrl: reg.registryUrl, username: reg.username, password: reg.password, description: reg.description
    })
    Object.assign(reg, res.data, { _dirty: false, _saving: false })
    toast.add({ title: 'Registry saved', description: `${reg.registryUrl} added successfully`, color: 'green' })
  } catch (e: any) {
    toast.add({ title: 'Error', description: e.response?.data?.message || 'Failed to save', color: 'red' })
    reg._saving = false
  }
}

async function updateRegistryCredential(reg: RegistryRow) {
  if (!editingClusterId.value || !reg.id) return
  const cluster = clusters.value.find(c => c.id === editingClusterId.value)
  if (!cluster) return
  reg._saving = true
  try {
    await $api.put(`/k8s/${cluster.uid}/registry-credentials/${reg.id}`, {
      registryUrl: reg.registryUrl, username: reg.username, password: reg.password, description: reg.description
    })
    reg._dirty = false
    toast.add({ title: 'Registry updated', color: 'green' })
  } catch (e: any) {
    toast.add({ title: 'Error', description: e.response?.data?.message || 'Failed to update', color: 'red' })
  } finally { reg._saving = false }
}

async function deleteRegistryCredential(reg: RegistryRow, idx: number) {
  if (!reg.id) { registryCredentials.value.splice(idx, 1); return }
  const cluster = clusters.value.find(c => c.id === editingClusterId.value)
  if (!cluster) return
  reg._deleting = true
  try {
    await $api.delete(`/k8s/${cluster.uid}/registry-credentials/${reg.id}`)
    registryCredentials.value.splice(idx, 1)
    toast.add({ title: 'Registry deleted', color: 'green' })
  } catch (e: any) {
    toast.add({ title: 'Error', description: e.response?.data?.message || 'Failed to delete', color: 'red' })
    reg._deleting = false
  }
}

const form = reactive({
  name: '',
  authType: 'KUBECONFIG',
  kubeconfig: '',
  vulnScanEnabled: false,
  backupEnabled: true,
  privateRegistryUser: '',
  privateRegistryPassword: ''
})

const fileInput = ref<HTMLInputElement | null>(null)
const kubeconfigTextarea = ref<any>(null)

const focusKubeconfig = () => {
    // Small delay to ensure render if needed, though mostly already rendered
    nextTick(() => {
        const textarea = kubeconfigTextarea.value?.textarea || kubeconfigTextarea.value?.$el?.querySelector('textarea')
        textarea?.focus()
    })
}

// ... existing code ...

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'apiServer', key: 'apiServer', label: 'API Server', sortable: true },
  { id: 'version', key: 'version', label: 'Version', sortable: true },
  { id: 'nodes', key: 'nodes', label: 'Nodes', sortable: true },
  { id: 'isActive', key: 'isActive', label: 'Status', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchClusters = async () => {
  loading.value = true
  try {
    await clusterStore.fetchClusters()
    clusters.value = clusterStore.clusters
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch clusters',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const openAddForm = () => {
  form.name = ''
  form.authType = 'KUBECONFIG'
  form.kubeconfig = ''
  form.vulnScanEnabled = false
  form.backupEnabled = true
  form.privateRegistryUser = ''
  form.privateRegistryPassword = ''
  validated.value = false
  validationResult.value = null
  editingClusterId.value = null
  registryCredentials.value = []
  showAddForm.value = true
}

const openEditForm = (cluster: Cluster) => {
  form.name = cluster.name
  form.authType = 'KUBECONFIG'
  form.kubeconfig = ''
  form.vulnScanEnabled = cluster.vulnScanEnabled || false
  form.backupEnabled = cluster.backupEnabled !== false
  form.privateRegistryUser = cluster.privateRegistryUser || ''
  form.privateRegistryPassword = cluster.privateRegistryPassword || ''
  validated.value = true
  validationResult.value = { valid: true }
  editingClusterId.value = cluster.id
  showAddForm.value = true
  // Load registry credentials for this cluster
  if (cluster.uid) loadRegistryCredentials(cluster.uid)
}

const cancelAddForm = () => {
  showAddForm.value = false
  form.name = ''
  form.authType = 'KUBECONFIG'
  form.kubeconfig = ''
  form.vulnScanEnabled = false
  form.backupEnabled = true
  form.privateRegistryUser = ''
  form.privateRegistryPassword = ''
  validated.value = false
  validationResult.value = null
  editingClusterId.value = null
  registryCredentials.value = []
}

const handleFileUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    form.kubeconfig = e.target?.result as string
    toast.add({
      title: 'File loaded',
      description: `${file.name} loaded successfully`,
      color: 'green'
    })
  }
  reader.readAsText(file)
}

const validateKubeconfig = async () => {
  if (!form.kubeconfig) {
    toast.add({
      title: 'Kubeconfig required',
      description: 'Please paste or upload a kubeconfig file',
      color: 'red'
    })
    return
  }

  validating.value = true
  try {
    const { data } = await $api.post('/clusters/validate', {
      name: form.name || 'temp-cluster',
      authType: form.authType,
      kubeconfig: form.kubeconfig
    })

    validationResult.value = data
    validated.value = data.valid

    if (data.valid) {
      // Auto-fill cluster name from validation result
      if (!form.name && data.clusterName) {
        form.name = data.clusterName
      }

      toast.add({
        title: 'Validation successful',
        description: `Cluster: ${data.clusterName} | Version: ${data.kubernetesVersion} | Nodes: ${data.nodeCount}`,
        color: 'green',
        timeout: 5000
      })
    } else {
      const errorMessage = data.message || data.errorDetails || 'Validation failed'
      toast.add({
        title: 'Validation failed',
        description: errorMessage,
        color: 'red',
        timeout: 5000
      })
    }
  } catch (error: any) {
    validated.value = false
    toast.add({
      title: 'Validation error',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    validating.value = false
  }
}

const handleSave = async () => {
  if (!validated.value) {
    toast.add({
      title: 'Validation required',
      description: 'Please validate the kubeconfig before saving',
      color: 'red'
    })
    return
  }

  if (!form.name) {
    toast.add({
      title: 'Name required',
      description: 'Please enter a cluster name',
      color: 'red'
    })
    return
  }
  loading.value = true
  try {
    const payload = {
      name: form.name,
      authType: form.authType,
      kubeconfig: form.kubeconfig,
      vulnScanEnabled: form.vulnScanEnabled,
      backupEnabled: form.backupEnabled,
      privateRegistryUser: form.privateRegistryUser,
      privateRegistryPassword: form.privateRegistryPassword
    }


    if (editingClusterId.value) {
      await $api.put(`/clusters/${editingClusterId.value}`, payload)
      toast.add({
        title: 'Cluster updated',
        description: `Cluster "${form.name}" has been updated successfully`,
        color: 'green'
      })
    } else {
      await $api.post('/clusters', payload)
      toast.add({
        title: 'Cluster created',
        description: `Cluster "${form.name}" has been added successfully`,
        color: 'green'
      })
    }

    cancelAddForm()
    await fetchClusters()
  } catch (error: any) {
    console.error('Failed to create cluster:', error)
    toast.add({
      title: 'Failed to create cluster',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const handleDelete = (cluster: any) => {
  clusterToDelete.value = cluster
  isDeleteModalOpen.value = true
}

const confirmDelete = async () => {
  if (!clusterToDelete.value) return
  
  confirmingDelete.value = true
  try {
    await $api.delete(`/clusters/${clusterToDelete.value.id}`)
    toast.add({
      title: 'Cluster fully purged',
      description: `Cluster "${clusterToDelete.value.name}" and all associated data have been deleted.`,
      color: 'green'
    })
    isDeleteModalOpen.value = false
    clusterToDelete.value = null
    await fetchClusters()
  } catch (error: any) {
    toast.add({
      title: 'Failed to delete cluster',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    confirmingDelete.value = false
  }
}

const selectCluster = (cluster: Cluster) => {
  clusterStore.selectCluster(cluster)
  toast.add({
    title: 'Cluster selected',
    description: `Switched to cluster: ${cluster.name}`,
    color: 'green'
  })
}

onMounted(async () => {
  await fetchClusters()
  // Auto-open form if no clusters exist
  if (clusters.value.length === 0) {
    showAddForm.value = true
  }
})
</script>

<template>
  <UDashboardPanel id="clusters">
    <template #header>
      <UDashboardNavbar title="Clusters">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <UButton
            v-if="!showAddForm"
            icon="i-lucide-plus"
            @click="openAddForm"
            label="Add Cluster"
            color="black"
          />
          <UButton
            v-if="showAddForm"
            icon="i-lucide-x"
            color="neutral"
            variant="ghost"
            @click="cancelAddForm"
          >
            Cancel
          </UButton>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="ghost"
            square
            :loading="loading"
            @click="fetchClusters"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Add Cluster Form (Premium Redesign) -->
      <div v-if="showAddForm" class="p-6 lg:p-8 border-b border-gray-200 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-900/50 flex items-start justify-center">
        <div class="w-full max-w-5xl bg-white dark:bg-gray-900 rounded-2xl shadow-2xl ring-1 ring-gray-200 dark:ring-gray-800 overflow-hidden">
          
          <!-- Form Header -->
          <div class="px-8 py-5 border-b border-gray-100 dark:border-gray-800 flex items-center justify-between bg-white dark:bg-gray-900">
             <div class="flex items-center gap-4">
                <div class="w-12 h-12 rounded-xl bg-primary-500/10 flex items-center justify-center">
                   <UIcon name="i-lucide-plus-circle" class="w-6 h-6 text-primary-500" />
                </div>
                <div>
                  <h2 class="text-xl font-bold text-gray-900 dark:text-white">{{ editingClusterId ? 'Update Cluster' : 'Connect New Cluster' }}</h2>
                  <p class="text-sm text-gray-500">Configure your Kubernetes connection and platform settings</p>
                </div>
             </div>
             <UButton color="gray" variant="ghost" icon="i-lucide-x" @click="cancelAddForm" size="lg" />
          </div>

          <div class="flex flex-col lg:flex-row h-full">
            <!-- Left Side: Connection & Config -->
            <div class="flex-1 p-8 lg:border-r border-gray-100 dark:border-gray-800 space-y-6">
              <div class="flex items-center justify-between mb-4">
                <h3 class="text-sm font-semibold text-gray-900 dark:text-white uppercase tracking-wider flex items-center gap-2">
                  <UIcon name="i-lucide-terminal" class="w-4 h-4 text-primary-500" />
                  Connection Configuration
                </h3>
                <div class="flex gap-2">
                  <UButton size="xs" color="gray" variant="soft" icon="i-lucide-upload" @click="fileInput?.click()">Upload File</UButton>
                  <UButton size="xs" color="gray" variant="soft" icon="i-lucide-clipboard" @click="focusKubeconfig">Paste Content</UButton>
                </div>
              </div>

              <input ref="fileInput" type="file" accept=".yaml,.yml,.config" class="hidden" @change="handleFileUpload" />

              <div class="relative group">
                <UTextarea
                  ref="kubeconfigTextarea"
                  v-model="form.kubeconfig"
                  placeholder="Paste your kubeconfig here..."
                  :rows="14"
                  class="font-mono text-xs w-full"
                />
                <div v-if="!form.kubeconfig" class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none opacity-40">
                  <UIcon name="i-lucide-file-code" class="w-12 h-12 mb-2 text-gray-400" />
                  <p class="text-sm text-center px-10 text-gray-500 dark:text-gray-400">Select a file or paste the content of your .kube/config</p>
                </div>
              </div>

              <div class="flex items-center justify-between pt-2">
                <div v-if="validationResult" class="flex items-center gap-2">
                  <UBadge :color="validated ? 'green' : 'red'" variant="subtle" class="rounded-full px-3">
                    <UIcon :name="validated ? 'i-lucide-check-circle' : 'i-lucide-alert-circle'" class="mr-1" />
                    {{ validated ? 'Config Validated' : 'Validation Failed' }}
                  </UBadge>
                </div>
                <div v-else class="text-xs text-gray-400">Not validated yet</div>

                <UButton
                  v-if="!validated || editingClusterId"
                  icon="i-lucide-shield-check"
                  color="black"
                  :loading="validating"
                  :disabled="!form.kubeconfig"
                  @click="validateKubeconfig"
                >
                  Validate
                </UButton>
              </div>
            </div>

            <!-- Right Side: Settings & Features -->
            <div class="flex-1 p-8 bg-gray-50/50 dark:bg-gray-900/50 space-y-8 overflow-y-auto">
              <!-- Header -->
              <div class="space-y-1">
                <h3 class="text-sm font-bold text-gray-900 dark:text-white uppercase tracking-widest flex items-center gap-2">
                  <UIcon name="i-lucide-settings-2" class="w-4 h-4 text-primary-500" />
                  Cluster Settings
                </h3>
                <p class="text-xs text-gray-500">Configure identifying information and optional features</p>
              </div>

              <!-- General Settings -->
              <div class="space-y-6">
                <UFormGroup label="Cluster Display Name" name="name" required help="This name will be used across the dashboard">
                  <UInput
                    v-model="form.name"
                    placeholder="e.g. Production Cluster"
                    icon="i-lucide-tag"
                    size="lg"
                    class="bg-white dark:bg-gray-950 shadow-sm"
                  />
                </UFormGroup>

                <!-- Cluster Info (Visible when validated) -->
                <transition
                  enter-active-class="transition duration-300 ease-out"
                  enter-from-class="transform -translate-y-2 opacity-0"
                  enter-to-class="transform translate-y-0 opacity-100"
                >
                  <div v-if="validated && validationResult" class="grid grid-cols-2 gap-3">
                    <div class="flex items-center gap-3 p-3 rounded-xl bg-white dark:bg-gray-950 border border-gray-200 dark:border-gray-800 shadow-sm">
                      <div class="w-8 h-8 rounded-lg bg-blue-500/10 flex items-center justify-center">
                        <UIcon name="i-lucide-cpu" class="w-4 h-4 text-blue-500" />
                      </div>
                      <div>
                        <p class="text-[10px] text-gray-400 uppercase font-black leading-none mb-1">K8s Version</p>
                        <p class="text-xs font-bold text-gray-900 dark:text-white leading-none">{{ validationResult.kubernetesVersion }}</p>
                      </div>
                    </div>
                    <div class="flex items-center gap-3 p-3 rounded-xl bg-white dark:bg-gray-950 border border-gray-200 dark:border-gray-800 shadow-sm">
                      <div class="w-8 h-8 rounded-lg bg-purple-500/10 flex items-center justify-center">
                        <UIcon name="i-lucide-layers" class="w-4 h-4 text-purple-500" />
                      </div>
                      <div>
                        <p class="text-[10px] text-gray-400 uppercase font-black leading-none mb-1">Nodes Count</p>
                        <p class="text-xs font-bold text-gray-900 dark:text-white leading-none">{{ validationResult.nodeCount }} Active</p>
                      </div>
                    </div>
                  </div>
                </transition>

                <!-- Vulnerability Scanning Section (Extremely Explicit Active/Passive) -->
                <div class="space-y-4">
                  <div class="p-6 bg-white dark:bg-gray-950 rounded-2xl border border-gray-200 dark:border-gray-800 shadow-sm space-y-6">
                    <div class="flex items-center gap-4">
                      <div class="w-14 h-14 rounded-2xl flex items-center justify-center transition-all duration-300 shadow-xl"
                           :class="form.vulnScanEnabled ? 'bg-orange-500 text-white shadow-orange-500/30' : 'bg-gray-100 dark:bg-gray-800 text-gray-400 shadow-none'">
                        <UIcon :name="form.vulnScanEnabled ? 'i-lucide-shield-check' : 'i-lucide-shield-off'" class="w-8 h-8" />
                      </div>
                      <div>
                        <h4 class="font-black text-gray-900 dark:text-white uppercase tracking-tighter text-xl">Scanning Status</h4>
                        <div class="flex items-center gap-2">
                           <div v-if="form.vulnScanEnabled" class="w-2 h-2 rounded-full bg-orange-500 animate-pulse"></div>
                           <span class="text-xs font-bold uppercase tracking-widest" :class="form.vulnScanEnabled ? 'text-orange-500' : 'text-gray-400'">
                              {{ form.vulnScanEnabled ? 'Currently Active' : 'Currently Passive' }}
                           </span>
                        </div>
                      </div>
                    </div>
                    
                    <div class="grid grid-cols-2 gap-4">
                      <UButton 
                        size="xl"
                        :color="form.vulnScanEnabled ? 'orange' : 'gray'" 
                        :variant="form.vulnScanEnabled ? 'solid' : 'soft'"
                        @click="form.vulnScanEnabled = true"
                        class="justify-center font-black py-4 transition-all active:scale-95"
                        :ui="{ rounded: 'rounded-xl' }"
                      >
                        <UIcon name="i-lucide-play-circle" class="mr-2 w-5 h-5" />
                        ACTIVE
                      </UButton>
                      <UButton 
                        size="xl"
                        :color="!form.vulnScanEnabled ? 'black' : 'gray'" 
                        :variant="!form.vulnScanEnabled ? 'solid' : 'soft'"
                        @click="form.vulnScanEnabled = false"
                        class="justify-center font-black py-4 transition-all active:scale-95"
                        :ui="{ rounded: 'rounded-xl' }"
                      >
                        <UIcon name="i-lucide-pause-circle" class="mr-2 w-5 h-5" />
                        PASSIVE
                      </UButton>
                    </div>
                  </div>

                  <transition
                    enter-active-class="transition duration-300 ease-out"
                    enter-from-class="transform -translate-y-4 opacity-0"
                    enter-to-class="transform translate-y-0 opacity-100"
                    leave-active-class="transition duration-200 ease-in"
                  >
                    <div v-if="form.vulnScanEnabled" class="space-y-5">
                      <!-- Default Fallback Credentials -->
                      <div class="p-6 bg-white dark:bg-gray-950 rounded-2xl border border-gray-200 dark:border-gray-800 shadow-xl">
                        <div class="flex items-center gap-2 text-[10px] font-black text-gray-400 uppercase tracking-[0.2em] mb-4">
                          <UIcon name="i-lucide-lock" class="w-3 h-3" />
                          Default Registry Credentials (Fallback)
                        </div>
                        <div class="grid grid-cols-2 gap-4">
                          <UFormGroup label="Default Username">
                            <UInput v-model="form.privateRegistryUser" placeholder="Username" icon="i-lucide-user" size="lg" class="bg-gray-50 dark:bg-gray-900/50" />
                          </UFormGroup>
                          <UFormGroup label="Default Password">
                            <UInput v-model="form.privateRegistryPassword" type="password" placeholder="••••••••" icon="i-lucide-key-round" size="lg" class="bg-gray-50 dark:bg-gray-900/50" />
                          </UFormGroup>
                        </div>
                        <p class="text-[10px] text-gray-400 mt-2">Used when image doesn't match any registry below</p>
                      </div>

                      <!-- Image Registry Whitelist -->
                      <div class="p-6 bg-white dark:bg-gray-950 rounded-2xl border border-gray-200 dark:border-gray-800 shadow-xl">
                        <div class="flex items-center justify-between mb-4">
                          <div class="flex items-center gap-2 text-[10px] font-black text-gray-400 uppercase tracking-[0.2em]">
                            <UIcon name="i-lucide-database" class="w-3 h-3" />
                            Image Registry Whitelist
                          </div>
                          <UButton size="xs" icon="i-lucide-plus" label="Add Registry" color="primary" variant="soft" @click="addNewRegistryRow()" :disabled="!editingClusterId" />
                        </div>

                        <p v-if="!editingClusterId" class="text-xs text-amber-500 mb-3">
                          <UIcon name="i-lucide-info" class="w-3 h-3 inline" /> Save the cluster first, then edit to manage registries.
                        </p>

                        <!-- Registry List -->
                        <div v-if="registryCredentials.length" class="space-y-3">
                          <div v-for="(reg, idx) in registryCredentials" :key="reg.id || idx"
                            class="p-4 rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-900/50">
                            <div class="grid grid-cols-12 gap-3 items-end">
                              <UFormGroup label="Registry URL" class="col-span-4">
                                <UInput v-model="reg.registryUrl" placeholder="harbor.example.com" icon="i-lucide-globe" size="sm" :disabled="!!reg.id" />
                              </UFormGroup>
                              <UFormGroup label="Username" class="col-span-3">
                                <UInput v-model="reg.username" placeholder="user" icon="i-lucide-user" size="sm" />
                              </UFormGroup>
                              <UFormGroup label="Password" class="col-span-3">
                                <UInput v-model="reg.password" type="password" placeholder="••••••••" icon="i-lucide-key-round" size="sm" />
                              </UFormGroup>
                              <div class="col-span-2 flex gap-1 pb-0.5">
                                <UButton v-if="!reg.id" icon="i-lucide-save" color="primary" size="xs" variant="soft" @click="saveRegistryCredential(reg)" :loading="reg._saving" />
                                <UButton v-if="reg.id && reg._dirty" icon="i-lucide-save" color="green" size="xs" variant="soft" @click="updateRegistryCredential(reg)" :loading="reg._saving" />
                                <UButton icon="i-lucide-trash-2" color="red" size="xs" variant="ghost" @click="deleteRegistryCredential(reg, idx)" :loading="reg._deleting" />
                              </div>
                            </div>
                            <UInput v-model="reg.description" placeholder="Description (optional)" size="xs" class="mt-2" />
                          </div>
                        </div>

                        <div v-else-if="editingClusterId" class="text-center py-6 text-gray-400">
                          <UIcon name="i-lucide-database" class="w-8 h-8 mb-2 mx-auto opacity-30" />
                          <p class="text-xs">No registries configured. Click "Add Registry" to start.</p>
                        </div>
                      </div>
                    </div>
                  </transition>
                </div>

                <!-- Backup Section -->
                <div class="p-6 bg-white dark:bg-gray-950 rounded-2xl border border-gray-200 dark:border-gray-800 shadow-sm space-y-6">
                  <div class="flex items-center gap-4">
                    <div class="w-14 h-14 rounded-2xl flex items-center justify-center transition-all duration-300 shadow-xl"
                         :class="form.backupEnabled ? 'bg-emerald-500 text-white shadow-emerald-500/30' : 'bg-gray-100 dark:bg-gray-800 text-gray-400 shadow-none'">
                      <UIcon :name="form.backupEnabled ? 'i-lucide-archive' : 'i-lucide-archive-x'" class="w-8 h-8" />
                    </div>
                    <div>
                      <h4 class="font-black text-gray-900 dark:text-white uppercase tracking-tighter text-xl">Automatic Backup</h4>
                      <div class="flex items-center gap-2">
                         <div v-if="form.backupEnabled" class="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></div>
                         <span class="text-xs font-bold uppercase tracking-widest" :class="form.backupEnabled ? 'text-emerald-500' : 'text-gray-400'">
                            {{ form.backupEnabled ? 'Currently Active' : 'Currently Passive' }}
                         </span>
                      </div>
                    </div>
                  </div>
                  
                  <p class="text-xs text-gray-500">
                    When active, cluster resources will be backed up to YAML files every night at 2:00 AM. Backups are stored in <code class="bg-gray-100 dark:bg-gray-800 px-1 rounded">/tmp/backup</code>.
                  </p>
                  
                  <div class="grid grid-cols-2 gap-4">
                    <UButton 
                      size="xl"
                      :color="form.backupEnabled ? 'emerald' : 'gray'" 
                      :variant="form.backupEnabled ? 'solid' : 'soft'"
                      @click="form.backupEnabled = true"
                      class="justify-center font-black py-4 transition-all active:scale-95"
                      :ui="{ rounded: 'rounded-xl' }"
                    >
                      <UIcon name="i-lucide-play-circle" class="mr-2 w-5 h-5" />
                      ACTIVE
                    </UButton>
                    <UButton 
                      size="xl"
                      :color="!form.backupEnabled ? 'black' : 'gray'" 
                      :variant="!form.backupEnabled ? 'solid' : 'soft'"
                      @click="form.backupEnabled = false"
                      class="justify-center font-black py-4 transition-all active:scale-95"
                      :ui="{ rounded: 'rounded-xl' }"
                    >
                      <UIcon name="i-lucide-pause-circle" class="mr-2 w-5 h-5" />
                      PASSIVE
                    </UButton>
                  </div>
                </div>
              </div>

              <!-- Footer Actions -->
              <div class="pt-8 border-t border-gray-100 dark:border-gray-800 space-y-4">
                <UButton
                  icon="i-lucide-save"
                  color="primary"
                  size="xl"
                  :loading="loading"
                  :disabled="!validated || !form.name"
                  @click="handleSave"
                  block
                  class="font-extrabold text-lg shadow-xl shadow-primary-500/20 active:scale-[0.98] transition-all"
                >
                  {{ editingClusterId ? 'Save Cluster Changes' : 'Connect & Finalize' }}
                </UButton>
                
                <div v-if="validationResult && !validated" class="p-4 rounded-xl bg-red-50 dark:bg-red-950/20 border border-red-200 dark:border-red-800/50 text-red-600 dark:text-red-400 text-xs flex items-start gap-3">
                  <UIcon name="i-lucide-alert-octagon" class="w-5 h-5 shrink-0" />
                  <div>
                    <p class="font-bold mb-1 uppercase">Validation Error</p>
                    <p>{{ validationResult.message || 'Please fix configuration errors before saving' }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Clusters Grid -->
      <div v-else class="p-6">
        <div v-if="clusters.length === 0 && !loading" class="text-center py-20">
          <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-gray-100 dark:bg-gray-800 mb-4">
             <UIcon name="i-lucide-cloud-off" class="w-8 h-8 text-gray-400" />
          </div>
          <h3 class="text-lg font-medium text-gray-900 dark:text-white">No Clusters Connected</h3>
          <p class="text-gray-500 mt-1 mb-6 max-w-sm mx-auto">Connect a Kubernetes cluster to start monitoring and managing your workloads.</p>
          <UButton icon="i-lucide-plus" color="black" @click="openAddForm">Add Your First Cluster</UButton>
        </div>

        <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          <UCard
            v-for="cluster in clusters"
            :key="cluster.id"
            :ui="{ 
                body: { padding: 'p-0' }, 
                header: { padding: 'p-4 sm:p-5' },
                footer: { padding: 'p-4 sm:p-5 bg-gray-50 dark:bg-gray-900/50' },
                ring: 'ring-1 ring-gray-200 dark:ring-gray-800 hover:ring-2 hover:ring-primary-500 dark:hover:ring-primary-400 transition-all duration-300'
            }"
            class="group cursor-pointer hover:shadow-lg transition-all"
            @click="openEditForm(cluster)"
          >
            <template #header>
              <div class="flex items-start justify-between gap-3">
                 <div class="flex items-center gap-3">
                    <div class="p-2 rounded-lg" :class="cluster.isActive ? 'bg-green-500/10 text-green-500' : 'bg-gray-500/10 text-gray-500'">
                        <UIcon name="i-lucide-server" class="w-6 h-6" />
                    </div>
                    <div>
                        <h3 class="font-semibold text-gray-900 dark:text-white leading-tight">{{ cluster.name }}</h3>
                        <p class="text-xs text-gray-500 mt-0.5 truncate max-w-[140px]">{{ cluster.apiServer }}</p>
                    </div>
                 </div>
                 <UBadge :color="cluster.isActive ? 'green' : 'gray'" variant="soft" size="xs" :ui="{ rounded: 'rounded-full' }">
                    <span class="flex items-center gap-1.5">
                        <span class="w-1.5 h-1.5 rounded-full bg-current animate-pulse" v-if="cluster.isActive"></span>
                        {{ cluster.isActive ? 'Online' : 'Offline' }}
                    </span>
                 </UBadge>
              </div>
            </template>
            
            <div class="p-5 space-y-4">
                <div class="grid grid-cols-2 gap-4">
                    <div class="space-y-1">
                        <p class="text-xs text-gray-500 font-medium uppercase tracking-wider">Version</p>
                        <p class="text-sm font-semibold">{{ cluster.version || '-' }}</p>
                    </div>
                     <div class="space-y-1">
                        <p class="text-xs text-gray-500 font-medium uppercase tracking-wider">Nodes</p>
                        <p class="text-sm font-semibold">{{ cluster.nodes || 0 }}</p>
                    </div>
                </div>
                
                <div class="pt-4 border-t border-gray-100 dark:border-gray-800 space-y-3">
                     <div class="space-y-1.5">
                        <div class="flex items-center justify-between text-xs">
                            <span class="text-gray-500">CPU Usage</span>
                            <span class="font-medium">{{ cluster.cpu || '0' }}</span>
                        </div>
                        <UProgress :value="35" color="blue" size="xs" />
                     </div>
                      <div class="space-y-1.5">
                        <div class="flex items-center justify-between text-xs">
                            <span class="text-gray-500">Memory</span>
                             <span class="font-medium">{{ cluster.memory || '0' }}</span>
                        </div>
                        <UProgress :value="62" color="purple" size="xs" />
                     </div>
                </div>
            </div>

            <template #footer>
                <div class="flex items-center gap-2">
                    <UButton block color="gray" variant="solid" size="sm" class="flex-1 font-bold" @click.stop="selectCluster(cluster)">
                        Dashboard
                    </UButton>
                    <UButton
                      icon="i-lucide-database"
                      color="orange"
                      variant="soft"
                      size="sm"
                      @click.stop="navigateTo(`/settings/helm-repos`); clusterStore.selectCluster(cluster)"
                      title="Manage Helm Repos"
                      class="hover:bg-orange-100 dark:hover:bg-orange-900/30 transition-colors"
                    />
                    <UButton
                      icon="i-lucide-trash-2"
                      color="red"
                      variant="soft"
                      size="sm"
                      @click.stop="handleDelete(cluster)"
                      title="Delete cluster and wipe data"
                      class="hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors"
                    />
                </div>
            </template>
          </UCard>
        </div>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Delete Confirmation Modal (Premium Redesign) -->
  <UModal 
    v-model:open="isDeleteModalOpen" 
    :prevent-close="confirmingDelete"
    title="Irreversible Action"
    description="Cluster Deletion & Data Wipe"
  >
    <template #body>
      <div class="space-y-6">
        <div class="flex items-center gap-4 text-red-600">
          <div class="w-14 h-14 rounded-2xl bg-red-50 dark:bg-red-950/30 flex items-center justify-center shadow-inner">
            <UIcon name="i-lucide-alert-triangle" class="w-8 h-8" />
          </div>
          <div>
            <h3 class="text-xl font-black uppercase tracking-tight">Irreversible Action</h3>
            <p class="text-xs font-bold text-red-500/70 uppercase tracking-widest leading-none">Cluster Deletion & Data Wipe</p>
          </div>
        </div>

        <div class="p-5 rounded-2xl bg-gray-50 dark:bg-gray-950 border border-gray-100 dark:border-gray-800 space-y-3">
          <p class="text-sm text-gray-700 dark:text-gray-300 leading-relaxed">
            Are you sure you want to delete <span class="font-black text-gray-900 dark:text-white">"{{ clusterToDelete?.name }}"</span>?
          </p>
          <div class="p-3 rounded-xl bg-white dark:bg-gray-900 border border-red-100 dark:border-red-900/30 text-[11px] text-red-600 dark:text-red-400 font-medium">
             <p class="flex items-start gap-2">
                <UIcon name="i-lucide-info" class="w-4 h-4 shrink-0 mt-0.5" />
                This will permanently wipe ALL cluster records, pods, deployments, services, and security scan results from the database.
             </p>
          </div>
        </div>

        <div class="flex items-center gap-3 pt-2">
          <UButton
            color="gray"
            variant="soft"
            size="lg"
            block
            class="flex-1 font-bold py-3"
            @click="isDeleteModalOpen = false"
            :disabled="confirmingDelete"
          >
            Cancel
          </UButton>
          <UButton
            color="red"
            variant="solid"
            size="lg"
            block
            class="flex-1 font-black py-3 shadow-lg shadow-red-500/20 active:scale-95"
            :loading="confirmingDelete"
            @click="confirmDelete"
          >
            Delete & Wipe
          </UButton>
        </div>
      </div>
    </template>
  </UModal>
</template>

