<script setup lang="ts">
import { ref } from 'vue'

interface SecurityAlert {
  id: number
  ruleName: string
  podName: string
  namespaceName: string
}

const props = defineProps<{
  modelValue: boolean
  alert: SecurityAlert
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'resolve': [note: string]
}>()

const isOpen = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const note = ref('')
const submitting = ref(false)

const handleSubmit = async () => {
  submitting.value = true
  try {
    emit('resolve', note.value)
    note.value = ''
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <UModal
    v-model:open="isOpen"
    title="Resolve Alert"
    size="lg"
  >
    <template #body>
      <div class="space-y-4">
        <p class="text-sm text-gray-600 dark:text-gray-400">
          You are about to resolve this alert:
        </p>
        <div class="bg-gray-100 dark:bg-gray-900 p-3 rounded">
          <p class="font-semibold">{{ alert.ruleName }}</p>
          <p class="text-sm text-gray-600 dark:text-gray-400">
            {{ alert.namespaceName }} / {{ alert.podName }}
          </p>
        </div>

        <UFormGroup label="Resolution Note">
          <UTextarea
            v-model="note"
            placeholder="Describe how you resolved this security issue..."
            :rows="3"
          />
        </UFormGroup>

        <p class="text-xs text-gray-500 dark:text-gray-400">
          After resolution, the alert will be marked as resolved and archived.
        </p>
      </div>
    </template>

    <template #footer>
      <div class="flex gap-2 justify-end">
        <UButton
          label="Cancel"
          color="neutral"
          @click="isOpen = false"
        />
        <UButton
          label="Resolve Alert"
          color="green"
          :loading="submitting"
          @click="handleSubmit"
        />
      </div>
    </template>
  </UModal>
</template>
