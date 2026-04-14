<script setup lang="ts">
const colorMode = useColorMode()
const authStore = useAuthStore()
const clusterStore = useClusterStore()

const color = computed(() => colorMode.value === 'dark' ? '#0f172a' : '#f8fafc')

// Initialize auth and clusters
onMounted(async () => {
  if (import.meta.client) {
    // Initialize preferences (font)
    const { initializePreferences } = usePreferences()
    initializePreferences()
    
    authStore.initializeAuth()

    if (authStore.isAuthenticated) {
      try {
        await clusterStore.fetchClusters()
        clusterStore.initializeSelection()
      } catch (error) {
        console.error('Failed to initialize clusters:', error)
      }
    }
  }
})

useHead({
  meta: [
    { charset: 'utf-8' },
    { name: 'viewport', content: 'width=device-width, initial-scale=1' },
    { key: 'theme-color', name: 'theme-color', content: color }
  ],
  link: [
    { rel: 'icon', href: '/favicon.ico' }
  ],
  htmlAttrs: {
    lang: 'en'
  }
})

const title = 'Poyraz Kubernetes'
const description = 'Enterprise Multi-Cluster Kubernetes Management Platform'

useSeoMeta({
  title,
  description,
  ogTitle: title,
  ogDescription: description,
  twitterCard: 'summary_large_image'
})
</script>

<template>
  <UApp :ui="{ root: 'min-h-screen bg-neutral-50 dark:bg-neutral-950' }">
    <NuxtLoadingIndicator color="#3b82f6" />

    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
    <GlobalConfirmModal />
  </UApp>
</template>

<style>
/* Smooth theme transition */
html {
  transition: background-color 0.3s ease, color 0.3s ease;
}

/* Apply dynamic font */
html, body {
  font-family: var(--font-sans);
}
</style>
