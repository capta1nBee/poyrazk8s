<script setup lang="ts">
import type { AuditLog } from '~/types/auditlog'
import { h, resolveComponent } from 'vue'

const { $api } = useNuxtApp()
const toast = useToast()
const clusterStore = useClusterStore()
const { hasPermission, loading: permissionsLoading } = usePagePermissions('AuditLog')

// Resolve components for use in h functions
const UAvatar = resolveComponent('UAvatar')
const UBadge = resolveComponent('UBadge')

const logs = ref<AuditLog[]>([])
const totalElements = ref(0)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)

// Filters
const filters = ref({
  username: '',
  action: '',
  details: ''
})

const fetchLogs = async () => {
  if (!hasPermission('view')) return
  
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: pageSize.value,
      username: filters.value.username || undefined,
      action: filters.value.action || undefined,
      details: filters.value.details || undefined,
      clusterUid: clusterStore.selectedClusterUid || undefined,
      sortBy: 'timestamp',
      direction: 'DESC'
    }
    
    const response = await $api.get('/admin/audit-logs', { params })
    logs.value = response.data.content
    totalElements.value = response.data.totalElements
  } catch (e) {
    toast.add({ title: 'Error', description: 'Failed to fetch audit logs', color: 'error' })
  } finally {
    loading.value = false
  }
}

// Watch filters, pagination and cluster selection
watch([page, pageSize, filters, () => clusterStore.selectedClusterUid], () => {
  if (!permissionsLoading.value) {
    fetchLogs()
  }
}, { deep: true })

// Reload data when permissions are loaded
watch(permissionsLoading, (loading) => {
  if (!loading && hasPermission('view')) {
    fetchLogs()
  }
})

const formatDate = (date: string) => {
  if (!date) return ''
  return new Date(date).toLocaleString('tr-TR')
}

const getActionColor = (action: string) => {
  if (action?.includes('login')) return 'success' as const
  if (action?.includes('logout')) return 'neutral' as const
  if (action?.includes('delete')) return 'error' as const
  if (action?.includes('exec')) return 'warning' as const
  return 'primary' as const
}

const columns = [
  { 
    accessorKey: 'username', 
    header: 'User',
    cell: ({ row }: any) => {
      return h('div', { class: 'flex items-center gap-2' }, [
        h(UAvatar, { alt: row.original.username, size: 'xs' }),
        h('span', { class: 'font-medium text-highlighted' }, row.original.username)
      ])
    }
  },
  { 
    accessorKey: 'timestamp', 
    header: 'Date',
    cell: ({ row }: any) => {
      return h('span', { class: 'text-xs font-mono text-muted' }, formatDate(row.original.timestamp))
    }
  },
  { 
    accessorKey: 'action', 
    header: 'Action',
    cell: ({ row }: any) => {
      const action = row.original.action
      return h(UBadge, { 
        color: getActionColor(action), 
        variant: 'subtle', 
        size: 'xs', 
        class: 'capitalize' 
      }, () => action)
    }
  },
  {
    accessorKey: 'clusterName',
    header: 'Cluster',
    cell: ({ row }: any) => {
      const name = row.original.clusterName
      const uid = row.original.clusterUid
      if (!name && !uid) {
        return h('span', { class: 'text-xs text-muted' }, 'Global')
      }
      return h('div', { class: 'flex flex-col' }, [
        h('span', { class: 'text-xs font-medium' }, name || 'Unknown'),
        h('span', { class: 'text-[10px] text-muted font-mono truncate max-w-[80px]', title: uid }, uid)
      ])
    }
  },
  { 
    accessorKey: 'details', 
    header: 'Details',
    cell: ({ row }: any) => {
      return h('div', { 
        class: 'max-w-md truncate text-xs text-muted', 
        title: row.original.details 
      }, row.original.details)
    }
  }
]
</script>

<template>
  <div v-if="permissionsLoading" class="flex items-center justify-center p-12 h-64">
    <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-primary" />
  </div>

  <div v-else-if="!hasPermission('view')" class="p-8 text-center flex flex-col items-center justify-center h-full">
    <div class="p-6 rounded-full bg-red-50 dark:bg-red-900/10 mb-4">
      <UIcon name="i-lucide-shield-off" class="w-12 h-12 text-red-500" />
    </div>
    <h2 class="text-2xl font-bold tracking-tight">Access Restricted</h2>
    <p class="text-gray-500 max-w-sm mx-auto mt-2">Audit logs are visible only to authorized users.</p>
  </div>

  <div v-else class="p-6 space-y-6 flex flex-col h-full">
    <div class="flex justify-between items-center">
      <div>
        <h1 class="text-2xl font-black uppercase tracking-tight">Audit Logs</h1>
        <p class="text-sm text-muted">Track user actions across the platform</p>
      </div>
      <UButton icon="i-lucide-refresh-cw" label="Refresh" variant="outline" @click="fetchLogs" :loading="loading" />
    </div>

    <!-- Filters -->
    <UCard>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <UFormGroup label="Username">
          <UInput v-model="filters.username" placeholder="Filter by user..." icon="i-lucide-user" />
        </UFormGroup>
        <UFormGroup label="Action">
          <UInput v-model="filters.action" placeholder="Filter by action (login, pod, etc.)" icon="i-lucide-activity" />
        </UFormGroup>
        <UFormGroup label="Details">
          <UInput v-model="filters.details" placeholder="Search in details..." icon="i-lucide-search" />
        </UFormGroup>
      </div>
    </UCard>

    <UCard :ui="{ body: 'p-0 flex flex-col overflow-hidden', root: 'flex flex-col flex-1 min-h-0' }">
      <UTable 
        :data="logs" 
        :columns="columns" 
        :loading="loading"
        class="flex-1 overflow-auto"
      >
        <template #empty-state>
          <div class="flex flex-col items-center justify-center py-12 text-muted">
            <UIcon name="i-lucide-clipboard-list" class="w-12 h-12 mb-2 opacity-20" />
            <p>No audit logs found matching your filters</p>
          </div>
        </template>
      </UTable>

      <!-- Pagination -->
      <div class="px-4 py-3 border-t border-default flex items-center justify-between">
        <div class="text-xs text-muted">
          Showing {{ logs.length }} of {{ totalElements }} logs
        </div>
        <UPagination 
          v-model="page" 
          :items-per-page="pageSize" 
          :total="totalElements"
          size="xs"
        />
      </div>
    </UCard>
  </div>
</template>
