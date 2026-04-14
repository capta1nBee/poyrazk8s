<script setup lang="ts">
import { ref, watch, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import yaml from 'js-yaml'

const props = defineProps<{
  open: boolean
  resourceName: string
  resourceKind: 'Deployment' | 'StatefulSet' | 'DaemonSet'
  namespace: string
  revision: any
}>()

const emit = defineEmits(['update:open', 'rollback'])

const k8s = useKubernetes()
const toast = useToast()

const details = ref<any>(null)
const loading = ref(false)
const isRollbackConfirmOpen = ref(false)
const isRollingBack = ref(false)

const editorRef = ref<HTMLElement | null>(null)
let editor: any = null

const updateEditorTheme = () => {
  if (!editor) return
  const isDark = document.documentElement.classList.contains('dark')
  editor.updateOptions({ theme: isDark ? 'vs-dark' : 'vs' })
}

const initMonaco = async () => {
  if (editor || !import.meta.client || !editorRef.value) return

  try {
    const monaco = await import('monaco-editor')
    if (!editorRef.value) return // Double check after async import
    
    editor = monaco.editor.create(editorRef.value, {
      value: details.value?.yaml || '',
      language: 'yaml',
      theme: document.documentElement.classList.contains('dark') ? 'vs-dark' : 'vs',
      readOnly: true,
      automaticLayout: true,
      minimap: { enabled: false },
      fontSize: 12,
      fontFamily: "'Fira Code', 'JetBrains Mono', 'Consolas', monospace",
      scrollBeyondLastLine: false,
      renderLineHighlight: 'all',
    })

    // Force layout
    setTimeout(() => {
      if (editor) editor.layout()
    }, 100)
  } catch (error) {
    console.error('Failed to initialize Monaco Editor:', error)
  }
}

onMounted(() => {
  if (isOpen.value) {
    initMonaco()
  }

  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.attributeName === 'class') {
        updateEditorTheme()
      }
    })
  })
  observer.observe(document.documentElement, { attributes: true })
})

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose()
  }
})

watch(() => details.value?.yaml, async (newVal) => {
  if (newVal) {
    if (!editor) {
      await nextTick()
      await initMonaco()
    } else {
      editor.setValue(newVal)
    }
  }
})

const highlightYaml = (yamlContent: string) => {
  return yamlContent
}

const revisionNumber = computed(() => props.revision?.revision || '')

const fetchDetails = async () => {
  if (!props.revision) return
  
  loading.value = true
  try {
    let data
    const rev = parseInt(props.revision.revision)
    
    if (props.resourceKind === 'Deployment') {
      data = await k8s.getDeploymentRevisionDetails(props.resourceName, rev, props.namespace)
    } else if (props.resourceKind === 'StatefulSet') {
      data = await k8s.getStatefulSetRevisionDetails(props.resourceName, rev, props.namespace)
    } else if (props.resourceKind === 'DaemonSet') {
      data = await k8s.getDaemonSetRevisionDetails(props.resourceName, rev, props.namespace)
    }
    
    details.value = data
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch revision details',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
    details.value = null
  } finally {
    loading.value = false
  }
}

const confirmRollback = () => {
  isRollbackConfirmOpen.value = true
}

const executeRollback = async () => {
  isRollingBack.value = true
  try {
    const rev = parseInt(props.revision.revision)
    
    if (props.resourceKind === 'Deployment') {
      await k8s.rollbackDeploymentToRevision(props.resourceName, rev, props.namespace)
    } else if (props.resourceKind === 'StatefulSet') {
      await k8s.rollbackStatefulSetToRevision(props.resourceName, rev, props.namespace)
    } else if (props.resourceKind === 'DaemonSet') {
      await k8s.rollbackDaemonSetToRevision(props.resourceName, rev, props.namespace)
    }
    
    toast.add({
      title: 'Rollback initiated',
      description: `Rolling back ${props.resourceKind.toLowerCase()} to revision ${rev}`,
      color: 'success'
    })
    
    isRollbackConfirmOpen.value = false
    emit('update:open', false)
    emit('rollback')
  } catch (error: any) {
    toast.add({
      title: 'Rollback failed',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  } finally {
    isRollingBack.value = false
  }
}

const formatDate = (timestamp: string) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString()
}

watch(() => props.open, (val) => {
  if (val) {
    fetchDetails()
  } else {
    details.value = null
    if (editor) {
      editor.dispose()
      editor = null
    }
  }
})
const isOpen = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})
</script>

