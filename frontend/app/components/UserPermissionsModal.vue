<script setup lang="ts">
import type { UserResponse, AssignPermissionsRequest } from '~/composables/useUsers'

const props = defineProps<{
  modelValue: boolean
  user: UserResponse | null
  permissions: AssignPermissionsRequest
  loading: boolean
  availableRoles?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'save': []
}>()

const { $api } = useNuxtApp()
const clusterStore = useClusterStore()

const roles = computed(() => props.availableRoles || ['ADMIN', 'OPERATOR', 'VIEWER'])

// Available pages fetched from API
const availablePages = ref<string[]>([])
const loadingPages = ref(false)

// Fetch available pages from backend
const fetchAvailablePages = async () => {
  loadingPages.value = true
  try {
    const response = await $api.get<string[]>('/authorization/pages')
    availablePages.value = response.data || []
  } catch (error) {
    console.error('Failed to fetch available pages:', error)
    // Fallback to basic pages if API fails
    availablePages.value = ['pods', 'deployments', 'services', 'configmaps', 'secrets', 'namespaces', 'nodes']
  } finally {
    loadingPages.value = false
  }
}

// Dropdown items for clusters
const getClusterItems = (assignment: any) => computed(() => [
  clusterStore.clusters.map(cluster => ({
    label: cluster.name,
    click: () => { assignment.cluster = cluster.name }
  }))
])

// Dropdown items for resource kinds (fetched from API)
const resourceKinds = ref<string[]>([])
const loadingResourceKinds = ref(false)

// Fetch resource kinds from backend
const fetchResourceKinds = async () => {
  loadingResourceKinds.value = true
  try {
    const response = await $api.get<string[]>('/authorization/resource-kinds')
    resourceKinds.value = response.data || []
  } catch (error) {
    console.error('Failed to fetch resource kinds:', error)
    // Fallback to basic resource kinds if API fails
    resourceKinds.value = [
      'Pod', 'Deployment', 'StatefulSet', 'DaemonSet', 'Service', 
      'ConfigMap', 'Secret', 'Namespace', 'Node'
    ]
  } finally {
    loadingResourceKinds.value = false
  }
}

const getResourceKindItems = (resource: any) => computed(() => [
  resourceKinds.value.map(kind => ({
    label: kind,
    click: () => { resource.kind = kind }
  }))
])

// Dropdown items for roles
const roleItems = computed(() => [
  roles.value.map(role => ({
    label: role,
    icon: permissions.value.roles.includes(role) ? 'i-lucide-check' : undefined,
    click: () => {
      const index = permissions.value.roles.indexOf(role)
      if (index > -1) {
        permissions.value.roles.splice(index, 1)
      } else {
        permissions.value.roles.push(role)
      }
    }
  }))
])

// Watch for modal open
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    // Fetch pages when modal opens if not already loaded
    if (availablePages.value.length === 0) {
      fetchAvailablePages()
    }
  }
})

// Fetch clusters, pages, and resource kinds on mount
onMounted(async () => {
  const promises: Promise<void>[] = []
  
  if (clusterStore.clusters.length === 0) {
    promises.push(clusterStore.fetchClusters().catch(error => {
      console.error('Failed to fetch clusters:', error)
    }))
  }
  
  if (availablePages.value.length === 0) {
    promises.push(fetchAvailablePages())
  }
  
  if (resourceKinds.value.length === 0) {
    promises.push(fetchResourceKinds())
  }
  
  await Promise.all(promises)
})

const resourceActions = ['view', 'refresh', 'view-yaml', 'edit-yaml', 'create', 'update', 'delete', 'patch', 'exec', 'logs', 'metrics', 'restart', 'scale']

const addAssignment = () => {
  props.permissions.assignments.push({
    cluster: '',
    namespace: '',
    resources: []
  })
}

const removeAssignment = (index: number) => {
  props.permissions.assignments.splice(index, 1)
}

const addResource = (assignmentIndex: number) => {
  props.permissions.assignments[assignmentIndex].resources.push({
    kind: 'Pod',
    namePattern: '*',
    actions: []
  })
}

