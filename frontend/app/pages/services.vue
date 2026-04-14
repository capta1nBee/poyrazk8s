<script setup lang="ts">
import type { Service } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('Service')

const services = ref<Service[]>([])
const filteredServices = computed(() => filterByName(services.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'type', key: 'type', label: 'Type', sortable: true },
  { id: 'clusterIP', key: 'clusterIP', label: 'Cluster IP', sortable: true },
  { id: 'externalIP', key: 'externalIP', label: 'External IP', sortable: true },
  { id: 'ports', key: 'ports', label: 'Ports', sortable: false },
  { id: 'k8sCreatedAt', key: 'k8sCreatedAt', label: 'Created At', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchServices = async () => {
  if (!selectedCluster.value) {
    return
  }
  if (permissionsLoading.value) {
    return
  }
  if (!hasPermission('view')) {
    return
  }
  
  loading.value = true
  try {
    const result = await k8s.fetchServices(undefined, includeDeleted.value)
    services.value = result || []
  } catch (error: any) {
    console.error('Fetch services error:', error)
    toast.add({
      title: 'Failed to fetch services',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const getTypeColor = (type: string) => {
  if (!type) return 'gray'
  const typeMap: Record<string, string> = {
    ClusterIP: 'blue',
    NodePort: 'green',
    LoadBalancer: 'purple',
    ExternalName: 'orange'
  }
  return typeMap[type] || 'gray'
}

const formatDate = (timestamp: string) => {
  if (!timestamp) return '-'
  try {
    const date = new Date(timestamp)
    if (isNaN(date.getTime())) return timestamp
    return date.toLocaleString()
  } catch {
    return timestamp
  }
}

const formatPorts = (ports: string | any[]) => {
  if (!ports) return '-'
  if (typeof ports === 'string') {
    try {
      const parsed = JSON.parse(ports)
      if (Array.isArray(parsed)) {
        return parsed.map((p: any) => {
          if (p.nodePort) return `${p.port}:${p.nodePort}/${p.protocol || 'TCP'}`
          return `${p.port}/${p.protocol || 'TCP'}`
        }).join(', ')
      }
    } catch {
      return ports
    }
  }
  if (Array.isArray(ports)) {
    return ports.map((p: any) => {
      if (p.nodePort) return `${p.port}:${p.nodePort}/${p.protocol || 'TCP'}`
      return `${p.port}/${p.protocol || 'TCP'}`
    }).join(', ')
  }
  return '-'
}

watch([selectedCluster, selectedNamespace, includeDeleted, permissionsLoading], () => {
  fetchServices()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="services">
    <template #header>
      <UDashboardNavbar title="Services">
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
            @click="fetchServices"
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
            {{ services.length }} services
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
          :rows="filteredServices"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #type-data="{ row }">
            <UBadge :color="getTypeColor(row.type)" variant="subtle">
              {{ row.type }}
            </UBadge>
          </template>

          <template #name-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }">
              {{ row.name }}
            </span>
          </template>

          <template #externalIP-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }">
              {{ row.externalIP || '-' }}
            </span>
          </template>

          <template #ports-data="{ row }">
            <span class="text-sm" :class="{ 'opacity-50': row.isDeleted }">
              {{ formatPorts(row.ports) }}
            </span>
          </template>

          <template #k8sCreatedAt-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400" :class="{ 'opacity-50': row.isDeleted }">
              {{ formatDate(row.k8sCreatedAt) }}
            </span>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="Service" @refresh="fetchServices" />
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Services in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>

