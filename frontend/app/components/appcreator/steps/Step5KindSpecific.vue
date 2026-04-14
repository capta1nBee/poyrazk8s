<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'

const store = useAppCreatorStore()
const w = computed(() => store.wizard)

const cronPresets = [
  { label: 'Every 5 min', value: '*/5 * * * *' },
  { label: 'Every hour', value: '0 * * * *' },
  { label: 'Daily midnight', value: '0 0 * * *' },
  { label: 'Every Monday', value: '0 0 * * 1' },
]

const nsKey = ref('')
const nsVal = ref('')
const addNodeSelector = () => {
  if (!nsKey.value.trim()) return
  store.wizard.daemonSetNodeSelector[nsKey.value.trim()] = nsVal.value
  nsKey.value = ''
  nsVal.value = ''
}
const removeNodeSelector = (key: string) => {
  const updated = { ...store.wizard.daemonSetNodeSelector }
  delete updated[key]
  store.wizard.daemonSetNodeSelector = updated
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h3 class="text-base font-semibold text-gray-900 dark:text-white">Kind-Specific Configuration</h3>
      <p class="text-sm text-gray-500 mt-0.5">Configure settings specific to <strong>{{ w.workloadType }}</strong>.</p>
    </div>

    <!-- Deployment -->
    <template v-if="w.workloadType === 'Deployment'">
      <div class="space-y-4">
        <UFormGroup label="Update Strategy">
          <div class="flex gap-3 mt-1">
            <button
              v-for="strategy in ['RollingUpdate', 'Recreate']"
              :key="strategy"
              :class="['flex-1 p-3 rounded-lg border-2 text-sm font-medium transition-all', w.deploymentStrategy === strategy ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 text-primary-600 dark:text-primary-400' : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-400 hover:border-gray-300']"
              @click="store.wizard.deploymentStrategy = strategy as any"
            >{{ strategy }}</button>
          </div>
        </UFormGroup>
        <template v-if="w.deploymentStrategy === 'RollingUpdate'">
          <div class="grid grid-cols-2 gap-4">
            <UFormGroup label="Max Surge" help="Max pods above desired (e.g. 25% or 1)">
              <UInput v-model="store.wizard.maxSurge" placeholder="25%" size="sm" />
            </UFormGroup>
            <UFormGroup label="Max Unavailable" help="Max pods that can be unavailable">
              <UInput v-model="store.wizard.maxUnavailable" placeholder="25%" size="sm" />
            </UFormGroup>
          </div>
        </template>
        <div class="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg text-xs text-blue-700 dark:text-blue-300">
          💡 <strong>RollingUpdate</strong> ensures zero-downtime deployments. <strong>Recreate</strong> stops all pods before starting new ones.
        </div>
      </div>
    </template>

    <!-- StatefulSet -->
    <template v-else-if="w.workloadType === 'StatefulSet'">
      <div class="space-y-4">
        <UFormGroup label="Headless Service Name" required help="A headless service will be created with this name">
          <UInput v-model="store.wizard.statefulSetServiceName" :placeholder="w.name || 'my-app'" size="sm" icon="i-lucide-share-2" />
        </UFormGroup>
        <UFormGroup label="Pod Management Policy">
          <div class="flex gap-3 mt-1">
            <button
              v-for="policy in ['OrderedReady', 'Parallel']"
              :key="policy"
              :class="['flex-1 p-3 rounded-lg border-2 text-sm font-medium transition-all', w.statefulSetPodManagementPolicy === policy ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 text-primary-600 dark:text-primary-400' : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-400']"
              @click="store.wizard.statefulSetPodManagementPolicy = policy as any"
            >{{ policy }}</button>
          </div>
        </UFormGroup>
        <div class="p-3 bg-amber-50 dark:bg-amber-900/20 rounded-lg text-xs text-amber-700 dark:text-amber-400">
          🗄️ StatefulSet pods get stable hostnames: <code class="font-mono">{{ w.name || 'app' }}-0</code>, <code class="font-mono">{{ w.name || 'app' }}-1</code>... Add persistent volumes in Step 7.
        </div>
      </div>
    </template>

    <!-- DaemonSet -->
    <template v-else-if="w.workloadType === 'DaemonSet'">
      <div class="space-y-4">
        <div class="p-3 bg-purple-50 dark:bg-purple-900/20 rounded-lg text-xs text-purple-700 dark:text-purple-400">
          🛡️ DaemonSet runs one pod on <strong>every node</strong>. Use NodeSelector to limit to specific nodes.
        </div>
        <UFormGroup label="Node Selector" help="Run pods only on nodes with these labels">
          <div class="flex gap-2 mb-2">
            <UInput v-model="nsKey" placeholder="kubernetes.io/os" size="sm" class="flex-1" />
            <UInput v-model="nsVal" placeholder="linux" size="sm" class="flex-1" @keyup.enter="addNodeSelector" />
            <UButton size="sm" color="purple" variant="soft" icon="i-lucide-plus" @click="addNodeSelector" />
          </div>
          <div class="space-y-1">
            <div v-for="(val, key) in w.daemonSetNodeSelector" :key="key" class="flex items-center gap-2 p-2 bg-purple-50/50 dark:bg-purple-900/10 border border-purple-200/50 rounded-lg text-xs">
              <code class="font-mono text-purple-700 dark:text-purple-300 flex-1">{{ key }}={{ val }}</code>
              <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-x" @click="removeNodeSelector(key)" />
            </div>
            <p v-if="!Object.keys(w.daemonSetNodeSelector).length" class="text-xs text-gray-400 italic">No node selectors — runs on all nodes.</p>
          </div>
        </UFormGroup>
      </div>
    </template>

    <!-- CronJob -->
    <template v-else-if="w.workloadType === 'CronJob'">
      <div class="space-y-4">
        <UFormGroup label="Cron Schedule" required>
          <UInput v-model="store.wizard.cronJobSchedule" placeholder="*/5 * * * *" size="sm" icon="i-lucide-clock" />
        </UFormGroup>
        <div class="flex flex-wrap gap-2">
          <UButton v-for="p in cronPresets" :key="p.value" size="xs" color="neutral" variant="soft" @click="store.wizard.cronJobSchedule = p.value">{{ p.label }}</UButton>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <UFormGroup label="Concurrency Policy">
            <USelectMenu v-model="store.wizard.cronJobConcurrencyPolicy" :items="['Allow', 'Forbid', 'Replace']" size="sm" />
          </UFormGroup>
          <UFormGroup label="Success History Limit">
            <UInput v-model.number="store.wizard.cronJobSuccessHistory" type="number" min="0" size="sm" />
          </UFormGroup>
          <UFormGroup label="Failed History Limit">
            <UInput v-model.number="store.wizard.cronJobFailedHistory" type="number" min="0" size="sm" />
          </UFormGroup>
        </div>
        <div class="p-3 bg-rose-50 dark:bg-rose-900/20 rounded-lg text-xs text-rose-700 dark:text-rose-400">
          ⏰ Schedule: <code class="font-mono font-bold">{{ w.cronJobSchedule }}</code> — uses standard 5-field cron format (min hour day month weekday).
        </div>
      </div>
    </template>
  </div>
</template>

