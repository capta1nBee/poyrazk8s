<script setup lang="ts">
/**
 * GitDeployModal.vue
 * Lets the user pick a Git connection, repository, branch and path before
 * triggering a GitOps deploy (Push YAML + Create PR/MR).
 *
 * Emits:
 *   confirm(payload) — { connectionId, repo, branch, gitPath }
 *   cancel
 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'confirm', payload: { connectionId: string; repo: string; branch: string; gitPath: string }): void
}>()

const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const toast = useToast()

interface GitConnection { id: string; name: string; provider: string }
interface GitRepo { fullName: string; defaultBranch: string }
interface GitBranch { name: string }

const connections  = ref<GitConnection[]>([])
const repos        = ref<GitRepo[]>([])
const branches     = ref<GitBranch[]>([])

const selectedConn   = ref<string>('')
const selectedRepo   = ref<string>('')
const selectedBranch = ref<string>('')
const gitPath        = ref<string>('k8s')

const loadingConns    = ref(false)
const loadingRepos    = ref(false)
const loadingBranches = ref(false)

const clusterUid = computed(() => clusterStore.selectedCluster?.uid)

const connOptions = computed(() =>
  connections.value.map(c => ({ label: `${c.name} (${c.provider})`, value: c.id }))
)
const repoOptions = computed(() =>
  repos.value.map(r => ({ label: r.fullName, value: r.fullName }))
)
const branchOptions = computed(() =>
  branches.value.map(b => ({ label: b.name, value: b.name }))
)

// Load connections on open
watch(() => props.open, async (v) => {
  if (!v || !clusterUid.value) return
  loadingConns.value = true
  try {
    const res = await $api.get(`/k8s/${clusterUid.value}/appcreator/git-connections`)
    connections.value = res.data
    if (connections.value.length > 0) selectedConn.value = connections.value[0].id
  } catch (e: any) {
    toast.add({ title: 'Failed to load git connections', color: 'error' })
  } finally {
    loadingConns.value = false
  }
})

// Load repos when connection changes
watch(selectedConn, async (id) => {
  if (!id || !clusterUid.value) return
  selectedRepo.value = ''
  selectedBranch.value = ''
  repos.value = []
  branches.value = []
  loadingRepos.value = true
  try {
    const res = await $api.get(`/k8s/${clusterUid.value}/appcreator/git-connections/${id}/repos`)
    repos.value = res.data
  } catch (e: any) {
    toast.add({ title: 'Failed to load repositories', color: 'error' })
  } finally {
    loadingRepos.value = false
  }
})

// Load branches when repo changes
watch(selectedRepo, async (repoFull) => {
  if (!repoFull || !selectedConn.value || !clusterUid.value) return
  selectedBranch.value = ''
  branches.value = []
  loadingBranches.value = true
  try {
    const res = await $api.get(
      `/k8s/${clusterUid.value}/appcreator/git-connections/${selectedConn.value}/branches`,
      { params: { repo: repoFull } }
    )
    branches.value = res.data
    // Pre-select default branch
    const repoObj = repos.value.find(r => r.fullName === repoFull)
    if (repoObj) selectedBranch.value = repoObj.defaultBranch
  } catch (e: any) {
    toast.add({ title: 'Failed to load branches', color: 'error' })
  } finally {
    loadingBranches.value = false
  }
})

const canConfirm = computed(() =>
  selectedConn.value && selectedRepo.value && selectedBranch.value
)

const onConfirm = () => {
  if (!canConfirm.value) return
  emit('confirm', {
    connectionId: selectedConn.value,
    repo: selectedRepo.value,
    branch: selectedBranch.value,
    gitPath: gitPath.value || 'k8s'
  })
  emit('update:open', false)
}

const onCancel = () => emit('update:open', false)
</script>

<template>
  <UModal :open="props.open" title="Deploy via GitOps" @update:open="emit('update:open', $event)">
    <template #body>
      <div class="space-y-4">
        <p class="text-sm text-gray-500 dark:text-gray-400">
          Select a Git connection, repository and base branch.
          A new feature branch will be created and a Pull Request / Merge Request will be opened automatically.
        </p>

        <!-- Connection -->
        <UFormField label="Git Connection" name="connection" required>
          <USelectMenu
            v-model="selectedConn"
            :items="connOptions"
            value-key="value"
            :loading="loadingConns"
            placeholder="Select a connection…"
            class="w-full"
          />
          <template #hint>
            <NuxtLink to="/appcreator/git-connections" class="text-xs text-primary-500 hover:underline">
              Manage connections →
            </NuxtLink>
          </template>
        </UFormField>

        <!-- Repository -->
        <UFormField label="Repository" name="repo" required>
          <USelectMenu
            v-model="selectedRepo"
            :items="repoOptions"
            value-key="value"
            :loading="loadingRepos"
            :disabled="!selectedConn || loadingRepos"
            placeholder="Select a repository…"
            class="w-full"
          />
        </UFormField>

        <!-- Branch -->
        <UFormField label="Base Branch" name="branch" required>
          <USelectMenu
            v-model="selectedBranch"
            :items="branchOptions"
            value-key="value"
            :loading="loadingBranches"
            :disabled="!selectedRepo || loadingBranches"
            placeholder="Select a branch…"
            class="w-full"
          />
        </UFormField>

        <!-- Path -->
        <UFormField label="YAML directory in repo" name="gitPath">
          <UInput v-model="gitPath" placeholder="k8s" />
          <template #hint>Files will be placed at &lt;path&gt;/&lt;appName&gt;/*.yaml</template>
        </UFormField>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="outline" @click="onCancel">Cancel</UButton>
        <UButton
          color="primary"
          icon="i-lucide-git-pull-request"
          :disabled="!canConfirm"
          @click="onConfirm"
        >
          Create PR / MR
        </UButton>
      </div>
    </template>
  </UModal>
</template>

