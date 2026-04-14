<script setup lang="ts">
import type { LDAPConfig } from '~/composables/useSettings'
import type { FormError, FormSubmitEvent } from '#ui/types'

const settings = useSettings()
const toast = useToast()

const loading = ref(false)
const testing = ref(false)
const syncing = ref(false)

const state = reactive({
  enabled: false,
  serverUrl: '',
  serverPort: 389,
  bindDn: '',
  bindPassword: '',
  userSearchBase: '',
  userSearchFilter: '(uid={0})',
  groupSearchBase: '',
  groupSearchFilter: '(member={0})',
  useSSL: false
})

const status = ref({
  enabled: false,
  connected: false
})

const validate = (state: any): FormError[] => {
  const errors = []
  if (!state.serverUrl) errors.push({ path: 'serverUrl', message: 'Server URL is required' })
  if (!state.bindDn) errors.push({ path: 'bindDn', message: 'Bind DN is required' })
  if (!state.userSearchBase) errors.push({ path: 'userSearchBase', message: 'User Search Base is required' })
  if (!state.userSearchFilter) errors.push({ path: 'userSearchFilter', message: 'User Search Filter is required' })
  return errors
}

const fetchConfig = async () => {
  loading.value = true
  try {
    const data = await settings.getLDAPConfig()
    state.enabled = data['ldap.enabled'] === 'true'
    state.serverUrl = data['ldap.server.url'] || ''
    state.serverPort = parseInt(data['ldap.server.port'] || '389')
    state.bindDn = data['ldap.bind.dn'] || ''
    state.bindPassword = data['ldap.bind.password'] || ''
    state.userSearchBase = data['ldap.user.search.base'] || ''
    state.userSearchFilter = data['ldap.user.search.filter'] || '(uid={0})'
    state.groupSearchBase = data['ldap.group.search.base'] || ''
    state.groupSearchFilter = data['ldap.group.search.filter'] || '(member={0})'
    state.useSSL = data['ldap.use.ssl'] === 'true'

    const statusData = await settings.getLDAPStatus()
    status.value = statusData
  } catch (error) {
    console.error('Failed to fetch LDAP config:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to load LDAP configuration',
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const onSubmit = async (event: FormSubmitEvent<any>) => {
  loading.value = true
  try {
    const config: Partial<LDAPConfig> = {
      'ldap.enabled': state.enabled ? 'true' : 'false',
      'ldap.server.url': state.serverUrl,
      'ldap.server.port': state.serverPort.toString(),
      'ldap.bind.dn': state.bindDn,
      'ldap.bind.password': state.bindPassword,
      'ldap.user.search.base': state.userSearchBase,
      'ldap.user.search.filter': state.userSearchFilter,
      'ldap.group.search.base': state.groupSearchBase,
      'ldap.group.search.filter': state.groupSearchFilter,
      'ldap.use.ssl': state.useSSL ? 'true' : 'false'
    }

    await settings.updateLDAPConfig(config)
    await settings.enableLDAP(state.enabled)
    
    // Update local status immediately
    status.value.enabled = state.enabled
    // If enabling LDAP and previously tested successfully, keep connected status
    // Otherwise, reset connected to enabled status
    if (!state.enabled) {
      status.value.connected = false
    }

    toast.add({
      title: 'Success',
      description: 'LDAP configuration saved successfully',
      color: 'green'
    })
  } catch (error) {
    console.error('Failed to save LDAP config:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to save LDAP configuration',
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const testConnection = async () => {
  if (!state.serverUrl || !state.bindDn) {
    toast.add({
      title: 'Validation Error',
      description: 'Please configure LDAP settings before testing',
      color: 'red'
    })
    return
  }

  testing.value = true
  try {
    const result = await settings.testLDAPConnection()
    
    // Update local status based on test result
    if (result.success) {
      status.value.connected = true
    } else {
      status.value.connected = false
    }
    
    toast.add({
      title: result.success ? 'Connection Successful' : 'Connection Failed',
      description: result.message,
      color: result.success ? 'green' : 'red'
    })
  } catch (error) {
    console.error('Failed to test LDAP connection:', error)
    status.value.connected = false
    toast.add({
      title: 'Error',
      description: 'Failed to test LDAP connection',
      color: 'red'
    })
  } finally {
    testing.value = false
  }
}

const syncUsers = async () => {
  syncing.value = true
  try {
    const result = await settings.syncLDAPUsers()
    toast.add({
      title: 'Success',
      description: `${result.message}. Synced ${result.count} users.`,
      color: 'green'
    })
  } catch (error) {
    console.error('Failed to sync LDAP users:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to sync LDAP users',
      color: 'red'
    })
  } finally {
    syncing.value = false
  }
}

onMounted(() => {
  fetchConfig()
})
</script>

<template>
  <div class="space-y-6">
    <UPageCard
      title="LDAP Configuration"
      description="Configure LDAP authentication and user synchronization"
    >
      <UForm :validate="validate" :state="state" class="space-y-6" @submit="onSubmit">
        <!-- Status & Enable Toggle -->
        <div class="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg border border-gray-200 dark:border-gray-800">
          <div class="flex-1">
            <div class="flex items-center gap-3 mb-1">
              <h3 class="text-sm font-semibold text-gray-900 dark:text-white">Enable LDAP Authentication</h3>
              <UBadge
                v-if="status.enabled"
                :label="status.connected ? 'Connected' : 'Disconnected'"
                :color="status.connected ? 'green' : 'red'"
                variant="subtle"
              />
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400">Allow users to authenticate via LDAP</p>
          </div>
          <UCheckbox v-model="state.enabled" size="lg" />
        </div>

        <!-- LDAP Server Settings -->
        <div class="space-y-4">
          <div class="pb-2 border-b border-gray-200 dark:border-gray-800">
            <h3 class="text-base font-semibold text-gray-900 dark:text-white">LDAP Server</h3>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">Configure your LDAP server connection</p>
          </div>

          <div class="space-y-4">
            <UFormField label="Server URL" name="serverUrl" required class="w-full">
              <UInput
                v-model="state.serverUrl"
                placeholder="ldap://ldap.example.com"
                icon="i-lucide-server"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <UFormField label="Port" name="serverPort" required class="w-full">
              <UInput
                v-model.number="state.serverPort"
                type="number"
                placeholder="389"
                icon="i-lucide-network"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <UFormField label="Bind DN" name="bindDn" required class="w-full">
              <UInput
                v-model="state.bindDn"
                placeholder="cn=admin,dc=example,dc=com"
                icon="i-lucide-user"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <UFormField label="Bind Password" name="bindPassword" required class="w-full">
              <UInput
                v-model="state.bindPassword"
                type="password"
                placeholder="••••••••"
                icon="i-lucide-lock"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <div class="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg border border-gray-200 dark:border-gray-800">
              <div class="flex-1">
                <h4 class="text-sm font-semibold text-gray-900 dark:text-white mb-1">Use SSL/TLS</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400">Encrypt connection to LDAP server</p>
              </div>
              <UCheckbox v-model="state.useSSL" />
            </div>
          </div>
        </div>

        <!-- User Search Settings -->
        <div class="space-y-4">
          <div class="pb-2 border-b border-gray-200 dark:border-gray-800">
            <h3 class="text-base font-semibold text-gray-900 dark:text-white">User Search</h3>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">Configure how to search for users in LDAP</p>
          </div>

          <div class="space-y-4">
            <UFormField label="User Search Base" name="userSearchBase" required class="w-full">
              <UInput
                v-model="state.userSearchBase"
                placeholder="ou=users,dc=example,dc=com"
                icon="i-lucide-users"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <UFormField label="User Search Filter" name="userSearchFilter" required class="w-full">
              <UInput
                v-model="state.userSearchFilter"
                placeholder="(uid={0})"
                icon="i-lucide-filter"
                size="lg"
                class="w-full"
              />
            </UFormField>
          </div>
        </div>

        <!-- Group Search Settings -->
        <div class="space-y-4">
          <div class="pb-2 border-b border-gray-200 dark:border-gray-800">
            <h3 class="text-base font-semibold text-gray-900 dark:text-white">Group Search (Optional)</h3>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">Configure how to search for groups in LDAP</p>
          </div>

          <div class="space-y-4">
            <UFormField label="Group Search Base" name="groupSearchBase" class="w-full">
              <UInput
                v-model="state.groupSearchBase"
                placeholder="ou=groups,dc=example,dc=com"
                icon="i-lucide-users-round"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <UFormField label="Group Search Filter" name="groupSearchFilter" class="w-full">
              <UInput
                v-model="state.groupSearchFilter"
                placeholder="(member={0})"
                icon="i-lucide-filter"
                size="lg"
                class="w-full"
              />
            </UFormField>
          </div>
        </div>

        <!-- Actions -->
        <div class="flex gap-3 pt-4 border-t border-gray-200 dark:border-gray-800">
          <UButton
            type="submit"
            label="Save Configuration"
            icon="i-lucide-save"
            size="lg"
            :loading="loading"
            :disabled="testing || syncing"
          />
          <UButton
            type="button"
            label="Test Connection"
            icon="i-lucide-test-tube"
            color="neutral"
            variant="outline"
            size="lg"
            :loading="testing"
            :disabled="loading || syncing"
            @click="testConnection"
          />
          <UButton
            type="button"
            label="Sync Users"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="outline"
            size="lg"
            :loading="syncing"
            :disabled="loading || testing || !state.enabled || !status.connected"
            @click="syncUsers"
          />
        </div>
      </UForm>
    </UPageCard>
  </div>
</template>

