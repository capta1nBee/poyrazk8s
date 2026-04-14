<script setup lang="ts">
const props = defineProps<{
  title: string
  description?: string
  label: string
  placeholder?: string
  initialValue?: string
  type?: 'text' | 'number' | 'select'
  options?: { value: string; label: string }[]
  icon?: string
}>()

const emit = defineEmits(['submit'])
const isOpen = defineModel<boolean>('open')

const value = ref(props.initialValue || '')
const loading = ref(false)

watch(() => props.initialValue, (v) => {
  value.value = v || ''
})

const handleSubmit = async () => {
  loading.value = true
  try {
    await emit('submit', value.value)
    isOpen.value = false
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <UModal 
    v-model:open="isOpen" 
    :title="title"
    :description="description"
    :ui="{ width: 'w-full sm:max-w-md' }"
  >
    <template #body>
      <div class="space-y-4">
        <UFormField :label="label" required>
          <USelect
            v-if="type === 'select'"
            v-model="value"
            :items="options"
            class="w-full"
          />
          <UInput
            v-else
            v-model="value"
            :type="type || 'text'"
            :placeholder="placeholder"
            class="w-full"
            autofocus
            @keypress.enter="handleSubmit"
          />
        </UFormField>
      </div>
      
      <div class="flex justify-end gap-2 mt-6">
        <UButton label="Cancel" color="neutral" variant="ghost" @click="isOpen = false" />
        <UButton 
          :label="title.split(' ')[0] || 'Submit'" 
          :icon="icon || 'i-lucide-check'" 
          color="primary" 
          :loading="loading" 
          @click="handleSubmit" 
        />
      </div>
    </template>
  </UModal>
</template>
