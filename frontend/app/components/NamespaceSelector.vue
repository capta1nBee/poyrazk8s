<script setup lang="ts">
const clusterStore = useClusterStore()
const k8s = useKubernetes()
const toast = useToast()
const authStore = useAuthStore()
const { $api } = useNuxtApp()

const props = defineProps<{
  collapsed?: boolean
  /** When true, fetches ALL namespaces unfiltered via /namespaces/for-page (T3 pages) */
  forPage?: boolean
}>()

const namespaces = ref<string[]>([])
const loading = ref(false)

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

const fetchNamespaces = async () => {
  if (!selectedCluster.value) return

  loading.value = true
  try {
    let namespaceNames: string[] = []

    if (props.forPage) {
      // T3 pages: return ALL namespaces unfiltered
      const response = await $api.get<string[]>(`/k8s/${selectedCluster.value.uid}/namespaces/for-page`)
      namespaceNames = (response.data ?? []).sort()
    } else if (authStore.user?.isSuperadmin) {
      const result = await k8s.fetchNamespaces()
      namespaceNames = result.map((ns: any) => ns.name).sort()
    } else {
      // Kullanıcının yetkili olduğu namespace'leri getir
      namespaceNames = await k8s.fetchAuthorizedNamespaces()
    }

    namespaces.value = namespaceNames

    // Eğer seçili namespace yetkili namespace'ler arasında değilse, ilk yetkili namespace'i seç
    if (selectedNamespace.value && !namespaces.value.includes(selectedNamespace.value)) {
      if (namespaces.value.length > 0) {
        selectNamespace(namespaces.value[0])
      } else {
        clusterStore.selectNamespace('')
      }
    }
    // Eğer hiç namespace seçili değilse ve yetkili namespace'ler varsa, ilkini seç
    else if (!selectedNamespace.value && namespaces.value.length > 0) {
      selectNamespace(namespaces.value[0])
    }
  } catch (error) {
    console.error('Failed to fetch namespaces:', error)
    namespaces.value = []
    // Hata durumunda namespace seçimini temizle
    if (selectedNamespace.value) {
      clusterStore.selectNamespace('')
    }
  } finally {
    loading.value = false
  }
}

const selectNamespace = (namespace: string) => {
  clusterStore.selectNamespace(namespace)
  toast.add({
    title: 'Namespace changed',
    description: `Switched to namespace: ${namespace}`,
    color: 'blue'
  })
}

/**
 * 🔴 KRİTİK KISIM
 * - items => grup array
 * - click ❌
 * - onSelect ✅
 */
const dropdownItems = computed(() => [
  namespaces.value.map(ns => ({
    label: ns,
    icon: ns === selectedNamespace.value ? 'i-lucide-check' : undefined,
    onSelect: () => selectNamespace(ns)
  }))
])

watch(selectedCluster, fetchNamespaces, { immediate: true })
</script>

<template>
  <div v-if="selectedCluster" class="flex items-center gap-1">
    <UDropdownMenu
      :items="dropdownItems"
      :popper="{ placement: 'bottom-start' }"
      :ui="{ content: 'max-h-96 overflow-y-auto' }"
    >
      <UButton
        :label="loading ? 'Loading...' : selectedNamespace || 'Select namespace'"
        icon="i-lucide-folder"
        trailing-icon="i-lucide-chevron-down"
        color="neutral"
        variant="ghost"
        size="sm"
        :loading="loading"
      />
    </UDropdownMenu>
  </div>
</template>
