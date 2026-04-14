<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'

const store = useAppCreatorStore()
const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const toast = useToast()

const popularImages = [
  { label: 'nginx', value: 'nginx' },
  { label: 'node', value: 'node' },
  { label: 'python', value: 'python' },
  { label: 'redis', value: 'redis' },
  { label: 'postgres', value: 'postgres' },
  { label: 'mysql', value: 'mysql' },
  { label: 'mongo', value: 'mongo' },
  { label: 'golang', value: 'golang' },
]

const fullImage = computed(() => `${store.wizard.image}:${store.wizard.imageTag}`)

// ── Build from Git ────────────────────────────────────────────────────────────
const repos = ref<{ fullName: string; defaultBranch: string }[]>([])
const branches = ref<{ name: string }[]>([])
const loadingRepos = ref(false)
const loadingBranches = ref(false)

const gitConnItems = computed(() =>
  store.gitConnections.map(c => ({ label: c.name + ' (' + c.provider + ')', value: c.id }))
)
const registryConnItems = computed(() =>
  store.registryConnections.map(c => ({ label: c.name + ' (' + c.registryType + ')', value: c.id }))
)
const repoItems = computed(() => repos.value.map(r => ({ label: r.fullName, value: r.fullName })))
const branchItems = computed(() => branches.value.map(b => ({ label: b.name, value: b.name })))

onMounted(async () => {
  await Promise.all([store.fetchGitConnections(), store.fetchRegistryConnections()])
})

