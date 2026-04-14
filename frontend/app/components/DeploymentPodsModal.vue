<script setup lang="ts">
import type { Pod } from '~/types/kubernetes'

const props = defineProps<{
  deploymentName: string
  namespace: string
}>()

const isOpen = defineModel<boolean>('open')
const k8s = useKubernetes()
const toast = useToast()

const pods = ref<Pod[]>([])
const loading = ref(false)

const fetchPods = async () => {
  loading.value = true
  try {
    pods.value = await k8s.getDeploymentPods(props.deploymentName, props.namespace)
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch pods',
      description: error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

watch(isOpen, (open) => {
  if (open) {
    fetchPods()
  }
})

const getStatusColor = (phase: string) => {
  switch (phase?.toLowerCase()) {
    case 'running': return 'success'
    case 'pending': return 'warning'
    case 'failed': return 'error'
    case 'succeeded': return 'neutral'
    default: return 'neutral'
  }
}

const formatDate = (timestamp: string) => {
  if (!timestamp) return '-'
  try {
    return new Date(timestamp).toLocaleString()
  } catch {
    return timestamp
  }
}
</script>

<template>
  <UModal 
    v-model:open="isOpen" 
    :title="`Pods - ${deploymentName}`"
    :description="`Pods managed by deployment ${deploymentName}`"
    :ui="{ width: 'w-full sm:max-w-[80vw]' }"
  >
    <template #body>
      <div v-if="loading" class="py-12 flex flex-col items-center justify-center gap-3">
        <UIcon name="i-lucide-loader-2" class="animate-spin text-3xl text-primary-500" />
        <p class="text-sm text-gray-500">Retrieving pods...</p>
      </div>
      
      <div v-else-if="pods.length === 0" class="py-12 flex flex-col items-center justify-center gap-2 text-gray-500">
        <UIcon name="i-lucide-info" class="text-3xl" />
        <p>No pods found for this deployment</p>
      </div>

      <div v-else class="space-y-3">
        <div v-for="pod in pods" :key="pod.uid" class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-gray-200 dark:border-gray-700">
          <div class="flex items-center gap-3">
            <UIcon name="i-lucide-box" class="text-gray-400" />
            <div class="flex flex-col">
              <span class="font-medium text-sm">{{ pod.name }}</span>
              <span class="text-xs text-gray-500">{{ pod.nodeName || 'No node assigned' }}</span>
            </div>
          </div>
          
          <div class="flex items-center gap-4">
            <div class="flex flex-col items-end">
              <UBadge :color="getStatusColor(pod.phase)" variant="subtle" size="sm">
                {{ pod.phase }}
              </UBadge>
              <span class="text-[10px] text-gray-400 mt-1">Restarts: {{ pod.restartCount }}</span>
            </div>
            
            <div class="text-right flex flex-col">
              <span class="text-xs font-mono text-gray-500">{{ formatDate(pod.k8sCreatedAt) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="flex justify-end mt-6">
        <UButton label="Close" color="neutral" variant="ghost" @click="isOpen = false" />
      </div>
    </template>
  </UModal>
</template>
