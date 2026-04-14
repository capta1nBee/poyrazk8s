<script setup lang="ts">
const props = defineProps<{
  currentStep: number
  totalSteps: number
}>()

const steps = [
  { label: 'Basic Info', icon: 'i-lucide-info' },
  { label: 'Container', icon: 'i-lucide-box' },
  { label: 'Ports & Env', icon: 'i-lucide-plug' },
  { label: 'Resources', icon: 'i-lucide-cpu' },
  { label: 'Kind Config', icon: 'i-lucide-settings-2' },
  { label: 'Add-ons', icon: 'i-lucide-puzzle' },
  { label: 'Volumes', icon: 'i-lucide-hard-drive' },
  { label: 'Review', icon: 'i-lucide-rocket' },
]
</script>

<template>
  <div class="flex items-center gap-0">
    <template v-for="(step, index) in steps" :key="index">
      <div class="flex flex-col items-center gap-1 min-w-[80px]">
        <div
          :class="[
            'w-9 h-9 rounded-full flex items-center justify-center text-sm font-bold border-2 transition-all duration-200',
            currentStep === index + 1
              ? 'bg-primary-500 border-primary-500 text-white shadow-md shadow-primary-500/30'
              : currentStep > index + 1
              ? 'bg-primary-100 dark:bg-primary-900 border-primary-500 text-primary-600 dark:text-primary-300'
              : 'bg-gray-100 dark:bg-gray-800 border-gray-300 dark:border-gray-600 text-gray-400'
          ]"
        >
          <UIcon v-if="currentStep > index + 1" name="i-lucide-check" class="w-4 h-4" />
          <span v-else>{{ index + 1 }}</span>
        </div>
        <span
          :class="[
            'text-[10px] font-medium text-center leading-tight',
            currentStep === index + 1 ? 'text-primary-600 dark:text-primary-400' : 'text-gray-400'
          ]"
        >{{ step.label }}</span>
      </div>
      <div
        v-if="index < steps.length - 1"
        :class="[
          'flex-1 h-0.5 mb-4 transition-all duration-300',
          currentStep > index + 1 ? 'bg-primary-500' : 'bg-gray-200 dark:bg-gray-700'
        ]"
      />
    </template>
  </div>
</template>

