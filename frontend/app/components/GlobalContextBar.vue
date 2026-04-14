<script setup lang="ts">
import type { Cluster } from '~/types/cluster'
import type { DropdownMenuItem } from '@nuxt/ui'

defineProps<{ collapsed?: boolean }>()
const clusterStore = useClusterStore()
const toast = useToast()

const clusters = computed(() => clusterStore.activeClusters)
const selectedCluster = computed(() => clusterStore.selectedCluster)

const selectCluster = (cluster: Cluster) => {
  clusterStore.selectCluster(cluster)
  toast.add({
    title: 'Cluster değiştirildi',
    description: `Aktif: ${cluster.name}`,
    color: 'green',
    icon: 'i-lucide-check'
  })
}

const dropdownItems = computed<DropdownMenuItem[][]>(() => {
  if (clusters.value.length === 0) return []
  return [[
    ...clusters.value.map(cluster => ({
      label: cluster.name,
      icon: selectedCluster.value?.uid === cluster.uid ? 'i-lucide-check' : 'i-lucide-database',
      onSelect: () => selectCluster(cluster)
    }))
  ]]
})
</script>

<template>
  <div class="w-full">
    <!-- Collapsed (Daraltılmış) Görünüm -->
    <div v-if="collapsed" class="flex flex-col items-center justify-center">
      <UTooltip text="Poyraz Kubernetes" placement="right">
        <img src="~/assets/logo/logo.svg" class="w-8 h-8 rounded-xl shadow-md" alt="Logo" />
      </UTooltip>
    </div>

    <!-- Expanded (Geniş) Görünüm -->
    <div v-else class="space-y-3">
      

      <!-- Cluster Seçici Dropdown -->
      <UDropdownMenu
        v-if="clusters.length > 0"
        :items="dropdownItems"
        :content="{ align: 'start', side: 'bottom', sideOffset: 8 }"
        class="w-full"
        :ui="{ width: 'w-full min-w-[200px]' }"
      >
        <UButton
          color="white"
          variant="solid"
          block
          class="justify-between bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 py-2 h-auto"
        >
          <div class="flex items-center gap-2.5 overflow-hidden">
             <!-- Durum İkonu -->
             <div class="relative flex items-center justify-center w-7 h-7 rounded bg-gray-100 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 flex-shrink-0">
               <UIcon name="i-lucide-server" class="w-4 h-4 text-gray-500" />
               <span class="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 bg-green-500 rounded-full border-2 border-white dark:border-gray-900"></span>
             </div>
             
             <!-- Yazı Alanı -->
             <div class="flex flex-col items-start truncate">
               <span class="text-[10px] text-gray-400 font-medium leading-none mb-0.5">Aktif Cluster</span>
               <span class="text-xs font-semibold text-gray-900 dark:text-gray-100 truncate w-24 text-left">
                 {{ selectedCluster?.name || 'Seçiniz' }}
               </span>
             </div>
          </div>
          <UIcon name="i-lucide-chevrons-up-down" class="w-4 h-4 text-gray-400 flex-shrink-0" />
        </UButton>
      </UDropdownMenu>
      
      <div v-else class="text-xs text-center border border-dashed border-gray-300 p-2 rounded text-gray-500">
        Cluster yok
      </div>
    </div>
  </div>
</template>