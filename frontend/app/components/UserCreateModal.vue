<script setup lang="ts">
import type { CreateUserRequest } from '~/composables/useUsers'
import type { FormError, FormSubmitEvent } from '#ui/types'

const props = defineProps<{
  modelValue: boolean
  availableRoles?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'submit': [data: CreateUserRequest]
}>()

const roles = computed(() => props.availableRoles || ['ADMIN', 'OPERATOR', 'VIEWER'])
const availableAuthTypes = ['LOCAL', 'LDAP']

// Dropdown items for auth type
const authTypeItems = computed(() => [
  availableAuthTypes.map(type => ({
    label: type,
    click: () => { state.authType = type }
  }))
])

// Dropdown items for roles
const roleItems = computed(() => [
  roles.value.map(role => ({
    label: role,
    icon: state.roles.includes(role) ? 'i-lucide-check' : undefined,
    click: () => {
      const index = state.roles.indexOf(role)
      if (index > -1) {
        state.roles.splice(index, 1)
      } else {
        state.roles.push(role)
      }
    }
  }))
])

const state = reactive<CreateUserRequest>({
  username: '',
  email: '',
  password: '',
  authType: 'LOCAL',
  isActive: true,
  isSuperadmin: false,
  roles: []
})

const validate = (state: any): FormError[] => {
  const errors = []
  if (!state.username) errors.push({ path: 'username', message: 'Username is required' })
  if (!state.email) errors.push({ path: 'email', message: 'Email is required' })
  if (state.authType === 'LOCAL' && !state.password) {
    errors.push({ path: 'password', message: 'Password is required for local users' })
  }
  return errors
}

const onSubmit = (event: FormSubmitEvent<any>) => {
  emit('submit', state)
}

const close = () => {
  emit('update:modelValue', false)
}
</script>

<template>
  <UModal
    :model-value="modelValue"
    @update:model-value="close"
    title="Create User"
    description="Add a new user to the system"
  >
    <template #body>
      <UForm :validate="validate" :state="state" class="space-y-4" @submit="onSubmit">
        <UFormField label="Username" name="username" required class="w-full">
          <UInput v-model="state.username" icon="i-lucide-user" size="lg" class="w-full" />
        </UFormField>

        <UFormField label="Email" name="email" required class="w-full">
          <UInput v-model="state.email" type="email" icon="i-lucide-mail" size="lg" class="w-full" />
        </UFormField>

        <UFormField label="Auth Type" name="authType" required class="w-full">
          <UDropdownMenu
            :items="authTypeItems"
            :content="{ align: 'start' }"
            :ui="{ content: 'w-(--reka-dropdown-menu-trigger-width)' }"
          >
            <UButton
              :label="state.authType || 'Select auth type'"
              trailing-icon="i-lucide-chevron-down"
              color="neutral"
              variant="outline"
              block
              class="justify-start w-full"
              size="lg"
            />
          </UDropdownMenu>
        </UFormField>

        <UFormField v-if="state.authType === 'LOCAL'" label="Password" name="password" required class="w-full">
          <UInput v-model="state.password" type="password" icon="i-lucide-lock" size="lg" class="w-full" />
        </UFormField>

        <UFormField label="Roles" name="roles" class="w-full">
          <UDropdownMenu
            :items="roleItems"
            :content="{ align: 'start' }"
            :ui="{ content: 'w-(--reka-dropdown-menu-trigger-width)' }"
          >
            <UButton
              :label="state.roles.length > 0 ? state.roles.join(', ') : 'Select roles'"
              trailing-icon="i-lucide-chevron-down"
              color="neutral"
              variant="outline"
              block
              class="justify-start w-full"
              size="lg"
            />
          </UDropdownMenu>
        </UFormField>

        <div class="flex items-center gap-4">
          <UFormField label="Active" name="isActive" class="w-full">
            <UToggle v-model="state.isActive" />
          </UFormField>

          <UFormField label="Superadmin" name="isSuperadmin" class="w-full">
            <UToggle v-model="state.isSuperadmin" />
          </UFormField>
        </div>

        <div class="flex justify-end gap-2 pt-4 border-t border-gray-200 dark:border-gray-800">
          <UButton label="Cancel" color="gray" variant="ghost" @click="close" />
          <UButton label="Create User" type="submit" />
        </div>
      </UForm>
    </template>
  </UModal>
</template>


