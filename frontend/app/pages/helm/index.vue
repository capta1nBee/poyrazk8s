<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const toast = useToast()

const loading = ref(false)
const searchLoading = ref(false)
const namespaceLoading = ref(false)

// Live releases from cluster via helm list
const releases = ref<any[]>([])
const packages = ref<any[]>([])
const namespaces = ref<string[]>([])

const searchQuery = ref('')
const selectedTab = ref(0)
const filterMode = ref<'all' | 'official' | 'verified'>('all')
const sortBy = ref('stars')

const selectedCluster = computed(() => clusterStore.selectedCluster)

// --- Installed Releases: fetch from live cluster via helm list ---
const fetchReleases = async () => {
  if (!selectedCluster.value) return
  loading.value = true

  try {
    const response = await $api.get(`/k8s/${selectedCluster.value.uid}/helm/releases`)
    
    const raw = response?.data || response
    const data = typeof raw === 'string' ? JSON.parse(raw) : raw

    // DO NOT PARSE DATES. JUST ASSIGN.
    releases.value = Array.isArray(data) ? data : []

  } catch (err) {
    console.error('Failed to fetch helm releases', err)
    releases.value = []
  } finally {
    loading.value = false
  }
}

const isUninstalling = ref(false)
const uninstallTarget = ref<string | null>(null)
const uninstallRelease = async (releaseName: string, namespace: string) => {
  isUninstalling.value = true
  uninstallTarget.value = releaseName
  try {
    await $api.delete(`/k8s/${selectedCluster.value?.uid}/helm/uninstall?releaseName=${releaseName}&namespace=${namespace}`)
    toast.add({ title: `"${releaseName}" uninstalled`, color: 'success' })
    await fetchReleases()
  } catch (err: any) {
    toast.add({ title: 'Uninstall failed', description: err?.response?.data?.message || err.message, color: 'red' })
  } finally {
    isUninstalling.value = false
    uninstallTarget.value = null
  }
}

// Columns (no longer used since we're switching to Grid view)
const helmColumns = []

// --- Edit Values & Upgrade Modal ---
const isEditValuesModalOpen = ref(false)
const isUpgradeConfirmOpen = ref(false)
const isUpgrading = ref(false)
const valuesReleaseName = ref('')
const valuesNamespace = ref('')
const valuesChartName = ref('')
const valuesChartVersion = ref('')
const valuesRepoName = ref('')
const valuesContent = ref('')
const valuesLoading = ref(false)
const savedRepositories = ref([])
const selectedRepo = ref(null)
const reposLoading = ref(false)

const fetchSavedRepositories = async () => {
  if (!selectedCluster.value?.uid) return
  reposLoading.value = true
  try {
    const { data } = await $api.get(`/k8s/${selectedCluster.value.uid}/helm-repos`)
    savedRepositories.value = data
    // Auto-select if there's only one or matching name
    if (data.length > 0) {
      const match = data.find(r => r.name.toLowerCase() === valuesRepoName.value?.toLowerCase())
      selectedRepo.value = match || data[0]
    }
  } catch (err) {
    console.error('Failed to fetch repositories:', err)
  } finally {
    reposLoading.value = false
  }
}

const showValues = async (row: any) => {
  valuesReleaseName.value = row.name
  valuesNamespace.value = row.namespace
  
  const fullChart = row.chart || ''
  // Standard helm chart naming: name-version. We extract the name part by stripping the trailing version.
  const chartName = fullChart.replace(/-\d+\.\d+\.\d+.*$/, '')
  const chartVersion = fullChart.substring(chartName.length + 1) || row.app_version
  
  valuesRepoName.value = '' // Reset
  valuesChartName.value = chartName
  valuesChartVersion.value = chartVersion
  selectedRepo.value = null

  valuesContent.value = ''
  isEditValuesModalOpen.value = true
  valuesLoading.value = true
  try {
    const response = await $api.get(`/k8s/${selectedCluster.value?.uid}/helm/releases/${encodeURIComponent(row.name)}/values?namespace=${row.namespace}`)
    let raw = response?.data || ''
    if (typeof raw === 'string' && raw.includes('USER-SUPPLIED VALUES:')) {
       raw = raw.replace(/^USER-SUPPLIED VALUES:\r?\n?/, '').trim()
    }
    valuesContent.value = raw
  } catch (err) {
    valuesContent.value = '# Failed to retrieve values'
  } finally {
    valuesLoading.value = false
  }
}

