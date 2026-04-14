<script setup lang="ts">
const { $api } = useNuxtApp()
const toast = useToast()
const authStore = useAuthStore()

// Check if user is superadmin for pages-actions tab
const showPagesActionsTab = computed(() => {
  // Check if user is superadmin
  const isSuperadmin = authStore.user?.isSuperadmin === true
  return isSuperadmin
})

// Tab items based on user permissions
const tabItems = computed(() => {
  const items = [
    { label: 'Mail', value: 'mail', icon: 'i-lucide-mail' },
    { label: 'LDAP', value: 'ldap', icon: 'i-lucide-key' },
    { label: 'Users', value: 'users', icon: 'i-lucide-users' },
    { label: 'Exec Perms', value: 'exec-perms', icon: 'i-lucide-terminal' }
  ]
  const shouldShow = showPagesActionsTab.value
  if (shouldShow) {
    items.push({ label: 'Pages Settings', value: 'pages-actions', icon: 'i-lucide-file-code' })
  }
  return items
})

const activeTab = ref('mail')
const loading = ref(false)


const mailSettings = ref({
  host: '',
  port: 587,
  username: '',
  password: '',
  from: '',
  enabled: false
})

const ldapSettings = ref({
  url: '',
  baseDn: '',
  bindDn: '',
  bindPassword: '',
  userSearchBase: '',
  userSearchFilter: '',
  enabled: false
})

const fetchMailSettings = async () => {
  loading.value = true
  try {
    const response = await $api.get('/admin/settings/mail')
    mailSettings.value = { ...mailSettings.value, ...response.data }
  } catch (error: any) {
    console.error('Failed to fetch mail settings:', error)
  } finally {
    loading.value = false
  }
}

const fetchLDAPSettings = async () => {
  loading.value = true
  try {
    const response = await $api.get('/admin/ldap/config')
    ldapSettings.value = { ...ldapSettings.value, ...response.data }
  } catch (error: any) {
    console.error('Failed to fetch LDAP settings:', error)
  } finally {
    loading.value = false
  }
}

const saveMailSettings = async () => {
  loading.value = true
  try {
    await $api.post('/admin/settings/mail', mailSettings.value)
    toast.add({
      title: 'Mail settings saved',
      description: 'Mail configuration has been updated successfully',
      color: 'success'
    })
  } catch (error: any) {
    toast.add({
      title: 'Failed to save mail settings',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

const saveLDAPSettings = async () => {
  loading.value = true
  try {
    await $api.post('/admin/ldap/config', ldapSettings.value)
    toast.add({
      title: 'LDAP settings saved',
      description: 'LDAP configuration has been updated successfully',
      color: 'success'
    })
  } catch (error: any) {
    toast.add({
      title: 'Failed to save LDAP settings',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

const testMailConnection = async () => {
  loading.value = true
  try {
    await $api.post('/admin/settings/mail/test')
    toast.add({
      title: 'Mail test successful',
      description: 'Mail connection test passed',
      color: 'success'
    })
  } catch (error: any) {
    toast.add({
      title: 'Mail test failed',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

const testLDAPConnection = async () => {
  loading.value = true
  try {
    await $api.post('/admin/ldap/test')
    toast.add({
      title: 'LDAP test successful',
      description: 'LDAP connection test passed',
      color: 'success'
    })
  } catch (error: any) {
    toast.add({
      title: 'LDAP test failed',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // Ensure auth is initialized
  if (!authStore.isAuthenticated) {
    authStore.initializeAuth()
  }
  
  // If user is not loaded but we have a token, fetch user info
  if (authStore.isAuthenticated && !authStore.user) {
    try {
      await authStore.fetchCurrentUser()
    } catch (error) {
      console.error('Failed to fetch current user:', error)
    }
  }
  
  fetchMailSettings()
  fetchLDAPSettings()
})
</script>

<template>
  <UDashboardPanel id="settings">
    <template #header>
      <UDashboardNavbar title="Settings">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="p-6">
        <UTabs v-model="activeTab" :items="tabItems">
          <!-- Mail Settings Tab -->
          <template #mail>
            <UCard>
              <template #header>
                <h3 class="text-lg font-semibold">Mail Configuration</h3>
              </template>

              <div class="space-y-4">
                <div class="flex items-center gap-2">
                  <USwitch v-model="mailSettings.enabled" />
                  <span class="text-sm font-medium">Enabled</span>
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">SMTP Host <span class="text-red-500">*</span></label>
                  <UInput v-model="mailSettings.host" placeholder="smtp.example.com" />
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">SMTP Port <span class="text-red-500">*</span></label>
                  <UInput v-model.number="mailSettings.port" type="number" placeholder="587" />
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">Username <span class="text-red-500">*</span></label>
                  <UInput v-model="mailSettings.username" placeholder="your-email@example.com" />
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">Password <span class="text-red-500">*</span></label>
                  <UInput v-model="mailSettings.password" type="password" placeholder="••••••••" />
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">From Address <span class="text-red-500">*</span></label>
                  <UInput v-model="mailSettings.from" placeholder="noreply@example.com" />
                </div>

                <div class="flex gap-2">
                  <UButton label="Save" @click="saveMailSettings" :loading="loading" />
                  <UButton label="Test Connection" variant="outline" @click="testMailConnection" :loading="loading" />
                </div>
              </div>
            </UCard>
          </template>

          <!-- LDAP Settings Tab -->
          <template #ldap>
            <UCard>
              <template #header>
                <h3 class="text-lg font-semibold">LDAP Configuration</h3>
              </template>

              <div class="space-y-4">
                <div class="flex items-center gap-2">
                  <USwitch v-model="ldapSettings.enabled" />
                  <span class="text-sm font-medium">Enabled</span>
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">LDAP URL <span class="text-red-500">*</span></label>
                  <UInput v-model="ldapSettings.url" placeholder="ldap://ldap.example.com:389" />
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">Base DN <span class="text-red-500">*</span></label>
                  <UInput v-model="ldapSettings.baseDn" placeholder="dc=example,dc=com" />
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">Bind DN <span class="text-red-500">*</span></label>
                  <UInput v-model="ldapSettings.bindDn" placeholder="cn=admin,dc=example,dc=com" />
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">Bind Password <span class="text-red-500">*</span></label>
                  <UInput v-model="ldapSettings.bindPassword" type="password" placeholder="••••••••" />
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">User Search Base <span class="text-red-500">*</span></label>
                  <UInput v-model="ldapSettings.userSearchBase" placeholder="ou=users,dc=example,dc=com" />
                </div>

                <div>
                  <label class="block text-sm font-medium mb-1">User Search Filter <span class="text-red-500">*</span></label>
                  <UInput v-model="ldapSettings.userSearchFilter" placeholder="(uid={0})" />
                </div>

                <div class="flex gap-2">
                  <UButton label="Save" @click="saveLDAPSettings" :loading="loading" />
                  <UButton label="Test Connection" variant="outline" @click="testLDAPConnection" :loading="loading" />
                </div>
              </div>
            </UCard>
          </template>

          <!-- Users Management Tab -->
          <template #users>
            <UserManagement />
          </template>

          <!-- Exec Permissions Tab -->
          <template #exec-perms>
            <SettingsCommandPermissions />
          </template>

          <template #pages-actions>
            <PagesActionsContent />
          </template>

        </UTabs>
      </div>
    </template>
  </UDashboardPanel>
</template>


