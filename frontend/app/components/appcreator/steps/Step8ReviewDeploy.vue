<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'

const store = useAppCreatorStore()
const toast = useToast()
const w = computed(() => store.wizard)

// ── Save as Template ──────────────────────────────────────────────────────────
const showTemplateModal = ref(false)
const templateForm = ref({
  name: '',
  description: '',
  category: 'Custom',
  icon: 'i-lucide-package',
  isPublic: false
})
const savingTemplate = ref(false)

const categoryOptions = ['Web', 'Database', 'Worker', 'Batch', 'Custom']
const iconOptions = [
  { label: 'Package', value: 'i-lucide-package' },
  { label: 'Globe', value: 'i-lucide-globe' },
  { label: 'Database', value: 'i-lucide-database' },
  { label: 'CPU', value: 'i-lucide-cpu' },
  { label: 'Server', value: 'i-lucide-server' },
  { label: 'Bookmark', value: 'i-lucide-bookmark' }
]

const openTemplateModal = () => {
  templateForm.value = {
    name: w.value.name ? `${w.value.name} Template` : '',
    description: '',
    category: 'Custom',
    icon: 'i-lucide-package',
    isPublic: false
  }
  showTemplateModal.value = true
}

const saveTemplate = async () => {
  if (!templateForm.value.name.trim()) {
    toast.add({ title: 'Template name is required', color: 'warning' })
    return
  }
  savingTemplate.value = true
  try {
    await store.saveAsTemplate(templateForm.value)
    toast.add({ title: 'Template saved!', description: `"${templateForm.value.name}" added to your templates.`, color: 'success' })
    showTemplateModal.value = false
  } catch (e: any) {
    toast.add({ title: 'Failed to save template', description: e?.response?.data?.message || e.message, color: 'error' })
  } finally {
    savingTemplate.value = false
  }
}

const workloadBadgeColor = computed(() => {
  const colors: Record<string, string> = { Deployment: 'cyan', StatefulSet: 'amber', DaemonSet: 'purple', CronJob: 'rose' }
  return colors[w.value.workloadType] || 'gray'
})

const summaryCards = computed(() => [
  { title: 'Workload', icon: 'i-lucide-layers', value: `${w.value.workloadType} — ${w.value.name || '—'}`, sub: `Namespace: ${w.value.namespace}` },
  { title: 'Container', icon: 'i-lucide-box', value: `${w.value.image}:${w.value.imageTag}`, sub: `${w.value.replicas} replica(s) · Pull: ${w.value.imagePullPolicy}` },
  { title: 'Resources', icon: 'i-lucide-cpu', value: `CPU: ${w.value.resources.requests.cpu}–${w.value.resources.limits.cpu}`, sub: `Memory: ${w.value.resources.requests.memory}–${w.value.resources.limits.memory}` },
  { title: 'Networking', icon: 'i-lucide-share-2', value: w.value.createService ? `Service (${w.value.serviceConfig.type})` : 'No Service', sub: w.value.createIngress ? `Ingress: ${w.value.ingressConfig.host}` : 'No Ingress' },
  { title: 'Add-ons', icon: 'i-lucide-puzzle', value: [w.value.hpa.enabled ? 'HPA' : '', w.value.configMaps.length ? `${w.value.configMaps.length} ConfigMap(s)` : '', w.value.secrets.length ? `${w.value.secrets.length} Secret(s)` : ''].filter(Boolean).join(' · ') || 'None', sub: '' },
  { title: 'Volumes', icon: 'i-lucide-hard-drive', value: w.value.volumes.length ? `${w.value.volumes.length} volume(s)` : 'No volumes', sub: w.value.volumes.map(v => v.name).join(', ') || 'Ephemeral storage' },
])

// YAML preview
const yamlContent = computed(() => {
  const files = store.yamlPreview
  if (!files) return '# Click "Refresh YAML" to preview generated manifests'
  return Object.entries(files).map(([, yaml]) => yaml).join('\n---\n')
})

const refreshing = ref(false)
const refreshYaml = async () => {
  refreshing.value = true
  await store.refreshYamlPreview()
  refreshing.value = false
}

onMounted(() => { refreshYaml() })

