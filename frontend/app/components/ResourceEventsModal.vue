<script setup lang="ts">
import type { K8sEvent } from '~/types/cluster'

const props = defineProps<{
  resourceName: string
  resourceKind: string
  namespace?: string
}>()

const isOpen = defineModel<boolean>('open')

const k8s = useKubernetes()
const toast = useToast()

const events = ref<K8sEvent[]>([])
const loading = ref(false)

const columns = [
  { id: 'lastSeen', key: 'lastSeen', label: 'Last Seen', sortable: true },
  { id: 'type', key: 'type', label: 'Type', sortable: true },
  { id: 'reason', key: 'reason', label: 'Reason', sortable: true },
  { id: 'message', key: 'message', label: 'Message' },
  { id: 'count', key: 'count', label: 'Count', sortable: true }
]

const fetchEvents = async () => {
  if (!isOpen.value) return
  
  loading.value = true
  try {
    const result = await k8s.getResourceEvents(props.resourceKind, props.resourceName, props.namespace)
    events.value = result || []
  } catch (error: any) {
    console.error('Fetch resource events error:', error)
    toast.add({
      title: 'Failed to fetch events',
      description: error.message,
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const formatAge = (timestamp: string | null) => {
  if (!timestamp) return '-'

  const now = Date.now()
  const seen = new Date(timestamp).getTime()
  const diff = Math.floor((now - seen) / 1000)

  if (diff < 60) return `${diff}s`
  if (diff < 3600) return `${Math.floor(diff / 60)}m`
  if (diff < 86400) return `${Math.floor(diff / 3600)}h`
  return `${Math.floor(diff / 86400)}d`
}

const eventTypeColor = (type: string) => {
  switch (type) {
    case 'Warning':
      return 'red'
    case 'Normal':
      return 'green'
    default:
      return 'gray'
  }
}

watch(isOpen, (newVal) => {
  if (newVal) {
    fetchEvents()
  }
})
</script>

<template>
  <UModal v-model:open="isOpen" :title="`Events - ${resourceName}`" class="sm:max-w-4xl">
    <template #body>
      <div class="space-y-4 min-h-[400px]">
        <div v-if="loading" class="flex items-center justify-center py-12">
          <UIcon name="i-lucide-loader-2" class="w-8 h-8 animate-spin text-gray-400" />
        </div>
        
        <template v-else>
          <LegacyTable
            :rows="events"
            :columns="columns"
            class="w-full"
          >
            <!-- Last Seen -->
            <template #lastSeen-data="{ row }">
              <span class="text-sm text-gray-600 dark:text-gray-400">
                {{ formatAge(row.lastSeen) }}
              </span>
            </template>

            <!-- Type -->
            <template #type-data="{ row }">
              <UBadge
                :color="eventTypeColor(row.type)"
                variant="soft"
                class="uppercase"
              >
                {{ row.type }}
              </UBadge>
            </template>

            <!-- Message -->
            <template #message-data="{ row }">
              <span class="text-sm text-gray-700 dark:text-gray-300 line-clamp-2" :title="row.message">
                {{ row.message }}
              </span>
            </template>

            <!-- Count -->
            <template #count-data="{ row }">
              <UBadge color="neutral" variant="subtle">
                {{ row.count || 1 }}
              </UBadge>
            </template>

            <template #empty>
              <div class="py-12 text-center text-gray-500">
                <UIcon name="i-lucide-info" class="w-8 h-8 mx-auto mb-2 opacity-20" />
                <p>No events found for this resource.</p>
              </div>
            </template>
          </LegacyTable>
        </template>
      </div>

      <div class="flex justify-end mt-4">
        <UButton label="Close" color="neutral" @click="isOpen = false" />
      </div>
    </template>
  </UModal>
</template>
