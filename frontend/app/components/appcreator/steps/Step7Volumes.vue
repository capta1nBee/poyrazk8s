<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'
import type { Volume, VolumeMount } from '~/stores/appcreator'

const store = useAppCreatorStore()
const w = computed(() => store.wizard)
const { $api } = useNuxtApp()
const clusterStore = useClusterStore()

// Existing PVCs from cluster
const existingPvcs = ref<string[]>([])
onMounted(async () => {
  if (!clusterStore.selectedCluster) return
  try {
    const res = await $api.get(`/k8s/${clusterStore.selectedCluster.uid}/persistentvolumeclaims`)
    existingPvcs.value = Array.isArray(res.data) ? res.data.map((p: any) => p.name || p) : []
  } catch {}
})

// Add volume form
const showAddVolume = ref(false)
const newVol = ref<Partial<Volume>>({ name: '', type: 'pvc', claimName: '' })

const addVolume = () => {
  if (!newVol.value.name?.trim()) return
  store.wizard.volumes.push({ ...newVol.value, name: newVol.value.name!.trim() } as Volume)
  // Auto-add mount for it
  store.wizard.volumeMounts.push({ name: newVol.value.name!.trim(), mountPath: `/data/${newVol.value.name!.trim()}`, readOnly: false, subPath: '' })
  newVol.value = { name: '', type: 'pvc', claimName: '' }
  showAddVolume.value = false
}

const removeVolume = (idx: number) => {
  const name = store.wizard.volumes[idx].name
  store.wizard.volumes.splice(idx, 1)
  const mountIdx = store.wizard.volumeMounts.findIndex(m => m.name === name)
  if (mountIdx >= 0) store.wizard.volumeMounts.splice(mountIdx, 1)
}

const getMountForVolume = (volName: string) => store.wizard.volumeMounts.find(m => m.name === volName)

