<script setup lang="ts">
import type { CustomResourceDefinition, CrdNames } from '~/types/customresourcedefinition'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('CustomResourceDefinition')

const resources = ref<CustomResourceDefinition[]>([])
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)

// ── Filters ─────────────────────────────────────────────────────────────────
const selectedGroup = ref<string>('')
const selectedKind = ref<string>('')

/** Parse the `names` field (may be JSON string or object) and extract kind */
function parseCrdNames(names: string | CrdNames | undefined): CrdNames {
  if (!names) return {}
  if (typeof names === 'string') {
    try { return JSON.parse(names) } catch { return {} }
  }
  return names
}

/** Unique API Group options derived from loaded resources */
const groupOptions = computed(() => {
  const groups = new Set<string>()
  resources.value.forEach(r => {
    if (r.groupName) groups.add(r.groupName)
  })
  return [
    { label: 'All Groups', value: '' },
    ...[...groups].sort().map(g => ({ label: g, value: g }))
  ]
})

/** Unique Kind options (filtered by selected group if any) */
const kindOptions = computed(() => {
  const kinds = new Set<string>()
  resources.value.forEach(r => {
    if (selectedGroup.value && r.groupName !== selectedGroup.value) return
    const parsed = parseCrdNames(r.names)
    if (parsed.kind) kinds.add(parsed.kind)
  })
  return [
    { label: 'All Kinds', value: '' },
    ...[...kinds].sort().map(k => ({ label: k, value: k }))
  ]
})

/** Reset kind filter when group changes (the kind may no longer exist in the new group) */
watch(selectedGroup, () => {
  const validKinds = kindOptions.value.map(k => k.value)
  if (selectedKind.value && !validKinds.includes(selectedKind.value)) {
    selectedKind.value = ''
  }
})

/** Filtered rows — applies permission filter, group filter, and kind filter */
const filteredResources = computed(() => {
  let rows = filterByName(resources.value)
  if (selectedGroup.value) {
    rows = rows.filter(r => r.groupName === selectedGroup.value)
  }
  if (selectedKind.value) {
    rows = rows.filter(r => parseCrdNames(r.names).kind === selectedKind.value)
  }
  return rows
})

// ── Columns ─────────────────────────────────────────────────────────────────
const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'group', key: 'groupName', label: 'API Group', sortable: true },
  { id: 'kind', key: 'kind', label: 'Kind', sortable: true },
  { id: 'scope', key: 'scope', label: 'Scope', sortable: true },
  { id: 'age', key: 'k8sCreatedAt', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchData = async () => {
  if (!selectedCluster.value || !hasPermission('view')) return

  loading.value = true
  try {
    const result = await k8s.fetchResources<CustomResourceDefinition>('CustomResourceDefinition', undefined, includeDeleted.value)
    resources.value = result
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch CustomResourceDefinitions',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

watch([selectedCluster, includeDeleted], () => {
  fetchData()
}, { immediate: true })

// Reload data when permissions are loaded
watch(permissionsLoading, (loading) => {
  if (!loading && hasPermission('view')) {
    fetchData()
  }
})
</script>

<template>
  <UDashboardPanel id="customresourcedefinitions">
    <template #header>
      <UDashboardNavbar title="CRDs">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton icon="i-lucide-refresh-cw" color="neutral" variant="ghost" square :loading="loading" @click="fetchData" />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar v-if="hasPermission('view')">
        <template #left>
          <USelectMenu
            v-model="selectedGroup"
            :items="groupOptions"
            value-key="value"
            label-key="label"
            placeholder="API Group…"
            class="w-56"
          />
          <USelectMenu
            v-model="selectedKind"
            :items="kindOptions"
            value-key="value"
            label-key="label"
            placeholder="Kind…"
            class="w-48 ml-2"
          />
          <div class="border-l border-gray-200 dark:border-gray-700 mx-3 h-6" />
          <UCheckbox v-model="includeDeleted" label="Include Deleted" color="neutral" class="mr-4" />
          <UBadge color="neutral" variant="subtle">{{ filteredResources.length }} / {{ resources.length }} CRDs</UBadge>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <template v-if="permissionsLoading">
        <div class="flex items-center justify-center h-full">
          <ULoader />
        </div>
      </template>
      <template v-else-if="!hasPermission('view')">
        <div class="flex flex-col items-center justify-center h-full space-y-4">
          <UIcon name="i-lucide-shield-off" class="w-12 h-12 text-gray-400" />
          <p class="text-gray-500">You do not have permission to view CRDs.</p>
        </div>
      </template>
      <template v-else>
        <LegacyTable :rows="filteredResources" :columns="columns" :loading="loading" class="w-full">
          <template #group-data="{ row }">
            <UBadge color="primary" variant="subtle" size="sm">{{ row.groupName || '-' }}</UBadge>
          </template>
          <template #kind-data="{ row }">
            <span class="text-sm font-medium">{{ parseCrdNames(row.names).kind || '-' }}</span>
          </template>
          <template #scope-data="{ row }">
            <UBadge :color="row.scope === 'Namespaced' ? 'info' : 'warning'" variant="subtle" size="sm">
              {{ row.scope || '-' }}
            </UBadge>
          </template>
          <template #age-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ useTimeAgo(row.k8sCreatedAt).value }}</span>
          </template>
          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="CustomResourceDefinition" @refresh="fetchData" />
          </template>
        </LegacyTable>
      </template>
    </template>
  </UDashboardPanel>
</template>
