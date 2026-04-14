<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'

const store = useAppCreatorStore()
const clusterStore = useClusterStore()
const { $api } = useNuxtApp()
const toast = useToast()

const namespaces = ref<string[]>([])
const nsLoading = ref(false)
const createNewNs = ref(false)
const newNsName = ref('')
const creatingNs = ref(false)

const workloadTypes = [
  { value: 'Deployment', icon: 'i-lucide-layers', description: 'Stateless apps with rolling updates' },
  { value: 'StatefulSet', icon: 'i-lucide-database', description: 'Stateful apps with persistent storage' },
  { value: 'DaemonSet', icon: 'i-lucide-cpu', description: 'Run one pod on every node' },
  { value: 'CronJob', icon: 'i-lucide-clock', description: 'Scheduled or recurring jobs' },
]

const fetchNamespaces = async () => {
  if (!clusterStore.selectedCluster) return
  nsLoading.value = true
  try {
    // /for-page does not require Namespace resource permission —
    // AppCreator is a Tier 3 page and only has page-level access.
    const { data } = await $api.get(`/k8s/${clusterStore.selectedCluster.uid}/namespaces/for-page`)
    namespaces.value = Array.isArray(data) && data.length > 0 ? data : ['default']
  } catch {
    namespaces.value = ['default']
  } finally {
    nsLoading.value = false
  }
}

const toggleCreateNs = (val: boolean) => {
  createNewNs.value = val
  if (!val) {
    // Revert to first available namespace when cancelling
    if (!namespaces.value.includes(store.wizard.namespace)) {
      store.wizard.namespace = namespaces.value[0] ?? 'default'
    }
    newNsName.value = ''
  }
}

const confirmCreateNs = async () => {
  const name = newNsName.value.trim()
  if (!name) { toast.add({ title: 'Please enter a namespace name', color: 'warning' }); return }
  if (!clusterStore.selectedCluster) return
  creatingNs.value = true
  try {
    await $api.post(`/k8s/${clusterStore.selectedCluster.uid}/namespaces`, { name })
    toast.add({ title: `Namespace "${name}" created`, color: 'success' })
    await fetchNamespaces()
    store.wizard.namespace = name
    createNewNs.value = false
    newNsName.value = ''
  } catch (e: any) {
    toast.add({ title: 'Failed to create namespace', description: e?.response?.data?.message ?? e.message, color: 'error' })
  } finally {
    creatingNs.value = false
  }
}

// immediate: true → ilk açılışta da tetikler; selectedCluster değişince de yeniden çeker
watch(() => clusterStore.selectedCluster, fetchNamespaces, { immediate: true })
</script>

<template>
  <div class="space-y-6">
    <div>
      <h3 class="text-base font-semibold text-gray-900 dark:text-white">Basic Information</h3>
      <p class="text-sm text-gray-500 mt-0.5">Give your application a name and choose its workload type.</p>
    </div>

    <div class="grid grid-cols-1 gap-5">
      <!-- App Name -->
      <UFormField label="Application Name" required>
        <UInput
          v-model="store.wizard.name"
          placeholder="my-app"
          size="md"
          icon="i-lucide-tag"
          class="w-full"
        />
        <template #hint>
          Lowercase letters, numbers and hyphens only — e.g. <code class="bg-gray-100 dark:bg-gray-800 px-1 rounded">my-web-app</code>
        </template>
      </UFormField>

      <!-- Description -->
      <UFormField label="Description">
        <UTextarea
          v-model="store.wizard.description"
          placeholder="Briefly describe what this application does…"
          :rows="2"
          class="w-full"
        />
      </UFormField>

      <!-- Namespace -->
      <div class="space-y-1.5">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
            Namespace <span class="text-red-500">*</span>
          </label>
          <div class="flex items-center gap-1.5">
            <span class="text-xs text-gray-500">{{ createNewNs ? 'Type new name' : 'Create new' }}</span>
            <USwitch :model-value="createNewNs" size="xs" @update:model-value="toggleCreateNs" />
          </div>
        </div>

        <!-- Existing namespace selector -->
        <USelectMenu
          v-if="!createNewNs"
          v-model="store.wizard.namespace"
          :items="namespaces"
          placeholder="Select namespace"
          :loading="nsLoading"
          searchable
          class="w-full"
        />

        <!-- New namespace input -->
        <div v-else class="flex gap-2">
          <UInput
            v-model="newNsName"
            placeholder="new-namespace"
            icon="i-lucide-folder-plus"
            class="flex-1"
            @keyup.enter="confirmCreateNs"
          />
          <UButton
            color="primary"
            variant="soft"
            :loading="creatingNs"
            icon="i-lucide-check"
            @click="confirmCreateNs"
          >Create</UButton>
          <UButton color="neutral" variant="ghost" icon="i-lucide-x" @click="toggleCreateNs(false)" />
        </div>

        <p v-if="createNewNs" class="text-xs text-amber-600 dark:text-amber-400">
          ⚡ A new Kubernetes namespace will be created when you click <strong>Create</strong>.
        </p>
        <p v-else class="text-xs text-gray-400">
          Selected: <code class="bg-gray-100 dark:bg-gray-800 px-1 rounded">{{ store.wizard.namespace }}</code>
        </p>
      </div>

      <!-- Workload Type cards -->
      <div class="space-y-1.5">
        <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
          Workload Type <span class="text-red-500">*</span>
        </label>
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div
            v-for="type in workloadTypes"
            :key="type.value"
            :class="[
              'p-3 rounded-xl border-2 cursor-pointer transition-all select-none',
              store.wizard.workloadType === type.value
                ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 shadow-sm'
                : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
            ]"
            @click="store.wizard.workloadType = type.value"
          >
            <div class="flex items-center gap-2 mb-1.5">
              <UIcon
                :name="type.icon"
                :class="['w-4 h-4', store.wizard.workloadType === type.value ? 'text-primary-500' : 'text-gray-400']"
              />
              <span :class="['text-xs font-semibold', store.wizard.workloadType === type.value ? 'text-primary-600 dark:text-primary-400' : 'text-gray-600 dark:text-gray-400']">
                {{ type.value }}
              </span>
            </div>
            <p class="text-[10px] text-gray-400 leading-snug">{{ type.description }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

