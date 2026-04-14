<script setup lang="ts">
import { useAppCreatorStore } from '~/stores/appcreator'

const store = useAppCreatorStore()
const { $api } = useNuxtApp()
const clusterStore = useClusterStore()

// IngressClasses from cluster
const ingressClasses = ref<string[]>(['nginx', 'traefik', 'istio'])
const pathTypeOptions = ['Prefix', 'Exact', 'ImplementationSpecific']

onMounted(async () => {
  if (!clusterStore.selectedCluster) return
  try {
    const res = await $api.get(`/k8s/${clusterStore.selectedCluster.uid}/ingressclasses`)
    if (Array.isArray(res.data) && res.data.length) ingressClasses.value = res.data.map((ic: any) => ic.name || ic)
  } catch {}
})

// Ingress annotation management
const annKey = ref('')
const annVal = ref('')
const addAnnotation = () => {
  if (!annKey.value.trim()) return
  if (!store.wizard.ingressConfig.annotations) store.wizard.ingressConfig.annotations = {}
  store.wizard.ingressConfig.annotations[annKey.value.trim()] = annVal.value
  annKey.value = ''
  annVal.value = ''
}
const removeAnnotation = (key: string) => {
  const anns = { ...store.wizard.ingressConfig.annotations }
  delete anns[key]
  store.wizard.ingressConfig.annotations = anns
}
const annotationEntries = computed(() => Object.entries(store.wizard.ingressConfig.annotations ?? {}))

// ConfigMap management
const cmName = ref('')
const cmKey = ref('')
const cmVal = ref('')
const addCm = () => {
  if (!cmName.value.trim()) return
  const existing = store.wizard.configMaps.find(c => c.name === cmName.value.trim())
  if (existing) { existing.data[cmKey.value] = cmVal.value }
  else { store.wizard.configMaps.push({ name: cmName.value.trim(), data: cmKey.value ? { [cmKey.value]: cmVal.value } : {} }) }
  cmKey.value = ''; cmVal.value = ''
}
const removeCm = (idx: number) => store.wizard.configMaps.splice(idx, 1)

// Secret management
const secName = ref('')
const secKey = ref('')
const secVal = ref('')
const addSec = () => {
  if (!secName.value.trim()) return
  const existing = store.wizard.secrets.find(s => s.name === secName.value.trim())
  if (existing) { existing.data[secKey.value] = secVal.value }
  else { store.wizard.secrets.push({ name: secName.value.trim(), type: 'Opaque', data: secKey.value ? { [secKey.value]: secVal.value } : {} }) }
  secKey.value = ''; secVal.value = ''
}
const removeSec = (idx: number) => store.wizard.secrets.splice(idx, 1)
</script>