const executeUpgrade = async () => {
  if (!selectedRepo.value && !valuesRepoName.value) {
    toast.add({ title: 'Repository Required', description: 'Please select a saved repository or provide a name.', color: 'amber' })
    return
  }
  isUpgrading.value = true
  try {
    const repoName = selectedRepo.value ? selectedRepo.value.name : valuesRepoName.value
    const fullChartRef = `${repoName}/${valuesChartName.value}`
    
    await $api.post(`/k8s/${selectedCluster.value?.uid}/helm/upgrade`, {
      releaseName: valuesReleaseName.value,
      namespace: valuesNamespace.value,
      chartName: fullChartRef,
      chartVersion: valuesChartVersion.value,
      customValuesYaml: valuesContent.value,
      repoId: selectedRepo.value?.id,
      repoUrl: selectedRepo.value?.url
    })
    toast.add({ title: 'Upgrade started! 🚀', color: 'success' })
    isUpgradeConfirmOpen.value = false
    isEditValuesModalOpen.value = false
    setTimeout(fetchReleases, 3000)
  } catch (err: any) {
    const msg = err?.response?.data?.error || err?.response?.data?.message || err?.response?.data?.logs || err.message || 'Upgrade failed'
    toast.add({ title: 'Upgrade failed', description: msg, color: 'red', timeout: 10000 })
  } finally {
    isUpgrading.value = false
  }
}

// --- Discover Market ---
const fetchPackages = async () => {
  if (!selectedCluster.value?.uid) return
  searchLoading.value = true
  try {
    let url = `/k8s/${selectedCluster.value.uid}/helm/packages/search?kind=0`
    if (searchQuery.value) url += `&ts_query_web=${encodeURIComponent(searchQuery.value)}`
    if (filterMode.value === 'official') url += '&official=true'
    if (filterMode.value === 'verified') url += '&verified_publisher=true'
    url += sortBy.value === 'stars' ? '&sort=stars&direction=desc' : '&sort=relevance&direction=desc'
    const response = await $api.get(url)
    const raw = response?.data || response
    const data = typeof raw === 'string' ? JSON.parse(raw) : raw
    packages.value = Array.isArray(data?.packages) ? data.packages : []
  } catch (err) {
    packages.value = []
  } finally {
    searchLoading.value = false
  }
}

const loadNamespaces = async () => {
  if (!selectedCluster.value) return
  namespaceLoading.value = true
  try {
    // /for-page does not require Namespace resource permission —
    // Helm is a Tier 3 page and only has page-level access.
    const { data } = await $api.get(`/k8s/${selectedCluster.value.uid}/namespaces/for-page`)
    namespaces.value = Array.isArray(data) && data.length > 0
      ? data.map((ns: any) => typeof ns === 'string' ? ns : ns.name || String(ns))
      : ['default']
  } catch {
    namespaces.value = ['default']
  } finally {
    namespaceLoading.value = false
  }
}

watch(selectedCluster, (newVal) => {
  if (newVal?.uid) {
    fetchReleases()
    fetchPackages()
    loadNamespaces()
    fetchSavedRepositories()
  }
}, { immediate: true })

watch(filterMode, fetchPackages)
watch(sortBy, fetchPackages)

let searchTimeout: any = null
const onSearchInput = () => {
  clearTimeout(searchTimeout)
  searchTimeout = setTimeout(fetchPackages, 400)
}

// --- Deploy Modal ---
const isDeployModalOpen = ref(false)
const selectedPackage = ref<any>(null)
const packageDetailsLoading = ref(false)
const isDeploying = ref(false)

const deployForm = ref({
  releaseName: '',
  namespace: 'default',
  customValues: ''
})

const openDeployModal = async (pkg: any) => {
  if (!selectedCluster.value) return
  selectedPackage.value = pkg
  deployForm.value = { releaseName: pkg.name || '', namespace: 'default', customValues: '' }
  isDeployModalOpen.value = true
  packageDetailsLoading.value = true

  if (namespaces.value.length === 0) await loadNamespaces()
  if (namespaces.value.length > 0) {
    deployForm.value.namespace = namespaces.value.includes('default') ? 'default' : namespaces.value[0]
  }

  try {
    const [detailRes, valuesRes] = await Promise.all([
      $api.get(`/k8s/${selectedCluster.value.uid}/helm/packages/helm/${pkg.repository?.name}/${pkg.name}`),
      $api.get(`/k8s/${selectedCluster.value.uid}/helm/packages/${pkg.package_id}/${pkg.version}/values`)
    ])
    selectedPackage.value = detailRes.data
    deployForm.value.customValues = valuesRes.data || ''
  } catch (err) {
    console.error('Failed to load package details', err)
  } finally {
    packageDetailsLoading.value = false
  }
}