async function onGitConnChange(connId: string) {
  store.wizard.buildGitConnectionId = connId
  store.wizard.buildRepoPath = ''
  store.wizard.buildBranch = ''
  repos.value = []
  branches.value = []
  if (!connId || !clusterStore.selectedCluster) return
  loadingRepos.value = true
  try {
    const res = await $api.get(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/git-connections/${connId}/repos`)
    repos.value = res.data
  } catch (e: any) {
    toast.add({ title: 'Failed to load repositories', color: 'error' })
  } finally {
    loadingRepos.value = false
  }
}

async function onRepoChange(repo: string) {
  store.wizard.buildRepoPath = repo
  store.wizard.buildBranch = ''
  branches.value = []
  if (!repo || !store.wizard.buildGitConnectionId || !clusterStore.selectedCluster) return
  loadingBranches.value = true
  try {
    const connId = store.wizard.buildGitConnectionId
    const res = await $api.get(
      `/k8s/${clusterStore.selectedCluster.uid}/appcreator/git-connections/${connId}/branches`,
      { params: { repo } }
    )
    branches.value = res.data
  } catch (e: any) {
    toast.add({ title: 'Failed to load branches', color: 'error' })
  } finally {
    loadingBranches.value = false
  }
}

// Build job polling
const building = ref(false)
const buildError = ref<string | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

async function triggerBuild() {
  building.value = true
  buildError.value = null
  try {
    const job = await store.startBuild()
    toast.add({ title: 'Build started', description: `Job: ${job.jobId}`, color: 'info' })
    pollTimer = setInterval(async () => {
      await store.pollBuildJob(job.jobId)
      const j = store.activeBuildJob
      if (j && (j.status === 'SUCCESS' || j.status === 'FAILED')) {
        clearInterval(pollTimer!)
        building.value = false
        if (j.status === 'SUCCESS') {
          toast.add({ title: 'Build succeeded!', description: j.imageRef ?? '', color: 'success' })
          // Auto-fill the image field with the built image reference
          if (j.imageRef) {
            const [imgName, imgTag] = j.imageRef.includes(':')
              ? j.imageRef.split(':')
              : [j.imageRef, 'latest']
            store.wizard.image = imgName
            store.wizard.imageTag = imgTag
          }
        } else {
          buildError.value = j.errorMessage ?? 'Build failed'
          toast.add({ title: 'Build failed', description: j.errorMessage ?? '', color: 'error' })
        }
      }
    }, 3000)
  } catch (e: any) {
    building.value = false
    buildError.value = e?.response?.data?.message || e.message
    toast.add({ title: 'Build trigger failed', color: 'error' })
  }
}

onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<template>
  <div class="space-y-6">
    <div>
      <h3 class="text-base font-semibold text-gray-900 dark:text-white">Container Configuration</h3>
      <p class="text-sm text-gray-500 mt-0.5">Choose a pre-built image or build directly from a Git repository.</p>
    </div>

    <!-- Mode toggle -->
    <div class="flex gap-2 p-1 bg-gray-100 dark:bg-gray-800 rounded-lg w-fit">
      <button
        v-for="mode in [{ label: 'Use Image', value: 'image', icon: 'i-lucide-box' }, { label: 'Build from Git', value: 'git', icon: 'i-lucide-git-branch' }]"
        :key="mode.value"
        :class="['flex items-center gap-1.5 px-4 py-1.5 text-sm rounded-md transition-all', store.wizard.buildMode === mode.value ? 'bg-white dark:bg-gray-700 shadow font-medium text-primary-600 dark:text-primary-400' : 'text-gray-500 hover:text-gray-700']"
        @click="store.wizard.buildMode = mode.value as 'image' | 'git'"
      >
        <UIcon :name="mode.icon" class="w-4 h-4" />
        {{ mode.label }}
      </button>
    </div>

    <!-- ── Use Image mode ── -->
    <div v-if="store.wizard.buildMode === 'image'" class="space-y-5">
      <UFormGroup label="Container Image" required>
        <div class="flex items-center gap-2">
          <UInput v-model="store.wizard.image" placeholder="nginx" class="flex-1" icon="i-lucide-box" />
          <span class="text-gray-400 font-mono text-lg select-none">:</span>
          <UInput v-model="store.wizard.imageTag" placeholder="latest" class="w-32" />
        </div>
        <template #help>
          Full image reference: <code class="bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded text-xs font-mono">{{ fullImage }}</code>
        </template>
      </UFormGroup>

      <!-- Quick select popular images -->
      <div class="space-y-2">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">Quick select</p>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="img in popularImages"
            :key="img.value"
            :class="['px-3 py-1.5 text-xs rounded-full border transition-all font-medium', store.wizard.image === img.value ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 text-primary-600 dark:text-primary-400' : 'border-gray-200 dark:border-gray-700 text-gray-500 hover:border-gray-300 hover:text-gray-700']"
            @click="store.wizard.image = img.value; store.wizard.imageTag = 'latest'"
          >{{ img.label }}</button>
        </div>
      </div>
    </div>

    <!-- ── Build from Git mode ── -->
    <div v-else class="space-y-4 bg-gray-50 dark:bg-gray-800/40 border border-dashed border-gray-300 dark:border-gray-600 rounded-xl p-5">
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <UFormGroup label="Git Connection" required>
          <USelectMenu
            :items="gitConnItems"
            :model-value="store.wizard.buildGitConnectionId"
            value-key="value"
            placeholder="Select a Git connection…"
            @update:model-value="onGitConnChange"
          />
        </UFormGroup>

        <UFormGroup label="Repository" required>
          <USelectMenu
            :items="repoItems"
            :model-value="store.wizard.buildRepoPath"
            value-key="value"
            placeholder="Select repository…"
            :loading="loadingRepos"
            :disabled="!store.wizard.buildGitConnectionId"
            @update:model-value="onRepoChange"
          />
        </UFormGroup>

        <UFormGroup label="Branch" required>
          <USelectMenu
            v-model="store.wizard.buildBranch"
            :items="branchItems"
            value-key="value"
            placeholder="Select branch…"
            :loading="loadingBranches"
            :disabled="!store.wizard.buildRepoPath"
          />
        </UFormGroup>

        <UFormGroup label="Dockerfile Path">
          <UInput v-model="store.wizard.buildDockerfilePath" placeholder="Dockerfile" icon="i-lucide-file-code" />
          <template #help>Relative path in repo, e.g. <code class="text-xs">docker/Dockerfile.prod</code></template>
        </UFormGroup>
      </div>

      <UFormGroup label="Push to Registry" required>
        <USelectMenu
          v-model="store.wizard.buildRegistryConnectionId"
          :items="registryConnItems"
          value-key="value"
          placeholder="Select a registry connection…"
        />
        <template #help>
          <NuxtLink to="/appcreator/registry-connections" class="text-primary-500 underline text-xs">Manage registry connections →</NuxtLink>
        </template>
      </UFormGroup>

      <!-- Build trigger & status -->
      <div class="flex items-center gap-3 pt-1 flex-wrap">
        <UButton
          icon="i-lucide-hammer"
          color="primary"
          :loading="building"
          :disabled="!store.wizard.buildGitConnectionId || !store.wizard.buildRepoPath || !store.wizard.buildBranch || !store.wizard.buildRegistryConnectionId"
          @click="triggerBuild"
        >Build &amp; Push Image</UButton>
        <template v-if="store.activeBuildJob">
          <UBadge
            :color="store.activeBuildJob.status === 'SUCCESS' ? 'success' : store.activeBuildJob.status === 'FAILED' ? 'error' : 'warning'"
            variant="subtle"
          >{{ store.activeBuildJob.status }}</UBadge>
          <span v-if="store.activeBuildJob.imageRef" class="text-xs text-gray-500 font-mono">{{ store.activeBuildJob.imageRef }}</span>
        </template>
      </div>
      <div v-if="buildError" class="text-xs text-red-500 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg px-3 py-2">
        <UIcon name="i-lucide-circle-x" class="inline w-3.5 h-3.5 mr-1" />{{ buildError }}
      </div>
    </div>

    <!-- Replicas (always visible) -->
    <UFormGroup label="Replicas">
      <div class="flex items-center gap-3">
        <UButton size="xs" color="neutral" variant="outline" icon="i-lucide-minus" :disabled="store.wizard.replicas <= 1" @click="store.wizard.replicas = Math.max(1, store.wizard.replicas - 1)" />
        <UInput v-model.number="store.wizard.replicas" type="number" :min="1" :max="50" class="w-20 text-center" />
        <UButton size="xs" color="neutral" variant="outline" icon="i-lucide-plus" @click="store.wizard.replicas = store.wizard.replicas + 1" />
        <span class="text-sm text-gray-500">pod{{ store.wizard.replicas !== 1 ? 's' : '' }}</span>
      </div>
      <template #help>Number of pod replicas to maintain. For StatefulSet, ordered deployment applies.</template>
    </UFormGroup>

    <!-- Image pull policy info (always visible) -->
    <div class="p-3.5 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl flex gap-3">
      <UIcon name="i-lucide-info" class="w-4 h-4 text-blue-500 shrink-0 mt-0.5" />
      <div class="text-xs text-blue-700 dark:text-blue-400 space-y-0.5">
        <p class="font-semibold">Image Pull Policy</p>
        <p>Using <code class="bg-blue-100 dark:bg-blue-900 px-1 rounded">latest</code> tag → <strong>Always</strong> pull policy. Use a specific tag for <strong>IfNotPresent</strong> in production.</p>
      </div>
    </div>
  </div>
</template>

