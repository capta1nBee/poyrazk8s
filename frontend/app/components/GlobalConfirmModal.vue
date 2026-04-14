<script setup lang="ts">
const { isOpen, title, description, confirmLabel, cancelLabel, confirmColor, confirm, cancel, icon } = useConfirm()
</script>

<template>
  <UModal 
    v-model:open="isOpen" 
    prevent-close
    :title="title"
    :description="description"
  >
    <template #body>
      <div class="sm:flex sm:items-start p-6">
        <!-- Icon Area -->
        <div 
          class="mx-auto flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full sm:mx-0 sm:h-10 sm:w-10"
          :class="[
            confirmColor === 'red' || confirmColor === 'error' ? 'bg-red-100 dark:bg-red-900/30' : 
            confirmColor === 'orange' || confirmColor === 'warning' ? 'bg-orange-100 dark:bg-orange-900/30' : 
            'bg-primary-100 dark:bg-primary-900/30'
          ]"
        >
          <UIcon 
            :name="icon || (
              confirmColor === 'red' || confirmColor === 'error' ? 'i-lucide-alert-triangle' : 
              confirmColor === 'orange' || confirmColor === 'warning' ? 'i-lucide-alert-circle' : 
              'i-lucide-info'
            )" 
            class="h-6 w-6"
            :class="[
              confirmColor === 'red' || confirmColor === 'error' ? 'text-red-600 dark:text-red-400' : 
              confirmColor === 'orange' || confirmColor === 'warning' ? 'text-orange-600 dark:text-orange-400' : 
              'text-primary-600 dark:text-primary-400'
            ]" 
          />
        </div>

        <!-- Content Area -->
        <div class="mt-3 text-center sm:ml-4 sm:mt-0 sm:text-left w-full">
          <!-- We hide the visual title here if UModal renders it, but usually with #body it replaces content. 
               However, to match the 'Premium' look exactly as per user snippet which seems to have custom structure: -->
          <h3 class="text-base font-semibold leading-6 text-gray-900 dark:text-white">
            {{ title }}
          </h3>
          <div class="mt-2">
            <p class="text-sm text-gray-500 dark:text-gray-400">
              {{ description }}
            </p>
          </div>
        </div>
      </div>
      
      <!-- Actions Area -->
      <div class="bg-gray-50 dark:bg-gray-900/50 px-6 py-4 flex items-center gap-3 rounded-b-lg">
        <UButton
          color="neutral"
          variant="soft"
          size="lg"
          block
          class="flex-1 font-bold"
          :label="cancelLabel || 'Cancel'"
          @click="cancel"
        />
        <UButton
          :color="confirmColor"
          variant="solid"
          size="lg"
          block
          class="flex-1 font-black shadow-lg active:scale-[0.98] transition-all"
          :class="`shadow-${confirmColor}-500/20`"
          :label="confirmLabel || 'Confirm'"
          @click="confirm"
        />
      </div>
    </template>
  </UModal>
</template>