const volumeTypeIcon = (type: string) => {
  const icons: Record<string, string> = { pvc: 'i-lucide-hard-drive', configMap: 'i-lucide-file-cog', secret: 'i-lucide-shield', emptyDir: 'i-lucide-folder', hostPath: 'i-lucide-server' }
  return icons[type] || 'i-lucide-database'
}
const volumeTypeColor = (type: string) => {
  const colors: Record<string, string> = { pvc: 'text-blue-500', configMap: 'text-green-500', secret: 'text-red-500', emptyDir: 'text-gray-400', hostPath: 'text-orange-500' }
  return colors[type] || 'text-gray-400'
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-start justify-between">
      <div>
        <h3 class="text-base font-semibold text-gray-900 dark:text-white">Volumes & Mounts</h3>
        <p class="text-sm text-gray-500 mt-0.5">Attach persistent storage, ConfigMaps, and Secrets to your container.</p>
      </div>
      <UButton size="sm" color="primary" icon="i-lucide-plus" @click="showAddVolume = true">Add Volume</UButton>
    </div>

    <!-- Add Volume Form -->
    <Transition name="slide-down">
      <div v-if="showAddVolume" class="p-4 bg-primary-50 dark:bg-primary-900/20 border border-primary-200 dark:border-primary-800 rounded-xl space-y-4">
        <h4 class="text-sm font-semibold text-primary-700 dark:text-primary-300">New Volume</h4>
        <div class="grid grid-cols-2 gap-3">
          <UFormGroup label="Volume Name" required>
            <UInput v-model="newVol.name" placeholder="my-data" size="sm" />
          </UFormGroup>
          <UFormGroup label="Volume Type">
            <USelectMenu v-model="newVol.type" :items="['pvc', 'configMap', 'secret', 'emptyDir', 'hostPath']" size="sm" />
          </UFormGroup>
        </div>
        <template v-if="newVol.type === 'pvc'">
          <UFormGroup label="PVC Name">
            <USelectMenu v-model="newVol.claimName" :items="existingPvcs" placeholder="Select existing PVC" searchable size="sm" />
          </UFormGroup>
          <p v-if="!existingPvcs.length" class="text-xs text-amber-600">No PVCs found in cluster. Create one first or use emptyDir.</p>
        </template>
        <UFormGroup v-else-if="newVol.type === 'configMap'" label="ConfigMap Name">
          <USelectMenu v-model="newVol.configMapName" :items="w.configMaps.map(c => c.name)" placeholder="Select ConfigMap" size="sm" />
        </UFormGroup>
        <UFormGroup v-else-if="newVol.type === 'secret'" label="Secret Name">
          <USelectMenu v-model="newVol.secretName" :items="w.secrets.map(s => s.name)" placeholder="Select Secret" size="sm" />
        </UFormGroup>
        <UFormGroup v-else-if="newVol.type === 'hostPath'" label="Host Path">
          <UInput v-model="newVol.hostPath" placeholder="/data/myapp" size="sm" />
        </UFormGroup>
        <div class="flex gap-2 justify-end">
          <UButton size="sm" color="neutral" variant="ghost" @click="showAddVolume = false">Cancel</UButton>
          <UButton size="sm" color="primary" icon="i-lucide-plus" :disabled="!newVol.name?.trim()" @click="addVolume">Add</UButton>
        </div>
      </div>
    </Transition>

    <!-- Volume List -->
    <div v-if="w.volumes.length" class="space-y-3">
      <div v-for="(vol, idx) in w.volumes" :key="idx" class="p-4 bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700 rounded-xl space-y-3">
        <div class="flex items-center gap-2">
          <UIcon :name="volumeTypeIcon(vol.type)" :class="['w-4 h-4 shrink-0', volumeTypeColor(vol.type)]" />
          <span class="text-sm font-semibold text-gray-800 dark:text-gray-200 font-mono">{{ vol.name }}</span>
          <UBadge size="xs" color="neutral" variant="subtle">{{ vol.type }}</UBadge>
          <div class="flex-1" />
          <UButton size="xs" color="red" variant="ghost" icon="i-lucide-trash-2" @click="removeVolume(idx)" />
        </div>
        <!-- Mount path config -->
        <div v-if="getMountForVolume(vol.name)" class="grid grid-cols-2 gap-3">
          <UFormGroup label="Mount Path">
            <UInput v-model="getMountForVolume(vol.name)!.mountPath" placeholder="/data" size="sm" icon="i-lucide-folder" />
          </UFormGroup>
          <UFormGroup label="Sub Path (optional)">
            <UInput v-model="getMountForVolume(vol.name)!.subPath" placeholder="subdir/" size="sm" />
          </UFormGroup>
          <div class="flex items-center gap-2">
            <UToggle v-model="getMountForVolume(vol.name)!.readOnly" size="sm" />
            <span class="text-xs text-gray-500">Read Only</span>
          </div>
        </div>
        <div v-if="vol.type === 'pvc'" class="text-xs text-gray-400 font-mono">PVC: {{ vol.claimName || '—' }}</div>
        <div v-else-if="vol.type === 'hostPath'" class="text-xs text-gray-400 font-mono">Host: {{ vol.hostPath || '—' }}</div>
      </div>
    </div>

    <div v-else class="flex flex-col items-center justify-center py-12 text-center text-gray-400">
      <UIcon name="i-lucide-hard-drive" class="w-10 h-10 mb-3 opacity-30" />
      <p class="text-sm font-medium">No volumes configured</p>
      <p class="text-xs mt-1">Containers are ephemeral by default. Add volumes for persistent storage.</p>
    </div>

    <div class="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg text-xs text-blue-700 dark:text-blue-400 flex gap-2">
      <UIcon name="i-lucide-info" class="w-4 h-4 shrink-0 mt-0.5" />
      <span>ConfigMaps and Secrets from Step 6 can be mounted as volumes. PVCs must exist in the cluster before deployment.</span>
    </div>
  </div>
</template>

<style scoped>
.slide-down-enter-active, .slide-down-leave-active { transition: all 0.2s ease; }
.slide-down-enter-from, .slide-down-leave-to { opacity: 0; transform: translateY(-10px); }
</style>

