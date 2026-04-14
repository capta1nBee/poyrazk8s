<script setup lang="ts">
import { computed } from 'vue'

interface SecurityAlert {
  id: number
  clusterUid: string
  eventType: string
  priority: string
  ruleName: string
  ruleDescription: string
  output: string
  namespaceName: string
  podName: string
  containerId: string
  eventData: any
  isAcknowledged: boolean
  acknowledgedBy: string
  acknowledgedAt: string
  acknowledgmentNote: string
  resolved: boolean
  resolvedBy: string
  resolvedAt: string
  resolutionNote: string
  createdAt: string
}

const props = defineProps<{
  modelValue: boolean
  alert: SecurityAlert
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const isOpen = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const eventDataJson = computed(() => 
  JSON.stringify(props.alert.eventData, null, 2)
)
</script>

<template>
  <UModal
    v-model:open="isOpen"
    title="Alert Details"
    size="2xl"
  >
    <template #body>
      <div class="space-y-6">
        <!-- Alert Header -->
        <div class="flex items-start justify-between pb-4 border-b dark:border-gray-700">
          <div>
            <h3 class="text-lg font-semibold">{{ alert.ruleName }}</h3>
            <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ alert.ruleDescription }}</p>
          </div>
          <UBadge 
            :color="alert.priority === 'CRITICAL' ? 'red' : alert.priority === 'HIGH' ? 'orange' : 'gray'"
            variant="subtle"
          >
            {{ alert.priority }}
          </UBadge>
        </div>

        <!-- Alert Output -->
        <div>
          <h4 class="font-semibold text-sm mb-2">Alert Message</h4>
          <div class="bg-gray-100 dark:bg-gray-900 p-3 rounded text-sm text-gray-800 dark:text-gray-200 break-all">
            {{ alert.output }}
          </div>
        </div>

        <!-- Resource Info -->
        <div class="grid grid-cols-2 gap-4">
          <div>
            <p class="text-xs text-gray-600 dark:text-gray-400 mb-1">Pod Name</p>
            <p class="font-mono text-sm font-semibold">{{ alert.podName || 'N/A' }}</p>
          </div>
          <div>
            <p class="text-xs text-gray-600 dark:text-gray-400 mb-1">Namespace</p>
            <p class="font-mono text-sm font-semibold">{{ alert.namespaceName || 'N/A' }}</p>
          </div>
          <div>
            <p class="text-xs text-gray-600 dark:text-gray-400 mb-1">Event Type</p>
            <p class="font-mono text-sm font-semibold">{{ alert.eventType }}</p>
          </div>
          <div>
            <p class="text-xs text-gray-600 dark:text-gray-400 mb-1">Container ID</p>
            <p class="font-mono text-sm font-semibold truncate">{{ alert.containerId || 'N/A' }}</p>
          </div>
        </div>

        <!-- Acknowledgement Info -->
        <div v-if="alert.isAcknowledged" class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
          <h4 class="font-semibold text-sm mb-2 flex items-center gap-2">
            <UIcon name="i-lucide-check-circle" class="w-4 h-4" />
            Acknowledged
          </h4>
          <div class="text-sm space-y-1">
            <p><span class="text-gray-600 dark:text-gray-400">By:</span> {{ alert.acknowledgedBy }}</p>
            <p><span class="text-gray-600 dark:text-gray-400">At:</span> {{ new Date(alert.acknowledgedAt).toLocaleString() }}</p>
            <p v-if="alert.acknowledgmentNote" class="mt-2">
              <span class="text-gray-600 dark:text-gray-400">Note:</span> {{ alert.acknowledgmentNote }}
            </p>
          </div>
        </div>

        <!-- Resolution Info -->
        <div v-if="alert.resolved" class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4">
          <h4 class="font-semibold text-sm mb-2 flex items-center gap-2">
            <UIcon name="i-lucide-check-circle" class="w-4 h-4" />
            Resolved
          </h4>
          <div class="text-sm space-y-1">
            <p><span class="text-gray-600 dark:text-gray-400">By:</span> {{ alert.resolvedBy }}</p>
            <p><span class="text-gray-600 dark:text-gray-400">At:</span> {{ new Date(alert.resolvedAt).toLocaleString() }}</p>
            <p v-if="alert.resolutionNote" class="mt-2">
              <span class="text-gray-600 dark:text-gray-400">Note:</span> {{ alert.resolutionNote }}
            </p>
          </div>
        </div>

        <!-- Timing -->
        <div class="text-sm text-gray-600 dark:text-gray-400">
          <p><span class="font-semibold">Created:</span> {{ new Date(alert.createdAt).toLocaleString() }}</p>
        </div>

        <!-- Event Data (if available) -->
        <div v-if="alert.eventData">
          <h4 class="font-semibold text-sm mb-2">Event Data</h4>
          <div class="bg-gray-100 dark:bg-gray-900 p-3 rounded text-xs font-mono overflow-x-auto max-h-60 overflow-y-auto">
            <pre class="text-gray-800 dark:text-gray-200">{{ eventDataJson }}</pre>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex justify-end">
        <UButton label="Close" color="neutral" @click="isOpen = false" />
      </div>
    </template>
  </UModal>
</template>
