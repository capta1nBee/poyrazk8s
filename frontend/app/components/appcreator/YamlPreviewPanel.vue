<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'

const store = useAppCreatorStore()
const selectedFile = ref<string | null>(null)

const fileList = computed(() => Object.keys(store.yamlPreview || {}))

const currentYaml = computed(() => {
  if (!store.yamlPreview) return ''
  const key = selectedFile.value || fileList.value[0] || ''
  return store.yamlPreview[key] || ''
})

watch(fileList, (list) => {
  if (list.length > 0 && !selectedFile.value) {
    selectedFile.value = list[0]
  }
})

const refreshing = ref(false)
const refreshPreview = async () => {
  refreshing.value = true
  await store.refreshYamlPreview()
  refreshing.value = false
}
</script>

<template>
  <div class="flex flex-col h-full bg-gray-950 rounded-xl border border-gray-800 overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between px-4 py-2.5 border-b border-gray-800 bg-gray-900">
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-file-code" class="w-4 h-4 text-primary-400" />
        <span class="text-xs font-semibold text-gray-300">YAML Preview</span>
        <UBadge v-if="fileList.length" color="primary" variant="subtle" size="xs">
          {{ fileList.length }} file{{ fileList.length > 1 ? 's' : '' }}
        </UBadge>
      </div>
      <UButton
        size="xs"
        color="neutral"
        variant="ghost"
        icon="i-lucide-refresh-cw"
        :loading="refreshing"
        @click="refreshPreview"
      />
    </div>

    <!-- File tabs -->
    <div v-if="fileList.length > 1" class="flex gap-1 px-3 pt-2 pb-1 border-b border-gray-800 bg-gray-900 overflow-x-auto">
      <button
        v-for="file in fileList"
        :key="file"
        :class="[
          'px-2.5 py-1 text-[10px] font-mono rounded transition-all whitespace-nowrap',
          selectedFile === file
            ? 'bg-primary-500/20 text-primary-300 border border-primary-500/40'
            : 'text-gray-500 hover:text-gray-300'
        ]"
        @click="selectedFile = file"
      >{{ file }}</button>
    </div>

    <!-- Empty state -->
    <div v-if="!store.yamlPreview" class="flex-1 flex flex-col items-center justify-center gap-3 p-6">
      <UIcon name="i-lucide-file-code-2" class="w-10 h-10 text-gray-700" />
      <p class="text-xs text-gray-500 text-center">
        Fill in the wizard to see YAML preview
      </p>
      <UButton size="xs" color="primary" variant="soft" @click="refreshPreview">
        Generate Preview
      </UButton>
    </div>

    <!-- YAML content -->
    <div v-else class="flex-1 overflow-auto">
      <pre class="text-[11px] font-mono text-gray-300 p-4 leading-relaxed whitespace-pre">{{ currentYaml }}</pre>
    </div>
  </div>
</template>

