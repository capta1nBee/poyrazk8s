<script setup lang="ts">
const { $api } = useNuxtApp()
const toast = useToast()
const confirmDialog = useConfirm()

const users = ref<any[]>([])
const loading = ref(false)
const isCreateModalOpen = ref(false)
const isEditModalOpen = ref(false)
const selectedUser = ref<any>(null)

const newUser = ref({
  username: '',
  email: '',
  password: '',
  role: 'USER'
})

const fetchUsers = async () => {
  loading.value = true
  try {
    const response = await $api.get('/admin/users')
    users.value = response.data
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch users',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

const createUser = async () => {
  loading.value = true
  try {
    await $api.post('/admin/users', newUser.value)
    toast.add({
      title: 'User created',
      description: 'User has been created successfully',
      color: 'success'
    })
    isCreateModalOpen.value = false
    newUser.value = { username: '', email: '', password: '', role: 'USER' }
    await fetchUsers()
  } catch (error: any) {
    toast.add({
      title: 'Failed to create user',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

const deleteUser = async (userId: number) => {
  if (!await confirmDialog.open({
    title: 'Delete User',
    description: 'Are you sure you want to delete this user?',
    confirmLabel: 'Delete',
    color: 'red'
  })) return

  loading.value = true
  try {
    await $api.delete(`/admin/users/${userId}`)
    toast.add({
      title: 'User deleted',
      description: 'User has been deleted successfully',
      color: 'success'
    })
    await fetchUsers()
  } catch (error: any) {
    toast.add({
      title: 'Failed to delete user',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

const openEditModal = (user: any) => {
  selectedUser.value = { ...user }
  isEditModalOpen.value = true
}

const updateUser = async () => {
  loading.value = true
  try {
    await $api.put(`/admin/users/${selectedUser.value.id}`, selectedUser.value)
    toast.add({
      title: 'User updated',
      description: 'User has been updated successfully',
      color: 'success'
    })
    isEditModalOpen.value = false
    await fetchUsers()
  } catch (error: any) {
    toast.add({
      title: 'Failed to update user',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div class="space-y-4">
    <div class="flex justify-between items-center">
      <h3 class="text-lg font-semibold">User Management</h3>
      <UButton label="Create User" icon="i-lucide-plus" @click="isCreateModalOpen = true" />
    </div>

    <UCard>
      <LegacyTable
        :rows="users"
        :columns="[
          { id: 'id', key: 'id', label: 'ID', sortable: true },
          { id: 'username', key: 'username', label: 'Username', sortable: true },
          { id: 'email', key: 'email', label: 'Email', sortable: true },
          { id: 'role', key: 'role', label: 'Role', sortable: true },
          { id: 'actions', key: 'actions', label: 'Actions' }
        ]"
        :loading="loading"
      >
        <template #role-data="{ row }">
          <UBadge :color="row.role === 'ADMIN' ? 'red' : 'blue'" variant="subtle">
            {{ row.role }}
          </UBadge>
        </template>

        <template #actions-data="{ row }">
          <div class="flex gap-2">
            <UButton
              icon="i-lucide-edit"
              size="xs"
              variant="ghost"
              @click="openEditModal(row)"
            />
            <UButton
              icon="i-lucide-trash"
              size="xs"
              variant="ghost"
              color="red"
              @click="deleteUser(row.id)"
            />
          </div>
        </template>
      </LegacyTable>
    </UCard>

    <!-- Create User Modal -->
    <UModal v-model:open="isCreateModalOpen" title="Create User">
      <div class="space-y-4 p-4">
        <div>
          <label class="block text-sm font-medium mb-1">Username <span class="text-red-500">*</span></label>
          <UInput v-model="newUser.username" />
        </div>

        <div>
          <label class="block text-sm font-medium mb-1">Email <span class="text-red-500">*</span></label>
          <UInput v-model="newUser.email" type="email" />
        </div>

        <div>
          <label class="block text-sm font-medium mb-1">Password <span class="text-red-500">*</span></label>
          <UInput v-model="newUser.password" type="password" />
        </div>

        <div>
          <label class="block text-sm font-medium mb-1">Role <span class="text-red-500">*</span></label>
          <USelect v-model="newUser.role" :items="['USER', 'ADMIN']" />
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton label="Cancel" variant="ghost" @click="isCreateModalOpen = false" />
          <UButton label="Create" @click="createUser" :loading="loading" />
        </div>
      </template>
    </UModal>

    <!-- Edit User Modal -->
    <UModal v-model:open="isEditModalOpen" title="Edit User">
      <div v-if="selectedUser" class="space-y-4 p-4">
        <div>
          <label class="block text-sm font-medium mb-1">Username <span class="text-red-500">*</span></label>
          <UInput v-model="selectedUser.username" />
        </div>

        <div>
          <label class="block text-sm font-medium mb-1">Email <span class="text-red-500">*</span></label>
          <UInput v-model="selectedUser.email" type="email" />
        </div>

        <div>
          <label class="block text-sm font-medium mb-1">Role <span class="text-red-500">*</span></label>
          <USelect v-model="selectedUser.role" :items="['USER', 'ADMIN']" />
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton label="Cancel" variant="ghost" @click="isEditModalOpen = false" />
          <UButton label="Update" @click="updateUser" :loading="loading" />
        </div>
      </template>
    </UModal>
  </div>
</template>


