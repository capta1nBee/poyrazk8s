<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'

const props = defineProps<{
  podName: string
  namespace: string
  containers?: Array<{ name: string }>
}>()

const isOpen = defineModel<boolean>('open')

const k8s = useKubernetes()
const toast = useToast()

/**
 * null   → hiç fetch edilmedi
 * {}     → fetch edildi ama veri yok
 * {..}   → metrics var
 */
const metrics = ref<Record<string, any> | null>(null)
const loading = ref(false)
const refreshInterval = ref<NodeJS.Timeout | null>(null)

const fetchMetrics = async () => {
  if (!isOpen.value) return

  loading.value = true
  metrics.value = null

  try {
    const podMetrics = await k8s.getPodMetrics(props.podName, props.namespace)

    // Transform array format to object format if needed
    if (podMetrics && podMetrics.containers && Array.isArray(podMetrics.containers)) {
      // Backend returns: { name, namespace, containers: [{name, cpu, memory}] }
      const transformed: Record<string, any> = {}
      podMetrics.containers.forEach((container: any) => {
        transformed[container.name] = {
          cpu: container.cpu,
          memory: container.memory
        }
      })
      metrics.value = transformed
    } else if (podMetrics && Object.keys(podMetrics).length > 0) {
      // Already in correct format
      metrics.value = podMetrics
    } else {
      metrics.value = {}
    }

  } catch (error: any) {
    console.error('❌ Error fetching metrics:', error)
    metrics.value = {}
    toast.add({
      title: 'Failed to fetch metrics',
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

const formatBytes = (memory: string | number) => {
  if (!memory) return '0 B'

  // If it's already a formatted string (e.g., "37208Ki")
  if (typeof memory === 'string') {
    // Parse Kubernetes memory format: Ki, Mi, Gi, etc.
    const match = memory.match(/^(\d+(?:\.\d+)?)(Ki|Mi|Gi|Ti|K|M|G|T|B)?$/)
    if (match) {
      const value = parseFloat(match[1])
      const unit = match[2] || 'B'

      // Convert to appropriate unit
      const units: Record<string, number> = {
        'B': 1,
        'K': 1000,
        'Ki': 1024,
        'M': 1000000,
        'Mi': 1024 * 1024,
        'G': 1000000000,
        'Gi': 1024 * 1024 * 1024,
        'T': 1000000000000,
        'Ti': 1024 * 1024 * 1024 * 1024
      }

      const bytes = value * (units[unit] || 1)
      const k = 1024
      const sizes = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
    }
    return memory
  }

  // If it's a number
  const k = 1024
  const sizes = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
  const i = Math.floor(Math.log(memory) / Math.log(k))
  return `${(memory / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

const formatCpu = (cpu: string | number) => {
  if (!cpu) return '0'

  // If it's a string with 'n' suffix (nanocores: e.g., "80772232n")
  if (typeof cpu === 'string' && cpu.endsWith('n')) {
    const nanocores = parseFloat(cpu)
    const cores = nanocores / 1000000000
    return `${cores.toFixed(3)} cores`
  }

  // If it's a string with 'm' suffix (millicores: e.g., "500m")
  if (typeof cpu === 'string' && cpu.endsWith('m')) {
    const millicores = parseFloat(cpu)
    const cores = millicores / 1000
    return `${cores.toFixed(3)} cores`
  }

  // If it's already a number (cores)
  return `${cpu} cores`
}

const containersToShow = computed(() => {
  if (!metrics.value || Object.keys(metrics.value).length === 0) {
    return []
  }

  // Filter out non-container keys (name, namespace, etc.)
  // Only include keys that have cpu/memory data
  const containerNames = Object.keys(metrics.value).filter(key => {
    const value = metrics.value![key]
    return value && typeof value === 'object' && (value.cpu || value.memory)
  })

  return containerNames.map(name => ({ name }))
})
</script>

<template>
  <UModal
    v-model:open="isOpen"
    :title="`Metrics - ${podName}`"
    :description="`Container metrics for pod in namespace ${namespace}`"
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
        <div
          v-for="container in containersToShow"
          :key="container.name"
          class="border rounded-lg p-4 dark:border-gray-700"
        >
          <h3 class="text-lg font-semibold mb-4 flex items-center gap-2">
            <UIcon name="i-lucide-box" class="w-5 h-5" />
            {{ container.name }}
          </h3>

          <div class="space-y-3 text-sm">
            <div v-if="metrics[container.name]?.cpu" class="flex justify-between items-center p-3 bg-gray-50 dark:bg-gray-800 rounded-lg">
              <div class="flex items-center gap-2">
                <UIcon name="i-lucide-cpu" class="w-4 h-4 text-blue-500" />
                <span class="text-gray-600 dark:text-gray-400">CPU Usage</span>
              </div>
              <span class="font-mono font-semibold">
                {{ formatCpu(metrics[container.name].cpu) }}
              </span>
            </div>

            <div v-if="metrics[container.name]?.memory" class="flex justify-between items-center p-3 bg-gray-50 dark:bg-gray-800 rounded-lg">
              <div class="flex items-center gap-2">
                <UIcon name="i-lucide-memory-stick" class="w-4 h-4 text-green-500" />
                <span class="text-gray-600 dark:text-gray-400">Memory Usage</span>
              </div>
              <span class="font-mono font-semibold">
                {{ formatBytes(metrics[container.name].memory) }}
              </span>
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

