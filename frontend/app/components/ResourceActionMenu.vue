<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import type { DropdownMenuItem } from '@nuxt/ui'

const props = defineProps<{
  resource: any
  kind: string
}>()

const emit = defineEmits(['refresh'])
const k8s = useKubernetes()
const toast = useToast()
const permissions = usePermissions()
const actionMapComp = useActionMap()
const authStore = useAuthStore()
const clusterStore = useClusterStore()
const confirmDialog = useConfirm()

// Permission cache for actions
const actionPermissions = ref<Record<string, boolean>>({})
const loadingPermissions = ref(false)

const isYamlOpen = ref(false)
const yamlMode = ref<'view' | 'edit'>('view')
const isLabelsOpen = ref(false)
const isAnnotationsOpen = ref(false)
const isLogsOpen = ref(false)
const isExecOpen = ref(false)
const isScaleOpen = ref(false)
const isMetricsOpen = ref(false)
const isRolloutHistoryOpen = ref(false)
const isPodsOpen = ref(false)
const isEndpointsOpen = ref(false)
const isEventsOpen = ref(false)
const isGenericActionOpen = ref(false)

const genericActionConfig = ref({
  title: '',
  description: '',
  label: '',
  placeholder: '',
  initialValue: '',
  type: 'text' as 'text' | 'number' | 'select',
  options: [] as { value: string; label: string }[],
  icon: '',
  onSubmit: (val: any) => {}
})

// Labels and annotations state
const labels = ref<Record<string, string>>({})
const annotations = ref<Record<string, string>>({})
const labelsLoading = ref(false)
const annotationsLoading = ref(false)

// Fetch labels from backend when modal opens
watch(isLabelsOpen, async (open) => {
  if (open) {
    labelsLoading.value = true
    try {
      const details = await k8s.getResourceDetails(
        props.kind,
        props.resource.name,
        props.resource.namespace
      )
      labels.value = details.labels || {}
    } catch (error: any) {
      toast.add({
        title: 'Failed to fetch labels',
        description: error.message,
        color: 'error'
      })
      labels.value = {}
    } finally {
      labelsLoading.value = false
    }
  }
})

// Fetch annotations from backend when modal opens
watch(isAnnotationsOpen, async (open) => {
  if (open) {
    annotationsLoading.value = true
    try {
      const details = await k8s.getResourceDetails(
        props.kind,
        props.resource.name,
        props.resource.namespace
      )
      annotations.value = details.annotations || {}
    } catch (error: any) {
      toast.add({
        title: 'Failed to fetch annotations',
        description: error.message,
        color: 'error'
      })
      annotations.value = {}
    } finally {
      annotationsLoading.value = false
    }
  }
})

// Function to load permissions
const loadPermissions = async () => {
  // Wait for resource to be available
  if (!props.resource || !props.resource.name) {
    loadingPermissions.value = true
    return
  }

  if (authStore.user?.isSuperadmin) {
    // Superadmin has all permissions - set all to true
    const actionCodes = getActionCodesForKind(props.kind)
    actionCodes.forEach(code => {
      actionPermissions.value[code] = true
    })
    loadingPermissions.value = false
    return
  }
  
  loadingPermissions.value = true
  try {
    // Define all possible actions for this resource kind
    const actionCodes = getActionCodesForKind(props.kind)
    const perms = await permissions.hasActionPermissions(
      props.kind,
      props.resource.name,
      props.resource.namespace,
      actionCodes
    )
    actionPermissions.value = perms
  } catch (error) {
    console.error('Failed to load action permissions:', error)
    // On error, set all to false for security
    const actionCodes = getActionCodesForKind(props.kind)
    actionCodes.forEach(code => {
      actionPermissions.value[code] = false
    })
  } finally {
    loadingPermissions.value = false
  }
}

// Watch for resource changes and reload permissions
watch(
  () => [props.resource?.name, props.resource?.namespace, props.kind, clusterStore.selectedCluster?.uid],
  () => {
    loadPermissions()
  },
  { immediate: false, deep: true }
)

// Load action permissions on mount
onMounted(async () => {
  // Ensure DB-driven action map is loaded first (cached after first call)
  await actionMapComp.ensureLoaded()
  await loadPermissions()
})

