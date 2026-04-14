<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'
import type { PortDef, EnvVar } from '~/stores/appcreator'

const store = useAppCreatorStore()

const addPort = () => {
  store.wizard.ports.push({ name: '', containerPort: 80, protocol: 'TCP' })
}
const removePort = (i: number) => store.wizard.ports.splice(i, 1)

const addEnvVar = () => {
  store.wizard.envVars.push({ name: '', value: '' })
}
const removeEnvVar = (i: number) => store.wizard.envVars.splice(i, 1)
</script>

<template>
  <div class="space-y-8">
    <div>
      <h3 class="text-base font-semibold text-gray-900 dark:text-white">Ports & Environment Variables</h3>
      <p class="text-sm text-gray-500 mt-0.5">Configure container ports and environment variables.</p>
    </div>

    <!-- Ports Section -->
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <div>
          <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300">Container Ports</h4>
          <p class="text-xs text-gray-400">Ports exposed by your container</p>
        </div>
        <UButton size="xs" color="primary" variant="soft" icon="i-lucide-plus" @click="addPort">
          Add Port
        </UButton>
      </div>

      <div v-if="store.wizard.ports.length === 0" class="py-6 text-center border border-dashed border-gray-200 dark:border-gray-700 rounded-lg">
        <UIcon name="i-lucide-plug" class="w-6 h-6 text-gray-300 mx-auto mb-1" />
        <p class="text-xs text-gray-400">No ports configured. Click "Add Port" to expose a port.</p>
      </div>

      <div v-for="(port, i) in store.wizard.ports" :key="i" class="flex items-center gap-2 p-3 bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-gray-200 dark:border-gray-700">
        <UInput v-model="port.name" placeholder="http" class="w-28" size="sm" />
        <UInput v-model.number="port.containerPort" type="number" placeholder="Port" class="w-24" size="sm" />
        <USelectMenu
          v-model="port.protocol"
          :items="['TCP', 'UDP', 'SCTP']"
          class="w-24"
          size="sm"
        />
        <UButton size="xs" color="red" variant="ghost" icon="i-lucide-trash-2" @click="removePort(i)" />
      </div>
    </div>

    <!-- Environment Variables Section -->
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <div>
          <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300">Environment Variables</h4>
          <p class="text-xs text-gray-400">Set environment variables for the container</p>
        </div>
        <UButton size="xs" color="primary" variant="soft" icon="i-lucide-plus" @click="addEnvVar">
          Add Variable
        </UButton>
      </div>

      <div v-if="store.wizard.envVars.length === 0" class="py-6 text-center border border-dashed border-gray-200 dark:border-gray-700 rounded-lg">
        <UIcon name="i-lucide-variable" class="w-6 h-6 text-gray-300 mx-auto mb-1" />
        <p class="text-xs text-gray-400">No environment variables configured.</p>
      </div>

      <div v-for="(env, i) in store.wizard.envVars" :key="i" class="flex items-center gap-2 p-3 bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-gray-200 dark:border-gray-700">
        <UInput v-model="env.name" placeholder="ENV_VAR_NAME" class="flex-1" size="sm" />
        <span class="text-gray-400 font-mono">=</span>
        <UInput v-model="env.value" placeholder="value" class="flex-1" size="sm" />
        <UButton size="xs" color="red" variant="ghost" icon="i-lucide-trash-2" @click="removeEnvVar(i)" />
      </div>
    </div>
  </div>
</template>

