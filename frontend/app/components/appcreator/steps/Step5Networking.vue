<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'

const store = useAppCreatorStore()

const firstPortNumber = computed(() => {
  const p = store.wizard.ports[0]
  return p ? p.containerPort : 80
})
</script>

<template>
  <div class="space-y-6">
    <div>
      <h3 class="text-base font-semibold text-gray-900 dark:text-white">Networking</h3>
      <p class="text-sm text-gray-500 mt-0.5">Configure how your application is exposed to traffic.</p>
    </div>

    <!-- Service toggle -->
    <div class="p-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl border border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between">
        <div>
          <h4 class="text-sm font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
            <UIcon name="i-lucide-share-2" class="w-4 h-4 text-primary-500" />
            Create Kubernetes Service
          </h4>
          <p class="text-xs text-gray-400 mt-0.5">Expose your pods via a stable DNS name within the cluster</p>
        </div>
        <UToggle v-model="store.wizard.createService" color="primary" />
      </div>

      <div v-if="store.wizard.createService" class="mt-4 p-3 bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 text-xs font-mono text-gray-500">
        <span class="text-primary-500">{{ store.wizard.name || 'app-name' }}</span>.<span class="text-blue-500">{{ store.wizard.namespace || 'default' }}</span>.svc.cluster.local:<span class="text-green-500">{{ firstPortNumber }}</span>
      </div>
    </div>

    <!-- Ingress toggle -->
    <div class="p-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl border border-gray-200 dark:border-gray-700">
      <div class="flex items-center justify-between">
        <div>
          <h4 class="text-sm font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
            <UIcon name="i-lucide-globe" class="w-4 h-4 text-blue-500" />
            Create Ingress (External Access)
          </h4>
          <p class="text-xs text-gray-400 mt-0.5">Expose your service via an HTTP/HTTPS hostname</p>
        </div>
        <UToggle
          v-model="store.wizard.createIngress"
          color="blue"
          :disabled="!store.wizard.createService"
        />
      </div>

      <div v-if="!store.wizard.createService && store.wizard.createIngress" class="mt-2 text-xs text-amber-600">
        ⚠ A Service must be created first for Ingress to work.
      </div>

      <Transition name="fade">
        <div v-if="store.wizard.createIngress && store.wizard.createService" class="mt-4 space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <UFormGroup label="Hostname" required>
              <UInput
                v-model="store.wizard.ingressHost"
                placeholder="app.example.com"
                icon="i-lucide-globe"
                size="sm"
              />
            </UFormGroup>
            <UFormGroup label="Path">
              <UInput
                v-model="store.wizard.ingressPath"
                placeholder="/"
                size="sm"
              />
            </UFormGroup>
          </div>
          <UFormGroup label="Ingress Class">
            <USelectMenu
              v-model="store.wizard.ingressClass"
              :items="['nginx', 'traefik', 'istio', 'haproxy', 'contour']"
              size="sm"
            />
          </UFormGroup>
        </div>
      </Transition>
    </div>
  </div>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s, transform 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(-8px); }
</style>

