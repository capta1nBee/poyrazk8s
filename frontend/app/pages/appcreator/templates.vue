<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'
import type { AppCreatorTemplate } from '~/stores/appcreator'

definePageMeta({ layout: 'default' })

const store = useAppCreatorStore()
const router = useRouter()
const toast = useToast()
const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const { hasPermission } = usePagePermissions('AppCreator')

onMounted(() => store.fetchTemplates())

const searchQuery = ref('')
const selectedCategory = ref('All')

const categories = computed(() => {
  const cats = new Set(['All', ...store.templates.map(t => t.category || 'Custom')])
  return [...cats]
})

const filtered = computed(() => {
  return store.templates.filter(t => {
    const matchCat = selectedCategory.value === 'All' || (t.category || 'Custom') === selectedCategory.value
    const q = searchQuery.value.toLowerCase()
    const matchQ = !q || t.name.toLowerCase().includes(q) || t.description?.toLowerCase().includes(q)
    return matchCat && matchQ
  })
})

// Preset (public) templates
const presetTemplates = computed(() => filtered.value.filter(t => t.isPublic))
// Custom (user-created) templates
const customTemplates = computed(() => filtered.value.filter(t => !t.isPublic))

const useTemplate = (template: AppCreatorTemplate) => {
  store.applyTemplate(template)
  router.push('/appcreator/create')
  toast.add({ title: `Template "${template.name}" applied`, color: 'success' })
}

// Delete custom template
const deletingId = ref<string | null>(null)
const deleteTemplate = async (template: AppCreatorTemplate) => {
  if (!clusterStore.selectedCluster) return
  deletingId.value = template.id
  try {
    await $api.delete(`/k8s/${clusterStore.selectedCluster.uid}/appcreator/templates/${template.id}`)
    store.templates = store.templates.filter(t => t.id !== template.id)
    toast.add({ title: 'Template deleted', color: 'success' })
  } catch (e: any) {
    toast.add({ title: 'Delete failed', description: e?.response?.data?.message || e.message, color: 'red' })
  } finally {
    deletingId.value = null
  }
}

const categoryIcon = (cat: string) => {
  const icons: Record<string, string> = {
    'Web': 'i-lucide-globe', 'Database': 'i-lucide-database', 'Worker': 'i-lucide-cpu',
    'Batch': 'i-lucide-timer', 'Custom': 'i-lucide-bookmark', 'All': 'i-lucide-layout-grid'
  }
  return icons[cat] || 'i-lucide-package'
}

const workloadBadgeColor = (wt: string) => {
  const colors: Record<string, string> = { Deployment: 'cyan', StatefulSet: 'amber', DaemonSet: 'purple', CronJob: 'rose' }
  return colors[wt] || 'gray'
}
</script>

<template>
  <UDashboardPanel id="appcreator-templates">
    <template #header>
      <UDashboardNavbar title="App Templates">
        <template #leading>
          <UButton icon="i-lucide-arrow-left" color="neutral" variant="ghost" to="/appcreator" />
        </template>
        <template #right>
          <UButton v-if="hasPermission('create')" color="primary" icon="i-lucide-plus" to="/appcreator/create">
            New App
          </UButton>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="max-w-6xl mx-auto py-8 px-6 space-y-8">
        <!-- Search & Filters -->
        <div class="flex flex-col sm:flex-row gap-3">
          <UInput v-model="searchQuery" icon="i-lucide-search" placeholder="Search templates..." class="flex-1" />
          <div class="flex gap-2 flex-wrap">
            <UButton
              v-for="cat in categories" :key="cat"
              :color="selectedCategory === cat ? 'primary' : 'neutral'"
              :variant="selectedCategory === cat ? 'solid' : 'outline'"
              size="sm" :icon="categoryIcon(cat)"
              @click="selectedCategory = cat"
            >{{ cat }}</UButton>
          </div>
        </div>

        <!-- Preset Templates -->
        <section v-if="presetTemplates.length">
          <h2 class="text-base font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
            <UIcon name="i-lucide-sparkles" class="w-4 h-4 text-primary-500" />Preset Templates
          </h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <div
              v-for="t in presetTemplates" :key="t.id"
              class="group relative p-5 bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 hover:border-primary-400 hover:shadow-md transition-all cursor-pointer"
              @click="useTemplate(t)"
            >
              <div class="flex items-start gap-3 mb-3">
                <div class="w-10 h-10 rounded-xl bg-primary-50 dark:bg-primary-900/30 flex items-center justify-center shrink-0">
                  <UIcon :name="t.icon || 'i-lucide-package'" class="w-5 h-5 text-primary-500" />
                </div>
                <div class="flex-1 min-w-0">
                  <h3 class="text-sm font-semibold text-gray-900 dark:text-white truncate">{{ t.name }}</h3>
                  <p class="text-xs text-gray-500 mt-0.5 line-clamp-2">{{ t.description }}</p>
                </div>
              </div>
              <div class="flex items-center gap-2">
                <UBadge size="xs" color="neutral" variant="subtle">{{ t.category || 'Custom' }}</UBadge>
                <UBadge v-if="t.config" size="xs" :color="workloadBadgeColor(JSON.parse(t.config || '{}').workloadType || 'Deployment')" variant="subtle">
                  {{ JSON.parse(t.config || '{}').workloadType || 'Deployment' }}
                </UBadge>
              </div>
              <div class="absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity">
                <UButton size="xs" color="primary" icon="i-lucide-arrow-right">Use</UButton>
              </div>
            </div>
          </div>
        </section>

        <!-- Custom Templates -->
        <section v-if="customTemplates.length">
          <h2 class="text-base font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
            <UIcon name="i-lucide-bookmark" class="w-4 h-4 text-amber-500" />My Templates
          </h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <div
              v-for="t in customTemplates" :key="t.id"
              class="group relative p-5 bg-white dark:bg-gray-900 rounded-2xl border border-gray-200 dark:border-gray-700 hover:border-amber-400 hover:shadow-md transition-all"
            >
              <div class="flex items-start gap-3 mb-3">
                <div class="w-10 h-10 rounded-xl bg-amber-50 dark:bg-amber-900/20 flex items-center justify-center shrink-0">
                  <UIcon :name="t.icon || 'i-lucide-bookmark'" class="w-5 h-5 text-amber-500" />
                </div>
                <div class="flex-1 min-w-0">
                  <h3 class="text-sm font-semibold text-gray-900 dark:text-white truncate">{{ t.name }}</h3>
                  <p class="text-xs text-gray-500 mt-0.5 line-clamp-2">{{ t.description }}</p>
                </div>
              </div>
              <div class="flex items-center justify-between">
                <UBadge size="xs" color="amber" variant="subtle">Custom</UBadge>
                <div class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <UButton size="xs" color="primary" icon="i-lucide-arrow-right" @click="useTemplate(t)">Use</UButton>
                  <UButton v-if="hasPermission('delete')" size="xs" color="red" variant="ghost" icon="i-lucide-trash-2"
                    :loading="deletingId === t.id" @click.stop="deleteTemplate(t)" />
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- Empty state -->
        <div v-if="!store.loading && !filtered.length" class="flex flex-col items-center justify-center py-20 text-center text-gray-400">
          <UIcon name="i-lucide-package-open" class="w-12 h-12 mb-4 opacity-30" />
          <p class="text-base font-medium">No templates found</p>
          <p class="text-sm mt-1">{{ searchQuery ? 'Try a different search term.' : 'Create an app and save it as a template.' }}</p>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>

