<script setup lang="ts">
import { ref, watch, computed } from 'vue'

const props = defineProps<{
  secretName: string
  namespace: string
}>()

const emit = defineEmits(['close'])
const isOpen = defineModel<boolean>('open')

const k8s = useKubernetes()
const toast = useToast()

const rawData = ref<Record<string, string>>({})
const loading = ref(false)
const showDecoded = ref(true)
const copiedKey = ref<string | null>(null)

/** Try to base64-decode a value; return original on failure */
function tryBase64Decode(value: string): { decoded: string; wasEncoded: boolean } {
  try {
    const decoded = atob(value)
    // Verify it's valid UTF-8 text (not binary garbage)
    const textDecoder = new TextDecoder('utf-8', { fatal: true })
    const bytes = Uint8Array.from(atob(value), c => c.charCodeAt(0))
    return { decoded: textDecoder.decode(bytes), wasEncoded: true }
  } catch {
    return { decoded: value, wasEncoded: false }
  }
}

const displayData = computed(() => {
  return Object.fromEntries(
    Object.entries(rawData.value).map(([key, value]) => {
      if (showDecoded.value) {
        const { decoded } = tryBase64Decode(value)
        return [key, decoded]
      }
      return [key, value]
    })
  )
})

const isBase64Map = computed(() => {
  return Object.fromEntries(
    Object.entries(rawData.value).map(([key, value]) => [key, tryBase64Decode(value).wasEncoded])
  )
})

async function copyValue(key: string, value: string) {
  await navigator.clipboard.writeText(value)
  copiedKey.value = key
  setTimeout(() => { copiedKey.value = null }, 2000)
}

watch(isOpen, async (open) => {
  if (open) {
    loading.value = true
    showDecoded.value = true
    rawData.value = {}
    try {
      rawData.value = await k8s.getSecretData(props.secretName, props.namespace)
    } catch (error: any) {
      toast.add({
        title: 'Failed to fetch Secret data',
        description: error.message,
        color: 'error'
      })
    } finally {
      loading.value = false
    }
  }
})
</script>

<template>
  <UModal
    v-model:open="isOpen"
    :title="`Secret Data — ${secretName}`"
    :ui="{ width: 'sm:max-w-2xl' }"
  >
    <template #body>
      <div class="space-y-3">
        <!-- Toolbar -->
        <div v-if="!loading && Object.keys(rawData).length" class="flex items-center justify-between">
          <p class="text-xs text-gray-500">{{ Object.keys(rawData).length }} key(s)</p>
          <div class="flex items-center gap-2">
            <span class="text-xs text-gray-500">Show decoded (base64)</span>
            <UToggle v-model="showDecoded" size="sm" />
          </div>
        </div>

        <div v-if="loading" class="flex items-center justify-center py-10">
          <UIcon name="i-lucide-loader-2" class="animate-spin w-6 h-6 text-gray-400" />
        </div>
        <div v-else-if="!Object.keys(rawData).length" class="text-gray-400 text-center py-8 text-sm">
          No data
        </div>
        <div v-else class="space-y-2 max-h-[60vh] overflow-y-auto pr-1">
          <div
            v-for="(value, key) in displayData" :key="key"
            class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden"
          >
            <div class="flex items-center justify-between px-3 py-1.5 bg-gray-50 dark:bg-gray-800">
              <span class="font-mono text-xs font-semibold text-blue-600 dark:text-blue-400">{{ key }}</span>
              <div class="flex items-center gap-1.5">
                <UBadge v-if="isBase64Map[key]" size="xs" color="amber" variant="subtle" label="base64" />
                <UButton
                  size="xs" variant="ghost" color="neutral"
                  :icon="copiedKey === key ? 'i-lucide-check' : 'i-lucide-copy'"
                  :color="copiedKey === key ? 'success' : 'neutral'"
                  @click="copyValue(key, value)"
                />
              </div>
            </div>
            <div class="font-mono text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap break-all bg-white dark:bg-gray-900 p-3 max-h-40 overflow-y-auto">
              {{ value || '(empty)' }}
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

