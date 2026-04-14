<script setup lang="ts">
const clusterStore = useClusterStore()
const { $api } = useNuxtApp()
const toast = useToast()

const repositories = ref([])
const loading = ref(false)
const isModalOpen = ref(false)
const saving = ref(false)

const clusterUid = computed(() => clusterStore.selectedCluster?.uid)

const form = reactive({
  id: null as string | null,
  name: '',
  url: '',
  isPrivate: false,
  username: '',
  password: ''
})

const fetchRepositories = async () => {
  if (!clusterUid.value) return
  loading.value = true
  try {
    const { data } = await $api.get(`/k8s/${clusterUid.value}/helm-repos`)
    repositories.value = data
  } catch (error: any) {
    console.error('Failed to fetch helm repositories:', error)
    toast.add({ title: 'Error', description: 'Failed to fetch helm repositories', color: 'red' })
  } finally {
    loading.value = false
  }
}

const openAddModal = () => {
  form.id = null
  form.name = ''
  form.url = ''
  form.isPrivate = false
  form.username = ''
  form.password = ''
  isModalOpen.value = true
}

const openEditModal = (repo: any) => {
  form.id = repo.id
  form.name = repo.name
  form.url = repo.url
  form.isPrivate = repo.isPrivate
  form.username = repo.username || ''
  form.password = '' // Don't show password
  isModalOpen.value = true
}

const handleSave = async () => {
  if (!clusterUid.value) return
  saving.value = true
  try {
    if (form.id) {
      await $api.put(`/k8s/${clusterUid.value}/helm-repos/${form.id}`, form)
      toast.add({ title: 'Success', description: 'Repository updated successfully', color: 'green' })
    } else {
      await $api.post(`/k8s/${clusterUid.value}/helm-repos`, form)
      toast.add({ title: 'Success', description: 'Repository added successfully', color: 'green' })
    }
    isModalOpen.value = false
    await fetchRepositories()
  } catch (error: any) {
    toast.add({ 
      title: 'Error', 
      description: error.response?.data?.message || 'Failed to save repository', 
      color: 'red' 
    })
  } finally {
    saving.value = false
  }
}

const confirmDelete = async (id: string) => {
  if (!clusterUid.value) return
  if (!confirm('Are you sure you want to delete this repository?')) return
  
  try {
    await $api.delete(`/k8s/${clusterUid.value}/helm-repos/${id}`)
    toast.add({ title: 'Success', description: 'Repository deleted', color: 'green' })
    await fetchRepositories()
  } catch (error: any) {
    toast.add({ title: 'Error', description: 'Failed to delete repository', color: 'red' })
  }
}

watch(clusterUid, (newVal) => {
  if (newVal) fetchRepositories()
}, { immediate: true })

const columns = [
  { key: 'name', label: 'Name' },
  { key: 'url', label: 'URL' },
  { key: 'isPrivate', label: 'Type' },
  { key: 'actions', label: '' }
]
</script>

<template>
  <div class="space-y-4">
    <div v-if="!clusterUid" class="p-12 text-center bg-gray-50 dark:bg-gray-900 rounded-2xl border-2 border-dashed border-gray-200 dark:border-gray-800 flex flex-col items-center justify-center space-y-4">
      <div class="w-16 h-16 rounded-full bg-orange-100 dark:bg-orange-900/30 flex items-center justify-center text-orange-500">
        <UIcon name="i-lucide-database-zap" class="w-8 h-8" />
      </div>
      <div class="max-w-xs">
        <h4 class="text-base font-bold text-gray-900 dark:text-white mb-1">No Cluster Selected</h4>
        <p class="text-sm text-gray-500 mb-6">Please choose a cluster from the dropdown below to manage its Helm repositories.</p>
        <ClusterSelector />
      </div>
    </div>

    <div v-else class="space-y-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
           <UIcon name="i-lucide-database" class="w-5 h-5 text-primary-500" />
           <h3 class="text-lg font-bold">Helm Repositories</h3>
           <UBadge variant="subtle" size="xs">{{ clusterStore.selectedCluster?.name }}</UBadge>
        </div>
        <UButton icon="i-lucide-plus" label="Add Repository" @click="openAddModal" color="black" />
      </div>

      <UCard :ui="{ body: { padding: 'p-0' } }">
        <LegacyTable :rows="repositories" :columns="columns" :loading="loading">
          <template #isPrivate-data="{ row }">
            <UBadge :color="row.isPrivate ? 'orange' : 'blue'" variant="subtle">
              {{ row.isPrivate ? 'Private' : 'Public' }}
            </UBadge>
          </template>
          
          <template #actions-data="{ row }">
            <div class="flex justify-end gap-2">
              <UButton icon="i-lucide-edit" variant="ghost" color="neutral" size="xs" @click="openEditModal(row)" />
              <UButton icon="i-lucide-trash" variant="ghost" color="red" size="xs" @click="confirmDelete(row.id)" />
            </div>
          </template>
        </LegacyTable>
      </UCard>
    </div>

    <UModal 
      v-model:open="isModalOpen" 
      :title="form.id ? 'Edit Repository' : 'Add Helm Repository'"
      description="Configure Helm repository details and credentials."
    >
      <template #body>
        <div class="space-y-4">
          <UFormGroup label="Repository Name" required help="Unique name to identify the repo.">
            <UInput v-model="form.name" placeholder="e.g. bitnami" />
          </UFormGroup>
          
          <UFormGroup label="Repository URL" required help="The full URL of the Helm repository.">
            <UInput v-model="form.url" placeholder="https://charts.bitnami.com/bitnami" />
          </UFormGroup>

          <div class="flex items-center gap-2 py-2">
            <USwitch v-model="form.isPrivate" />
            <span class="text-sm font-medium">This is a private repository</span>
          </div>

          <transition 
            enter-active-class="transition duration-200 ease-out" 
            enter-from-class="transform -translate-y-2 opacity-0"
            enter-to-class="transform translate-y-0 opacity-100"
          >
            <div v-if="form.isPrivate" class="space-y-4 pt-2 border-t border-gray-100 dark:border-gray-800">
              <UFormGroup label="Username">
                <UInput v-model="form.username" placeholder="Registry username" />
              </UFormGroup>
              <UFormGroup label="Password">
                <UInput v-model="form.password" type="password" placeholder="••••••••" />
              </UFormGroup>
            </div>
          </transition>

          <div class="flex justify-end gap-3 pt-4 border-t border-gray-100 dark:border-gray-800 mt-4">
            <UButton color="neutral" variant="outline" @click="isModalOpen = false">Cancel</UButton>
            <UButton color="primary" :loading="saving" @click="handleSave">Save Repository</UButton>
          </div>
        </div>
      </template>
    </UModal>
  </div>
</template>
