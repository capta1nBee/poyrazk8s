<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import yaml from 'js-yaml'

const props = defineProps<{
  kind: string
  name: string
  namespace?: string
  mode?: 'view' | 'edit'
}>()

const emit = defineEmits(['update'])
const isOpen = defineModel<boolean>('open')

const k8s = useKubernetes()
const toast = useToast()

// ── Workload restart after ConfigMap / Secret apply ─────────────────────────
const showRestartModal = ref(false)
const restartCandidates = ref<{ kind: string; name: string; selected: boolean }[]>([])
const restarting = ref(false)

async function fetchRestartCandidates() {
  if (!props.namespace) return
  try {
    const workloads = await k8s.fetchRelatedWorkloads(
      props.kind as 'ConfigMap' | 'Secret',
      props.name,
      props.namespace
    )
    restartCandidates.value = workloads.map(w => ({ ...w, selected: false }))
  } catch { /* ignore — modal simply won't show */ }
}

async function doRestartSelected() {
  restarting.value = true
  const ns = props.namespace
  const selected = restartCandidates.value.filter(c => c.selected)
  for (const w of selected) {
    try {
      await k8s.rolloutRestart(w.kind, w.name, ns)
    } catch (e: any) {
      toast.add({ title: `Failed to restart ${w.kind}/${w.name}`, description: e.message, color: 'error' })
    }
  }
  restarting.value = false
  showRestartModal.value = false
  if (selected.length) {
    toast.add({ title: `${selected.length} workload(s) restarted`, color: 'success' })
  }
}

const content = ref('')
const loading = ref(false)
const editorRef = ref<HTMLElement | null>(null)
let editor: any = null

/* ---------------- THEME ---------------- */

const updateEditorTheme = () => {
  if (!editor) return
  const dark = document.documentElement.classList.contains('dark')
  editor.updateOptions({ theme: dark ? 'vs-dark' : 'vs' })
}

/* ---------------- DATA ---------------- */