<template>
  <div class="space-y-6">
    <div>
      <h3 class="text-base font-semibold text-gray-900 dark:text-white">Add-ons</h3>
      <p class="text-sm text-gray-500 mt-0.5">Configure networking, auto-scaling, and configuration resources.</p>
    </div>

    <!-- Service -->
    <div class="p-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl border border-gray-200 dark:border-gray-700 space-y-3">
      <div class="flex items-center justify-between">
        <h4 class="text-sm font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
          <UIcon name="i-lucide-share-2" class="w-4 h-4 text-primary-500" />Service
        </h4>
        <USwitch v-model="store.wizard.createService" />
      </div>
      <template v-if="store.wizard.createService">
        <div>
          <p class="text-xs font-medium text-gray-600 dark:text-gray-400 mb-1.5">Service Type</p>
          <div class="flex gap-2">
            <button
              v-for="t in ['ClusterIP','LoadBalancer','NodePort']" :key="t"
              :class="['px-3 py-1.5 rounded-lg border text-xs font-medium transition-all', store.wizard.serviceConfig.type === t ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 text-primary-600' : 'border-gray-200 dark:border-gray-700 text-gray-500']"
              @click="store.wizard.serviceConfig.type = t as any"
            >{{ t }}</button>
          </div>
        </div>
        <p class="text-xs text-gray-500">
          Service will be named <code class="font-mono">{{ store.wizard.name || 'app-name' }}</code> in namespace <code class="font-mono">{{ store.wizard.namespace }}</code>.
        </p>
      </template>
    </div>

    <!-- Ingress -->
    <div class="p-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl border border-gray-200 dark:border-gray-700 space-y-3">
      <div class="flex items-center justify-between">
        <h4 class="text-sm font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
          <UIcon name="i-lucide-globe" class="w-4 h-4 text-blue-500" />Ingress
        </h4>
        <USwitch v-model="store.wizard.createIngress" :disabled="!store.wizard.createService" />
      </div>
      <template v-if="store.wizard.createIngress && store.wizard.createService">
        <div class="grid grid-cols-2 gap-3">
          <UFormField label="Hostname" required>
            <UInput v-model="store.wizard.ingressConfig.host" placeholder="app.example.com" size="sm" icon="i-lucide-globe" class="w-full" />
          </UFormField>
          <UFormField label="Path">
            <UInput v-model="store.wizard.ingressConfig.path" placeholder="/" size="sm" icon="i-lucide-route" class="w-full" />
          </UFormField>
          <UFormField label="Path Type">
            <div class="flex gap-1.5 flex-wrap pt-1">
              <button
                v-for="pt in pathTypeOptions" :key="pt"
                :class="['px-2.5 py-1 rounded-md border text-xs font-medium transition-all', store.wizard.ingressConfig.pathType === pt ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400' : 'border-gray-200 dark:border-gray-700 text-gray-500 hover:border-gray-300']"
                @click="store.wizard.ingressConfig.pathType = pt as any"
              >{{ pt }}</button>
            </div>
          </UFormField>
          <UFormField label="Ingress Class">
            <USelectMenu
              v-model="store.wizard.ingressConfig.ingressClass"
              :items="ingressClasses"
              size="sm"
              class="w-full"
            />
          </UFormField>
        </div>
        <!-- TLS -->
        <div class="flex items-center gap-3 pt-1">
          <div class="flex items-center gap-2">
            <USwitch v-model="store.wizard.ingressConfig.tlsEnabled" size="sm" />
            <span class="text-xs font-medium text-gray-600 dark:text-gray-400">Enable TLS</span>
          </div>
          <UInput
            v-if="store.wizard.ingressConfig.tlsEnabled"
            v-model="store.wizard.ingressConfig.tlsSecret"
            placeholder="tls-secret-name"
            size="sm"
            class="flex-1"
            icon="i-lucide-lock"
          />
        </div>
        <!-- Annotations -->
        <div class="space-y-2 pt-1">
          <p class="text-xs font-semibold text-gray-600 dark:text-gray-400 flex items-center gap-1.5">
            <UIcon name="i-lucide-tags" class="w-3.5 h-3.5" />
            Annotations
            <UBadge v-if="annotationEntries.length" color="blue" variant="subtle" size="xs">{{ annotationEntries.length }}</UBadge>
          </p>
          <div class="flex gap-2">
            <UInput v-model="annKey" placeholder="nginx.ingress.kubernetes.io/rewrite-target" size="sm" class="flex-1" @keyup.enter="addAnnotation" />
            <UInput v-model="annVal" placeholder="/" size="sm" class="w-24" @keyup.enter="addAnnotation" />
            <UButton size="sm" color="blue" variant="soft" icon="i-lucide-plus" @click="addAnnotation" />
          </div>
          <div v-for="[key, val] in annotationEntries" :key="key" class="flex items-center gap-2 p-2 bg-blue-50/60 dark:bg-blue-900/10 border border-blue-200/50 rounded-lg text-xs">
            <code class="text-blue-700 dark:text-blue-300 flex-1 truncate">{{ key }}: {{ val }}</code>
            <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-x" @click="removeAnnotation(key)" />
          </div>
        </div>
      </template>
      <p v-if="!store.wizard.createService" class="text-xs text-amber-600 dark:text-amber-400">⚠ Enable Service first to configure Ingress.</p>
    </div>

    <!-- HPA -->
    <div class="p-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl border border-gray-200 dark:border-gray-700 space-y-3">
      <div class="flex items-center justify-between">
        <h4 class="text-sm font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
          <UIcon name="i-lucide-trending-up" class="w-4 h-4 text-green-500" />Horizontal Pod Autoscaler (HPA)
        </h4>
        <USwitch v-model="store.wizard.hpa.enabled" color="success" />
      </div>
      <template v-if="store.wizard.hpa.enabled">
        <div class="grid grid-cols-2 gap-3">
          <UFormField label="Min Replicas">
            <UInput v-model.number="store.wizard.hpa.minReplicas" type="number" min="1" size="sm" class="w-full" />
          </UFormField>
          <UFormField label="Max Replicas">
            <UInput v-model.number="store.wizard.hpa.maxReplicas" type="number" :min="store.wizard.hpa.minReplicas" size="sm" class="w-full" />
          </UFormField>
          <UFormField label="Target CPU %">
            <UInput v-model.number="store.wizard.hpa.targetCPU" type="number" min="0" max="100" size="sm" class="w-full" />
          </UFormField>
          <UFormField label="Target Memory %">
            <UInput v-model.number="store.wizard.hpa.targetMemory" type="number" min="0" max="100" size="sm" class="w-full" />
          </UFormField>
        </div>
      </template>
    </div>

    <!-- ConfigMaps -->
    <div class="space-y-3">
      <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
        <UIcon name="i-lucide-file-cog" class="w-4 h-4 text-blue-500" />ConfigMaps
        <UBadge v-if="store.wizard.configMaps.length" color="blue" variant="subtle" size="xs">{{ store.wizard.configMaps.length }}</UBadge>
      </h4>
      <div class="flex gap-2">
        <UInput v-model="cmName" placeholder="configmap-name" size="sm" class="flex-1" />
        <UInput v-model="cmKey" placeholder="KEY" size="sm" class="w-28" />
        <UInput v-model="cmVal" placeholder="value" size="sm" class="flex-1" @keyup.enter="addCm" />
        <UButton size="sm" color="blue" variant="soft" icon="i-lucide-plus" @click="addCm" />
      </div>
      <div v-for="(cm, idx) in store.wizard.configMaps" :key="idx" class="flex items-center gap-2 p-2.5 bg-blue-50/50 dark:bg-blue-900/10 border border-blue-200/50 rounded-lg text-xs">
        <UIcon name="i-lucide-file-cog" class="w-3.5 h-3.5 text-blue-500 shrink-0" />
        <span class="font-mono font-medium text-blue-700 dark:text-blue-300 flex-1">{{ cm.name }}</span>
        <span class="text-gray-400">{{ Object.keys(cm.data).length }} keys</span>
        <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-x" @click="removeCm(idx)" />
      </div>
    </div>

    <!-- Secrets -->
    <div class="space-y-3">
      <h4 class="text-sm font-semibold text-gray-700 dark:text-gray-300 flex items-center gap-2">
        <UIcon name="i-lucide-shield" class="w-4 h-4 text-red-500" />Secrets
        <UBadge v-if="store.wizard.secrets.length" color="red" variant="subtle" size="xs">{{ store.wizard.secrets.length }}</UBadge>
      </h4>
      <div class="flex gap-2">
        <UInput v-model="secName" placeholder="secret-name" size="sm" class="flex-1" />
        <UInput v-model="secKey" placeholder="KEY" size="sm" class="w-28" />
        <UInput v-model="secVal" placeholder="value" type="password" size="sm" class="flex-1" @keyup.enter="addSec" />
        <UButton size="sm" color="red" variant="soft" icon="i-lucide-plus" @click="addSec" />
      </div>
      <div v-for="(sec, idx) in store.wizard.secrets" :key="idx" class="flex items-center gap-2 p-2.5 bg-red-50/50 dark:bg-red-900/10 border border-red-200/50 rounded-lg text-xs">
        <UIcon name="i-lucide-shield" class="w-3.5 h-3.5 text-red-500 shrink-0" />
        <span class="font-mono font-medium text-red-700 dark:text-red-300 flex-1">{{ sec.name }}</span>
        <span class="text-gray-400">{{ Object.keys(sec.data).length }} keys • ••••••</span>
        <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-x" @click="removeSec(idx)" />
      </div>
    </div>
  </div>
</template>

