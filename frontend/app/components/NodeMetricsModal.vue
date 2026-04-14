<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'

const props = defineProps<{
  nodeName: string
}>()

const isOpen = defineModel<boolean>('open')

const k8s = useKubernetes()
const toast = useToast()

const metrics = ref<Record<string, any> | null>(null)
const loading = ref(false)
const refreshInterval = ref<NodeJS.Timeout | null>(null)

const fetchMetrics = async () => {
  if (!isOpen.value) return

  loading.value = true
  metrics.value = null

  try {
    const nodeMetrics = await k8s.getNodeMetrics(props.nodeName)

    metrics.value =
      nodeMetrics && Object.keys(nodeMetrics).length > 0
        ? nodeMetrics
        : {}
  } catch (error: any) {
    metrics.value = {}
    toast.add({
      title: 'Failed to fetch node metrics',
      description: error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

const startAutoRefresh = () => {
  if (refreshInterval.value) return
  refreshInterval.value = setInterval(fetchMetrics, 5000)
}

const stopAutoRefresh = () => {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value)
    refreshInterval.value = null
  }
}

watch(isOpen, (open) => {
  if (open) {
    fetchMetrics()
    startAutoRefresh()
  } else {
    stopAutoRefresh()
    metrics.value = null
  }
})

onUnmounted(stopAutoRefresh)

/* ----------------- helpers ----------------- */

const formatBytes = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

const formatCpu = (cpu: string | number) => {
  if (!cpu) return '0'
  if (typeof cpu === 'string' && cpu.endsWith('m')) {
    return `${parseFloat(cpu) / 1000} cores`
  }
  return `${cpu} cores`
}

const parseCapacity = (capacityStr: string) => {
  try {
    return typeof capacityStr === 'string' ? JSON.parse(capacityStr) : capacityStr
  } catch {
    return {}
  }
}

const capacity = computed(() => parseCapacity(metrics.value?.capacity || '{}'))
const allocatable = computed(() => parseCapacity(metrics.value?.allocatable || '{}'))
</script>

<template>
  <UModal
    v-model:open="isOpen"
    :title="`Node Metrics - ${nodeName}`"
    :description="`Resource metrics for node ${nodeName}`"
  >
    <template #body>
      <!-- LOADING -->
      <div v-if="loading" class="flex justify-center py-8">
        <UIcon name="i-lucide-loader-2" class="animate-spin text-2xl" />
      </div>

      <!-- EMPTY -->
      <div
        v-else-if="metrics !== null && Object.keys(metrics).length === 0"
        class="text-center py-8 text-gray-500"
      >
        No metrics available
      </div>

      <!-- METRICS -->
      <div v-else-if="metrics" class="space-y-4">
        <!-- Node Status -->
        <div class="border rounded-lg p-4 dark:border-gray-700">
          <h3 class="text-lg font-semibold mb-4 flex items-center gap-2">
            <UIcon name="i-lucide-server" class="w-5 h-5" />
            Node Status
          </h3>
          <div class="space-y-2 text-sm">
            <div class="flex justify-between">
              <span class="text-gray-600 dark:text-gray-400">Status</span>
              <span class="font-mono font-semibold">{{ metrics.status || 'Unknown' }}</span>
            </div>
          </div>
        </div>

        <!-- Capacity -->
        <div v-if="capacity && Object.keys(capacity).length > 0" class="border rounded-lg p-4 dark:border-gray-700">
          <h3 class="text-lg font-semibold mb-4 flex items-center gap-2">
            <UIcon name="i-lucide-hard-drive" class="w-5 h-5" />
            Capacity
          </h3>
          <div class="space-y-2 text-sm">
            <div v-if="capacity.cpu" class="flex justify-between">
              <span class="text-gray-600 dark:text-gray-400">CPU</span>
              <span class="font-mono font-semibold">{{ capacity.cpu }} cores</span>
            </div>
            <div v-if="capacity.memory" class="flex justify-between">
              <span class="text-gray-600 dark:text-gray-400">Memory</span>
              <span class="font-mono font-semibold">{{ capacity.memory }}</span>
            </div>
            <div v-if="capacity.pods" class="flex justify-between">
              <span class="text-gray-600 dark:text-gray-400">Pods</span>
              <span class="font-mono font-semibold">{{ capacity.pods }}</span>
            </div>
          </div>
        </div>

        <!-- Allocatable -->
        <div v-if="allocatable && Object.keys(allocatable).length > 0" class="border rounded-lg p-4 dark:border-gray-700">
          <h3 class="text-lg font-semibold mb-4 flex items-center gap-2">
            <UIcon name="i-lucide-cpu" class="w-5 h-5" />
            Allocatable
          </h3>
          <div class="space-y-2 text-sm">
            <div v-if="allocatable.cpu" class="flex justify-between">
              <span class="text-gray-600 dark:text-gray-400">CPU</span>
              <span class="font-mono font-semibold">{{ allocatable.cpu }} cores</span>
            </div>
            <div v-if="allocatable.memory" class="flex justify-between">
              <span class="text-gray-600 dark:text-gray-400">Memory</span>
              <span class="font-mono font-semibold">{{ allocatable.memory }}</span>
            </div>
            <div v-if="allocatable.pods" class="flex justify-between">
              <span class="text-gray-600 dark:text-gray-400">Pods</span>
              <span class="font-mono font-semibold">{{ allocatable.pods }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="flex justify-between gap-2 mt-6">
        <UButton
          label="Refresh"
          icon="i-lucide-refresh-cw"
          variant="ghost"
          :loading="loading"
          @click="fetchMetrics"
        />
        <UButton label="Close" color="neutral" @click="isOpen = false" />
      </div>
    </template>
  </UModal>
</template>
