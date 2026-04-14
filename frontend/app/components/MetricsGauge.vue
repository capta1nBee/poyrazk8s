<script setup lang="ts">
const props = defineProps<{
  value: number
  max: number
  label: string
  unit?: string
  color?: 'green' | 'blue' | 'yellow' | 'red' | 'purple'
}>()

const percentage = computed(() => {
  if (props.max === 0) return 0
  return Math.min((props.value / props.max) * 100, 100)
})

const gaugeColor = computed(() => {
  if (percentage.value >= 90) return 'red'
  if (percentage.value >= 70) return 'yellow'
  return 'green'
})

const colorClasses = {
  green: 'text-green-500',
  blue: 'text-blue-500',
  yellow: 'text-yellow-500',
  red: 'text-red-500',
  purple: 'text-purple-500'
}

const formatValue = (value: number): string => {
  if (value >= 1024 * 1024 * 1024) {
    return `${(value / (1024 * 1024 * 1024)).toFixed(2)}G`
  }
  if (value >= 1024 * 1024) {
    return `${(value / (1024 * 1024)).toFixed(2)}M`
  }
  if (value >= 1024) {
    return `${(value / 1024).toFixed(2)}K`
  }
  return value.toFixed(2)
}
</script>

<template>
  <div class="flex flex-col items-center gap-2">
    <div class="relative w-24 h-24">
      <svg class="transform -rotate-90 w-24 h-24" viewBox="0 0 100 100">
        <!-- Background circle -->
        <circle
          cx="50"
          cy="50"
          r="40"
          fill="none"
          stroke="currentColor"
          stroke-width="8"
          class="text-gray-200 dark:text-gray-700"
        />
        <!-- Progress circle -->
        <circle
          cx="50"
          cy="50"
          r="40"
          fill="none"
          stroke="currentColor"
          stroke-width="8"
          :stroke-dasharray="`${2 * Math.PI * 40}`"
          :stroke-dashoffset="`${2 * Math.PI * 40 * (1 - percentage / 100)}`"
          :class="colorClasses[gaugeColor]"
          stroke-linecap="round"
          class="transition-all duration-500"
        />
      </svg>
      <div class="absolute inset-0 flex flex-col items-center justify-center">
        <span class="text-lg font-bold" :class="colorClasses[gaugeColor]">
          {{ Math.round(percentage) }}%
        </span>
      </div>
    </div>
    <div class="text-center">
      <div class="text-sm font-semibold text-gray-700 dark:text-gray-300">{{ label }}</div>
      <div class="text-xs text-gray-500 dark:text-gray-400">
        {{ formatValue(value) }} / {{ formatValue(max) }} {{ unit || '' }}
      </div>
    </div>
  </div>
</template>

