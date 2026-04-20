<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  configMapName: string
  namespace: string
}>()

const emit = defineEmits(['close'])
const isOpen = defineModel<boolean>('open')

const k8s = useKubernetes()
const toast = useToast()

const data = ref<Record<string, string>>({})
const loading = ref(false)

// Fetch data from backend when modal opens
watch(isOpen, async (open) => {
  if (open) {
    loading.value = true
    try {
      data.value = await k8s.getConfigMapData(props.configMapName, props.namespace)
    } catch (error: any) {
      toast.add({
        title: 'Failed to fetch ConfigMap data',
        description: error.message,
        color: 'error'
      })
      data.value = {}
    } finally {
      loading.value = false
    }
  }
}, { immediate: true })
</script>

<template>
  <UModal 
    v-model:open="isOpen" 
    :title="`ConfigMap Data - ${configMapName}`"
    :description="`View data for ${configMapName}`"
  >
    <template #body>
      <div class="space-y-2">
        <div v-if="loading" class="flex items-center justify-center py-8">
          <UIcon name="i-lucide-loader-2" class="animate-spin text-2xl" />
        </div>
        <div v-else-if="!data || Object.keys(data).length === 0" class="text-gray-500 text-center py-4">
          No data
        </div>
        <div v-else class="space-y-3">
          <div v-for="(value, key) in data" :key="key" class="border rounded-lg p-3 bg-gray-50 dark:bg-gray-800">
            <div class="font-mono text-sm font-semibold text-blue-600 dark:text-blue-400 mb-2">{{ key }}:</div>
            <div class="font-mono text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap break-words bg-white dark:bg-gray-900 p-2 rounded">
              {{ value }}
            </div>
          </div>
        </div>
      </div>
      <div class="flex justify-end mt-4">
        <UButton label="Close" color="neutral" @click="isOpen = false" />
      </div>
    </template>
  </UModal>
</template>