// Get action codes for a resource kind — delegated to DB-driven composable
function getActionCodesForKind(kind: string): string[] {
  return actionMapComp.getActionCodesForKind(kind)
}

// Check if user has permission for an action
function hasPermission(actionCode: string): boolean {
  if (authStore.user?.isSuperadmin) {
    return true
  }
  // Wait for permissions to load before checking
  if (loadingPermissions.value) {
    return false
  }
  return actionPermissions.value[actionCode] === true
}

const items = computed(() => {
  // Don't render actions while permissions are loading
  if (loadingPermissions.value) {
    return []
  }
  
  const actions: DropdownMenuItem[][] = []

  // Global Actions Group
  const globalActions: DropdownMenuItem[] = []
  
  if (hasPermission('view-yaml')) {
    globalActions.push({
      label: 'View YAML',
      icon: 'i-lucide-file-code',
      onSelect: () => {
        yamlMode.value = 'view'
        isYamlOpen.value = true
      }
    })
  }
  
  if (hasPermission('edit-yaml')) {
    globalActions.push({
      label: 'Edit YAML',
      icon: 'i-lucide-edit',
      onSelect: () => {
        yamlMode.value = 'edit'
        isYamlOpen.value = true
      }
    })
  }
  
  if (globalActions.length > 0) {
    actions.push(globalActions)
  }
  
  // Common actions - check permissions for each
  const commonActions: DropdownMenuItem[] = []
  
  if (hasPermission('show-labels')) {
    commonActions.push({
      label: 'Show Labels',
      icon: 'i-lucide-tags',
      onSelect: () => {
        isLabelsOpen.value = true
      }
    })
  }
  
  if (hasPermission('show-annotations')) {
    commonActions.push({
      label: 'Show Annotations',
      icon: 'i-lucide-file-text',
      onSelect: () => {
        isAnnotationsOpen.value = true
      }
    })
  }
  
  if (hasPermission('refresh')) {
    commonActions.push({
      label: 'Refresh',
      icon: 'i-lucide-refresh-cw',
      onSelect: () => emit('refresh')
    })
  }
  
  if (hasPermission('events')) {
    commonActions.push({
      label: 'Events',
      icon: 'i-lucide-list',
      onSelect: () => {
        isEventsOpen.value = true
      }
    })
  }

  if (commonActions.length > 0) {
    actions.push(commonActions)
  }

  // Resource Specific Actions
  const resourceActions: DropdownMenuItem[] = []

  if (props.kind === 'Pod') {
    if (hasPermission('logs')) {
      resourceActions.push({
        label: 'Logs',
        icon: 'i-lucide-scroll-text',
        onSelect: () => {
          isLogsOpen.value = true
        }
      })
    }
    
    if (hasPermission('exec')) {
      resourceActions.push({
        label: 'Exec',
        icon: 'i-lucide-terminal',
        onSelect: () => {
          isExecOpen.value = true
        }
      })
    }

    
    if (hasPermission('metrics')) {
      resourceActions.push({
        label: 'Show Metrics',
        icon: 'i-lucide-activity',
        onSelect: () => {
          isMetricsOpen.value = true
        }
      })
    }
    
    if (hasPermission('restart')) {
      resourceActions.push({
        label: 'Restart',
        icon: 'i-lucide-rotate-cw',
        onSelect: async () => {
          try {
            await k8s.restartPod(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to restart pod', description: e.message, color: 'error' })
          }
        }
      })
    }

    if (hasPermission('force-delete')) {
      resourceActions.push({
        label: 'Force Delete',
        icon: 'i-lucide-trash-2',
        color: 'error',
        onSelect: async () => {
          if (!await confirmDialog.open({
            title: 'Force Delete Pod',
            description: `Are you sure you want to force delete pod ${props.resource.name}?`,
            confirmLabel: 'Force Delete',
            color: 'red'
          })) return
          try {
            await k8s.forceDeletePod(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to force delete pod', description: e.message, color: 'error' })
          }
        }
      })
    }
  }

  if (props.kind === 'Deployment') {
    if (hasPermission('restart')) {
      resourceActions.push({
        label: 'Restart',
        icon: 'i-lucide-rotate-cw',
        onSelect: async () => {
          try {
            await k8s.restartDeployment(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to restart deployment', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('scale')) {
      resourceActions.push({
        label: 'Scale',
        icon: 'i-lucide-scaling',
        onSelect: () => {
          isScaleOpen.value = true
        }
      })
    }
    
    const isPaused = props.resource.paused
    if (hasPermission('pause') && !isPaused) {
      resourceActions.push({
        label: 'Pause',
        icon: 'i-lucide-pause',
        onSelect: async () => {
          try {
            await k8s.pauseDeployment(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to pause deployment', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('resume') && isPaused) {
      resourceActions.push({
        label: 'Resume',
        icon: 'i-lucide-play',
        onSelect: async () => {
          try {
            await k8s.resumeDeployment(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to resume deployment', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('rollback')) {
      resourceActions.push({
        label: 'Rollback',
        icon: 'i-lucide-undo',
        onSelect: async () => {
          if (!await confirmDialog.open({
            title: 'Rollback Deployment',
            description: `Are you sure you want to rollback deployment ${props.resource.name}?`,
            confirmLabel: 'Rollback',
            color: 'orange' // using orange for warning/rollback kind of action
          })) return
          try {
            await k8s.rollbackDeployment(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to rollback deployment', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('view-pods')) {
      resourceActions.push({
        label: 'View Pods',
        icon: 'i-lucide-box',
        onSelect: () => {
          isPodsOpen.value = true
        }
      })
    }
    
    if (hasPermission('view-history')) {
      resourceActions.push({
        label: 'Show Rollout History',
        icon: 'i-lucide-history',
        onSelect: () => {
          isRolloutHistoryOpen.value = true
        }
      })
    }
  }

  if (props.kind === 'StatefulSet') {
    if (hasPermission('restart')) {
      resourceActions.push({
        label: 'Restart',
        icon: 'i-lucide-rotate-cw',
        onSelect: async () => {
          try {
            await k8s.restartStatefulSet(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to restart statefulset', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('scale')) {
      resourceActions.push({
        label: 'Scale',
        icon: 'i-lucide-scaling',
        onSelect: () => {
          isScaleOpen.value = true
        }
      })
    }
    
    if (hasPermission('view-history')) {
      resourceActions.push({
        label: 'Show Rollout History',
        icon: 'i-lucide-history',
        onSelect: () => {
          isRolloutHistoryOpen.value = true
        }
      })
    }
  }

  if (props.kind === 'DaemonSet') {
    if (hasPermission('restart')) {
      resourceActions.push({
        label: 'Restart',
        icon: 'i-lucide-rotate-cw',
        onSelect: async () => {
          try {
            await k8s.restartDaemonSet(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to restart daemonset', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('pause')) {
      resourceActions.push({
        label: 'Pause',
        icon: 'i-lucide-pause',
        onSelect: async () => {
          try {
            await k8s.pauseDaemonSet(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to pause daemonset', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('resume')) {
      resourceActions.push({
        label: 'Resume',
        icon: 'i-lucide-play',
        onSelect: async () => {
          try {
            await k8s.resumeDaemonSet(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to resume daemonset', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('view-history')) {
      resourceActions.push({
        label: 'Show Rollout History',
        icon: 'i-lucide-history',
        onSelect: () => {
          isRolloutHistoryOpen.value = true
        }
      })
    }
  }

  if (props.kind === 'Node') {
    if (hasPermission('cordon')) {
      resourceActions.push({
        label: 'Cordon',
        icon: 'i-lucide-shield-off',
        disabled: props.resource.unschedulable,
        onSelect: async () => {
          try {
            await k8s.cordonNode(props.resource.name)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to cordon node', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('uncordon')) {
      resourceActions.push({
        label: 'Uncordon',
        icon: 'i-lucide-shield-check',
        disabled: !props.resource.unschedulable,
        onSelect: async () => {
          try {
            await k8s.uncordonNode(props.resource.name)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to uncordon node', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('drain')) {
      resourceActions.push({
        label: 'Drain',
        icon: 'i-lucide-arrow-right-from-line',
        color: 'error',
        onSelect: async () => {
          if (!await confirmDialog.open({
            title: 'Drain Node',
            description: `Are you sure you want to drain node ${props.resource.name}?`,
            confirmLabel: 'Drain',
            color: 'red'
          })) return
          try {
            await k8s.drainNode(props.resource.name)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to drain node', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('metrics')) {
      resourceActions.push({
        label: 'Show Metrics',
        icon: 'i-lucide-bar-chart',
        onSelect: () => {
          isMetricsOpen.value = true
        }
      })
    }
  }

  if (props.kind === 'Job') {
    resourceActions.push(
      {
        label: 'Terminate',
        icon: 'i-lucide-x-circle',
        color: 'error',
        onSelect: async () => {
          if (!await confirmDialog.open({
            title: 'Terminate Job',
            description: `Are you sure you want to terminate job ${props.resource.name}?`,
            confirmLabel: 'Terminate',
            color: 'red'
          })) return
          try {
            await k8s.terminateJob(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to terminate job', description: e.message, color: 'error' })
          }
        }
      }
    )
  }

  if (props.kind === 'CronJob') {
    const isSuspended = props.resource.suspend
    
    if (hasPermission('run-now')) {
      resourceActions.push({
        label: 'Run Now',
        icon: 'i-lucide-play',
        onSelect: async () => {
          try {
            await k8s.runCronJob(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to run cronjob', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('suspend') && !isSuspended) {
      resourceActions.push({
        label: 'Suspend',
        icon: 'i-lucide-pause',
        onSelect: async () => {
          try {
            await k8s.suspendCronJob(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to suspend cronjob', description: e.message, color: 'error' })
          }
        }
      })
    }
    
    if (hasPermission('resume') && isSuspended) {
      resourceActions.push({
        label: 'Resume',
        icon: 'i-lucide-play',
        onSelect: async () => {
          try {
            await k8s.resumeCronJob(props.resource.name, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Failed to resume cronjob', description: e.message, color: 'error' })
          }
        }
      })
    }
  }

  // Service Actions
  if (props.kind === 'Service') {
    if (hasPermission('change-type')) {
      resourceActions.push(
        {
          label: 'Change Type',
          icon: 'i-lucide-settings',
          onSelect: () => {
            genericActionConfig.value = {
              title: 'Change Service Type',
              description: `Change the type of service ${props.resource.name}`,
              label: 'Service Type',
              initialValue: props.resource.type,
              type: 'select',
              options: [
                { label: 'ClusterIP', value: 'ClusterIP' },
                { label: 'NodePort', value: 'NodePort' },
                { label: 'LoadBalancer', value: 'LoadBalancer' },
                { label: 'ExternalName', value: 'ExternalName' }
              ],
              icon: 'i-lucide-settings',
              onSubmit: async (newType: string) => {
                try {
                  await k8s.changeServiceType(props.resource.name, newType, props.resource.namespace)
                  emit('refresh')
                } catch (e: any) {
                  toast.add({ title: 'Failed to change service type', description: e.message, color: 'error' })
                }
              }
            }
            isGenericActionOpen.value = true
          }
        }
      )
    }
    
    if (hasPermission('view-endpoints')) {
      resourceActions.push({
        label: 'View Endpoints',
        icon: 'i-lucide-network',
        onSelect: () => {
          isEndpointsOpen.value = true
        }
      })
    }
  }

  // Ingress Actions
  if (props.kind === 'Ingress') {
    if (hasPermission('test-route')) {
      resourceActions.push({
        label: 'Test Route',
        icon: 'i-lucide-test-tube',
        onSelect: () => {
          genericActionConfig.value = {
            title: 'Test Route',
            description: `Test an ingress route for ${props.resource.name}`,
            label: 'Path',
            placeholder: 'e.g. /api/v1',
            initialValue: '/',
            type: 'text',
            icon: 'i-lucide-test-tube',
            onSubmit: async (path: string) => {
              try {
                const result = await k8s.testIngressRoute(props.resource.name, path, props.resource.namespace)
                const status = result.status || 'unknown'
                const color = status === 'available' ? 'success' : (status === 'pending' ? 'warning' : 'neutral')
                toast.add({ 
                  title: 'Route test completed', 
                  description: `Status: ${status}. Result: ${JSON.stringify(result.loadBalancer || 'No LoadBalancer yet')}`, 
                  color 
                })
              } catch (e: any) {
                toast.add({ title: 'Failed to test route', description: e.message, color: 'error' })
              }
            }
          }
          isGenericActionOpen.value = true
        }
      })
    }
  }

  // PVC Actions
  if (props.kind === 'PersistentVolumeClaim') {
    resourceActions.push(
      {
        label: 'Resize',
        icon: 'i-lucide-maximize',
        onSelect: () => {
          genericActionConfig.value = {
            title: 'Resize PVC',
            description: `Expand the capacity of persistent volume claim ${props.resource.name}`,
            label: 'New Size',
            placeholder: 'e.g. 20Gi',
            initialValue: props.resource.capacity?.storage || '',
            type: 'text',
            icon: 'i-lucide-maximize',
            onSubmit: async (newSize: string) => {
              try {
                await k8s.resizePVC(props.resource.name, newSize, props.resource.namespace)
                emit('refresh')
              } catch (e: any) {
                toast.add({ title: 'Failed to resize PVC', description: e.message, color: 'error' })
              }
            }
          }
          isGenericActionOpen.value = true
        }
      },
      {
        label: 'View Usage',
        icon: 'i-lucide-bar-chart',
        onSelect: async () => {
          try {
            const usage = await k8s.getPVCUsage(props.resource.name, props.resource.namespace)
            toast.add({ title: `PVC usage: ${JSON.stringify(usage)}`, color: 'neutral' })
          } catch (e: any) {
            toast.add({ title: 'Failed to get PVC usage', description: e.message, color: 'error' })
          }
        }
      }
    )
  }

  if (resourceActions.length > 0) {
    actions.push(resourceActions)
  }

  // Delete Action - only show if user has permission
  if (hasPermission('delete')) {
    actions.push([
      {
        label: 'Delete',
        icon: 'i-lucide-trash-2',
        color: 'error',
        onSelect: async () => {
          if (!await confirmDialog.open({
            title: `Delete ${props.kind}`,
            description: `Are you sure you want to delete ${props.resource.name}?`,
            confirmLabel: 'Delete',
            color: 'red'
          })) return
          try {
            await k8s.deleteResource(props.kind, props.resource.name, false, props.resource.namespace)
            emit('refresh')
          } catch (e: any) {
            toast.add({ title: 'Delete failed', description: e.message, color: 'error' })
          }
        }
      }
    ])
  }

  return actions
})
</script>

<template>
  <div>
    <UDropdownMenu :items="items" :popper="{ placement: 'bottom-end' }">
      <UButton
        icon="i-lucide-ellipsis-vertical"
        size="xs"
        color="neutral"
        variant="ghost"
      />
    </UDropdownMenu>

     <ResourceYamlModal
      v-model:open="isYamlOpen"
      :kind="kind"
      :name="resource.name"
      :namespace="resource.namespace"
      :mode="yamlMode"
      @update="emit('refresh')"
    />

    <DeploymentPodsModal
      v-model:open="isPodsOpen"
      :deployment-name="resource.name"
      :namespace="resource.namespace"
    />

    <ServiceEndpointsModal
      v-model:open="isEndpointsOpen"
      :service-name="resource.name"
      :namespace="resource.namespace"
    />

    <ResourceEventsModal
      v-model:open="isEventsOpen"
      :resource-name="resource.name"
      :resource-kind="kind"
      :namespace="resource.namespace"
    />

    <ResourceGenericActionModal
      v-model:open="isGenericActionOpen"
      v-bind="genericActionConfig"
      @submit="genericActionConfig.onSubmit"
    />

    <!-- Labels Modal -->
    <UModal 
      v-model:open="isLabelsOpen" 
      :title="`Labels - ${resource.name}`"
      :description="`View labels for ${resource.name}`"
    >
      <template #body>
        <div class="space-y-2">
          <div v-if="labelsLoading" class="flex items-center justify-center py-8">
            <UIcon name="i-lucide-loader-2" class="animate-spin text-2xl" />
          </div>
          <div v-else-if="!labels || Object.keys(labels).length === 0" class="text-gray-500 text-center py-4">
            No labels
          </div>
          <div v-else v-for="(value, key) in labels" :key="key" class="flex items-center gap-2 p-2 bg-gray-50 dark:bg-gray-800 rounded">
            <span class="font-mono text-sm font-semibold text-blue-600 dark:text-blue-400">{{ key }}:</span>
            <span class="font-mono text-sm text-gray-700 dark:text-gray-300">{{ value }}</span>
          </div>
        </div>
        <div class="flex justify-end mt-4">
          <UButton label="Close" color="neutral" @click="isLabelsOpen = false" />
        </div>
      </template>
    </UModal>

    <!-- Annotations Modal -->
    <UModal 
      v-model:open="isAnnotationsOpen" 
      :title="`Annotations - ${resource.name}`"
      :description="`View annotations for ${resource.name}`"
    >
      <template #body>
        <div class="space-y-2">
          <div v-if="annotationsLoading" class="flex items-center justify-center py-8">
            <UIcon name="i-lucide-loader-2" class="animate-spin text-2xl" />
          </div>
          <div v-else-if="!annotations || Object.keys(annotations).length === 0" class="text-gray-500 text-center py-4">
            No annotations
          </div>
          <div v-else v-for="(value, key) in annotations" :key="key" class="p-2 bg-gray-50 dark:bg-gray-800 rounded">
            <div class="font-mono text-sm font-semibold text-blue-600 dark:text-blue-400 mb-1">{{ key }}:</div>
            <div class="font-mono text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap break-words">{{ value }}</div>
          </div>
        </div>
        <div class="flex justify-end mt-4">
          <UButton label="Close" color="neutral" @click="isAnnotationsOpen = false" />
        </div>
      </template>
    </UModal>

    <!-- Pod Logs Modal -->
    <PodLogsModal
      v-if="kind === 'Pod'"
      v-model:open="isLogsOpen"
      :pod-name="resource.name"
      :namespace="resource.namespace"
      :container="resource.containers?.[0]?.name"
    />

    <!-- Pod Exec Modal -->
    <PodExecModal
      v-if="kind === 'Pod'"
      v-model:open="isExecOpen"
      :pod-name="resource.name"
      :namespace="resource.namespace"
      :container="resource.containers?.[0]?.name"
    />

    <!-- Container Metrics Modal (for Pods) -->
    <ContainerMetricsModal
      v-if="kind === 'Pod'"
      v-model:open="isMetricsOpen"
      :pod-name="resource.name"
      :namespace="resource.namespace"
    />

    <!-- Node Metrics Modal -->
    <NodeMetricsModal
      v-if="kind === 'Node'"
      v-model:open="isMetricsOpen"
      :node-name="resource.name"
    />

    <!-- Scale Modal -->
    <ScaleModal
      v-if="kind === 'Deployment' || kind === 'StatefulSet'"
      v-model:open="isScaleOpen"
      :resource-name="resource.name"
      :resource-kind="kind"
      :namespace="resource.namespace"
      :current-replicas="kind === 'Deployment' ? resource.replicasDesired : resource.replicas"
      @scale="async (replicas: number) => {
        try {
          if (kind === 'Deployment') {
            await k8s.scaleDeployment(resource.name, replicas, resource.namespace)
          } else if (kind === 'StatefulSet') {
            await k8s.scaleStatefulSet(resource.name, replicas, resource.namespace)
          }
          emit('refresh')
        } catch (e: any) {
          toast.add({ title: `Failed to scale ${kind.toLowerCase()}`, description: e.message, color: 'error' })
        }
      }"
    />

    <RolloutHistoryModal
      v-if="kind === 'Deployment' || kind === 'StatefulSet' || kind === 'DaemonSet'"
      v-model:open="isRolloutHistoryOpen"
      :resource-name="resource.name"
      :resource-kind="kind as 'Deployment' | 'StatefulSet' | 'DaemonSet'"
      :namespace="resource.namespace"
      @rollback="emit('refresh')"
    />
  </div>
</template>
