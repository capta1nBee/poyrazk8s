<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'

const store = useAppCreatorStore()

// ConfigMap management
const cmKey = ref('')
const cmValue = ref('')
const addCmEntry = () => {
  if (!cmKey.value.trim()) return
  store.wizard.configMap[cmKey.value.trim()] = cmValue.value
  cmKey.value = ''
  cmValue.value = ''
}
const removeCmEntry = (key: string) => {
  const updated = { ...store.wizard.configMap }
  delete updated[key]
  store.wizard.configMap = updated
}

// Secret management
const secretKey = ref('')
const secretValue = ref('')
const addSecretEntry = () => {
  if (!secretKey.value.trim()) return
  store.wizard.secret[secretKey.value.trim()] = secretValue.value
  secretKey.value = ''
  secretValue.value = ''
}
const removeSecretEntry = (key: string) => {
  const updated = { ...store.wizard.secret }
  delete updated[key]
  store.wizard.secret = updated
}

const cmEntries = computed(() => Object.entries(store.wizard.configMap))
const secretEntries = computed(() => Object.entries(store.wizard.secret))
</script>

<template>
  <div class="space-y-8">
    <div>
      <h3 class="text-base font-semibold text-gray-900 dark:text-white">Configuration & Secrets</h3>
      <p class="text-sm text-gray-500 mt-0.5">Add ConfigMap data and Secrets for your application.</p>
    </div>

    <!-- ConfigMap -->
    <div class="space-y-3">
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-file-cog" class="w-4 h-4 text-blue-500" />
        <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300">ConfigMap Data</h4>
        <UBadge v-if="cmEntries.length" color="blue" variant="subtle" size="xs">{{ cmEntries.length }}</UBadge>
      </div>

      <div class="flex gap-2">
        <UInput v-model="cmKey" placeholder="KEY" class="flex-1" size="sm" />
        <UInput v-model="cmValue" placeholder="value" class="flex-1" size="sm" @keyup.enter="addCmEntry" />
        <UButton size="sm" color="blue" variant="soft" icon="i-lucide-plus" @click="addCmEntry" />
      </div>

      <div v-if="cmEntries.length" class="space-y-1.5">
        <div
          v-for="[key, val] in cmEntries"
          :key="key"
          class="flex items-center gap-2 p-2.5 bg-blue-50/50 dark:bg-blue-900/10 border border-blue-200/50 dark:border-blue-800/50 rounded-lg"
        >
          <code class="text-xs text-blue-700 dark:text-blue-300 flex-1 truncate font-mono">{{ key }}</code>
          <span class="text-gray-400 text-xs">=</span>
          <code class="text-xs text-gray-600 dark:text-gray-300 flex-1 truncate font-mono">{{ val }}</code>
          <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-x" @click="removeCmEntry(key)" />
        </div>
      </div>
      <p v-else class="text-xs text-gray-400 italic">No ConfigMap entries. Keys will be available as environment variables or mounted files.</p>
    </div>

    <!-- Secrets -->
    <div class="space-y-3">
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-shield" class="w-4 h-4 text-red-500" />
        <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300">Secret Data</h4>
        <UBadge v-if="secretEntries.length" color="red" variant="subtle" size="xs">{{ secretEntries.length }}</UBadge>
      </div>

      <div class="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200/50 dark:border-red-800/50 rounded-lg flex gap-2 text-xs text-red-600 dark:text-red-400">
        <UIcon name="i-lucide-alert-triangle" class="w-3.5 h-3.5 shrink-0 mt-0.5" />
        Secret values are stored in base64 encoded format. Avoid committing sensitive values to source control.
      </div>

      <div class="flex gap-2">
        <UInput v-model="secretKey" placeholder="SECRET_KEY" class="flex-1" size="sm" />
        <UInput v-model="secretValue" placeholder="secret-value" type="password" class="flex-1" size="sm" @keyup.enter="addSecretEntry" />
        <UButton size="sm" color="red" variant="soft" icon="i-lucide-plus" @click="addSecretEntry" />
      </div>

      <div v-if="secretEntries.length" class="space-y-1.5">
        <div
          v-for="[key] in secretEntries"
          :key="key"
          class="flex items-center gap-2 p-2.5 bg-red-50/50 dark:bg-red-900/10 border border-red-200/50 dark:border-red-800/50 rounded-lg"
        >
          <code class="text-xs text-red-700 dark:text-red-300 flex-1 font-mono">{{ key }}</code>
          <span class="text-gray-400 text-xs">= ••••••••</span>
          <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-x" @click="removeSecretEntry(key)" />
        </div>
      </div>
      <p v-else class="text-xs text-gray-400 italic">No secrets defined.</p>
    </div>
  </div>
</template>