const fetchContent = async () => {
  loading.value = true
  try {
    const yamlContent = await k8s.getResourceYAML(
      props.kind,
      props.name,
      props.namespace
    )

    try {
      const parsed = yaml.load(yamlContent)
      content.value = yaml.dump(parsed, {
        indent: 2,
        lineWidth: -1,
        noRefs: true,
        sortKeys: false
      })
    } catch {
      content.value = yamlContent
    }
  } catch (e: any) {
    toast.add({
      title: 'Failed to fetch YAML',
      description: e.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

/* ---------------- MONACO ---------------- */

const initMonaco = async () => {
  if (editor || !editorRef.value || !import.meta.client) return

  const monaco = await import('monaco-editor')

  editor = monaco.editor.create(editorRef.value, {
    value: content.value,
    language: 'yaml',
    readOnly: props.mode === 'view',
    automaticLayout: true,
    theme: document.documentElement.classList.contains('dark')
      ? 'vs-dark'
      : 'vs',
    fontSize: 14,
    minimap: { enabled: true },
    scrollBeyondLastLine: false
  })

  editor.onDidChangeModelContent(() => {
    if (props.mode === 'edit') {
      content.value = editor.getValue()
    }
  })

  nextTick(() => editor.layout())
}

const destroyMonaco = () => {
  if (editor) {
    editor.dispose()
    editor = null
  }
}

/* ---------------- ACTIONS ---------------- */

const copyToClipboard = () => {
  navigator.clipboard.writeText(content.value)
  toast.add({ title: 'YAML copied', color: 'success' })
}

const handleSave = async () => {
  try {
    yaml.load(content.value)

    const parsed = yaml.load(content.value)
    content.value = yaml.dump(parsed, {
      indent: 2,
      lineWidth: -1,
      noRefs: true,
      sortKeys: false
    })

    await k8s.applyResourceYAML(
      props.kind,
      props.name,
      content.value,
      props.namespace,
      false
    )

    emit('update')
    isOpen.value = false

    // Offer workload restart if we just edited a ConfigMap or Secret
    if ((props.kind === 'ConfigMap' || props.kind === 'Secret') && props.namespace) {
      await fetchRestartCandidates()
      if (restartCandidates.value.length) {
        showRestartModal.value = true
      }
    }
  } catch (e: any) {
    toast.add({
      title: 'Invalid YAML',
      description: e.message,
      color: 'error'
    })
  }
}

/* ---------------- LIFECYCLE ---------------- */

watch(isOpen, async (v) => {
  if (v) {
    await fetchContent()
    nextTick(initMonaco)
  } else {
    destroyMonaco()
    content.value = ''
  }
})

watch(() => props.mode, (m) => {
  editor?.updateOptions({ readOnly: m === 'view' })
})

onMounted(() => {
  const observer = new MutationObserver(updateEditorTheme)
  observer.observe(document.documentElement, { attributes: true })
})

onBeforeUnmount(destroyMonaco)
</script>

<template>
  <!-- FULLSCREEN OVERLAY -->
  <UModal
    v-model:open="isOpen"
    fullscreen
    :ui="{ background: 'bg-black/60' }"
  >
    <template #content>
      <!-- ORTALAMA -->
      <div class="fixed inset-0 flex items-center justify-center">
        <!-- %80 PANEL -->
        <div class="w-[80vw] h-[80vh] bg-gray-900 rounded-lg shadow-xl flex flex-col overflow-hidden">

          <!-- HEADER -->
          <div class="px-6 py-4 border-b border-gray-800 flex items-center justify-between">
            <div>
              <h3 class="text-lg font-bold flex items-center gap-2">
                <UIcon :name="mode === 'edit' ? 'i-lucide-edit' : 'i-lucide-file-code'" />
                {{ mode === 'edit' ? 'Edit' : 'View' }} YAML
              </h3>
              <p class="text-xs text-gray-500 font-mono">
                {{ kind }}/{{ name }}
              </p>
            </div>

            <div class="flex items-center gap-2">
              <UButton
                icon="i-lucide-copy"
                variant="ghost"
                color="neutral"
                @click="copyToClipboard"
              />
              <UButton
                icon="i-lucide-x"
                variant="ghost"
                color="neutral"
                @click="isOpen = false"
              />
            </div>
          </div>

          <!-- EDITOR (🔥 SCROLL MONACO'DA) -->
          <div class="flex-1 relative bg-[#0d1117]">
            <div
              v-if="loading"
              class="absolute inset-0 flex items-center justify-center text-gray-500"
            >
              <UIcon name="i-lucide-loader-2" class="animate-spin text-3xl" />
            </div>

            <div
              v-else
              ref="editorRef"
              class="absolute inset-0"
            />
          </div>

          <!-- FOOTER -->
          <div class="px-6 py-4 border-t border-gray-800 flex justify-between items-center">
            <span class="text-xs text-gray-500">
              {{ mode === 'edit'
                ? 'Be careful editing live resources'
                : 'Read-only view' }}
            </span>

            <div class="flex gap-2">
              <UButton
                variant="ghost"
                color="neutral"
                @click="isOpen = false"
              >
                Cancel
              </UButton>

              <UButton
                v-if="mode === 'edit'"
                color="primary"
                icon="i-lucide-save"
                @click="handleSave"
              >
                Save & Apply
              </UButton>
            </div>
          </div>

        </div>
      </div>
    </template>
  </UModal>

  <!-- ── WORKLOAD RESTART MODAL ───────────────────────────────────────── -->
  <UModal v-model:open="showRestartModal" title="Restart Related Workloads?">
    <template #body>
      <div class="space-y-4 p-1">
        <p class="text-sm text-gray-600 dark:text-gray-400">
          The <strong>{{ props.kind }}</strong> <code class="font-mono">{{ props.name }}</code> was updated.
          Select workloads in namespace <code class="font-mono">{{ props.namespace }}</code> that you want to restart now:
        </p>
        <div class="space-y-1.5 max-h-60 overflow-y-auto">
          <label
            v-for="(w, i) in restartCandidates" :key="i"
            class="flex items-center gap-3 px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800/50"
          >
            <input type="checkbox" v-model="w.selected" class="rounded" />
            <UBadge :label="w.kind" size="xs" :color="w.kind === 'Deployment' ? 'primary' : w.kind === 'StatefulSet' ? 'violet' : 'amber'" variant="soft" />
            <span class="font-mono text-sm">{{ w.name }}</span>
          </label>
        </div>
        <div class="flex justify-between items-center pt-2 border-t border-gray-100 dark:border-gray-700">
          <div class="flex gap-2">
            <UButton size="xs" variant="ghost" @click="restartCandidates.forEach(c => c.selected = true)">Select All</UButton>
            <UButton size="xs" variant="ghost" @click="restartCandidates.forEach(c => c.selected = false)">Clear</UButton>
          </div>
          <div class="flex gap-2">
            <UButton variant="ghost" color="neutral" @click="showRestartModal = false">Skip</UButton>
            <UButton
              color="primary"
              icon="i-lucide-rotate-ccw"
              :loading="restarting"
              :disabled="!restartCandidates.some(c => c.selected)"
              @click="doRestartSelected"
            >
              Restart Selected
            </UButton>
          </div>
        </div>
      </div>
    </template>
  </UModal>
</template>

<style scoped>
/* Monaco uses its own scrollbar */
</style>
