<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'

const store = useAppCreatorStore()

const presets = [
  {
    label: 'Micro',
    icon: 'i-lucide-zap',
    color: 'green',
    requests: { cpu: '50m', memory: '64Mi' },
    limits: { cpu: '100m', memory: '128Mi' }
  },
  {
    label: 'Small',
    icon: 'i-lucide-box',
    color: 'blue',
    requests: { cpu: '100m', memory: '128Mi' },
    limits: { cpu: '500m', memory: '512Mi' }
  },
  {
    label: 'Medium',
    icon: 'i-lucide-layers',
    color: 'orange',
    requests: { cpu: '250m', memory: '512Mi' },
    limits: { cpu: '1000m', memory: '1Gi' }
  },
  {
    label: 'Large',
    icon: 'i-lucide-server',
    color: 'red',
    requests: { cpu: '500m', memory: '1Gi' },
    limits: { cpu: '2000m', memory: '2Gi' }
  }
]

const applyPreset = (preset: typeof presets[0]) => {
  store.wizard.resources.requests = { ...preset.requests }
  store.wizard.resources.limits = { ...preset.limits }
}

const isActivePreset = (preset: typeof presets[0]) => {
  return store.wizard.resources.requests.cpu === preset.requests.cpu &&
    store.wizard.resources.requests.memory === preset.requests.memory
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h3 class="text-base font-semibold text-gray-900 dark:text-white">Resource Limits</h3>
      <p class="text-sm text-gray-500 mt-0.5">Define CPU and memory resource requests and limits.</p>
    </div>

    <!-- Presets -->
    <div>
      <p class="text-xs font-medium text-gray-500 mb-2">Quick Presets</p>
      <div class="grid grid-cols-4 gap-3">
        <button
          v-for="preset in presets"
          :key="preset.label"
          :class="[
            'p-3 rounded-lg border-2 text-left transition-all',
            isActivePreset(preset)
              ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
              : 'border-gray-200 dark:border-gray-700 hover:border-gray-300'
          ]"
          @click="applyPreset(preset)"
        >
          <UIcon :name="preset.icon" :class="['w-5 h-5 mb-1', isActivePreset(preset) ? 'text-primary-500' : 'text-gray-400']" />
          <p :class="['text-xs font-bold', isActivePreset(preset) ? 'text-primary-600 dark:text-primary-400' : 'text-gray-700 dark:text-gray-300']">{{ preset.label }}</p>
          <p class="text-[10px] text-gray-400 mt-0.5">{{ preset.requests.cpu }} / {{ preset.requests.memory }}</p>
        </button>
      </div>
    </div>

    <!-- Manual input -->
    <div class="grid grid-cols-2 gap-6">
      <div class="space-y-4">
        <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <UIcon name="i-lucide-arrow-down-to-line" class="w-4 h-4 text-green-500" />
          Requests (guaranteed)
        </h4>
        <UFormGroup label="CPU">
          <UInput v-model="store.wizard.resources.requests.cpu" placeholder="100m" size="sm" />
        </UFormGroup>
        <UFormGroup label="Memory">
          <UInput v-model="store.wizard.resources.requests.memory" placeholder="128Mi" size="sm" />
        </UFormGroup>
      </div>

      <div class="space-y-4">
        <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
          <UIcon name="i-lucide-arrow-up-to-line" class="w-4 h-4 text-red-500" />
          Limits (maximum)
        </h4>
        <UFormGroup label="CPU">
          <UInput v-model="store.wizard.resources.limits.cpu" placeholder="500m" size="sm" />
        </UFormGroup>
        <UFormGroup label="Memory">
          <UInput v-model="store.wizard.resources.limits.memory" placeholder="512Mi" size="sm" />
        </UFormGroup>
      </div>
    </div>

    <div class="p-3 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-lg text-xs text-amber-700 dark:text-amber-400 flex gap-2">
      <UIcon name="i-lucide-alert-triangle" class="w-4 h-4 shrink-0 mt-0.5" />
      <span>CPU is expressed in millicores (1000m = 1 core). Memory uses Ki, Mi, Gi suffixes. Limits must be ≥ Requests.</span>
    </div>
  </div>
</template>