const deployChart = async () => {
  if (!deployForm.value.releaseName) {
    toast.add({ title: 'Release name is required', color: 'red' })
    return
  }
  isDeploying.value = true
  try {
    const response = await $api.post(`/k8s/${selectedCluster.value?.uid}/helm/deploy`, {
      releaseName: deployForm.value.releaseName,
      deployName: deployForm.value.releaseName,
      namespace: deployForm.value.namespace,
      chartName: `${selectedPackage.value?.repository?.name}/${selectedPackage.value?.name}`,
      chartVersion: selectedPackage.value?.version,
      repoUrl: selectedPackage.value?.repository?.url,
      repoId: savedRepositories.value.find(r => r.url === selectedPackage.value?.repository?.url)?.id,
      customValuesYaml: deployForm.value.customValues
    })
    
    const result = response?.data
    if (result?.success === false || result?.status === 'FAILED') {
      // Meaningful error from backend (e.g. already exists)
      const msg = result?.logs || 'Deployment failed'
      toast.add({ title: 'Installation failed', description: msg, color: 'red', timeout: 10000 })
      return
    }
    
    toast.add({ title: 'Deployment started! 🚀', color: 'success' })
    isDeployModalOpen.value = false
    selectedTab.value = 0
    setTimeout(fetchReleases, 3000)
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.response?.data?.logs || err.message || 'Unknown error'
    toast.add({ title: 'Deployment failed', description: msg, color: 'red', timeout: 10000 })
  } finally {
    isDeploying.value = false
  }
}
</script>

