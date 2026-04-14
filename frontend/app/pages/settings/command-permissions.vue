<script setup lang="ts">
const { $api } = useNuxtApp()
const toast = useToast()
const authStore = useAuthStore()

const allowedCommands = ref([])
const roles = ref([])
const loading = ref(false)
const modalOpen = ref(false)

// Use roleTemplateName (string) instead of the legacy numeric roleId
const newCommand = ref({
  roleTemplateName: '',
  commandPattern: '',
  description: ''
})

const roleItems = computed(() => {
  return roles.value.map(role => ({
    label: role.displayName || role.name,
    icon: role.name === newCommand.value.roleTemplateName ? 'i-lucide-check' : undefined,
    onSelect: () => {
      newCommand.value.roleTemplateName = role.name
    }
  }))
})

const selectedRoleName = computed(() => {
  const found = roles.value.find(r => r.name === newCommand.value.roleTemplateName)
  return found ? (found.displayName || found.name) : 'Select a role'
})

const fetchRoles = async () => {
  try {
    // /admin/roles → RoleTemplateController → returns active RoleTemplate list
    const response = await $api.get('/admin/roles')
    roles.value = response.data || response
  } catch (e: any) {
    toast.add({ title: 'Error', description: 'Failed to fetch roles: ' + e.message, color: 'red' })
  }
}

const fetchAllowedCommands = async () => {
  loading.value = true
  try {
    const response = await $api.get('/exec/allowed-commands')
    allowedCommands.value = response.data
  } catch (e) {
    toast.add({ title: 'Error', description: 'Failed to fetch allowed commands', color: 'red' })
  } finally {
    loading.value = false
  }
}

const saveCommand = async () => {
  if (!newCommand.value.roleTemplateName || !newCommand.value.commandPattern) return
  try {
    await $api.post('/exec/allowed-commands', newCommand.value)
    toast.add({ title: 'Success', description: 'Command permission saved', color: 'green' })
    modalOpen.value = false
    fetchAllowedCommands()
    newCommand.value = { roleTemplateName: '', commandPattern: '', description: '' }
  } catch (e) {
    toast.add({ title: 'Error', description: 'Failed to save command', color: 'red' })
  }
}

const deleteCommand = async (id) => {
    try {
        await $api.delete(`/exec/allowed-commands/${id}`)
        toast.add({ title: 'Deleted', description: 'Command permission removed', color: 'blue' })
        fetchAllowedCommands()
    } catch (e) {
        toast.add({ title: 'Error', description: 'Failed to delete command', color: 'red' })
    }
}

onMounted(() => {
    fetchRoles()
    fetchAllowedCommands()
})

const columns = [
  { key: 'role', label: 'Role' },
  { key: 'commandPattern', label: 'Command Pattern (Regex)' },
  { key: 'description', label: 'Description' },
  { key: 'actions', label: 'Actions' }
]
</script>

<template>
  <div class="p-6 space-y-6">
    <div class="flex justify-between items-center">
      <div>
        <h1 class="text-2xl font-black uppercase tracking-tight">Exec Command Permissions</h1>
        <p class="text-sm text-gray-500">Define which roles can execute specific commands in pods.</p>
      </div>
      <UButton icon="i-lucide-plus" label="Add Permission" color="primary" @click="modalOpen = true" />
    </div>

    <UCard :ui="{ body: { padding: 'p-0' } }">
      <LegacyTable 
        :rows="allowedCommands" 
        :columns="columns" 
        :loading="loading"
        search-placeholder="Search permissions..."
      >
        <template #role-data="{ row }">
          <UBadge color="neutral" variant="subtle">{{ row.role.name }}</UBadge>
        </template>
        
        <template #commandPattern-data="{ row }">
          <code class="text-xs bg-gray-100 dark:bg-gray-800 p-1 rounded">{{ row.commandPattern }}</code>
        </template>
        
        <template #actions-data="{ row }">
          <UButton icon="i-lucide-trash-2" color="red" variant="ghost" size="xs" @click="deleteCommand(row.id)" />
        </template>
      </LegacyTable>
    </UCard>

    <UModal v-model:open="modalOpen" title="Add Command Permission">
      <template #body>
        <form @submit.prevent="saveCommand" class="space-y-4">
          <UFormField label="Target Role">
            <UDropdownMenu
              :items="roleItems"
              :content="{ align: 'start' }"
              :ui="{ content: 'w-[--reka-dropdown-menu-trigger-width]' }"
            >
              <UButton
                :label="selectedRoleName"
                icon="i-lucide-users"
                trailing-icon="i-lucide-chevron-down"
                color="neutral"
                variant="outline"
                block
                class="justify-between"
              />
            </UDropdownMenu>
          </UFormField>
          
          <UFormField label="Command Pattern (Regex)">
            <UInput v-model="newCommand.commandPattern" placeholder="e.g. ^ls.* or ^cat /etc/hosts" />
            <template #description>
                Use ^ and $ for exact matching. Regex is supported.
            </template>
          </UFormField>

          <UFormField label="Description">
            <UInput v-model="newCommand.description" placeholder="Optional notes" />
          </UFormField>
        </form>
      </template>
      <template #footer>
        <UButton label="Cancel" color="neutral" variant="ghost" @click="modalOpen = false" />
        <UButton label="Save Permission" color="primary" @click="saveCommand" :disabled="!newCommand.roleTemplateName || !newCommand.commandPattern" />
      </template>
    </UModal>
  </div>
</template>
