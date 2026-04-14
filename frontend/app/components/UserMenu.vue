<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'

defineProps<{
  collapsed?: boolean
}>()

const authStore = useAuthStore()
const colorMode = useColorMode()
const toast = useToast()
const { currentFont, availableFonts, setFont, setTheme } = usePreferences()

const showExpiryWarning = ref(false)
const remainingTime = ref('')
const remainingSeconds = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const updateTimer = () => {
  if (!authStore.tokenExpiry) return

  const now = Math.floor(Date.now() / 1000)
  const diff = authStore.tokenExpiry - now
  remainingSeconds.value = diff

  if (diff <= 0) {
    authStore.logout()
    if (import.meta.client) window.location.href = '/login'
    if (timer) clearInterval(timer)
    return
  }

  // Show warning if 5 minutes (300 seconds) or less
  if (diff <= 300 && !showExpiryWarning.value) {
    showExpiryWarning.value = true
  }

  const mins = Math.floor(diff / 60)
  const secs = diff % 60
  remainingTime.value = `${mins}:${secs.toString().padStart(2, '0')}`
}

onMounted(() => {
  if (import.meta.client) {
    timer = setInterval(updateTimer, 1000)
    updateTimer()
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const extendSession = async () => {
  try {
    await authStore.extendSession()
    showExpiryWarning.value = false
    toast.add({
      title: 'Session extended',
      color: 'green'
    })
  } catch (error) {
    console.error('Failed to extend session:', error)
  }
}

const user = computed(() => ({
  name: authStore.username || 'User',
  avatar: {
    alt: authStore.username || 'User'
  }
}))

const handleLogout = async () => {
  try {
    authStore.logout()

    toast.add({
      title: 'Logged out',
      description: 'You have been successfully logged out',
      color: 'green'
    })

    if (import.meta.client) {
      window.location.href = '/login'
    } else {
      await navigateTo('/login', { replace: true })
    }
  } catch (error) {
    console.error('Logout error:', error)
    if (import.meta.client) {
      window.location.href = '/login'
    }
  }
}

// Font style icons
const getFontStyleIcon = (style: string) => {
  switch (style) {
    case 'modern': return 'i-lucide-sparkles'
    case 'classic': return 'i-lucide-book-open'
    case 'technical': return 'i-lucide-code'
    case 'elegant': return 'i-lucide-crown'
    case 'minimal': return 'i-lucide-minus'
    default: return 'i-lucide-type'
  }
}

const items = computed<DropdownMenuItem[][]>(() => ([[{
  type: 'label',
  label: user.value.name,
  avatar: user.value.avatar
}], [{
  label: 'Settings',
  icon: 'i-lucide-settings',
  to: '/settings'
}], [{
  label: 'Theme',
  icon: 'i-lucide-palette',
  children: [{
    label: 'Light',
    icon: 'i-lucide-sun',
    type: 'checkbox',
    checked: colorMode.value === 'light',
    onSelect(e: Event) {
      e.preventDefault()
      setTheme('light')
    }
  }, {
    label: 'Dark',
    icon: 'i-lucide-moon',
    type: 'checkbox',
    checked: colorMode.value === 'dark',
    onSelect(e: Event) {
      e.preventDefault()
      setTheme('dark')
    }
  }, {
    label: 'System',
    icon: 'i-lucide-laptop',
    type: 'checkbox',
    checked: colorMode.preference === 'system',
    onSelect(e: Event) {
      e.preventDefault()
      setTheme('system')
    }
  }]
}, {
  label: 'Font',
  icon: 'i-lucide-type',
  children: availableFonts.map(font => ({
    label: font.label,
    icon: getFontStyleIcon(font.style),
    type: 'checkbox' as const,
    checked: currentFont.value.name === font.name,
    onSelect(e: Event) {
      e.preventDefault()
      setFont(font.name)
      toast.add({
        title: 'Font changed',
        description: `Switched to ${font.label}`,
        color: 'green'
      })
    }
  }))
}], [{
  label: 'Log out',
  icon: 'i-lucide-log-out',
  color: 'error' as const,
  onSelect: handleLogout
}]]))
</script>

<template>
  <div class="flex flex-col">
    <UDropdownMenu
      :items="items"
      :content="{ align: 'center', collisionPadding: 12 }"
      :ui="{ content: collapsed ? 'w-56' : 'w-(--reka-dropdown-menu-trigger-width) min-w-56' }"
    >
      <UButton
        v-bind="{
          ...user,
          label: collapsed ? undefined : user?.name,
          trailingIcon: collapsed ? undefined : 'i-lucide-chevrons-up-down'
        }"
        color="neutral"
        variant="ghost"
        block
        :square="collapsed"
        class="data-[state=open]:bg-neutral-100 dark:data-[state=open]:bg-neutral-800 transition-colors"
        :ui="{
          trailingIcon: 'text-neutral-400'
        }"
      />
    </UDropdownMenu>

    <div v-if="!collapsed && remainingSeconds > 0" class="px-3 py-1.5 text-[10px] text-neutral-400 font-mono text-center tracking-wider">
      Session: {{ remainingTime }}
    </div>

    <!-- Session Expiry Modal -->
    <UModal
      v-if="showExpiryWarning"
      v-model:open="showExpiryWarning"
      :prevent-close="true"
      :ui="{ width: 'sm:max-w-md' }"
      title="Session Expiring"
      description="Automatic Logout Imminent"
    >
      <template #body>
        <div class="space-y-6">
          <div class="flex items-center gap-4 text-amber-500">
            <div class="w-14 h-14 rounded-2xl bg-amber-50 dark:bg-amber-950/30 flex items-center justify-center">
              <UIcon name="i-lucide-clock-3" class="w-8 h-8" />
            </div>
            <div>
              <h3 class="text-xl font-bold tracking-tight">Session Expiring</h3>
              <p class="text-xs font-medium text-amber-500/70 uppercase tracking-wide">Automatic Logout Imminent</p>
            </div>
          </div>

          <div class="p-5 rounded-xl bg-neutral-50 dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 space-y-4 text-center">
            <p class="text-sm text-neutral-600 dark:text-neutral-300 leading-relaxed">
              Your secure session is about to expire. For your security, you will be logged out in:
            </p>
            <div class="text-4xl font-bold font-mono text-primary-500 tracking-tight tabular-nums animate-pulse">
              {{ remainingTime }}
            </div>
            <p class="text-xs text-neutral-400 font-medium">
              Would you like to extend your session and continue working?
            </p>
          </div>

          <div class="flex items-center gap-3 pt-2">
            <UButton
              color="neutral"
              variant="outline"
              size="lg"
              block
              class="flex-1 font-medium py-3"
              @click="handleLogout"
            >
              Logout Now
            </UButton>
            <UButton
              color="primary"
              variant="solid"
              size="lg"
              block
              class="flex-1 font-semibold py-3 shadow-lg shadow-primary-500/20"
              @click="extendSession"
            >
              Extend Session
            </UButton>
          </div>
        </div>
      </template>
    </UModal>
  </div>
</template>