// Download YAML
const downloadYaml = () => {
  const content = yamlContent.value
  const blob = new Blob([content], { type: 'text/yaml' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${w.value.name || 'app'}-manifests.yaml`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h3 class="text-base font-semibold text-gray-900 dark:text-white">Review & Deploy</h3>
      <p class="text-sm text-gray-500 mt-0.5">Review your configuration before deploying to the cluster.</p>
    </div>

    <!-- App badge -->
    <div class="flex items-center gap-3 p-3 bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-gray-200 dark:border-gray-700">
      <UBadge :color="workloadBadgeColor" variant="subtle" size="lg">{{ w.workloadType }}</UBadge>
      <span class="font-mono font-semibold text-gray-800 dark:text-gray-200">{{ w.name || 'my-app' }}</span>
      <span class="text-xs text-gray-400">in <code class="font-mono">{{ w.namespace }}</code></span>
    </div>

    <!-- Summary Cards -->
    <div class="grid grid-cols-2 gap-3">
      <div v-for="card in summaryCards" :key="card.title" class="p-3 bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700 rounded-xl">
        <div class="flex items-center gap-2 mb-1">
          <UIcon :name="card.icon" class="w-4 h-4 text-primary-500 shrink-0" />
          <span class="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">{{ card.title }}</span>
        </div>
        <p class="text-sm font-medium text-gray-800 dark:text-gray-200 truncate">{{ card.value || '—' }}</p>
        <p v-if="card.sub" class="text-xs text-gray-400 truncate mt-0.5">{{ card.sub }}</p>
      </div>
    </div>

    <!-- Probes status -->
    <div class="flex gap-2 flex-wrap">
      <UBadge v-if="w.probes.liveness.enabled" color="green" variant="subtle" icon="i-lucide-heart-pulse">Liveness Probe</UBadge>
      <UBadge v-if="w.probes.readiness.enabled" color="blue" variant="subtle" icon="i-lucide-activity">Readiness Probe</UBadge>
      <UBadge v-if="w.hpa.enabled" color="green" variant="subtle" icon="i-lucide-trending-up">HPA: {{ w.hpa.minReplicas }}–{{ w.hpa.maxReplicas }}</UBadge>
      <UBadge v-if="w.createIngress && w.ingressConfig.tlsEnabled" color="green" variant="subtle" icon="i-lucide-lock">TLS Enabled</UBadge>
    </div>

    <!-- YAML Preview -->
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <UIcon name="i-lucide-code-2" class="w-4 h-4" />Generated YAML
        </h4>
        <div class="flex gap-2">
          <UButton size="xs" color="neutral" variant="outline" icon="i-lucide-refresh-cw" :loading="refreshing" @click="refreshYaml">Refresh</UButton>
          <UButton size="xs" color="neutral" variant="outline" icon="i-lucide-download" @click="downloadYaml">Download</UButton>
        </div>
      </div>
      <div class="relative rounded-xl overflow-hidden border border-gray-200 dark:border-gray-700">
        <pre class="text-xs font-mono p-4 bg-gray-900 text-green-400 overflow-auto max-h-80 leading-relaxed">{{ yamlContent }}</pre>
      </div>
    </div>

    <!-- Save as Template -->
    <div class="flex justify-end">
      <UButton size="sm" color="neutral" variant="outline" icon="i-lucide-bookmark-plus" @click="openTemplateModal">
        Save as Template
      </UButton>
    </div>

    <!-- Deploy info box -->
    <div class="p-4 bg-primary-50 dark:bg-primary-900/20 border border-primary-200 dark:border-primary-800 rounded-xl text-sm text-primary-700 dark:text-primary-300 space-y-1">
      <p class="font-semibold flex items-center gap-2"><UIcon name="i-lucide-rocket" class="w-4 h-4" />Ready to deploy!</p>
      <p class="text-xs">Use <strong>Deploy Direct</strong> to apply to the cluster immediately, or <strong>Deploy via Git</strong> to create a Pull Request.</p>
    </div>
  </div>

  <!-- Save as Template Modal -->
  <UModal v-model:open="showTemplateModal" title="Save as Template">
    <template #body>
      <div class="space-y-4">
        <UFormField label="Template Name" name="name" required>
          <UInput v-model="templateForm.name" placeholder="My App Template" class="w-full" />
        </UFormField>
        <UFormField label="Description" name="description">
          <UInput v-model="templateForm.description" placeholder="Brief description of this template" class="w-full" />
        </UFormField>
        <UFormField label="Category" name="category">
          <USelectMenu v-model="templateForm.category" :items="categoryOptions" class="w-full" />
        </UFormField>
        <UFormField label="Icon" name="icon">
          <USelectMenu v-model="templateForm.icon" :items="iconOptions" value-key="value" class="w-full" />
        </UFormField>
        <UCheckbox v-model="templateForm.isPublic" label="Make public (visible to all users on this cluster)" />
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="outline" @click="showTemplateModal = false">Cancel</UButton>
        <UButton color="primary" icon="i-lucide-bookmark-plus" :loading="savingTemplate" @click="saveTemplate">Save Template</UButton>
      </div>
    </template>
  </UModal>
</template>

