<script setup lang="ts">
import type { Cluster } from '~/types/cluster'

defineProps<{
  collapsed?: boolean
}>()

const clusterStore = useClusterStore()
const toast = useToast()

const clusters = computed(() => {
  return clusterStore.activeClusters
})
const selectedCluster = computed(() => {
  return clusterStore.selectedCluster
})

const selectCluster = (cluster: Cluster) => {
  clusterStore.selectCluster(cluster)
  toast.add({
    title: 'Cluster selected',
    description: `Switched to cluster: ${cluster.name}`,
    color: 'green'
  })
}
</script>

<template>
  <div class="w-full space-y-1">
    <div v-if="!collapsed" class="px-3 py-2 text-xs font-semibold text-gray-500 uppercase">
      Clusters
    </div>

    <div v-if="clusters.length > 0" class="space-y-1">
      <UButton
        v-for="cluster in clusters"
        :key="cluster.id"
        :label="collapsed ? undefined : cluster.name"
        :icon="collapsed ? 'i-lucide-database' : undefined"
        :trailing-icon="cluster.id === selectedCluster?.id ? 'i-lucide-check' : undefined"
        color="neutral"
        :variant="cluster.id === selectedCluster?.id ? 'soft' : 'ghost'"
        class="w-full justify-start"
        @click="selectCluster(cluster)"
      />
    </div>

    <div v-else class="px-3 py-4 text-center text-sm text-gray-500">
      <UIcon name="i-lucide-database" class="w-5 h-5 mx-auto mb-2 opacity-50" />
      <div v-if="!collapsed">No clusters available</div>
    </div>
  </div>
</template>

