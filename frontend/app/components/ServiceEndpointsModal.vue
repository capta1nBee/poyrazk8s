<script setup lang="ts">
const props = defineProps<{
  serviceName: string
  namespace: string
}>()

const isOpen = defineModel<boolean>('open')
const k8s = useKubernetes()
const toast = useToast()

const endpoints = ref<any[]>([])
const loading = ref(false)

const fetchEndpoints = async () => {
  loading.value = true
  try {
    const response = await k8s.getServiceEndpoints(props.serviceName, props.namespace)
    endpoints.value = response.endpoints || []
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch endpoints',
      description: error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

watch(isOpen, (open) => {
  if (open) {
    fetchEndpoints()
  }
})
</script>

<template>
  <UModal 
    v-model:open="isOpen" 
    :title="`Endpoints - ${serviceName}`"
    :ui="{ width: 'w-full sm:max-w-[80vw]' }"
  >
    <template #body>
      <div v-if="loading" class="py-12 flex flex-col items-center justify-center gap-3">
        <UIcon name="i-lucide-loader-2" class="animate-spin text-3xl text-primary-500" />
        <p class="text-sm text-gray-500">Retrieving endpoints...</p>
      </div>
      
      <div v-else-if="endpoints.length === 0" class="py-12 flex flex-col items-center justify-center gap-2 text-gray-500">
        <UIcon name="i-lucide-info" class="text-3xl" />
        <p>No endpoints found for this service</p>
      </div>

      <div v-else class="space-y-3">
        <div v-for="(ep, index) in endpoints" :key="index" class="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-gray-200 dark:border-gray-700">
          <div class="flex items-center gap-3">
            <UIcon name="i-lucide-network" :class="ep.ready ? 'text-success-500' : 'text-error-500'" />
            <div class="flex flex-col">
              <span class="font-mono text-sm">{{ ep.ip }}:{{ ep.port }}</span>
              <span class="text-xs text-gray-500">{{ ep.targetName || 'External' }}</span>
            </div>
          </div>
          
          <div class="flex items-center gap-4">
            <UBadge :color="ep.ready ? 'success' : 'error'" variant="subtle" size="sm">
              {{ ep.ready ? 'Ready' : 'Not Ready' }}
            </UBadge>
            
            <div class="text-right hidden sm:flex flex-col">
              <span class="text-[10px] text-gray-400 font-mono uppercase">Node</span>
              <span class="text-xs text-gray-500">{{ ep.nodeName || '-' }}</span>
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
