<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

definePageMeta({
  layout: 'default'
})

const route = useRoute()

// Redirect to /settings/mail if on /settings
if (route.path === '/settings') {
  navigateTo('/settings/mail', { replace: true })
}

const links = [[{
  label: 'Mail',
  icon: 'i-lucide-mail',
  to: '/settings/mail'
}, {
  label: 'LDAP',
  icon: 'i-lucide-users-round',
  to: '/settings/ldap'
}, {
  label: 'Users',
  icon: 'i-lucide-user-cog',
  to: '/settings/users'
}, {
  label: 'Exec Perms',
  icon: 'i-lucide-shield-check',
  to: '/settings/command-permissions'
}, {
  label: 'Exec Sessions',
  icon: 'i-lucide-video',
  to: '/settings/exec-sessions',
  badge: 'Admin'
}, {
  label: 'Helm Repos',
  icon: 'i-lucide-database',
  to: '/settings/helm-repos'
}, {
  label: 'Runtime Security',
  icon: 'i-lucide-shield-alert',
  to: '/settings/runtime-security'
}, {
  label: 'Page Settings',
  icon: 'i-lucide-file-code',
  to: '/settings/pages-actions'
}]] satisfies NavigationMenuItem[][]
</script>

<template>
  <UDashboardPanel id="settings" :ui="{ body: 'lg:py-12' }">
    <template #header>
      <UDashboardNavbar title="Settings">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <!-- NOTE: The `-mx-1` class is used to align with the `DashboardSidebarCollapse` button here. -->
        <UNavigationMenu :items="links" highlight class="-mx-1 flex-1" />
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="flex flex-col gap-4 sm:gap-6 lg:gap-12 w-full mx-auto px-4">
        <NuxtPage />
      </div>
    </template>
  </UDashboardPanel>
</template>
