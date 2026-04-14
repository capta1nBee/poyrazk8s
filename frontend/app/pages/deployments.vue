<script setup lang="ts">
import type { Deployment } from '~/types/kubernetes'

const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const { hasPermission, loading: permissionsLoading, filterByName } = usePagePermissions('Deployment')

const deployments = ref<Deployment[]>([])
const filteredDeployments = computed(() => filterByName(deployments.value))
const loading = ref(false)
const includeDeleted = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Name', sortable: true },
  { id: 'ownerInfo', key: 'ownerInfo', label: 'Owner', sortable: false },
  { id: 'namespace', key: 'namespace', label: 'Namespace', sortable: true },
  { id: 'replicasDesired', key: 'replicasDesired', label: 'Desired', sortable: true },
  { id: 'replicasReady', key: 'replicasReady', label: 'Ready', sortable: true },
  { id: 'replicasAvailable', key: 'replicasAvailable', label: 'Available', sortable: true },
  { id: 'strategy', key: 'strategy', label: 'Strategy', sortable: true },
  { id: 'k8sCreatedAt', key: 'k8sCreatedAt', label: 'Created At', sortable: true },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const fetchDeployments = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    const data = await k8s.fetchDeployments(undefined, includeDeleted.value)
    deployments.value = data.map(d => {
      let ownerInfo = null
      if (d.labels) {
        try {
          const labels = typeof d.labels === 'string' ? JSON.parse(d.labels) : d.labels
          if (labels.developer || labels.team) {
            ownerInfo = {
              developer: labels.developer,
              developer_mail: labels.developer_mail,
              team: labels.team,
              team_mail: labels.team_mail
            }
          }
        } catch (e) {
          console.error('Failed to parse labels', e)
        }
      }
      return { ...d, ownerInfo }
    })
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch deployments',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

// Actions handled by ResourceActionMenu

const formatDate = (timestamp: string) => {
  if (!timestamp) return '-'
  try {
    const date = new Date(timestamp)
    return date.toLocaleString()
  } catch {
    return timestamp
  }
}

watch([selectedCluster, selectedNamespace, includeDeleted, permissionsLoading], () => {
  fetchDeployments()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="deployments">
    <template #header>
      <UDashboardNavbar title="Deployments">
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
            @click="fetchDeployments"
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
            {{ deployments.length }} deployments
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
          :rows="filteredDeployments"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #name-data="{ row }">
            <span :class="{ 'opacity-50': row.isDeleted }" class="font-medium">
              {{ row.name }}
            </span>
          </template>

          <template #ownerInfo-data="{ row }">
            <div v-if="row.ownerInfo" class="flex flex-col gap-1 py-1 min-w-[180px]">
              <div v-if="row.ownerInfo.developer" class="flex items-center gap-1.5 group">
                <UIcon name="i-lucide-user" class="w-3.5 h-3.5 text-primary-500 shrink-0" />
                <div class="flex flex-col">
                  <span class="text-xs font-semibold text-gray-900 dark:text-gray-100 leading-tight">
                    {{ row.ownerInfo.developer }}
                  </span>
                  <span v-if="row.ownerInfo.developer_mail" class="text-[10px] text-gray-500 dark:text-gray-400 truncate max-w-[150px]" :title="row.ownerInfo.developer_mail">
                    {{ row.ownerInfo.developer_mail }}
                  </span>
                </div>
              </div>
              <div v-if="row.ownerInfo.team" class="flex items-center gap-1.5 border-t border-gray-100 dark:border-gray-800 pt-1 mt-0.5">
                <UIcon name="i-lucide-users" class="w-3.5 h-3.5 text-blue-500 shrink-0" />
                <div class="flex flex-col">
                  <span class="text-[11px] font-medium text-gray-700 dark:text-gray-300 leading-tight truncate max-w-[150px]" :title="row.ownerInfo.team">
                    {{ row.ownerInfo.team }}
                  </span>
                  <span v-if="row.ownerInfo.team_mail" class="text-[9px] text-gray-500 dark:text-gray-400 truncate max-w-[150px]" :title="row.ownerInfo.team_mail">
                    {{ row.ownerInfo.team_mail }}
                  </span>
                </div>
              </div>
            </div>
            <span v-else class="text-xs text-gray-400 italic">No owner labels</span>
          </template>

          <template #strategy-data="{ row }">
            <span class="text-sm" :class="{ 'opacity-50': row.isDeleted }">
              {{ row.strategy || '-' }}
            </span>
          </template>

          <template #k8sCreatedAt-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400" :class="{ 'opacity-50': row.isDeleted }">
              {{ formatDate(row.k8sCreatedAt) }}
            </span>
          </template>

          <template #actions-data="{ row }">
            <ResourceActionMenu :resource="row" kind="Deployment" @refresh="fetchDeployments" />
          </template>
	      </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Deployments in this namespace.
        </p>
      </div>
    </template>
  </UDashboardPanel>
</template>