<template>
  <UModal 
    v-model:open="isOpen" 
    :title="`Revision ${revisionNumber} Details`"
    :description="`${resourceKind} ${resourceName}`"
  >
    <template #body>
      <div v-if="loading" class="flex items-center justify-center py-8">
        <UIcon name="i-lucide-loader-2" class="animate-spin text-2xl" />
      </div>

      <div v-else-if="!details" class="text-center py-8 text-gray-500">
        No details available
      </div>

      <div v-else class="space-y-4">
        <!-- Metadata -->
        <div class="space-y-2">
          <div class="flex items-center gap-2">
            <span class="text-sm font-semibold text-gray-600 dark:text-gray-400">Revision:</span>
            <span class="font-mono font-bold text-blue-600 dark:text-blue-400">{{ details.revision }}</span>
          </div>

          <div v-if="details.changeCause" class="flex items-start gap-2">
            <span class="text-sm font-semibold text-gray-600 dark:text-gray-400">Change Cause:</span>
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ details.changeCause }}</span>
          </div>

          <div class="flex items-center gap-2">
            <span class="text-sm font-semibold text-gray-600 dark:text-gray-400">Created:</span>
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ formatDate(details.createdAt) }}</span>
          </div>
        </div>

        <!-- YAML Content -->
        <div v-if="details.yaml" class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
          <div class="bg-gray-100 dark:bg-gray-800 px-3 py-2 border-b border-gray-200 dark:border-gray-700">
            <span class="text-sm font-semibold text-gray-700 dark:text-gray-300">YAML</span>
          </div>
          <div class="h-96 relative bg-gray-50 dark:bg-gray-950 border border-gray-200 dark:border-gray-700">
            <div ref="editorRef" class="absolute inset-0" />
          </div>
        </div>
      </div>

      <div class="flex justify-between mt-6">
        <UButton 
          label="Apply Rollback" 
          color="primary"
          icon="i-lucide-undo"
          @click="confirmRollback" 
        />
        <UButton 
          label="Close" 
          color="neutral" 
          variant="ghost"
          @click="emit('update:open', false)" 
        />
      </div>
    </template>
  </UModal>

  <!-- Rollback Confirmation Modal -->
  <UModal 
    v-model:open="isRollbackConfirmOpen"
    title="Confirm Rollback"
    description="Are you sure you want to rollback to this revision?"
  >
    <template #body>
      <div class="space-y-4">
        <div class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4">
          <div class="flex gap-3">
            <UIcon name="i-lucide-alert-triangle" class="text-yellow-600 dark:text-yellow-400 text-xl flex-shrink-0 mt-0.5" />
            <div class="space-y-1">
              <p class="text-sm font-semibold text-yellow-800 dark:text-yellow-200">
                This will rollback {{ resourceKind.toLowerCase() }} "{{ resourceName }}" to revision {{ revisionNumber }}
              </p>
              <p class="text-sm text-yellow-700 dark:text-yellow-300">
                This action will trigger a new rollout and may cause downtime.
              </p>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2">
          <UButton 
            label="Cancel" 
            color="neutral" 
            variant="ghost"
            @click="isRollbackConfirmOpen = false" 
          />
          <UButton 
            label="Confirm Rollback" 
            color="error"
            icon="i-lucide-undo"
            :loading="isRollingBack"
            @click="executeRollback" 
          />
        </div>
      </div>
    </template>
  </UModal>
</template>

<style scoped>
/* Removed Shiki styles */
</style>