const removeResource = (assignmentIndex: number, resourceIndex: number) => {
  props.permissions.assignments[assignmentIndex].resources.splice(resourceIndex, 1)
}

const close = () => {
  emit('update:modelValue', false)
}

const save = () => {
  emit('save')
}
</script>

<template>
  <UModal
    :model-value="modelValue"
    @update:model-value="close"
    :ui="{ width: 'max-w-4xl' }"
    :title="`Manage Permissions - ${user?.username}`"
    description="Configure UI permissions and resource access for this user"
  >
    <template #body>
      <div class="space-y-6">
        <!-- UI Permissions -->
        <div class="space-y-4">
          <div class="border-b border-gray-200 dark:border-gray-800 pb-2 flex justify-between items-center">
            <div>
              <h4 class="text-base font-semibold">UI Permissions</h4>
              <p class="text-xs text-gray-500 dark:text-gray-400">Select pages the user can access</p>
            </div>
            <div class="flex gap-2">
              <UButton
                label="Select All"
                size="xs"
                variant="ghost"
                @click="permissions.uiPermissions.pages = [...availablePages]"
                :disabled="loadingPages || availablePages.length === 0"
              />
              <UButton
                label="Deselect All"
                size="xs"
                variant="ghost"
                color="red"
                @click="permissions.uiPermissions.pages = []"
                :disabled="loadingPages || permissions.uiPermissions.pages.length === 0"
              />
            </div>
          </div>

          <div v-if="loadingPages" class="flex items-center justify-center py-4">
            <UIcon name="i-lucide-loader-2" class="animate-spin text-gray-400 mr-2" />
            <span class="text-sm text-gray-500">Loading pages...</span>
          </div>
          <div v-else-if="availablePages.length === 0" class="text-center py-4 text-gray-500">
            <p class="text-sm">No pages available</p>
          </div>
          <div v-else class="grid grid-cols-2 sm:grid-cols-3 gap-3 max-h-64 overflow-y-auto p-1">
            <div v-for="page in availablePages" :key="page" class="flex items-center gap-2">
              <UCheckbox
                :model-value="permissions.uiPermissions.pages.includes(page)"
                @update:model-value="(val) => {
                  if (val) {
                    if (!permissions.uiPermissions.pages.includes(page)) {
                      permissions.uiPermissions.pages.push(page)
                    }
                  } else {
                    const index = permissions.uiPermissions.pages.indexOf(page)
                    if (index > -1) permissions.uiPermissions.pages.splice(index, 1)
                  }
                }"
              />
              <span class="text-sm capitalize">{{ page.replace(/-/g, ' ') }}</span>
            </div>
          </div>
        </div>

        <!-- Resource Permissions -->
        <div class="space-y-4">
          <div class="border-b border-gray-200 dark:border-gray-800 pb-2 flex justify-between items-center">
            <div>
              <h4 class="text-base font-semibold">Resource Permissions</h4>
              <p class="text-xs text-gray-500 dark:text-gray-400">Define cluster and resource access</p>
            </div>
            <UButton
              label="Add Assignment"
              icon="i-lucide-plus"
              size="xs"
              @click="addAssignment"
            />
          </div>

          <div v-for="(assignment, aIndex) in permissions.assignments" :key="aIndex" class="border border-gray-200 dark:border-gray-800 rounded-lg p-4 space-y-4">
            <div class="flex justify-between items-start">
              <h5 class="text-sm font-semibold">Assignment #{{ aIndex + 1 }}</h5>
              <UButton
                icon="i-lucide-trash-2"
                size="xs"
                color="red"
                variant="ghost"
                @click="removeAssignment(aIndex)"
              />
            </div>

            <div class="grid grid-cols-2 gap-4">
              <UFormField label="Cluster" class="w-full">
                <UDropdownMenu
                  :items="getClusterItems(assignment).value"
                  :content="{ align: 'start' }"
                  :ui="{ content: 'w-(--reka-dropdown-menu-trigger-width)' }"
                >
                  <UButton
                    :label="assignment.cluster || 'Select cluster'"
                    trailing-icon="i-lucide-chevron-down"
                    color="neutral"
                    variant="outline"
                    block
                    class="justify-start w-full"
                    size="sm"
                  />
                </UDropdownMenu>
              </UFormField>

              <UFormField label="Namespace" class="w-full">
                <UInput v-model="assignment.namespace" size="sm" class="w-full" placeholder="e.g., default or *" />
              </UFormField>
            </div>

            <div class="space-y-3">
              <div class="flex justify-between items-center">
                <span class="text-sm font-medium">Resources</span>
                <UButton
                  label="Add Resource"
                  icon="i-lucide-plus"
                  size="xs"
                  variant="ghost"
                  @click="addResource(aIndex)"
                />
              </div>

              <div v-for="(resource, rIndex) in assignment.resources" :key="rIndex" class="bg-gray-50 dark:bg-gray-900/50 rounded p-3 space-y-3">
                <div class="flex justify-between items-start">
                  <span class="text-xs font-medium text-gray-600 dark:text-gray-400">Resource #{{ rIndex + 1 }}</span>
                  <UButton
                    icon="i-lucide-x"
                    size="xs"
                    color="red"
                    variant="ghost"
                    @click="removeResource(aIndex, rIndex)"
                  />
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <UFormField label="Kind" class="w-full">
                    <UDropdownMenu
                      :items="getResourceKindItems(resource).value"
                      :content="{ align: 'start' }"
                      :ui="{ content: 'w-(--reka-dropdown-menu-trigger-width)' }"
                    >
                      <UButton
                        :label="resource.kind || 'Select kind'"
                        trailing-icon="i-lucide-chevron-down"
                        color="neutral"
                        variant="outline"
                        block
                        class="justify-start w-full"
                        size="sm"
                      />
                    </UDropdownMenu>
                  </UFormField>

                  <UFormField label="Name Pattern" class="w-full">
                    <UInput v-model="resource.namePattern" size="sm" class="w-full" placeholder="e.g., *, app-*, *-prod" />
                  </UFormField>
                </div>

                <UFormField label="Actions" class="w-full">
                  <div class="flex flex-wrap gap-2">
                    <div v-for="action in resourceActions" :key="action" class="flex items-center gap-1">
                      <UCheckbox
                        :model-value="resource.actions.includes(action)"
                        @update:model-value="(val) => {
                          if (val) {
                            if (!resource.actions.includes(action)) {
                              resource.actions.push(action)
                            }
                          } else {
                            const index = resource.actions.indexOf(action)
                            if (index > -1) resource.actions.splice(index, 1)
                          }
                        }"
                      />
                      <span class="text-xs">{{ action }}</span>
                    </div>
                  </div>
                </UFormField>
              </div>
            </div>
          </div>

          <div v-if="permissions.assignments.length === 0" class="text-center py-8 text-gray-500 dark:text-gray-400">
            <p class="text-sm">No resource assignments. Click "Add Assignment" to create one.</p>
          </div>
        </div>

        <!-- Policy Roles -->
        <div class="space-y-4">
          <div class="border-b border-gray-200 dark:border-gray-800 pb-2">
            <h4 class="text-base font-semibold">Policy Roles</h4>
            <p class="text-xs text-gray-500 dark:text-gray-400">Assign policy-level roles</p>
          </div>

          <UFormField label="Roles" class="w-full">
            <UDropdownMenu
              :items="roleItems"
              :content="{ align: 'start' }"
              :ui="{ content: 'w-(--reka-dropdown-menu-trigger-width)' }"
            >
              <UButton
                :label="permissions.roles.length > 0 ? permissions.roles.join(', ') : 'Select roles'"
                trailing-icon="i-lucide-chevron-down"
                color="neutral"
                variant="outline"
                block
                class="justify-start w-full"
                size="lg"
              />
            </UDropdownMenu>
          </UFormField>
        </div>
      </div>

      <div class="flex justify-end gap-2 pt-6 border-t border-gray-200 dark:border-gray-800">
        <UButton label="Cancel" color="gray" variant="ghost" @click="close" />
        <UButton label="Save Permissions" @click="save" :loading="loading" />
      </div>
    </template>
  </UModal>
</template>
