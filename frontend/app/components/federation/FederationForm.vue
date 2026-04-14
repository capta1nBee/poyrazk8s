<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'

const props = defineProps<{
    federation?: any
}>()

const emit = defineEmits(['save', 'cancel'])

const { $api } = useNuxtApp()
const toast = useToast()

const form = ref({
    id: undefined,
    name: '',
    masterClusterId: undefined as number | undefined,
    memberClusterIds: [] as number[],
    resources: [] as any[]
})

const clusters = ref<any[]>([])
const clusterOptions = computed(() => clusters.value.map(c => ({
    id: c.id,
    name: c.name,
    uid: c.uid
})))

const selectedMaster = computed(() => clusters.value.find(c => c.id === form.value.masterClusterId))

const masterDropdownItems = computed(() => {
    return [
        clusters.value.map(cluster => ({
            label: cluster.name,
            icon: 'i-lucide-database',
            onSelect: () => {
                form.value.masterClusterId = cluster.id
            }
        }))
    ]
})

const namespaces = ref<string[]>([])
const loadingResources = ref(false)
const discoveredResources = ref<Record<string, string[]>>({}) // kind -> names[]

const fetchClusters = async () => {
    try {
        const response = await $api.get('/clusters/active')
        clusters.value = response.data
    } catch (e: any) {
        console.error('Fetch clusters error:', e)
        toast.add({ title: 'Error', description: 'Failed to fetch clusters', color: 'red' })
    }
}

const fetchNamespaces = async (clusterId: number) => {
    const cluster = clusters.value.find(c => c.id === clusterId)
    if (!cluster) return
    try {
        // /for-page does not require Namespace resource permission —
        // Federation is a Tier 3 page and only has page-level access.
        const { data } = await $api.get(`/k8s/${cluster.uid}/namespaces/for-page`)
        namespaces.value = Array.isArray(data) ? data : data.map((n: any) => n.name ?? n)
    } catch (e: any) {
        console.error('Failed to fetch namespaces', e)
    }
}

const fetchResources = async (clusterId: number, namespace: string, kind: string) => {
    const cluster = clusters.value.find(c => c.id === clusterId)
    if (!cluster || !namespace || !kind) return
    
    const key = `${namespace}-${kind}`
    if (discoveredResources.value[key]) return

    loadingResources.value = true
    try {
        const path = kind.toLowerCase() + 's'
        // API path uses cluster UID
        const { data } = await $api.get(`/k8s/${cluster.uid}/namespaces/${namespace}/${path}`)
        // v3 items pattern: often expecting { label, value } or just strings. 
        // We'll map to strings for simplicity in the list
        discoveredResources.value[key] = data.map((r: any) => r.name)
    } catch (e: any) {
        console.error(`Failed to fetch ${kind}`, e)
        discoveredResources.value[key] = []
    } finally {
        loadingResources.value = false
    }
}

onMounted(async () => {
    await fetchClusters()
    if (props.federation) {
        form.value = {
            id: props.federation.id,
            name: props.federation.name,
            masterClusterId: props.federation.masterClusterId,
            memberClusterIds: props.federation.members.map((m: any) => m.clusterId),
            resources: props.federation.resources.map((r: any) => ({ 
                kind: r.kind, 
                namespace: r.namespace, 
                name: r.name 
            }))
        }
    }
})

// Watchers for dynamic discovery
watch(() => form.value.masterClusterId, (newId) => {
    if (newId) {
        namespaces.value = []
        discoveredResources.value = {}
        fetchNamespaces(newId)
    }
})

const addResource = () => {
    form.value.resources.push({ kind: 'Deployment', namespace: 'default', name: '' })
}

const removeResource = (index: number) => {
    form.value.resources.splice(index, 1)
}

const submit = () => {
    if (!form.value.name || !form.value.masterClusterId || form.value.memberClusterIds.length === 0 || form.value.resources.length === 0) {
        toast.add({ title: 'Validation Error', description: 'Please fill all required fields and add at least one member and resource', color: 'red' })
        return
    }
    emit('save', form.value)
}
</script>

