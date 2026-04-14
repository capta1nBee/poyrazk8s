<script setup lang="ts">
import MonacoEditor, { loader } from '@guolao/vue-monaco-editor'
import * as monaco from 'monaco-editor'

// Configure loader to use the local monaco instance instead of CDN for offline support
loader.config({ monaco })

const props = withDefaults(defineProps<{
  modelValue: string
  height?: string
  loading?: boolean
}>(), {
  height: '360px',
  loading: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()
</script>

<template>
  <div :style="{ height: height }" class="w-full relative">
    <div v-if="loading" class="absolute inset-0 z-10 bg-gray-50/50 dark:bg-gray-900/50 flex items-center justify-center">
      <UIcon name="i-lucide-loader-2" class="animate-spin text-2xl text-primary-500" />
    </div>
    <MonacoEditor
      language="yaml"
      theme="vs-dark"
      :value="modelValue"
      class="w-full h-full rounded-lg overflow-hidden border border-gray-200 dark:border-gray-700"
      :options="{
        minimap: { enabled: false },
        fontSize: 12,
        lineNumbers: 'on',
        wordWrap: 'on',
        scrollBeyondLastLine: false,
        automaticLayout: true
      }"
      @change="emit('update:modelValue', $event)"
    />
  </div>
</template>
