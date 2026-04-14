<script setup lang="ts">
import type { MailConfig } from '~/composables/useSettings'
import type { FormError, FormSubmitEvent } from '#ui/types'

const settings = useSettings()
const toast = useToast()

const loading = ref(false)
const testing = ref(false)

const state = reactive({
  enabled: false,
  host: '',
  port: 587,
  username: '',
  password: '',
  useTLS: true,
  fromAddress: 'noreply@k8s-platform.local',
  fromName: 'K8s Platform'
})

const validate = (state: any): FormError[] => {
  const errors = []
  if (!state.host) errors.push({ path: 'host', message: 'Host is required' })
  if (!state.fromAddress) errors.push({ path: 'fromAddress', message: 'From Address is required' })
  if (!state.fromName) errors.push({ path: 'fromName', message: 'From Name is required' })
  return errors
}

const fetchConfig = async () => {
  loading.value = true
  try {
    const data = await settings.getMailConfig()
    state.enabled = data['mail.enabled'] === 'true'
    state.host = data['mail.smtp.host'] || ''
    state.port = parseInt(data['mail.smtp.port'] || '587')
    state.username = data['mail.smtp.username'] || ''
    state.password = data['mail.smtp.password'] || ''
    state.useTLS = data['mail.smtp.use.tls'] === 'true'
    state.fromAddress = data['mail.from.address'] || 'noreply@k8s-platform.local'
    state.fromName = data['mail.from.name'] || 'K8s Platform'
  } catch (error) {
    console.error('Failed to fetch mail config:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to load mail configuration',
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const onSubmit = async (event: FormSubmitEvent<any>) => {
  loading.value = true
  try {
    const config: Partial<MailConfig> = {
      'mail.enabled': state.enabled ? 'true' : 'false',
      'mail.smtp.host': state.host,
      'mail.smtp.port': state.port.toString(),
      'mail.smtp.username': state.username,
      'mail.smtp.password': state.password,
      'mail.smtp.use.tls': state.useTLS ? 'true' : 'false',
      'mail.from.address': state.fromAddress,
      'mail.from.name': state.fromName
    }

    await settings.updateMailConfig(config)
    toast.add({
      title: 'Success',
      description: 'Mail configuration saved successfully',
      color: 'green'
    })
  } catch (error) {
    console.error('Failed to save mail config:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to save mail configuration',
      color: 'red'
    })
  } finally {
    loading.value = false
  }
}

const testConnection = async () => {
  testing.value = true
  try {
    const result = await settings.testMailConnection()
    toast.add({
      title: result.success ? 'Connection Successful' : 'Connection Failed',
      description: result.message,
      color: result.success ? 'green' : 'red'
    })
  } catch (error) {
    console.error('Failed to test mail connection:', error)
    toast.add({
      title: 'Error',
      description: 'Failed to test mail connection',
      color: 'red'
    })
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  fetchConfig()
})
</script>

<template>
  <div class="space-y-6">
    <UPageCard
      title="Mail Configuration"
      description="Configure SMTP settings for email notifications"
    >
      <UForm :validate="validate" :state="state" class="space-y-6" @submit="onSubmit">
        <!-- Enable Toggle -->
        <div class="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg border border-gray-200 dark:border-gray-800">
          <div class="flex-1">
            <h3 class="text-sm font-semibold text-gray-900 dark:text-white mb-1">Enable Mail Service</h3>
            <p class="text-xs text-gray-500 dark:text-gray-400">Allow the system to send email notifications</p>
          </div>
          <UCheckbox v-model="state.enabled" size="lg" />
        </div>

        <!-- SMTP Server Settings -->
        <div class="space-y-4">
          <div class="pb-2 border-b border-gray-200 dark:border-gray-800">
            <h3 class="text-base font-semibold text-gray-900 dark:text-white">SMTP Server</h3>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">Configure your SMTP server connection</p>
          </div>

          <div class="space-y-4">
            <UFormField label="Host" name="host" required class="w-full">
              <UInput
                v-model="state.host"
                placeholder="smtp.gmail.com"
                icon="i-lucide-server"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <UFormField label="Port" name="port" required class="w-full">
              <UInput
                v-model.number="state.port"
                type="number"
                placeholder="587"
                icon="i-lucide-network"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <UFormField label="Username" name="username" class="w-full">
              <UInput
                v-model="state.username"
                placeholder="user@example.com (optional)"
                icon="i-lucide-user"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <UFormField label="Password" name="password" class="w-full">
              <UInput
                v-model="state.password"
                type="password"
                placeholder="•••••••• (optional)"
                icon="i-lucide-lock"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <div class="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg border border-gray-200 dark:border-gray-800">
              <div class="flex-1">
                <h4 class="text-sm font-semibold text-gray-900 dark:text-white mb-1">Use TLS/SSL</h4>
                <p class="text-xs text-gray-500 dark:text-gray-400">Encrypt connection to SMTP server</p>
              </div>
              <UCheckbox v-model="state.useTLS" />
            </div>
          </div>
        </div>

        <!-- Sender Settings -->
        <div class="space-y-4">
          <div class="pb-2 border-b border-gray-200 dark:border-gray-800">
            <h3 class="text-base font-semibold text-gray-900 dark:text-white">Sender Information</h3>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">Configure the sender details for outgoing emails</p>
          </div>

          <div class="space-y-4">
            <UFormField label="From Address" name="fromAddress" required class="w-full">
              <UInput
                v-model="state.fromAddress"
                placeholder="noreply@k8s-platform.local"
                icon="i-lucide-mail"
                size="lg"
                class="w-full"
              />
            </UFormField>

            <UFormField label="From Name" name="fromName" required class="w-full">
              <UInput
                v-model="state.fromName"
                placeholder="K8s Platform"
                icon="i-lucide-user-circle"
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
            :disabled="testing"
          />
          <UButton
            type="button"
            label="Test Connection"
            icon="i-lucide-test-tube"
            color="neutral"
            variant="outline"
            size="lg"
            :loading="testing"
            :disabled="loading"
            @click="testConnection"
          />
        </div>
      </UForm>
    </UPageCard>
  </div>
</template>

