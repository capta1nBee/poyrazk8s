<script setup lang="ts">
import { ref, watch, nextTick, onUnmounted } from 'vue'

const props = defineProps<{
  podName: string
  namespace: string
  container?: string
}>()

const isOpen = defineModel<boolean>('open')
const isFullscreen = ref(false)

const podWebSocket = usePodWebSocket()
const toast = useToast()
const { $api } = useNuxtApp()

const loading = ref(false)
const sessionUid = ref<string | null>(null)
const wsConnection = ref<ReturnType<typeof podWebSocket.connectPodExec> | null>(null)
const terminalContainer = ref<HTMLElement | null>(null)

// Terminal instances
let term: any = null
let fitAddon: any = null

// RRWEB INTEGRATION
const rrwebLoaded = ref(false)
const stopRecording = ref<(() => void) | null>(null)
const recordingEvents = ref<any[]>([])

const loadScript = (src: string): Promise<void> => {
  return new Promise((resolve, reject) => {
    if (document.querySelector(`script[src="${src}"]`)) {
      resolve()
      return
    }
    const script = document.createElement('script')
    script.src = src
    script.onload = () => resolve()
    script.onerror = () => reject(new Error(`Failed to load ${src}`))
    document.head.appendChild(script)
  })
}

const loadCss = (href: string) => {
  if (document.querySelector(`link[href="${href}"]`)) return
  const link = document.createElement('link')
  link.rel = 'stylesheet'
  link.href = href
  document.head.appendChild(link)
}

const syncRecording = async () => {
  if (!sessionUid.value || recordingEvents.value.length === 0) return
  
  const events = [...recordingEvents.value]
  recordingEvents.value = []
  
  try {
    await $api.post(`/exec/recording/${sessionUid.value}`, {
      eventData: JSON.stringify(events)
    })
  } catch (e) {
    console.error('Failed to sync recording', e)
  }
}

const startRrweb = async () => {
  if (rrwebLoaded.value) {
    initRecording()
    return
  }

  try {
    await loadScript('/lib/rrweb/rrweb.min.js')
    rrwebLoaded.value = true
    initRecording()
  } catch (e) {
    console.error('Failed to start rrweb', e)
  }
}

const initRecording = () => {
  if (typeof (window as any).rrweb === 'undefined') return
  
  try {
    stopRecording.value = (window as any).rrweb.record({
      emit(event: any) {
        recordingEvents.value.push(event)
        if (recordingEvents.value.length >= 50) {
          syncRecording()
        }
      },
      // xterm uses canvas/webgl which rrweb might miss without this, 
      // but simpler is to let it record DOM if xterm is in DOM mode.
      recordCanvas: true 
    })
  } catch (e) {
    console.error('Failed to initialize rrweb recording', e)
  }
}

const initTerminal = async () => {
  try {
    loadCss('/lib/xterm/xterm.css')
    await loadScript('/lib/xterm/xterm.js')
    await loadScript('/lib/xterm/xterm-addon-fit.js')

    const Terminal = (window as any).Terminal
    const FitAddon = (window as any).FitAddon.FitAddon

    term = new Terminal({
      cursorBlink: true,
      fontSize: 14,
      fontFamily: 'Menlo, Monaco, "Courier New", monospace',
      theme: {
        background: '#030712', // gray-950
        foreground: '#e5e7eb', // gray-200
        cursor: '#22c55e'      // green-500
      }
    })

    fitAddon = new FitAddon()
    term.loadAddon(fitAddon)

    if (terminalContainer.value) {
      term.open(terminalContainer.value)

      // Delayed fit — modal transition takes ~300ms, fit multiple times to ensure correct sizing
      const doFit = () => { try { fitAddon.fit() } catch {} }
      doFit()
      setTimeout(doFit, 100)
      setTimeout(doFit, 300)
      setTimeout(doFit, 500)

      // Handle resize
      window.addEventListener('resize', doFit)

      // Send resize to backend PTY whenever xterm dimensions change
      term.onResize(({ cols, rows }: { cols: number; rows: number }) => {
        if (wsConnection.value) {
          wsConnection.value.send(JSON.stringify({ type: 'resize', cols, rows }))
        }
      })

      term.onData((data: string) => {
        if (wsConnection.value) {
          wsConnection.value.send(data)
        }
      })
    }
  } catch (e) {
    console.error('Failed to init terminal', e)
    toast.add({ title: 'Error', description: 'Failed to load terminal libraries', color: 'red' })
  }
}

