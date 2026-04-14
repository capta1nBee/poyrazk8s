<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'

const store = useAppCreatorStore()
const router = useRouter()
const route = useRoute()
const toast = useToast()
const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const { hasPermission } = usePagePermissions('AppCreator')

// Auto-save every 30s
let autoSaveTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  const appId = route.query.appId as string | undefined
  if (appId && clusterStore.selectedCluster) {
    try {
      const res = await $api.get(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/apps/${appId}`)
      store.currentApp = res.data
      const config = JSON.parse(res.data.config || '{}')
      const [img, tag] = (config.image || ':latest').split(':')
      Object.assign(store.wizard, { ...config, image: img, imageTag: tag || 'latest', templateId: res.data.templateId })
    } catch {
      toast.add({ title: 'Failed to load application', color: 'red' })
    }
  } else {
    // If a template was pre-applied (via applyTemplate), keep the wizard state.
    // Only reset if the wizard is completely fresh (no template selected).
    if (!store.wizard.templateId) {
      store.resetWizard()
    }
  }
  // Push initial history snapshot
  store.pushHistory()
  // Start auto-save
  autoSaveTimer = setInterval(() => store.saveDraft(), 30000)
})

onUnmounted(() => { if (autoSaveTimer) clearInterval(autoSaveTimer) })

// Keyboard shortcuts for undo/redo
onMounted(() => {
  const handler = (e: KeyboardEvent) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) { e.preventDefault(); store.undo() }
    if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.key === 'z' && e.shiftKey))) { e.preventDefault(); store.redo() }
  }
  window.addEventListener('keydown', handler)
  onUnmounted(() => window.removeEventListener('keydown', handler))
})

const stepComponents: Record<number, any> = {
  1: resolveComponent('AppcreatorStepsStep1BasicInfo'),
  2: resolveComponent('AppcreatorStepsStep2Container'),
  3: resolveComponent('AppcreatorStepsStep3PortsEnv'),
  4: resolveComponent('AppcreatorStepsStep4Resources'),
  5: resolveComponent('AppcreatorStepsStep5KindSpecific'),
  6: resolveComponent('AppcreatorStepsStep6Addons'),
  7: resolveComponent('AppcreatorStepsStep7Volumes'),
  8: resolveComponent('AppcreatorStepsStep8ReviewDeploy'),
}

const currentStepComponent = computed(() => stepComponents[store.currentStep])

const goNext = () => {
  store.pushHistory()
  if (store.currentStep < store.totalSteps) store.currentStep++
}
const goPrev = () => {
  if (store.currentStep > 1) store.currentStep--
}

const isFirstStep = computed(() => store.currentStep === 1)
const isLastStep = computed(() => store.currentStep === store.totalSteps)

const canProceed = computed(() => {
  if (store.currentStep === 1) return !!store.wizard.name && !!store.wizard.namespace
  if (store.currentStep === 2) return !!store.wizard.image
  return true
})

const saving = ref(false)
const saveDraft = async () => {
  saving.value = true
  try {
    await store.saveApp()
    toast.add({ title: 'Draft saved', color: 'success' })
  } catch (e: any) {
    toast.add({ title: 'Save failed', description: e?.response?.data?.message || e.message, color: 'red' })
  } finally {
    saving.value = false
  }
}

const showYaml = ref(false)
const previewLoading = ref(false)
const openYamlPreview = async () => {
  previewLoading.value = true
  await store.refreshYamlPreview()
  previewLoading.value = false
  showYaml.value = true
}

const deployLoading = ref(false)
const showGitModal = ref(false)
const deploySuccess = ref(false)
const deployResult = ref<any>(null)

const deployApp = async () => {
  deployLoading.value = true
  try {
    await store.saveApp()
    const result = await store.deployApp('direct')
    deployResult.value = result
    deploySuccess.value = true
    toast.add({ title: '🚀 Deployment successful!', description: `${store.wizard.name} deployed to ${store.wizard.namespace}`, color: 'success' })
  } catch (e: any) {
    toast.add({ title: 'Deployment failed', description: e?.response?.data?.message || e.message, color: 'red' })
  } finally {
    deployLoading.value = false
  }
}

const deployViaGit = async (opts: { connectionId: string; repo: string; branch: string; gitPath: string }) => {
  deployLoading.value = true
  try {
    await store.saveApp()
    const result = await store.deployApp('git', opts)
    const prUrl = result?.gitPrUrl
    toast.add({ title: 'Pull Request created! 🎉', description: prUrl ? `PR opened at ${prUrl}` : 'YAML pushed to Git', color: 'success' })
    router.push('/appcreator')
  } catch (e: any) {
    toast.add({ title: 'Git deploy failed', description: e?.response?.data?.message || e.message, color: 'red' })
  } finally {
    deployLoading.value = false
  }
}
</script>

<template>
  <UDashboardPanel id="appcreator-create">
    <template #header>
      <UDashboardNavbar :title="store.currentApp ? 'Edit Application' : 'New Application'">
        <template #leading>
          <UButton icon="i-lucide-arrow-left" color="neutral" variant="ghost" to="/appcreator" />
        </template>
        <template #right>
          <!-- Undo/Redo -->
          <UButton size="sm" color="neutral" variant="ghost" icon="i-lucide-undo-2" :disabled="!store.canUndo" @click="store.undo()" />
          <UButton size="sm" color="neutral" variant="ghost" icon="i-lucide-redo-2" :disabled="!store.canRedo" @click="store.redo()" />
          <!-- Auto-save indicator -->
          <span v-if="store.lastSavedAt" class="text-xs text-gray-400 hidden sm:flex items-center gap-1">
            <UIcon name="i-lucide-check-circle" class="w-3 h-3 text-green-500" />Saved
          </span>
          <UButton size="sm" color="neutral" variant="soft" icon="i-lucide-eye" :loading="previewLoading" @click="openYamlPreview">Preview YAML</UButton>
          <UButton size="sm" color="neutral" variant="outline" icon="i-lucide-save" :loading="saving" @click="saveDraft">Save Draft</UButton>
          <template v-if="isLastStep">
            <UButton v-if="hasPermission('deploy')" size="sm" color="primary" icon="i-lucide-rocket" :loading="deployLoading" @click="deployApp">Deploy</UButton>
            <UButton size="sm" color="neutral" variant="outline" icon="i-lucide-git-pull-request" :loading="deployLoading" @click="showGitModal = true">Deploy via Git</UButton>
          </template>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Success screen -->
      <Transition name="fade">
        <div v-if="deploySuccess" class="flex flex-col items-center justify-center h-full py-24 text-center space-y-6">
          <div class="w-24 h-24 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center animate-bounce">
            <UIcon name="i-lucide-rocket" class="w-12 h-12 text-green-500" />
          </div>
          <h2 class="text-2xl font-bold text-gray-900 dark:text-white">🎉 Deployed Successfully!</h2>
          <p class="text-gray-500 max-w-md">
            <strong>{{ store.wizard.name }}</strong> has been deployed to <code class="font-mono bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">{{ store.wizard.namespace }}</code>.
          </p>
          <div class="flex gap-3">
            <UButton color="primary" icon="i-lucide-layout-grid" to="/appcreator">View Applications</UButton>
            <UButton color="neutral" variant="outline" icon="i-lucide-plus" @click="() => { store.resetWizard(); deploySuccess = false }">Create Another</UButton>
          </div>
        </div>
      </Transition>

      <div v-if="!deploySuccess" class="max-w-5xl mx-auto py-8 px-6 space-y-8">
        <!-- Stepper -->
        <AppcreatorWizardStepper :current-step="store.currentStep" :total-steps="store.totalSteps" />

        <!-- Step content -->
        <div class="bg-white dark:bg-gray-900 rounded-2xl border border-gray-100 dark:border-gray-800 shadow-sm p-8">
          <component :is="currentStepComponent" />
        </div>

        <!-- Navigation buttons -->
        <div class="flex items-center justify-between">
          <UButton color="neutral" variant="outline" icon="i-lucide-chevron-left" :disabled="isFirstStep" @click="goPrev">Previous</UButton>
          <div class="flex items-center gap-2">
            <span class="text-xs text-gray-400">Step {{ store.currentStep }} of {{ store.totalSteps }}</span>
          </div>
          <UButton v-if="!isLastStep" color="primary" trailing-icon="i-lucide-chevron-right" :disabled="!canProceed" @click="goNext">Next</UButton>
          <div v-else class="flex items-center gap-2">
            <UButton v-if="hasPermission('deploy')" color="primary" icon="i-lucide-rocket" :loading="deployLoading" @click="deployApp">Deploy Direct</UButton>
            <UButton color="neutral" variant="outline" icon="i-lucide-git-pull-request" :loading="deployLoading" @click="showGitModal = true">Deploy via Git</UButton>
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>

  <!-- YAML Preview Modal -->
  <UModal v-model:open="showYaml" title="Generated YAML" fullscreen>
    <template #body>
      <AppcreatorYamlPreviewPanel class="h-full" />
    </template>
  </UModal>

  <!-- Git Deploy Modal -->
  <AppcreatorGitDeployModal v-model:open="showGitModal" @confirm="deployViaGit" />
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>

