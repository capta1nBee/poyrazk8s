<script setup lang="ts">
import type { Secret } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('Secret')

const secrets = ref<Secret[]>([])
const filteredSecrets = computed(() => filterByName(secrets.value))
const loading = ref(false)
const includeDeleted = ref(false)
const selectedDataModal = ref<{ name: string; namespace: string } | null>(null)
const isDataModalOpen = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'type', key: 'type', label: 'Type', sortable: true },
  { id: 'dataKeys', key: 'dataKeys', label: 'Data Keys', sortable: false },
  { id: 'age', key: 'age', label: 'Age', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchSecrets = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    const result = await k8s.fetchSecrets(undefined, includeDeleted.value)
    secrets.value = result.map((secret: any) => ({
      ...secret,
      dataKeys: secret.keys ? secret.keys.join(', ') : (secret.data ? Object.keys(secret.data).join(', ') : '-')
    }))
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch Secrets',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const showData = (secret: Secret) => {
  selectedDataModal.value = { name: secret.name, namespace: secret.namespace }
  isDataModalOpen.value = true
}

const getSecretTypeColor = (type: string) => {
  const typeMap: Record<string, string> = {
    'Opaque': 'blue',
    'kubernetes.io/service-account-token': 'purple',
    'kubernetes.io/dockercfg': 'orange',
    'kubernetes.io/dockerconfigjson': 'orange',
    'kubernetes.io/basic-auth': 'yellow',
    'kubernetes.io/ssh-auth': 'yellow',
    'kubernetes.io/tls': 'green',
    'bootstrap.kubernetes.io/token': 'pink'
  }
  return typeMap[type] || 'gray'
}

watch([selectedCluster, selectedNamespace, includeDeleted, permissionsLoading], () => {
  fetchSecrets()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="secrets">
    <template #header>
      <UDashboardNavbar title="Secrets">
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
            @click="fetchSecrets"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <NamespaceSelector />
          <div class="border-l border-gray-200 dark:border-gray-700 mx-2" />
          <UCheckbox 
            v-model="includeDeleted" 
            label="Include Deleted" 
            color="neutral"
            class="mr-4"
          />
          <UBadge color="neutral" variant="subtle" v-if="hasPermission('view')">
            {{ secrets.length }} secrets
          </UBadge>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="permissionsLoading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>

      <template v-else-if="hasPermission('view')">
        <LegacyTable
          :rows="filteredSecrets"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #type-data="{ row }">
            <UBadge :color="getSecretTypeColor(row.type)" variant="subtle">
              {{ row.type }}
            </UBadge>
          </template>

          <template #dataKeys-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ row.dataKeys || '-' }}</span>
          </template>

          <template #age-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">{{ row.age || '-' }}</span>
          </template>

          <template #actions-data="{ row }">
            <div class="flex items-center gap-2">
              <UButton 
                icon="i-lucide-eye" 
                size="xs" 
                variant="ghost" 
                label="Show Data"
                @click="showData(row)"
              />
              <ResourceActionMenu :resource="row" kind="Secret" @refresh="fetchSecrets" />
            </div>
          </template>
        </LegacyTable>

        <SecretDataModal
          v-if="selectedDataModal"
          v-model:open="isDataModalOpen"
          :secret-name="selectedDataModal.name"
          :namespace="selectedDataModal.namespace"
        />
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Secrets in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>

