<script setup lang="ts">
import type { NuxtError } from '#app'

const props = defineProps<{
  error: NuxtError
}>()

const is403 = computed(() => props.error.statusCode === 403)
const is404 = computed(() => props.error.statusCode === 404)

const title = computed(() => {
  if (is403.value) return 'Erişim Reddedildi'
  if (is404.value) return 'Sayfa Bulunamadı'
  return 'Bir Hata Oluştu'
})

const description = computed(() => {
  if (is403.value) return 'Bu sayfaya erişim izniniz bulunmamaktadır.'
  if (is404.value) return 'Aradığınız sayfa bulunamadı.'
  return props.error.message || 'Beklenmeyen bir hata oluştu.'
})

const icon = computed(() => {
  if (is403.value) return 'i-lucide-shield-x'
  if (is404.value) return 'i-lucide-file-question'
  return 'i-lucide-alert-triangle'
})

const iconColor = computed(() => {
  if (is403.value) return 'text-orange-500'
  if (is404.value) return 'text-gray-500'
  return 'text-red-500'
})

useSeoMeta({
  title: title.value,
  description: description.value
})

useHead({
  htmlAttrs: {
    lang: 'tr'
  }
})

const authStore = useAuthStore()

const handleBack = () => {
  // Clear error and redirect to home without losing session
  clearError({ redirect: '/' })
}

const handleLogin = () => {
  // Logout current user and redirect to login
  authStore.logout()
  clearError({ redirect: '/login' })
}
</script>

<template>
  <UApp>
    <div class="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div class="max-w-md w-full text-center space-y-6">
        <!-- Icon -->
        <div class="flex justify-center">
          <UIcon :name="icon" :class="[iconColor, 'w-24 h-24']" />
        </div>

        <!-- Error Code -->
        <div class="text-6xl font-bold text-gray-300 dark:text-gray-700">
          {{ error.statusCode }}
        </div>

        <!-- Title -->
        <h1 class="text-2xl font-semibold text-gray-900 dark:text-white">
          {{ title }}
        </h1>

        <!-- Description -->
        <p class="text-gray-600 dark:text-gray-400">
          {{ description }}
        </p>

        <!-- Additional info for 403 -->
        <div v-if="is403" class="bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800 rounded-lg p-4">
          <p class="text-sm text-orange-700 dark:text-orange-300">
            <UIcon name="i-lucide-info" class="inline-block mr-1" />
            Bu sayfayı görüntülemek için yetkiniz bulunmuyor.
            Yöneticinizle iletişime geçerek gerekli izinleri talep edebilirsiniz.
          </p>
        </div>

        <!-- Actions -->
        <div class="flex flex-col sm:flex-row gap-3 justify-center pt-4">
          <UButton
            label="Ana Sayfaya Dön"
            icon="i-lucide-home"
            size="lg"
            @click="handleBack"
          />
          <UButton
            v-if="is403"
            label="Farklı Hesapla Giriş Yap"
            icon="i-lucide-log-in"
            variant="outline"
            size="lg"
            @click="handleLogin"
          />
        </div>

        <!-- Error details (collapsed) -->
        <UCollapsible v-if="error.message && !is403 && !is404" class="mt-6">
          <UButton
            label="Hata Detaylarını Göster"
            icon="i-lucide-chevron-down"
            variant="ghost"
            size="sm"
            color="neutral"
            block
          />
          <template #content>
            <div class="mt-2 p-4 bg-gray-100 dark:bg-gray-800 rounded-lg text-left">
              <pre class="text-xs text-gray-600 dark:text-gray-400 overflow-auto">{{ error.message }}</pre>
            </div>
          </template>
        </UCollapsible>
      </div>
    </div>
  </UApp>
</template>