const connect = async () => {
  wsConnection.value?.close()
  loading.value = true
  sessionUid.value = null
  
  // Reset terminal
  if (term) {
    term.reset()
    term.write('Connecting to pod...\r\n')
  } else {
    await initTerminal()
  }

  // Ensure terminal is properly sized before connecting
  await nextTick()
  if (fitAddon) {
    try { fitAddon.fit() } catch {}
    await new Promise(r => setTimeout(r, 200))
    try { fitAddon.fit() } catch {}
  }

  wsConnection.value = podWebSocket.connectPodExec(
    props.podName,
    props.namespace,
    props.container,
    {
      onMessage: (message: string) => {
        loading.value = false

        // Extract session ID
        if (message.includes('Session') && !sessionUid.value) {
          const match = message.match(/Session(?:ID)?:\s*([a-f0-9-]+)/i)
          if (match) {
            sessionUid.value = match[1]
            startRrweb()

            // Send initial resize so the backend PTY knows the correct terminal size
            if (term && wsConnection.value) {
              const { cols, rows } = term
              wsConnection.value.send(JSON.stringify({ type: 'resize', cols, rows }))
            }
          }
        }

        if (message.startsWith('ERROR:')) {
          term?.write(`\r\n\x1b[31m${message}\x1b[0m\r\n`)
          return
        }

        term?.write(message)
      },
      onError: () => {
        loading.value = false
        term?.write('\r\n\x1b[31mConnection error!\x1b[0m\r\n')
      },
      onClose: () => {
        loading.value = false
        term?.write('\r\n\x1b[33mConnection closed.\x1b[0m\r\n')
        stopRecording.value?.()
        syncRecording()
      }
    }
  )
}

const disconnect = () => {
  wsConnection.value?.close()
  wsConnection.value = null
  if (term) {
    term.dispose()
    term = null
  }
}

// Re-fit terminal on fullscreen toggle (wait for CSS transition)
watch(isFullscreen, () => {
  const doFit = () => { try { if (fitAddon) fitAddon.fit() } catch {} }
  nextTick(doFit)
  setTimeout(doFit, 150)
  setTimeout(doFit, 350)
})

watch(isOpen, async (v) => {
  if (v) {
    isFullscreen.value = false
    // Wait for modal transition
    setTimeout(() => {
        connect()
    }, 100)
  } else {
    disconnect()
  }
})

onUnmounted(disconnect)
</script>

<template>
  <UModal
    v-model:open="isOpen"
    fullscreen
    :ui="{ background: 'bg-black/80' }"
  >
    <template #content>
      <div class="fixed inset-0 flex items-center justify-center" :class="isFullscreen ? '' : 'p-4'">
        <div
          class="bg-gray-950 shadow-2xl flex flex-col border border-gray-800 transition-all duration-300"
          :class="isFullscreen ? 'w-screen h-screen' : 'w-full h-full max-w-6xl max-h-[90vh] rounded-lg'"
        >

          <!-- HEADER -->
          <div class="px-4 py-3 border-b border-gray-800 flex items-center justify-between bg-gray-900" :class="isFullscreen ? '' : 'rounded-t-lg'">
            <div class="flex items-center gap-3">
              <div class="flex gap-1.5">
                <div class="w-3 h-3 rounded-full bg-red-500/80"></div>
                <div class="w-3 h-3 rounded-full bg-yellow-500/80"></div>
                <div class="w-3 h-3 rounded-full bg-green-500/80"></div>
              </div>
              <div class="text-sm font-mono text-gray-400 ml-2">
                {{ podName }} <span v-if="container" class="text-gray-600">({{ container }})</span>
              </div>
            </div>

            <div class="flex items-center gap-3">
              <div class="flex items-center gap-2 text-xs font-mono">
                <span
                  class="w-2 h-2 rounded-full animate-pulse"
                  :class="wsConnection ? 'bg-green-500' : 'bg-red-500'"
                ></span>
                <span :class="wsConnection ? 'text-green-500' : 'text-red-500'">
                    {{ wsConnection ? 'CONNECTED' : 'OFFLINE' }}
                </span>
              </div>
              <UButton
                :icon="isFullscreen ? 'i-lucide-minimize-2' : 'i-lucide-maximize-2'"
                variant="ghost"
                color="neutral"
                size="xs"
                @click="isFullscreen = !isFullscreen"
              />
              <UButton
                icon="i-lucide-x"
                variant="ghost"
                color="neutral"
                size="xs"
                @click="isOpen = false"
              />
            </div>
          </div>

          <!-- TERMINAL CONTAINER -->
          <div class="flex-1 relative bg-[#030712] p-2 overflow-hidden">
            <div ref="terminalContainer" class="w-full h-full"></div>
             <!-- LOADING OVERLAY -->
            <div 
              v-if="loading"
              class="absolute inset-0 flex items-center justify-center bg-black/50 z-10"
            >
              <UIcon name="i-lucide-loader-2" class="w-8 h-8 text-white animate-spin" />
            </div>
          </div>

        </div>
      </div>
    </template>
  </UModal>
</template>

<style>
/* xterm.js custom scrollbar */
.xterm-viewport::-webkit-scrollbar {
  width: 8px;
}
.xterm-viewport::-webkit-scrollbar-track {
  background: #111827; 
}
.xterm-viewport::-webkit-scrollbar-thumb {
  background: #374151; 
  border-radius: 4px;
}
.xterm-viewport::-webkit-scrollbar-thumb:hover {
  background: #4b5563; 
}
</style>