<template>
  <UDashboardPage>
    <UDashboardPanel grow>
      <UDashboardNavbar title="App Market (Helm)">
        <template #right>
          <div class="flex items-center gap-2">
            <UButton size="sm" :variant="selectedTab === 0 ? 'solid' : 'ghost'" :color="selectedTab === 0 ? 'primary' : 'gray'" icon="i-lucide-hard-drive" label="Installed" @click="selectedTab = 0" />
            <UButton size="sm" :variant="selectedTab === 1 ? 'solid' : 'ghost'" :color="selectedTab === 1 ? 'primary' : 'gray'" icon="i-lucide-globe" label="Discover" @click="selectedTab = 1; if (packages.length === 0) fetchPackages()" />
          </div>
        </template>
      </UDashboardNavbar>

      <UDashboardPanelContent class="bg-gray-50 dark:bg-gray-900/50 overflow-y-auto">
        <div v-if="selectedCluster" class="p-6 max-w-7xl mx-auto space-y-6">

          <!-- INSTALLED RELEASES (live from cluster) -->
          <div v-show="selectedTab === 0">
            <UCard class="shadow-sm">
              <template #header>
                <div class="flex justify-between items-center">
                  <div>
                    <h3 class="text-lg font-semibold">Installed Releases</h3>
                    <p class="text-xs text-gray-400 mt-0.5">Live data from cluster via <code class="bg-gray-100 dark:bg-gray-800 px-1 rounded">helm list --all-namespaces</code></p>
                  </div>
                  <div class="flex gap-2">
                    <UButton icon="i-lucide-refresh-cw" color="gray" variant="ghost" :loading="loading" @click="fetchReleases" />
                  </div>
                </div>
              </template>

              <div v-if="loading" class="py-10 text-center">
                <UIcon name="i-lucide-loader-2" class="animate-spin w-8 h-8 text-primary-500 mx-auto" />
              </div>
              <div v-else-if="releases.length > 0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 p-6">
                <div
                  v-for="row in releases"
                  :key="row.name + row.namespace"
                  class="group bg-white dark:bg-gray-900 rounded-xl border border-gray-100 dark:border-gray-800 hover:border-primary-500/50 hover:shadow-2xl hover:-translate-y-1 transition-all duration-300 overflow-hidden flex flex-col"
                >
                  <div class="p-4 space-y-3 flex-1 flex flex-col">
                    <div class="flex justify-between items-start">
                      <div class="w-12 h-12 bg-gray-50 dark:bg-gray-800 rounded-lg p-1.5 flex items-center justify-center border border-gray-100 dark:border-gray-700">
                        <!-- Helm CLI doesn't return logo URLs natively, so we fallback to icon -->
                        <UIcon name="i-lucide-hard-drive" class="w-6 h-6 text-primary-500" />
                      </div>
                      <div class="flex flex-col items-end gap-1">
                        <UBadge
                          :color="row.status === 'deployed' ? 'green' : row.status === 'failed' ? 'red' : row.status === 'superseded' ? 'yellow' : 'gray'"
                          variant="subtle"
                          size="xs"
                          class="uppercase"
                        >
                          {{ row.status }}
                        </UBadge>
                        <div class="text-[10px] text-gray-400 mt-1">Rev: {{ row.revision }}</div>
                      </div>
                    </div>
                    
                    <div class="flex-1">
                      <h4 class="font-bold text-gray-900 dark:text-white truncate" :title="row.name">{{ row.name }}</h4>
                      <p class="text-xs text-gray-500 truncate mt-1" :title="row.chart">{{ row.chart }}</p>
                      
                      <div class="mt-3 grid grid-cols-2 gap-2 text-xs">
                        <div class="bg-gray-50 dark:bg-gray-800/50 rounded-md p-2 border border-gray-100 dark:border-gray-800">
                          <span class="text-gray-400 block text-[10px] uppercase mb-0.5">App Version</span>
                          <span class="font-medium truncate block" :title="row.app_version">{{ row.app_version || 'N/A' }}</span>
                        </div>
                        <div class="bg-gray-50 dark:bg-gray-800/50 rounded-md p-2 border border-gray-100 dark:border-gray-800">
                          <span class="text-gray-400 block text-[10px] uppercase mb-0.5">Namespace</span>
                          <span class="font-medium truncate block" :title="row.namespace">{{ row.namespace }}</span>
                        </div>
                      </div>
                    </div>

                    <div class="text-[10px] text-gray-400 truncate mt-1 mb-2" :title="row.updated">
                      <UIcon name="i-lucide-clock" class="inline-block mr-1 align-text-bottom" />
                      {{ row.updated }}
                    </div>

                    <div class="flex gap-2 pt-3 border-t border-gray-100 dark:border-gray-800">
                      <UButton
                        size="xs"
                        color="gray"
                        variant="soft"
                        icon="i-lucide-file-code"
                        class="flex-1 justify-center"
                        @click="showValues(row)"
                      >
                        Edit Values
                      </UButton>
                      <UButton
                        size="xs"
                        color="red"
                        variant="soft"
                        icon="i-lucide-trash"
                        :loading="isUninstalling && uninstallTarget === row.name"
                        @click="uninstallRelease(row.name, row.namespace)"
                      />
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="text-center py-10 text-gray-500">No releases found on this cluster.</div>
            </UCard>
          </div>

          <!-- DISCOVER MARKET -->
          <div v-show="selectedTab === 1" class="space-y-6">
            <div class="bg-white dark:bg-gray-900 rounded-2xl shadow-sm border border-gray-200 dark:border-gray-800 p-6">
              <div class="flex gap-3">
                <UInput v-model="searchQuery" icon="i-lucide-search" placeholder="Search thousands of charts..." size="xl" @input="onSearchInput" class="flex-1" />
                <USelectMenu v-model="sortBy" :items="['stars', 'relevance']" class="w-40" size="xl">
                  <template #label>
                    <div class="flex items-center gap-2">
                      <UIcon :name="sortBy === 'stars' ? 'i-lucide-award' : 'i-lucide-search'" :class="sortBy === 'stars' ? 'text-orange-500' : 'text-blue-500'" />
                      {{ sortBy === 'stars' ? 'Popularity' : 'Relevance' }}
                    </div>
                  </template>
                </USelectMenu>
                <UButton icon="i-lucide-refresh-cw" color="gray" variant="ghost" :loading="searchLoading" @click="fetchPackages" />
              </div>

              <!-- Segmented filter bar -->
              <div class="flex flex-wrap items-center gap-3 mt-4 pt-4 border-t border-gray-100 dark:border-gray-800">
                <div class="flex p-1 bg-gray-100 dark:bg-gray-800 rounded-lg shadow-inner gap-0.5">
                  <button @click="filterMode = 'all'" :class="['px-4 py-1.5 text-xs font-semibold rounded-md transition-all', filterMode === 'all' ? 'bg-white dark:bg-gray-700 shadow text-primary-600 dark:text-primary-400' : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300']">All Charts</button>
                  <button @click="filterMode = 'official'" :class="['px-4 py-1.5 text-xs font-semibold rounded-md transition-all flex items-center gap-1.5', filterMode === 'official' ? 'bg-blue-600 shadow text-white' : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300']">
                    <UIcon name="i-lucide-check-circle" class="w-3.5 h-3.5" /> Official
                  </button>
                  <button @click="filterMode = 'verified'" :class="['px-4 py-1.5 text-xs font-semibold rounded-md transition-all flex items-center gap-1.5', filterMode === 'verified' ? 'bg-green-600 shadow text-white' : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300']">
                    <UIcon name="i-lucide-shield-check" class="w-3.5 h-3.5" /> Verified
                  </button>
                </div>
                <div class="ml-auto flex items-center gap-3">
                  <UBadge v-if="filterMode === 'official'" color="blue" variant="subtle" size="xs">Official Only</UBadge>
                  <UBadge v-if="filterMode === 'verified'" color="green" variant="subtle" size="xs">Verified Only</UBadge>
                  <span class="text-[11px] text-gray-400">{{ packages.length }} results</span>
                </div>
              </div>
            </div>

            <!-- Skeletons -->
            <div v-if="searchLoading" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
              <div v-for="i in 8" :key="i" class="h-52 rounded-xl animate-pulse bg-gray-200 dark:bg-gray-800" />
            </div>

            <!-- Empty -->
            <div v-else-if="packages.length === 0" class="py-20 text-center bg-white dark:bg-gray-900 rounded-xl border border-dashed border-gray-300 dark:border-gray-700">
              <UIcon name="i-lucide-box" class="w-12 h-12 text-gray-400 mb-2 mx-auto" />
              <p class="text-gray-500">No applications found.</p>
            </div>

            <!-- Package Grid -->
            <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
              <div
                v-for="pkg in packages"
                :key="pkg.package_id"
                @click="openDeployModal(pkg)"
                class="group cursor-pointer bg-white dark:bg-gray-900 rounded-xl border border-gray-100 dark:border-gray-800 hover:border-primary-500/50 hover:shadow-2xl hover:-translate-y-1 transition-all duration-300 overflow-hidden"
              >
                <div class="p-4 space-y-3">
                  <div class="flex justify-between items-start">
                    <div class="w-12 h-12 bg-gray-50 dark:bg-gray-800 rounded-lg p-1.5 flex items-center justify-center border border-gray-100 dark:border-gray-700">
                      <img v-if="pkg.logo_image_id" :src="`https://artifacthub.io/image/${pkg.logo_image_id}`" class="max-w-full max-h-full object-contain" />
                      <UIcon v-else name="i-lucide-box" class="w-6 h-6 text-gray-400" />
                    </div>
                    <div class="flex flex-col items-end gap-1">
                      <div class="flex items-center gap-1">
                        <UBadge v-if="pkg.official" color="blue" variant="subtle" size="xs">Official</UBadge>
                        <UBadge v-if="pkg.verified_publisher" color="green" variant="subtle" size="xs">Verified</UBadge>
                      </div>
                      <div class="flex items-center text-xs bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">
                        <UIcon name="i-lucide-star" class="w-3 h-3 text-yellow-500 mr-1" />{{ pkg.stars || 0 }}
                      </div>
                    </div>
                  </div>
                  <h4 class="font-bold text-gray-900 dark:text-white truncate">{{ pkg.display_name || pkg.name }}</h4>
                  <p class="text-xs text-gray-500 line-clamp-2 h-8">{{ pkg.description }}</p>
                  <div class="flex items-center gap-2 pt-2 text-[10px] text-gray-400 border-t border-gray-100 dark:border-gray-800">
                    <span class="flex items-center gap-1"><UIcon name="i-lucide-tag" />v{{ pkg.version }}</span>
                    <span class="truncate">{{ pkg.repository?.name }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

        </div>
        <div v-else class="p-20 text-center text-gray-500">Please select a cluster to use the App Market.</div>
      </UDashboardPanelContent>
    </UDashboardPanel>

    <!-- Edit Values Modal -->
    <UModal 
      v-model:open="isEditValuesModalOpen" 
      title="Update Release Configuration" 
      size="xl"
      description="Modify the values.yaml configuration for this Helm release."
    >
      <template #body>
        <div class="space-y-4 flex flex-col h-[80vh]">
          <div class="flex items-center justify-between mb-2">
            <div class="flex items-center gap-2">
              <UBadge variant="subtle" color="primary">{{ valuesReleaseName }}</UBadge>
              <span class="text-sm text-gray-400">@</span>
              <UBadge variant="subtle" color="gray">{{ valuesNamespace }}</UBadge>
            </div>
            <div class="text-xs text-gray-500 font-mono">
              {{ valuesChartName }}:{{ valuesChartVersion }}
            </div>
          </div>

          <div class="flex-1 min-h-0 border border-gray-200 dark:border-gray-800 rounded-lg overflow-hidden">
            <MonacoYamlEditor
              v-model="valuesContent"
              :loading="valuesLoading"
              height="100%"
            />
          </div>

          <div class="flex justify-between items-center pt-4 border-t border-gray-100 dark:border-gray-800 mt-auto">
            <p class="text-xs text-gray-500 max-w-md italic">
              Be careful! Upgrading will apply these values to the live release.
            </p>
            <div class="flex gap-2">
              <UButton color="neutral" variant="outline" @click="isEditValuesModalOpen = false">Cancel</UButton>
              <UButton color="primary" @click="isUpgradeConfirmOpen = true">Confirm & Upgrade</UButton>
            </div>
          </div>
        </div>
      </template>
    </UModal>

    <!-- Upgrade Confirmation Modal -->
    <UModal 
      v-model:open="isUpgradeConfirmOpen" 
      title="Confirm Helm Upgrade"
      description="Verify repository information before upgrading."
    >
      <template #body>
        <div class="space-y-4">
          <div class="p-4 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-lg flex gap-3">
            <UIcon name="i-lucide-alert-triangle" class="w-5 h-5 text-amber-500 shrink-0" />
            <p class="text-xs text-amber-700 dark:text-amber-400">
              Helm requires the original <strong>Repository Name</strong> to fetch the chart package before applying your values.
            </p>
          </div>
          
          <UFormGroup label="Chart Repository" required help="Select a saved repository to fetch the chart package.">
            <div class="space-y-2">
              <USelectMenu
                v-model="selectedRepo"
                :items="savedRepositories"
                label-key="name"
                placeholder="Select a saved repository..."
                icon="i-lucide-database"
              />
              <div v-if="savedRepositories.length === 0" class="text-xs text-red-500 italic mt-1">
                No saved repositories found. Please add one in Cluster Settings first.
              </div>
            </div>
          </UFormGroup>
          
          <div class="flex justify-end gap-3 mt-4 pt-4 border-t border-gray-100 dark:border-gray-800">
            <UButton color="neutral" variant="outline" @click="isUpgradeConfirmOpen = false">Cancel</UButton>
            <UButton color="primary" :loading="isUpgrading" @click="executeUpgrade">Upgrade Release</UButton>
          </div>
        </div>
      </template>
    </UModal>

    <!-- Deploy Modal — Nuxt UI v3 pattern (v-model:open + #body slot) -->
    <UModal
      v-model:open="isDeployModalOpen"
      :title="`Deploy — ${selectedPackage?.display_name || selectedPackage?.name || ''}`"
      :description="selectedPackage?.description"
      :ui="{ width: 'sm:max-w-4xl' }"
    >
      <template #body>
        <div v-if="packageDetailsLoading" class="flex justify-center py-8">
          <UIcon name="i-lucide-loader-2" class="animate-spin text-2xl text-primary-500" />
          <span class="ml-3 text-gray-500">Loading chart details...</span>
        </div>

        <div v-else class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <UFormGroup label="Release Name" required>
              <UInput v-model="deployForm.releaseName" placeholder="my-release" />
            </UFormGroup>
            <UFormGroup label="Namespace" required>
              <USelectMenu
                v-model="deployForm.namespace"
                :items="namespaces"
                placeholder="Select Namespace"
                searchable
                :loading="namespaceLoading"
              />
            </UFormGroup>
          </div>

          <UFormGroup label="Values (YAML)">
            <UTextarea
              v-model="deployForm.customValues"
              :rows="20"
              class="font-mono text-xs"
              placeholder="# Override default values here..."
            />
            <p class="text-xs text-gray-400 mt-1">Editing the chart's default values.yaml. Changes here will be used during installation.</p>
          </UFormGroup>

          <div class="flex justify-end gap-3 pt-2 border-t border-gray-100 dark:border-gray-800">
            <UButton color="gray" variant="soft" @click="isDeployModalOpen = false">Cancel</UButton>
            <UButton color="primary" :loading="isDeploying" icon="i-lucide-rocket" @click="deployChart">
              Deploy to Cluster
            </UButton>
          </div>
        </div>
      </template>
    </UModal>

  </UDashboardPage>
</template>
