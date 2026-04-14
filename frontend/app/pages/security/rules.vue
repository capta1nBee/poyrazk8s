<script setup lang="ts">
import type { FormSubmitEvent } from '#ui/types'

interface SecurityRule {
  id: number
  name: string
  description: string
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  enabled: boolean
  ruleType: string
  createdBy: string
  updatedAt: string
}

const clusterStore = useClusterStore()
const { $api } = useNuxtApp()
const toast = useToast()
const { hasPermission, loading: permissionsLoading } = usePagePermissions('SecurityRule')

const rules = ref<SecurityRule[]>([])
const loading = ref(false)
const selectedRule = ref<SecurityRule | null>(null)
const isCreateModalOpen = ref(false)
const isEditModalOpen = ref(false)
const isDeleteConfirmOpen = ref(false)
const isDeleteLoading = ref(false)
const searchTerm = ref('')
const filterPriority = ref<string | null>(null)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const columns = [
  { id: 'name', key: 'name', label: 'Rule Name', sortable: true },
  { id: 'description', key: 'description', label: 'Description' },
  { id: 'priority', key: 'priority', label: 'Priority', sortable: true},
  { id: 'ruleType', key: 'ruleType', label: 'Type' },
  { id: 'enabled', key: 'enabled', label: 'Status' },
  { id: 'createdBy', key: 'createdBy', label: 'Created By' },
  { id: 'updatedAt', key: 'updatedAt', label: 'Updated' },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

const priorityBadgeColor = (priority: string) => {
  const colors: Record<string, string> = {
    'CRITICAL': 'red',
    'HIGH': 'orange',
    'MEDIUM': 'yellow',
    'LOW': 'green'
  }
  return colors[priority] || 'gray'
}

const fetchRules = async () => {
  if (!selectedCluster.value) return
  if (permissionsLoading.value) return
  if (!hasPermission('view')) return

  loading.value = true
  try {
    const params: any = {
      clusterUid: selectedCluster.value.uid
    }

    if (searchTerm.value) {
      params.name = searchTerm.value
    }
    if (filterPriority.value) {
      params.priority = filterPriority.value
    }

    const response = await $api.get<any>('/security/rules', {
      params
    })

    rules.value = Array.isArray(response.data) ? response.data : response.data.content || []
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch rules',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const handleCreateRule = async (ruleData: any) => {
  try {
    await $api.post('/security/rules', {
      ...ruleData,
      clusterUid: selectedCluster.value?.uid
    })

    toast.add({
      title: 'Success',
      description: 'Rule created successfully',
      color: 'green'
    })

    isCreateModalOpen.value = false
    fetchRules()
  } catch (error: any) {
    toast.add({
      title: 'Failed to create rule',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  }
}

const handleEditRule = async (ruleData: any) => {
  if (!selectedRule.value) return

  try {
    await $api.put(`/security/rules/${selectedRule.value.id}`, ruleData)

    toast.add({
      title: 'Success',
      description: 'Rule updated successfully',
      color: 'green'
    })

    isEditModalOpen.value = false
    selectedRule.value = null
    fetchRules()
  } catch (error: any) {
    toast.add({
      title: 'Failed to update rule',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  }
}

const toggleRule = async (rule: SecurityRule) => {
  try {
    await $api.patch(`/security/rules/${rule.id}/toggle`)

    toast.add({
      title: 'Success',
      description: `Rule ${rule.enabled ? 'disabled' : 'enabled'}`,
      color: 'green'
    })

    fetchRules()
  } catch (error: any) {
    toast.add({
      title: 'Failed to toggle rule',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  }
}

const deleteRule = async () => {
  if (!selectedRule.value) return

  isDeleteLoading.value = true
  try {
    await $api.delete(`/security/rules/${selectedRule.value.id}`)

    toast.add({
      title: 'Success',
      description: 'Rule deleted successfully',
      color: 'green'
    })

    isDeleteConfirmOpen.value = false
    selectedRule.value = null
    fetchRules()
  } catch (error: any) {
    toast.add({
      title: 'Failed to delete rule',
      description: error.response?.data?.message || error.message,
      color: 'red'
    })
  } finally {
    isDeleteLoading.value = false
  }
}

watch([selectedCluster, permissionsLoading], () => {
  fetchRules()
}, { immediate: true })
</script>

<template>
  <UDashboardPanel id="security-rules">
    <template #header>
      <UDashboardNavbar title="Security Rules">
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
            @click="fetchRules"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <div class="flex items-center gap-2">
            <NamespaceSelector for-page />
            <div class="border-l border-gray-200 dark:border-gray-700 mx-1 h-5" />
            <UInput
              v-model="searchTerm"
              placeholder="Search rules..."
              icon="i-lucide-search"
              size="sm"
              :ui="{ base: 'min-w-[200px]' }"
              @update:model-value="fetchRules"
            />
            <div class="border-l border-gray-200 dark:border-gray-700 mx-2 h-6" />
            <UDropdownMenu
              :items="[['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map(p => ({
                label: p,
                icon: filterPriority === p ? 'i-lucide-check' : undefined,
                onSelect: () => { filterPriority = p; fetchRules() }
              })), [{ label: 'Clear', icon: 'i-lucide-x', onSelect: () => { filterPriority = null; fetchRules() } }]]"
              :popper="{ placement: 'bottom-start' }"
            >
              <UButton
                :label="filterPriority || 'All Priorities'"
                icon="i-lucide-filter"
                trailing-icon="i-lucide-chevron-down"
                color="neutral"
                variant="ghost"
                size="sm"
              />
            </UDropdownMenu>
            <UBadge v-if="hasPermission('view')" color="neutral" variant="subtle">
              {{ rules.length }} rules
            </UBadge>
          </div>
        </template>
        <template #right>
          <UButton
            v-if="hasPermission('create')"
            icon="i-lucide-plus"
            label="Create"
            @click="isCreateModalOpen = true"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="permissionsLoading" class="flex items-center justify-center h-64">
        <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
      </div>

      <template v-else-if="hasPermission('view')">
        <LegacyTable
          :rows="rules"
          :columns="columns"
          :loading="loading"
          class="w-full"
        >
          <template #priority-data="{ row }">
            <UBadge :color="priorityBadgeColor(row.priority)" variant="subtle" size="sm">
              {{ row.priority }}
            </UBadge>
          </template>

          <template #enabled-data="{ row }">
            <div class="flex items-center gap-2">
              <span class="relative flex h-2.5 w-2.5">
                <span v-if="row.enabled" class="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                <span class="relative inline-flex rounded-full h-2.5 w-2.5" :class="row.enabled ? 'bg-green-500' : 'bg-gray-400'"></span>
              </span>
              <span class="text-sm font-medium" :class="row.enabled ? 'text-green-700 dark:text-green-400' : 'text-gray-500 dark:text-gray-400'">
                {{ row.enabled ? 'Active' : 'Disabled' }}
              </span>
            </div>
          </template>

          <template #updatedAt-data="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-400">
              {{ row.updatedAt ? new Date(row.updatedAt).toLocaleDateString() + ' ' + new Date(row.updatedAt).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) : 'N/A' }}
            </span>
          </template>

          <template #actions-data="{ row }">
            <div class="flex items-center gap-1 justify-end">
              <UTooltip :text="row.enabled ? 'Disable' : 'Enable'" v-if="hasPermission('update')">
                <UButton
                  :icon="row.enabled ? 'i-lucide-power-off' : 'i-lucide-power'"
                  :color="row.enabled ? 'orange' : 'green'"
                  variant="ghost"
                  size="xs"
                  @click="toggleRule(row)"
                />
              </UTooltip>

              <UTooltip text="Edit" v-if="hasPermission('update')">
                <UButton
                  icon="i-lucide-edit"
                  color="blue"
                  variant="ghost"
                  size="xs"
                  @click="selectedRule = row; isEditModalOpen = true"
                />
              </UTooltip>

              <UTooltip text="Delete" v-if="hasPermission('delete')">
                <UButton
                  icon="i-lucide-trash-2"
                  color="red"
                  variant="ghost"
                  size="xs"
                  @click="selectedRule = row; isDeleteConfirmOpen = true"
                />
              </UTooltip>
            </div>
          </template>
        </LegacyTable>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-center">
        <UIcon name="i-lucide-shield-alert" class="w-12 h-12 text-red-500 mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-white">Access Denied</h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          You do not have permission to view Security Rules.
        </p>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Create Rule Modal -->
  <CreateSecurityRuleModal
    v-model="isCreateModalOpen"
    @create="handleCreateRule"
  />

  <!-- Edit Rule Modal -->
  <EditSecurityRuleModal
    v-if="selectedRule"
    v-model="isEditModalOpen"
    :rule="selectedRule"
    @update="handleEditRule"
  />

  <!-- Delete Confirmation -->
  <UModal 
    v-model:open="isDeleteConfirmOpen"
    title="Delete Security Rule"
    :description="`Are you sure you want to delete the rule '${selectedRule?.name}'?`"
  >
    <template #body>
      <div class="space-y-4">
        <div class="flex items-center gap-3 text-red-600 dark:text-red-400">
          <UIcon name="i-lucide-alert-triangle" class="w-6 h-6" />
          <p class="font-medium">This action cannot be undone.</p>
        </div>
        <p class="text-sm text-gray-600 dark:text-gray-400">
          This rule will no longer be applied to the cluster.
        </p>
      </div>
      <div class="flex justify-end gap-2 mt-4">
        <UButton label="Cancel" color="neutral" variant="ghost" @click="isDeleteConfirmOpen = false" />
        <UButton
          label="Delete"
          color="error"
          icon="i-lucide-trash-2"
          :loading="isDeleteLoading"
          @click="deleteRule"
        />
      </div>
    </template>
  </UModal>
</template>