<template>
  <div class="bg-white dark:bg-gray-900 rounded-2xl shadow-xl ring-1 ring-gray-200 dark:ring-gray-800 p-6 space-y-6">
      <div class="flex items-center justify-between border-b pb-4 border-gray-100 dark:border-gray-800">
          <h2 class="text-xl font-bold flex items-center gap-2">
              <UIcon name="i-lucide-share-2" class="text-primary-500" />
              {{ form.id ? 'Edit' : 'Create' }} Federation
          </h2>
          <UButton icon="i-lucide-x" color="gray" variant="ghost" @click="emit('cancel')" />
      </div>

      <UFormGroup label="Federation Name" required>
          <UInput v-model="form.name" placeholder="e.g. Prod-DR-Sync" size="lg" icon="i-lucide-tag" />
      </UFormGroup>

      <div class="grid grid-cols-2 gap-4">
          <UFormGroup label="Master Cluster (Source)" required>
              <UDropdownMenu
                v-if="clusters.length > 0"
                :items="masterDropdownItems"
                :content="{ align: 'start', side: 'bottom', sideOffset: 8 }"
                class="w-full"
                :ui="{ width: 'w-full min-w-[200px]' }"
              >
                <UButton
                  color="white"
                  variant="solid"
                  block
                  class="justify-between bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 py-2.5 h-auto"
                >
                  <div class="flex items-center gap-2.5 overflow-hidden">
                     <div class="relative flex items-center justify-center w-7 h-7 rounded bg-gray-100 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 flex-shrink-0">
                       <UIcon name="i-lucide-server" class="w-4 h-4 text-primary-500" />
                     </div>
                     <div class="flex flex-col items-start truncate text-left">
                       <span class="text-[10px] text-gray-400 font-medium leading-none mb-0.5 uppercase tracking-wider">Source Cluster</span>
                       <span class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
                         {{ selectedMaster?.name || 'Select Source' }}
                       </span>
                     </div>
                  </div>
                  <UIcon name="i-lucide-chevrons-up-down" class="w-4 h-4 text-gray-400 flex-shrink-0" />
                </UButton>
              </UDropdownMenu>
              <div v-else class="text-xs text-gray-500 italic p-3 border border-dashed rounded-lg bg-gray-50 dark:bg-gray-800/20">
                  Loading clusters...
              </div>
          </UFormGroup>

          <UFormGroup label="Member Clusters (Targets)" required>
              <USelectMenu 
                v-model="form.memberClusterIds" 
                :items="clusterOptions.filter(c => c.id !== form.masterClusterId)" 
                multiple 
                value-key="id"
                label-key="name" 
                placeholder="Select Target Clusters"
                class="w-full"
                size="lg"
              >
                <template #label>
                  <div v-if="form.memberClusterIds.length > 0" class="flex flex-wrap gap-1">
                    <UBadge 
                      v-for="id in form.memberClusterIds" 
                      :key="id" 
                      size="xs" 
                      variant="soft" 
                      color="primary"
                    >
                      {{ clusterOptions.find(c => c.id === id)?.name }}
                    </UBadge>
                  </div>
                  <span v-else class="text-gray-400">Select Target Clusters</span>
                </template>
              </USelectMenu>
          </UFormGroup>
      </div>

      <div class="border-t border-gray-100 dark:border-gray-800 pt-6">
          <div class="flex items-center justify-between mb-4">
               <h3 class="text-sm font-bold uppercase tracking-widest text-gray-500 flex items-center gap-2">
                   <UIcon name="i-lucide-box" /> Resources to Sync
               </h3>
               <UButton icon="i-lucide-plus" size="xs" color="black" @click="addResource" variant="soft">Add Resource</UButton>
          </div>

          <div class="space-y-3">
              <div v-for="(res, index) in form.resources" :key="index" class="p-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl border border-gray-100 dark:border-gray-800 space-y-3">
                  <div class="flex items-center gap-3">
                      <USelectMenu 
                        v-model="res.kind" 
                        :items="['Deployment', 'StatefulSet', 'DaemonSet', 'Pod']" 
                        class="w-1/3" 
                        placeholder="Kind" 
                        @change="res.name = ''" 
                      />
                      
                      <USelectMenu 
                        v-model="res.namespace" 
                        :items="namespaces" 
                        class="w-1/3" 
                        placeholder="Namespace" 
                        searchable
                        @change="res.name = ''"
                      />
                      
                      <div class="flex-1 flex gap-2 overflow-hidden">
                        <USelectMenu 
                            v-model="res.name" 
                            :items="discoveredResources[`${res.namespace}-${res.kind}`] || []" 
                            placeholder="Resource Name" 
                            class="flex-1"
                            searchable
                            @focus="form.masterClusterId && fetchResources(form.masterClusterId, res.namespace, res.kind)"
                        >
                            <template #empty>
                                <div class="p-2 text-xs text-gray-500">No resources found</div>
                            </template>
                        </USelectMenu>
                        <UButton icon="i-lucide-trash" color="red" variant="soft" @click="removeResource(index)" />
                      </div>
                  </div>
              </div>

              <div v-if="form.resources.length === 0" class="text-sm text-center py-6 text-gray-400 bg-gray-50 dark:bg-gray-800/20 rounded-xl border border-dashed border-gray-200 dark:border-gray-800">
                  No resources added. Please add at least one resource to monitor and sync from master.
              </div>
          </div>
      </div>

      <div class="pt-6 border-t border-gray-100 dark:border-gray-800 flex justify-end gap-3">
          <UButton color="gray" variant="soft" @click="emit('cancel')" size="lg">Cancel</UButton>
          <UButton color="primary" @click="submit" class="font-bold" size="lg" icon="i-lucide-save">Save Federation</UButton>
      </div>
  </div>
</template>
