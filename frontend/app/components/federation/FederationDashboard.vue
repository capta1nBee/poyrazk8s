<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
    federation: any
}>()

const emit = defineEmits(['refresh'])
const { $api } = useNuxtApp()
const toast = useToast()
const rollingBack = ref<number | null>(null)
const syncing = ref(false)

const handleSync = async () => {
    syncing.value = true
    try {
        await $api.post(`/federations/${props.federation.id}/sync`)
        toast.add({ title: 'Sync Triggered', description: 'Federation synchronization has been started in the background.', color: 'green' })
        // We wait a bit then refresh to see the status change
        setTimeout(() => emit('refresh'), 1000)
    } catch (e: any) {
        toast.add({ title: 'Sync Failed', description: e?.response?.data?.message || e.message, color: 'red' })
    } finally {
        syncing.value = false
    }
}

const handleRollback = async (resourceId: number) => {
    rollingBack.value = resourceId
    try {
        await $api.post(`/federations/${props.federation.id}/resources/${resourceId}/rollback`)
        toast.add({ title: 'Success', description: 'Resource rollbacked successfully', color: 'green' })
        emit('refresh')
    } catch (e: any) {
        toast.add({ title: 'Rollback Failed', description: e?.response?.data?.message || e.message, color: 'red' })
    } finally {
        rollingBack.value = null
    }
}

const getStatusColor = (status: string) => {
    if (status === 'Success') return 'text-green-500 bg-green-50 dark:bg-green-500/10 border-green-200 dark:border-green-500/20'
    if (status === 'Pending') return 'text-yellow-600 bg-yellow-50 dark:bg-yellow-500/10 border-yellow-200 dark:border-yellow-500/20'
    return 'text-red-500 bg-red-50 dark:bg-red-500/10 border-red-200 dark:border-red-500/20'
}

const getStatusIcon = (status: string) => {
    if (status === 'Success') return 'i-lucide-check-circle'
    if (status === 'Pending') return 'i-lucide-clock'
    return 'i-lucide-alert-circle'
}

const getGlobalStatusColor = (status: string) => {
    if (status === 'Success') return 'bg-green-50 text-green-500'
    if (status === 'Pending' || !status) return 'bg-yellow-50 text-yellow-500'
    return 'bg-red-50 text-red-500'
}

const getGlobalStatusTextClass = (status: string) => {
    if (status === 'Success') return 'text-green-600'
    if (status === 'Pending' || !status) return 'text-yellow-600'
    return 'text-red-600'
}

const formatDate = (dateString: string) => {
    if (!dateString) return 'Never'
    return new Date(dateString).toLocaleString()
}

const parseDependencies = (statusStr: string) => {
    try {
        return JSON.parse(statusStr || '[]')
    } catch (e) {
        return []
    }
}

const getDepIcon = (status: string) => {
    if (status === 'Synced') return 'i-lucide-check-circle-2'
    if (status === 'Missing') return 'i-lucide-help-circle'
    return 'i-lucide-alert-triangle'
}

const getDepColor = (status: string) => {
    if (status === 'Synced') return 'text-green-500'
    if (status === 'Missing') return 'text-yellow-500'
    return 'text-red-500'
}

const getDepBadgeColor = (status: string) => {
    if (status === 'Synced') return 'green'
    if (status === 'Missing') return 'yellow'
    return 'red'
}
</script>

