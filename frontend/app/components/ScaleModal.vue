<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  resourceName: string
  resourceKind: string
  namespace?: string
  currentReplicas?: number
}>()

const emit = defineEmits(['close', 'scale'])
const isOpen = defineModel<boolean>('open')

const replicas = ref<number>(props.currentReplicas || 1)
const loading = ref(false)
const toast = useToast()

watch(() => props.currentReplicas, (val) => {
  if (val !== undefined) {
    replicas.value = val
  }
})

const handleScale = async () => {
  if (replicas.value < 0) {
    toast.add({ title: 'Invalid replicas', description: 'Replicas must be 0 or greater', color: 'error' })
    return
  }
  emit('scale', replicas.value)
  isOpen.value = false
}
</script>

<template>
  <UModal 
    v-model:open="isOpen" 
    :title="`Scale ${resourceKind} - ${resourceName}`"
    :description="`Set the number of replicas for ${resourceName}`"
  >
    <template #body>
      <div class="space-y-4">
        <UFormField label="Replicas" required>
          <UInput
            v-model.number="replicas"
            type="number"
            min="0"
            placeholder="Enter number of replicas"
            class="w-full"
          />
        </UFormField>
        <div class="text-sm text-gray-500">
          Current replicas: {{ currentReplicas || 0 }}
        </div>
      </div>
      <div class="flex justify-end gap-2 mt-4">
        <UButton label="Cancel" color="neutral" variant="ghost" @click="isOpen = false" />
        <UButton label="Scale" color="primary" :loading="loading" @click="handleScale" />
      </div>
    </template>
  </UModal>
</template>

