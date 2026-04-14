<script setup lang="ts">
import type { Namespace } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const { $api } = useNuxtApp()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('Namespace')

const namespaces = ref<Namespace[]>([])
const filteredNamespaces = computed(() => filterByName(namespaces.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'status', key: 'status', label: 'Status', sortable: true },
  { id: 'age', key: 'age', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchNamespaces = async () => {
  if (!selectedCluster.value) return

  loading.value = true
  try {
    const response = await $api.get<Namespace[]>(`/k8s/${selectedCluster.value.uid}/namespaces`, {
      params: { includeDeleted: includeDeleted.value }
    })
    namespaces.value = response.data
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch namespaces',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const selectNamespace = (namespace: Namespace) => {
  clusterStore.selectNamespace(namespace.name)
  toast.add({
    title: 'Namespace selected',
    description: `Switched to namespace: ${namespace.name}`,
    color: 'green'
  })
}

const getStatusColor = (status: string) => {
  return status === 'Active' ? 'green' : 'red'
}

watch([selectedCluster, includeDeleted, permissionsLoading], () => {
  fetchNamespaces()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="namespaces">
    <template #header>
      <UDashboardNavbar title="Namespaces">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="ghost"
            square
            :loading="loading"
            @click="fetchNamespaces"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UCheckbox 
            v-model="includeDeleted" 
            label="Include Deleted" 
            color="neutral"
            class="mr-4"
          />
          <UBadge color="neutral" variant="subtle">
            {{ namespaces.length }} namespaces
          </UBadge>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
	      <LegacyTable
        :rows="filteredNamespaces"
        :columns="columns"
        :loading="loading"
        class="w-full"
      >
        <template #status-data="{ row }">
          <UBadge :color="getStatusColor(row.status)" variant="subtle">
            {{ row.status }}
          </UBadge>
        </template>

        <template #actions-data="{ row }">
          <div class="flex items-center gap-2">
            <UButton
              icon="i-lucide-check"
              size="xs"
              color="neutral"
              variant="ghost"
              title="Select Namespace"
              @click="selectNamespace(row)"
            />
            <ResourceActionMenu :resource="row" kind="Namespace" @refresh="fetchNamespaces" />
          </div>
        </template>
	      </LegacyTable>
    </template>
  </UDashboardPanel>
</template>

