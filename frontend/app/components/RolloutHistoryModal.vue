<script setup lang="ts">
import { ref, watch, computed, h, resolveComponent } from 'vue'

const props = defineProps<{
  open: boolean
  resourceName: string
  resourceKind: 'Deployment' | 'StatefulSet' | 'DaemonSet'
  namespace: string
}>()

const emit = defineEmits(['update:open', 'rollback'])

const k8s = useKubernetes()
const toast = useToast()

const history = ref<any[]>([])
const loading = ref(false)
const selectedRevision = ref<any>(null)
const isDetailsOpen = ref(false)

// Direct Rollback Logic
const isRollbackConfirmOpen = ref(false)
const isRollingBack = ref(false)
const selectedRollbackRevision = ref<any>(null)

const confirmRollback = (revision: any) => {
  selectedRollbackRevision.value = revision
  isRollbackConfirmOpen.value = true
}

const viewDetails = (revision: any) => {
  selectedRevision.value = revision
  isDetailsOpen.value = true
}

const executeRollback = async () => {
  if (!selectedRollbackRevision.value) return

  isRollingBack.value = true
  try {
    const rev = parseInt(selectedRollbackRevision.value.revision)
    
    if (props.resourceKind === 'Deployment') {
      await k8s.rollbackDeploymentToRevision(props.resourceName, rev, props.namespace)
    } else if (props.resourceKind === 'StatefulSet') {
      await k8s.rollbackStatefulSetToRevision(props.resourceName, rev, props.namespace)
    } else if (props.resourceKind === 'DaemonSet') {
      await k8s.rollbackDaemonSetToRevision(props.resourceName, rev, props.namespace)
    }
    
    toast.add({
      title: 'Rollback initiated',
      description: `Rolling back ${props.resourceKind.toLowerCase()} to revision ${rev}`,
      color: 'success'
    })
    
    isRollbackConfirmOpen.value = false
    emit('update:open', false)
    emit('rollback')
  } catch (error: any) {
    toast.add({
      title: 'Rollback failed',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  } finally {
    isRollingBack.value = false
  }
}

const formatDate = (timestamp: string) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString()
}

const fetchHistory = async () => {
  loading.value = true
  try {
    let data
    if (props.resourceKind === 'Deployment') {
      data = await k8s.getDeploymentHistory(props.resourceName, props.namespace)
    } else if (props.resourceKind === 'StatefulSet') {
      data = await k8s.getStatefulSetHistory(props.resourceName, props.namespace)
    } else if (props.resourceKind === 'DaemonSet') {
      data = await k8s.getDaemonSetHistory(props.resourceName, props.namespace)
    }
    history.value = data || []
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch rollout history',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
    history.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.open, (isOpen) => {
  if (isOpen) {
    fetchHistory()
  }
})
const isOpen = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value)
})

// Column definitions with render functions
const UButton = resolveComponent('UButton')

const columns = [
  { 
    accessorKey: 'revision', 
    header: 'Revision',
    cell: ({ row }: any) => h('span', { class: 'font-mono font-semibold text-blue-600 dark:text-blue-400' }, row.original.revision)
  },
  { 
    accessorKey: 'changeCause', 
    header: 'Change Cause',
    cell: ({ row }: any) => h('span', { class: 'text-sm text-gray-700 dark:text-gray-300' }, row.original.changeCause || 'No change cause recorded')
  },
  { 
    accessorKey: 'createdAt', 
    header: 'Created',
    cell: ({ row }: any) => h('span', { class: 'text-sm text-gray-600 dark:text-gray-400' }, formatDate(row.original.createdAt))
  },
  { 
    id: 'actions', 
    header: '',
    cell: ({ row }: any) => h('div', { class: 'flex gap-2 justify-end' }, [
      h(UButton, {
        label: 'Details',
        size: 'xs',
        color: 'neutral',
        variant: 'ghost',
        icon: 'i-lucide-file-text',
        onClick: () => viewDetails(row.original)
      }),
      h(UButton, {
        label: 'Rollback',
        size: 'xs',
        color: 'warning',
        variant: 'soft',
        icon: 'i-lucide-rotate-ccw',
        onClick: () => confirmRollback(row.original)
      })
    ])
  }
]
</script>

<template>
  <UModal 
    v-model:open="isOpen" 
    :title="`Rollout History - ${resourceName}`"
    :description="`View rollout history for ${resourceKind.toLowerCase()}`"
  >
    <template #body>
      <div v-if="loading" class="flex items-center justify-center py-8">
        <UIcon name="i-lucide-loader-2" class="animate-spin text-2xl" />
      </div>

      <div v-else-if="history.length === 0" class="text-center py-8 text-gray-500">
        No rollout history found
      </div>

      <div v-else class="space-y-2">
        <UTable :data="history" :columns="columns" />
      </div>

      <div class="flex justify-end mt-4 gap-2">
        <UButton 
          label="Refresh" 
          color="neutral" 
          variant="ghost"
          icon="i-lucide-refresh-cw"
          :loading="loading"
          @click="fetchHistory" 
        />
        <UButton 
          label="Close" 
          color="neutral" 
          @click="emit('update:open', false)" 
        />
      </div>
    </template>
  </UModal>

  <RolloutRevisionDetailsModal
    v-model:open="isDetailsOpen"
    :resource-name="resourceName"
    :resource-kind="resourceKind"
    :namespace="namespace"
    :revision="selectedRevision"
    @rollback="() => {
      isDetailsOpen = false
      emit('update:open', false)
      emit('rollback')
    }"
  />

  <!-- Rollback Confirmation Modal -->
  <UModal 
    v-model:open="isRollbackConfirmOpen"
    title="Confirm Rollback"
    :description="`Are you sure you want to rollback to revision ${selectedRollbackRevision?.revision}?`"
  >
    <template #body>
      <div class="space-y-4">
        <div class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4">
          <div class="flex gap-3">
            <UIcon name="i-lucide-alert-triangle" class="text-yellow-600 dark:text-yellow-400 text-xl flex-shrink-0 mt-0.5" />
            <div class="space-y-1">
              <p class="text-sm font-semibold text-yellow-800 dark:text-yellow-200">
                This will rollback {{ resourceKind.toLowerCase() }} "{{ resourceName }}" to revision {{ selectedRollbackRevision?.revision }}
              </p>
              <p class="text-sm text-yellow-700 dark:text-yellow-300">
                This action will trigger a new rollout and may cause downtime.
              </p>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2">
          <UButton 
            label="Cancel" 
            color="neutral" 
            variant="ghost"
            @click="isRollbackConfirmOpen = false" 
          />
          <UButton 
            label="Confirm Rollback" 
            color="error"
            icon="i-lucide-undo"
            :loading="isRollingBack"
            @click="executeRollback" 
          />
        </div>
      </div>
    </template>
  </UModal>
</template>
