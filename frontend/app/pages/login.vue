<script setup lang="ts">
definePageMeta({ layout: false })

const authStore = useAuthStore()
const toast = useToast()

// Safe color mode access
const colorMode = ref<'light' | 'dark'>('dark')

onMounted(() => {
  if (import.meta.client) {
    const cm = useColorMode()
    colorMode.value = cm.value as 'light' | 'dark'
    watch(() => cm.value, (val) => {
      colorMode.value = val as 'light' | 'dark'
    })
  }
})

const form = reactive({
  username: '',
  password: ''
})

const loading = ref(false)
const errorMessage = ref('')

const handleLogin = async () => {
  if (!form.username || !form.password) return

  loading.value = true
  errorMessage.value = ''

  try {
    await authStore.login(form)
    toast.add({ title: 'Login successful', color: 'green' })
    navigateTo('/')
  } catch {
    errorMessage.value = 'Invalid credentials'
  } finally {
    loading.value = false
  }
}

const toggleTheme = () => {
  if (import.meta.client) {
    const cm = useColorMode()
    cm.preference = colorMode.value === 'dark' ? 'light' : 'dark'
  }
}
</script>

<template>
  <div class="relative min-h-screen bg-gradient-to-br from-slate-50 via-blue-50 to-white dark:from-neutral-950 dark:via-neutral-900 dark:to-neutral-950 overflow-hidden">
    <!-- Compass Background -->
    <div class="absolute -top-32 -left-32 z-0 pointer-events-none">
      <svg class="w-[600px] h-[600px] opacity-[0.06] dark:opacity-[0.04]" viewBox="0 0 200 200">
        <circle cx="100" cy="100" r="90" stroke="#2563eb" stroke-width="1" fill="none" />
        <circle cx="100" cy="100" r="60" stroke="#94a3b8" stroke-width="0.8" fill="none" />
        <circle cx="100" cy="100" r="30" stroke="#94a3b8" stroke-width="0.5" fill="none" />
        <line x1="100" y1="0" x2="100" y2="200" stroke="#2563eb" stroke-width="1" />
        <line x1="0" y1="100" x2="200" y2="100" stroke="#64748b" stroke-width="0.6" />
        <line x1="30" y1="30" x2="170" y2="170" stroke="#94a3b8" stroke-width="0.4" />
        <line x1="170" y1="30" x2="30" y2="170" stroke="#94a3b8" stroke-width="0.4" />
        <polygon points="100,5 95,18 105,18" fill="#2563eb" />
        <text x="100" y="195" text-anchor="middle" font-size="8" fill="#64748b" font-weight="500">S</text>
        <text x="100" y="12" text-anchor="middle" font-size="8" fill="#2563eb" font-weight="600">N</text>
        <text x="8" y="103" text-anchor="middle" font-size="8" fill="#64748b" font-weight="500">W</text>
        <text x="192" y="103" text-anchor="middle" font-size="8" fill="#64748b" font-weight="500">E</text>
      </svg>
    </div>

    <!-- Secondary Compass (bottom right) -->
    <div class="absolute -bottom-20 -right-20 z-0 pointer-events-none">
      <svg class="w-[400px] h-[400px] opacity-[0.04] dark:opacity-[0.03]" viewBox="0 0 200 200">
        <circle cx="100" cy="100" r="90" stroke="#3b82f6" stroke-width="1" fill="none" />
        <circle cx="100" cy="100" r="60" stroke="#64748b" stroke-width="0.5" fill="none" />
        <line x1="100" y1="10" x2="100" y2="190" stroke="#3b82f6" stroke-width="0.8" />
        <line x1="10" y1="100" x2="190" y2="100" stroke="#64748b" stroke-width="0.5" />
        <polygon points="100,15 96,25 104,25" fill="#3b82f6" />
      </svg>
    </div>

    <!-- Theme Toggle -->
    <div class="absolute top-6 right-6 z-20">
      <UButton
        :icon="colorMode === 'dark' ? 'i-lucide-sun' : 'i-lucide-moon'"
        color="neutral"
        variant="ghost"
        size="lg"
        square
        @click="toggleTheme"
      />
    </div>

    <!-- Login Container -->
    <div class="relative z-10 flex min-h-screen items-center justify-center px-4 py-12">
      <div class="w-full max-w-md animate-fade-in">
        <!-- Logo & Branding -->
        <div class="text-center mb-8">
          <div class="mb-6 flex justify-center">
            <img src="~/assets/logo/logo.svg" class="w-16 h-16" alt="Logo" />
          </div>
          <h1 class="text-3xl font-bold text-neutral-900 dark:text-neutral-100 tracking-tight">
            Poyraz
          </h1>
          <p class="text-neutral-500 dark:text-neutral-400 mt-2">
            Kubernetes Management Console
          </p>
        </div>

        <!-- Login Card -->
        <div class="bg-white dark:bg-neutral-900 rounded-2xl shadow-xl shadow-neutral-200/50 dark:shadow-neutral-950/50 border border-neutral-200 dark:border-neutral-800 overflow-hidden">
          <!-- Card Header -->
          <div class="px-8 pt-8 pb-2">
            <h2 class="text-xl font-semibold text-neutral-900 dark:text-neutral-100">
              Welcome back
            </h2>
            <p class="text-sm text-neutral-500 dark:text-neutral-400 mt-1">
              Sign in to access your clusters
            </p>
          </div>

          <!-- Card Body -->
          <div class="px-8 py-6">
            <UAlert
              v-if="errorMessage"
              color="error"
              variant="soft"
              class="mb-6"
              icon="i-lucide-alert-circle"
            >
              <template #title>
                {{ errorMessage }}
              </template>
            </UAlert>

            <form @submit.prevent="handleLogin" class="space-y-5">
              <div class="space-y-2">
                <label class="text-sm font-medium text-neutral-700 dark:text-neutral-300">
                  Username
                </label>
                <UInput
                  v-model="form.username"
                  placeholder="Enter your username"
                  icon="i-lucide-user"
                  size="lg"
                  :disabled="loading"
                  class="w-full"
                />
              </div>

              <div class="space-y-2">
                <label class="text-sm font-medium text-neutral-700 dark:text-neutral-300">
                  Password
                </label>
                <UInput
                  v-model="form.password"
                  type="password"
                  placeholder="Enter your password"
                  icon="i-lucide-lock"
                  size="lg"
                  :disabled="loading"
                  class="w-full"
                />
              </div>

              <UButton
                type="submit"
                block
                size="lg"
                :loading="loading"
                class="mt-6 font-semibold shadow-lg shadow-primary-500/25 hover:shadow-xl hover:shadow-primary-500/30 transition-all duration-200"
              >
                <span v-if="!loading" class="flex items-center gap-2">
                  Sign in
                  <UIcon name="i-lucide-arrow-right" class="w-4 h-4" />
                </span>
                <span v-else>Signing in...</span>
              </UButton>
            </form>
          </div>
        </div>

        <!-- Footer -->
        <div class="mt-8 text-center">
          <p class="text-xs text-neutral-400 dark:text-neutral-500">
            © 2026 Poyraz • Internal Platform
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.5s ease-out forwards;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
