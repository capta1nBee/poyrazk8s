<script setup lang="ts">
import { ref, computed, watch, nextTick, onUnmounted } from 'vue'

const props = defineProps<{
  podName: string
  namespace: string
  container?: string
}>()

const isOpen = defineModel<boolean>('open')
const isFullscreen = ref(false)

const podWebSocket = usePodWebSocket()
const toast = useToast()

const logs = ref<string[]>([])
const loading = ref(false)
const following = ref(true)
const tailLines = ref(100)
const wsConnection = ref<ReturnType<typeof podWebSocket.connectPodLogs> | null>(null)
const logContainer = ref<HTMLElement | null>(null)
const searchQuery = ref('')

const filteredLogs = computed(() => {
  if (!searchQuery.value) return logs.value
  return logs.value.filter(l =>
    l.toLowerCase().includes(searchQuery.value.toLowerCase())
  )
})

const connect = () => {
  wsConnection.value?.close()
  loading.value = true
  logs.value = []

  wsConnection.value = podWebSocket.connectPodLogs(
    props.podName,
    props.namespace,
    props.container,
    {
      follow: following.value,
      tail: tailLines.value,
      onMessage: (message: string) => {
        loading.value = false

        if (message.startsWith('ERROR:')) {
          toast.add({ title: 'Log error', description: message, color: 'error' })
          return
        }

        logs.value.push(message)

        nextTick(() => {
          if (following.value && logContainer.value) {
            logContainer.value.scrollTop = logContainer.value.scrollHeight
          }
        })
      },
      onError: () => {
        loading.value = false
        toast.add({
          title: 'WebSocket error',
          description: 'Failed to connect to pod logs',
          color: 'error'
        })
      },
      onClose: () => {
        loading.value = false
      }
    }
  )
}

const disconnect = () => {
  wsConnection.value?.close()
  wsConnection.value = null
}

watch(isOpen, (v) => {
  v ? connect() : disconnect()
})

onUnmounted(disconnect)

const copyLogs = () => {
  navigator.clipboard.writeText(logs.value.join('\n'))
  toast.add({ title: 'Logs copied', color: 'success' })
}

const clearLogs = () => {
  logs.value = []
}
</script>

<template>
  <UModal
    v-model:open="isOpen"
    fullscreen
    :ui="{ background: 'bg-black/60' }"
  >
    <template #content>
      <div class="fixed inset-0 flex items-center justify-center" :class="isFullscreen ? '' : 'p-4'">
        <div
          class="bg-gray-900 shadow-xl flex flex-col overflow-hidden transition-all duration-300"
          :class="isFullscreen ? 'w-screen h-screen' : 'w-[80vw] h-[80vh] rounded-lg'"
        >

          <!-- HEADER -->
          <div class="px-6 py-4 border-b border-gray-800 flex items-center justify-between">
            <div>
              <h3 class="text-lg font-bold">Pod Logs</h3>
              <p class="text-xs text-gray-500 font-mono">
                {{ podName }}
                <span v-if="container">({{ container }})</span>
              </p>
            </div>
            <div class="flex items-center gap-1">
              <UButton
                :icon="isFullscreen ? 'i-lucide-minimize-2' : 'i-lucide-maximize-2'"
                variant="ghost"
                color="neutral"
                size="xs"
                @click="isFullscreen = !isFullscreen"
              />
              <UButton icon="i-lucide-x" variant="ghost" @click="isOpen = false" />
            </div>
          </div>

          <!-- TOOLBAR -->
          <div class="px-6 py-3 border-b border-gray-800 flex items-center gap-4 bg-gray-900/50">
            <UInput
              v-model="searchQuery"
              icon="i-lucide-search"
              placeholder="Filter logs..."
              size="sm"
              class="max-w-xs"
            />

            <div class="flex items-center gap-2 text-xs text-gray-400 uppercase">
              Tail
              <UInput v-model.number="tailLines" type="number" size="xs" class="w-20" />
            </div>

            <div class="flex items-center gap-2 text-xs text-gray-400 uppercase">
              <USwitch v-model="following" size="sm" />
              Follow
            </div>

            <div class="flex-1" />

            <UButton size="xs" variant="ghost" icon="i-lucide-refresh-cw" @click="connect">
              Reconnect
            </UButton>
            <UButton size="xs" variant="ghost" icon="i-lucide-copy" @click="copyLogs">
              Copy
            </UButton>
            <UButton size="xs" variant="ghost" icon="i-lucide-trash-2" @click="clearLogs">
              Clear
            </UButton>
          </div>

          <!-- 🔥 SCROLL ALANI (GARANTİLİ) -->
          <div
            ref="logContainer"
            class="flex-1 overflow-y-auto bg-gray-950 font-mono text-sm p-4"
          >
            <div v-if="loading && logs.length === 0" class="h-full flex items-center justify-center text-gray-500">
              Loading logs…
            </div>

            <div v-else-if="logs.length === 0" class="h-full flex items-center justify-center text-gray-500">
              No logs available
            </div>

            <div v-else class="space-y-1">
              <div
                v-for="(log, i) in filteredLogs"
                :key="i"
                class="flex gap-4 px-2 py-0.5 rounded hover:bg-gray-900/60"
              >
                <span class="w-10 text-right text-[10px] text-gray-600 select-none">
                  {{ i + 1 }}
                </span>
                <span class="whitespace-pre-wrap break-all text-gray-300" v-html="log" />
              </div>
            </div>
          </div>

          <!-- FOOTER -->
          <div class="px-6 py-4 border-t border-gray-800 flex justify-between items-center">
            <span class="text-[10px] text-gray-500 font-mono uppercase">
              {{ logs.length }} lines
            </span>
            <UButton color="primary" @click="isOpen = false">Done</UButton>
          </div>

        </div>
      </div>
    </template>
  </UModal>
</template>

<style scoped>
::-webkit-scrollbar {
  width: 6px;
}
::-webkit-scrollbar-thumb {
  background: #374151;
  border-radius: 8px;
}
::-webkit-scrollbar-thumb:hover {
  background: #4b5563;
}
</style>