<template>
  <div class="h-full flex flex-col space-y-6">
      <!-- Dashboard Header Stats -->
      <div class="grid grid-cols-3 gap-4">
          <div class="p-5 rounded-2xl bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 shadow-sm flex items-center gap-4">
              <div class="w-12 h-12 rounded-xl bg-primary-50 dark:bg-primary-500/10 flex flex-col items-center justify-center text-primary-500">
                  <UIcon name="i-lucide-server" class="w-6 h-6" />
              </div>
              <div>
                  <p class="text-xs text-gray-500 font-bold uppercase tracking-widest">Master Cluster</p>
                  <p class="text-lg font-black text-gray-900 dark:text-gray-100 truncate">{{ federation.masterClusterName }}</p>
              </div>
          </div>
          <div class="p-5 rounded-2xl bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 shadow-sm flex items-center gap-4">
              <div class="w-12 h-12 rounded-xl bg-purple-50 dark:bg-purple-500/10 flex flex-col items-center justify-center text-purple-500">
                  <UIcon name="i-lucide-cpu" class="w-6 h-6" />
              </div>
              <div>
                  <p class="text-xs text-gray-500 font-bold uppercase tracking-widest">Targets</p>
                  <p class="text-lg font-black text-gray-900 dark:text-gray-100">{{ federation.members?.length || 0 }} Clusters</p>
              </div>
          </div>
          <div class="p-5 rounded-2xl bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 shadow-sm flex items-center gap-4">
              <div class="w-12 h-12 rounded-xl flex flex-col items-center justify-center"
                   :class="getGlobalStatusColor(federation.status)">
                  <UIcon :name="getStatusIcon(federation.status || 'Pending')" class="w-6 h-6" />
              </div>
              <div>
                  <p class="text-xs text-gray-500 font-bold uppercase tracking-widest">Global Status</p>
                  <p class="text-lg font-black" :class="getGlobalStatusTextClass(federation.status)">{{ federation.status || 'Pending' }}</p>
              </div>
          </div>
      </div>

      <!-- Resources List & Health Tree-View -->
      <div class="flex-1 bg-white dark:bg-gray-800 rounded-2xl border border-gray-100 dark:border-gray-700 shadow-sm overflow-hidden flex flex-col">
          <div class="p-5 border-b border-gray-100 dark:border-gray-700 flex justify-between items-center bg-gray-50/50 dark:bg-gray-900/50">
              <div>
                  <h3 class="font-bold text-gray-900 dark:text-gray-100 text-lg flex items-center gap-2">
                       <UIcon name="i-lucide-activity" class="text-primary-500" /> Sync Health Panel
                  </h3>
                  <p class="text-xs text-gray-500 mt-1">Real-time resource synchronization and dependency health map</p>
              </div>
              <div class="flex items-center gap-2">
                  <UBadge color="gray" variant="outline" class="hidden md:flex items-center gap-1.5 px-3 py-1">
                      <div class="w-1.5 h-1.5 rounded-full bg-primary-500 animate-pulse"></div>
                      <span class="text-[10px] font-bold tracking-tighter uppercase">Watcher Active</span>
                  </UBadge>
                  <UButton 
                    icon="i-lucide-refresh-cw" 
                    color="white" 
                    size="sm" 
                    variant="soft"
                    :loading="syncing"
                    @click="handleSync"
                  >Force Resync</UButton>
              </div>
          </div>

          <div class="p-0 overflow-y-auto max-h-[700px] flex-1">
              <div v-for="res in federation.resources" :key="res.id" class="border-b last:border-b-0 border-gray-100 dark:border-gray-700/50 p-6 flex flex-col lg:flex-row gap-8 hover:bg-gray-50/30 dark:hover:bg-gray-800/30 transition-colors">
                  <!-- Info Side -->
                  <div class="lg:w-1/3 space-y-4">
                      <div class="flex items-start justify-between">
                          <div>
                              <div class="flex items-center gap-2 mb-1">
                                  <UBadge color="gray" variant="soft" size="xs">{{ res.kind }}</UBadge>
                                  <UBadge color="gray" variant="soft" size="xs">{{ res.namespace }}</UBadge>
                              </div>
                              <h4 class="font-bold text-lg text-gray-900 dark:text-gray-100">{{ res.name }}</h4>
                          </div>
                      </div>

                      <div class="p-4 rounded-xl border flex items-center gap-3" :class="getStatusColor(res.syncStatus)">
                           <UIcon :name="getStatusIcon(res.syncStatus)" class="w-5 h-5 shrink-0" />
                           <div class="flex-1 overflow-hidden">
                               <p class="font-bold text-sm leading-none m-0">{{ res.syncStatus || 'Pending' }}</p>
                               <p v-if="res.errorMessage" class="text-[10px] mt-1.5 font-medium opacity-90 line-clamp-3 leading-relaxed" :title="res.errorMessage">{{ res.errorMessage }}</p>
                           </div>
                      </div>

                      <div class="text-[11px] text-gray-500 space-y-2 py-1 border-t border-gray-50 dark:border-gray-700/50">
                          <p class="flex justify-between"><span>Last Sync:</span> <strong class="text-gray-700 dark:text-gray-300 font-black">{{ formatDate(res.lastSyncTime) }}</strong></p>
                          <p class="flex justify-between" v-if="res.lastErrorTime"><span>Last Error:</span> <strong class="text-red-600 dark:text-red-400 font-black">{{ formatDate(res.lastErrorTime) }}</strong></p>
                      </div>

                      <div class="pt-2">
                          <UButton
                            v-if="res.previousStateYaml"
                            icon="i-lucide-history"
                            size="sm"
                            :color="res.syncStatus === 'Error' ? 'red' : 'gray'"
                            variant="soft"
                            block
                            :loading="rollingBack === res.id"
                            @click="handleRollback(res.id)"
                            class="font-bold text-xs"
                          >
                            Rollback to Snapshot
                          </UButton>
                          <p v-else class="text-[10px] text-gray-400 text-center uppercase tracking-widest font-bold mt-2">No previous YAML snapshot</p>
                      </div>
                  </div>

                  <!-- Tree View Side -->
                  <div class="lg:w-2/3 border-l border-gray-100 dark:border-gray-700 pl-8 flex flex-col justify-center relative min-h-[160px]">
                      <h5 class="text-[10px] font-black text-gray-400 uppercase tracking-[0.2em] mb-4 absolute top-0 left-8">Dependency Status</h5>
                      
                      <!-- Main Root -->
                      <div class="flex items-center gap-4 relative z-10 py-2">
                          <div class="flex-none p-2.5 rounded-xl shadow-sm border border-gray-100 dark:border-gray-700" :class="getStatusColor(res.syncStatus)">
                              <UIcon name="i-lucide-layers" class="w-6 h-6" />
                          </div>
                          <div>
                              <p class="font-black text-base text-gray-900 dark:text-white leading-tight">{{ res.name }}</p>
                              <p class="text-xs text-gray-500 font-medium uppercase tracking-wider">Root {{ res.kind }}</p>
                          </div>
                      </div>

                      <!-- Dynamic Branches -->
                      <div class="ml-5 mt-2 pl-6 border-l-2 border-gray-200 dark:border-gray-700/50 space-y-4 relative z-10 py-4">
                          <div v-if="!res.dependencyStatus || parseDependencies(res.dependencyStatus).length === 0" class="flex items-center gap-2 text-gray-400 italic text-xs">
                              <UIcon name="i-lucide-info" class="w-4 h-4" />
                              No explicit dependencies detected
                          </div>
                          
                          <div v-for="(dep, idx) in parseDependencies(res.dependencyStatus)" :key="idx" 
                               class="flex items-center gap-3 relative before:absolute before:w-6 before:h-0.5 before:bg-gray-200 dark:before:bg-gray-700/50 before:-left-6 before:top-1/2">
                              <UIcon :name="getDepIcon(dep.status)" :class="getDepColor(dep.status)" class="w-5 h-5" />
                              <div class="flex-1 flex items-center justify-between gap-4">
                                  <div class="text-sm">
                                      <span class="text-gray-400 font-bold uppercase text-[10px] tracking-wider">{{ dep.kind }}:</span> 
                                      <span class="font-bold text-gray-800 dark:text-gray-200 ml-1">{{ dep.name }}</span>
                                  </div>
                                  <UBadge :color="getDepBadgeColor(dep.status)" size="xs" variant="soft" class="font-black uppercase tracking-tighter text-[9px] px-2 py-0.5">
                                      {{ dep.status || 'Unknown' }}
                                  </UBadge>
                              </div>
                          </div>
                      </div>
                      
                      <!-- Connection Lines -->
                      <div class="absolute w-px h-[calc(100%-80px)] bg-gray-100 dark:bg-gray-800 left-[43px] top-[75px] -z-10"></div>
                  </div>
              </div>
          </div>
      </div>
  </div>
</template>
