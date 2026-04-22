<script setup lang="ts">
import type { UserResponse, CreateUserRequest, UpdateUserRequest } from '~/composables/useUsers'
import type { LDAPUser } from '~/composables/useSettings'

definePageMeta({ requiredPermission: 'users' })

const { $api }    = useNuxtApp()
const usersApi    = useUsers()
const settings    = useSettings()
const toast       = useToast()
const clusterStore = useClusterStore()
const authStore   = useAuthStore()
const confirmDialog = useConfirm()

// Permission checks (SUPERADMIN only)
const canCreate            = computed(() => authStore.user?.isSuperadmin)
const canEdit              = computed(() => authStore.user?.isSuperadmin)
const canDelete            = computed(() => authStore.user?.isSuperadmin)
const canManagePermissions = computed(() => authStore.user?.isSuperadmin)

// ── State ─────────────────────────────────────────────────────────────────────
const users       = ref<UserResponse[]>([])
const loading     = ref(false)
const activePanel = ref<'list' | 'create' | 'edit' | 'permissions'>('list')
const selectedUser = ref<UserResponse | null>(null)

// LDAP search state
const ldapSearchQuery   = ref('')
const ldapSearchResults = ref<LDAPUser[]>([])
const ldapSearching     = ref(false)
const showLdapResults   = ref(false)

// Auth types
const availableAuthTypes = [
  { label: 'Local', value: 'LOCAL' },
  { label: 'LDAP',  value: 'LDAP'  }
]

// ── Role Templates (new RBAC system) ──────────────────────────────────────────
interface RoleTemplateMeta { id: number; name: string; displayName: string; color?: string }
const availableRoleTemplates = ref<RoleTemplateMeta[]>([])

const fetchRoleTemplates = async () => {
  try {
    const res = await $api.get<RoleTemplateMeta[]>('/admin/roles')
    availableRoleTemplates.value = res.data || []
  } catch { /* ignore */ }
}

// User's current role assignments
interface RoleAssignment { roleName: string; roleId?: number; roleDisplayName?: string; roleColor?: string; clusterUid: string }
const userRoleAssignments = ref<RoleAssignment[]>([])

const fetchUserAssignments = async (username: string) => {
  try {
    const res = await $api.get<RoleAssignment[]>(`/admin/roles/users/${username}`)
    userRoleAssignments.value = res.data || []
  } catch { userRoleAssignments.value = [] }
}

// Role assignment form
const newAssignment = reactive<{ roleId: number | null; clusterUids: string[] }>({ roleId: null, clusterUids: [] })

const clusterOptions = computed(() =>
  clusterStore.clusters.map(c => ({ label: c.name, value: c.uid ?? c.name }))
)

// ── Create user form ──────────────────────────────────────────────────────────
const createState = reactive<CreateUserRequest>({
  username: '', email: '', password: '', authType: 'LOCAL', isActive: true, isSuperadmin: false, roles: []
})

// ── Edit user form ────────────────────────────────────────────────────────────
const editState = reactive<UpdateUserRequest>({
  username: '', email: '', password: '', isActive: true, isSuperadmin: false, roles: []
})

// Table columns
const columns = [
  { id: 'username', key: 'username', label: 'Username', sortable: true },
  { id: 'email', key: 'email', label: 'Email', sortable: true },
  { id: 'authType', key: 'authType', label: 'Auth Type' },
  { id: 'roles', key: 'roles', label: 'Roles' },
  { id: 'isActive', key: 'isActive', label: 'Status' },
  { id: 'actions', key: 'actions', label: 'Actions' }
]

// Fetch users from backend
const fetchUsers = async () => {
  loading.value = true
  try {
    users.value = await usersApi.getAllUsers()
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

// Reset create form
const resetCreateForm = () => {
  Object.assign(createState, {
    username: '',
    email: '',
    password: '',
    authType: 'LOCAL',
    isActive: true,
    isSuperadmin: false,
    roles: []
  })
  ldapSearchQuery.value = ''
  ldapSearchResults.value = []
  showLdapResults.value = false
}

// LDAP user search
const searchLDAPUsers = async () => {
  if (!ldapSearchQuery.value || ldapSearchQuery.value.length < 2) {
    ldapSearchResults.value = []
    return
  }
  
  ldapSearching.value = true
  try {
    ldapSearchResults.value = await settings.searchLDAPUsers(ldapSearchQuery.value, 10)
    showLdapResults.value = true
  } catch (error: any) {
    toast.add({
      title: 'LDAP search failed',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
    ldapSearchResults.value = []
  } finally {
    ldapSearching.value = false
  }
}

// Select LDAP user from search results
const selectLDAPUser = (user: LDAPUser) => {
  createState.username = user.username
  createState.email = user.email
  showLdapResults.value = false
  ldapSearchQuery.value = user.displayName || user.username
}

// Debounced LDAP search
let searchTimeout: NodeJS.Timeout | null = null
const debouncedLDAPSearch = () => {
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    searchLDAPUsers()
  }, 300)
}

// Open create panel
const openCreatePanel = () => {
  resetCreateForm()
  activePanel.value = 'create'
}

// Submit create user
const onCreateSubmit = async () => {
  if (!createState.username || !createState.email) {
    toast.add({ title: 'Validation Error', description: 'Username and email are required', color: 'error' })
    return
  }
  if (createState.authType === 'LOCAL' && !createState.password) {
    toast.add({ title: 'Validation Error', description: 'Password is required for local users', color: 'error' })
    return
  }

  loading.value = true
  try {
    await usersApi.createUser(createState)
    toast.add({ title: 'User created', description: 'User has been created successfully', color: 'success' })
    activePanel.value = 'list'
    resetCreateForm()
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

// Open edit panel
const openEditPanel = (user: UserResponse) => {
  selectedUser.value = user
  Object.assign(editState, {
    username: user.username,
    email: user.email,
    password: '',
    isActive: user.isActive,
    isSuperadmin: user.isSuperadmin,
    roles: [...user.roles]
  })
  activePanel.value = 'edit'
}

// Submit edit user
const onEditSubmit = async () => {
  if (!selectedUser.value) return
  if (!editState.username || !editState.email) {
    toast.add({ title: 'Validation Error', description: 'Username and email are required', color: 'error' })
    return
  }

  loading.value = true
  try {
    // Exclude roles — role management is handled via the Permissions panel (Casbin)
    const { roles, ...payload } = editState
    await usersApi.updateUser(selectedUser.value.id, payload as UpdateUserRequest)
    toast.add({ title: 'User updated', description: 'User has been updated successfully', color: 'success' })
    activePanel.value = 'list'
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

// Delete user
const deleteUser = async (user: UserResponse) => {
  if (!await confirmDialog.open({
    title: 'Delete User',
    description: `Are you sure you want to delete user "${user.username}"?`,
    confirmLabel: 'Delete',
    color: 'red'
  })) return

  loading.value = true
  try {
    await usersApi.deleteUser(user.id)
    toast.add({ title: 'User deleted', description: 'User has been deleted successfully', color: 'success' })
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

// ── Permissions Panel: new RBAC role-template assignment ──────────────────────
const openPermissionsPanel = async (user: UserResponse) => {
  selectedUser.value = user
  userRoleAssignments.value = []
  newAssignment.roleId = null
  newAssignment.clusterUids = []
  activePanel.value = 'permissions'
  loading.value = true
  try {
    await fetchUserAssignments(user.username)
  } finally {
    loading.value = false
  }
}

const addRoleAssignment = async () => {
  if (!selectedUser.value || !newAssignment.roleId) return
  const role = availableRoleTemplates.value.find(r => r.id === newAssignment.roleId)
  if (!role) return

  const clusters = newAssignment.clusterUids.length > 0 ? newAssignment.clusterUids : ['*']
  loading.value = true
  try {
    await $api.post(`/admin/roles/${role.id}/users`, {
      username: selectedUser.value.username,
      clusterUids: clusters
    })
    toast.add({ title: 'Role assigned', description: `"${role.displayName || role.name}" has been assigned`, color: 'success' })
    await fetchUserAssignments(selectedUser.value.username)
    newAssignment.roleId = null
    newAssignment.clusterUids = []
  } catch (error: any) {
    toast.add({ title: 'Assignment failed', description: error.response?.data?.message || error.message, color: 'error' })
  } finally {
    loading.value = false
  }
}

const removeRoleAssignment = async (roleName: string, clusterUid: string) => {
  if (!selectedUser.value) return
  const role = availableRoleTemplates.value.find(r => r.name === roleName)
  if (!role) return

  loading.value = true
  try {
    await $api.delete(`/admin/roles/${role.id}/users/${selectedUser.value.username}`, {
      params: { clusterUid }
    })
    toast.add({ title: 'Role removed', color: 'success' })
    await fetchUserAssignments(selectedUser.value.username)
  } catch (error: any) {
    toast.add({ title: 'Removal failed', description: error.response?.data?.message || error.message, color: 'error' })
  } finally {
    loading.value = false
  }
}

// ── Initialize ─────────────────────────────────────────────────────────────────
onMounted(async () => {
  await Promise.all([
    fetchUsers(),
    fetchRoleTemplates(),
    clusterStore.clusters.length === 0
      ? clusterStore.fetchClusters().catch(e => console.error('fetchClusters failed:', e))
      : Promise.resolve()
  ])
})
</script>

<template>
  <div class="space-y-6">
    <UPageCard
      title="User Management"
      description="Manage users, roles, and permissions"
    >
      <!-- Action buttons -->
      <template #header>
        <div class="flex items-center justify-between w-full">
          <div class="flex items-center gap-2">
            <h3 class="text-lg font-semibold">User Management</h3>
            <UBadge color="neutral" variant="subtle">{{ users.length }} users</UBadge>
          </div>
          <div class="flex items-center gap-2">
            <UButton
              icon="i-lucide-refresh-cw"
              color="neutral"
              variant="ghost"
              square
              :loading="loading"
              @click="fetchUsers"
            />
            <UButton
              v-if="activePanel === 'list' && canCreate"
              label="Create User"
              icon="i-lucide-plus"
              @click="openCreatePanel"
            />
            <UButton
              v-else-if="activePanel !== 'list'"
              label="Back to List"
              icon="i-lucide-arrow-left"
              variant="ghost"
              @click="activePanel = 'list'"
            />
          </div>
        </div>
      </template>
      <!-- User List -->
      <div v-if="activePanel === 'list'" class="p-4">
        <LegacyTable :rows="users" :columns="columns" :loading="loading">
          <template #authType-data="{ row }">
            <UBadge :color="row.authType === 'LOCAL' ? 'info' : 'primary'" variant="subtle">
              {{ row.authType }}
            </UBadge>
          </template>

          <template #roles-data="{ row }">
            <div class="flex gap-1 flex-wrap">
              <UBadge v-for="role in row.roles" :key="role" color="neutral" variant="subtle" size="sm">
                {{ role }}
              </UBadge>
              <span v-if="!row.roles || row.roles.length === 0" class="text-sm text-gray-400">No roles</span>
            </div>
          </template>

          <template #isActive-data="{ row }">
            <UBadge :color="row.isActive ? 'success' : 'error'" variant="subtle">
              {{ row.isActive ? 'Active' : 'Inactive' }}
            </UBadge>
          </template>

          <template #actions-data="{ row }">
            <div class="flex gap-1">
              <UButton
                v-if="canManagePermissions"
                icon="i-lucide-shield"
                size="xs"
                color="info"
                variant="ghost"
                square
                title="Manage Permissions"
                @click="openPermissionsPanel(row)"
              />
              <UButton
                v-if="canEdit"
                icon="i-lucide-pencil"
                size="xs"
                color="neutral"
                variant="ghost"
                square
                title="Edit User"
                @click="openEditPanel(row)"
              />
              <UButton
                v-if="canDelete"
                icon="i-lucide-trash-2"
                size="xs"
                color="error"
                variant="ghost"
                square
                title="Delete User"
                @click="deleteUser(row)"
              />
            </div>
          </template>
        </LegacyTable>
      </div>

      <!-- Create User Panel -->
      <div v-else-if="activePanel === 'create'" class="p-6 max-w-2xl mx-auto">
        <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-800 p-6">
          <h3 class="text-lg font-semibold mb-6">Create New User</h3>

          <div class="space-y-4">
            <!-- Auth Type Selection First -->
            <div>
              <label class="block text-sm font-medium mb-1">Auth Type *</label>
              <USelect v-model="createState.authType" :items="availableAuthTypes" value-key="value" />
            </div>

            <!-- LDAP User Search (when LDAP is selected) -->
            <div v-if="createState.authType === 'LDAP'" class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4 space-y-3">
              <label class="block text-sm font-medium text-blue-800 dark:text-blue-200">
                Search LDAP User
              </label>
              <div class="relative">
                <UInput 
                  v-model="ldapSearchQuery" 
                  icon="i-lucide-search" 
                  placeholder="Type to search LDAP users..."
                  :loading="ldapSearching"
                  @input="debouncedLDAPSearch"
                  @focus="showLdapResults = ldapSearchResults.length > 0"
                />
                <!-- LDAP Search Results Dropdown -->
                <div 
                  v-if="showLdapResults && ldapSearchResults.length > 0" 
                  class="absolute z-10 w-full mt-1 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 shadow-lg max-h-60 overflow-y-auto"
                >
                  <button
                    v-for="user in ldapSearchResults"
                    :key="user.username"
                    type="button"
                    class="w-full px-4 py-2 text-left hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-3"
                    @click="selectLDAPUser(user)"
                  >
                    <UIcon name="i-lucide-user" class="w-4 h-4 text-blue-500" />
                    <div>
                      <div class="font-medium">{{ user.displayName || user.username }}</div>
                      <div class="text-xs text-gray-500">{{ user.email }}</div>
                    </div>
                  </button>
                </div>
                <div 
                  v-else-if="showLdapResults && ldapSearchQuery.length >= 2 && ldapSearchResults.length === 0 && !ldapSearching" 
                  class="absolute z-10 w-full mt-1 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 shadow-lg p-4 text-center text-gray-500"
                >
                  No LDAP users found
                </div>
              </div>
              <p class="text-xs text-blue-600 dark:text-blue-400">
                Search by username, email, or display name. Select a user to auto-fill the fields below.
              </p>
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium mb-1">Username *</label>
                <UInput 
                  v-model="createState.username" 
                  icon="i-lucide-user" 
                  placeholder="Enter username"
                  :disabled="createState.authType === 'LDAP' && !!createState.username"
                />
              </div>
              <div>
                <label class="block text-sm font-medium mb-1">Email *</label>
                <UInput 
                  v-model="createState.email" 
                  type="email" 
                  icon="i-lucide-mail" 
                  placeholder="Enter email"
                  :disabled="createState.authType === 'LDAP' && !!createState.email"
                />
              </div>
            </div>

            <div v-if="createState.authType === 'LOCAL'">
              <label class="block text-sm font-medium mb-1">Password *</label>
              <UInput v-model="createState.password" type="password" icon="i-lucide-lock" placeholder="Enter password" />
            </div>

            <div class="flex gap-6">
              <UCheckbox v-model="createState.isActive" label="Active" />
              <UCheckbox v-model="createState.isSuperadmin" label="Superadmin" />
            </div>

            <div class="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-800">
              <UButton label="Cancel" color="neutral" variant="ghost" @click="activePanel = 'list'" />
              <UButton label="Create User" :loading="loading" @click="onCreateSubmit" />
            </div>
          </div>
        </div>
      </div>

      <!-- Edit User Panel -->
      <div v-else-if="activePanel === 'edit' && selectedUser" class="p-6 max-w-2xl mx-auto">
        <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-800 p-6">
          <h3 class="text-lg font-semibold mb-6">Edit User - {{ selectedUser.username }}</h3>

          <div class="space-y-4">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium mb-1">Username *</label>
                <UInput v-model="editState.username" icon="i-lucide-user" />
              </div>
              <div>
                <label class="block text-sm font-medium mb-1">Email *</label>
                <UInput v-model="editState.email" type="email" icon="i-lucide-mail" />
              </div>
            </div>

            <div v-if="selectedUser?.authType !== 'LDAP'">
              <label class="block text-sm font-medium mb-1">Password</label>
              <p class="text-xs text-gray-500 mb-1">Leave empty to keep current password</p>
              <UInput v-model="editState.password" type="password" icon="i-lucide-lock" />
            </div>
            <div v-else class="flex items-center gap-2 p-3 rounded-lg bg-blue-50 dark:bg-blue-900/20 text-sm text-blue-700 dark:text-blue-300">
              <UIcon name="i-lucide-info" class="w-4 h-4 flex-shrink-0" />
              <span>LDAP users authenticate via the directory server. Password cannot be changed here.</span>
            </div>

            <div class="flex gap-6">
              <UCheckbox v-model="editState.isActive" label="Active" />
              <UCheckbox v-model="editState.isSuperadmin" label="Superadmin" />
            </div>

            <div class="flex justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-800">
              <UButton label="Cancel" color="neutral" variant="ghost" @click="activePanel = 'list'" />
              <UButton label="Update User" :loading="loading" @click="onEditSubmit" />
            </div>
          </div>
        </div>
      </div>

      <!-- Permissions Panel — new RBAC Role Template assignment -->
      <div v-else-if="activePanel === 'permissions' && selectedUser" class="p-6 max-w-4xl mx-auto">
        <div class="bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-800 p-6 space-y-6">
          <div>
            <h3 class="text-lg font-semibold">Role Template Management</h3>
            <p class="text-sm text-gray-500 mt-1">
              <UIcon name="i-lucide-user" class="inline w-4 h-4 mr-1" />
              Assign role templates to <strong>{{ selectedUser.username }}</strong>.
              Each template defines page, namespace, and resource permissions.
            </p>
          </div>

          <!-- Current assignments -->
          <div>
            <h4 class="text-sm font-semibold mb-3">Current Assignments</h4>
            <div v-if="loading" class="flex items-center gap-2 text-gray-400 text-sm">
              <UIcon name="i-lucide-loader-2" class="w-4 h-4 animate-spin" /> Loading...
            </div>
            <div v-else-if="userRoleAssignments.length === 0" class="text-sm text-gray-400 italic">
              No roles have been assigned to this user yet.
            </div>
            <div v-else class="space-y-2">
              <div
                v-for="(asgn, i) in userRoleAssignments"
                :key="i"
                class="flex items-center justify-between p-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800"
              >
                <div class="flex items-center gap-3">
                  <span
                    class="w-3 h-3 rounded-full flex-shrink-0"
                    :style="{ background: asgn.roleColor || '#6366f1' }"
                  />
                  <div>
                    <p class="font-medium text-sm">{{ asgn.roleDisplayName || asgn.roleName }}</p>
                    <p class="text-xs text-gray-500">
                      Cluster: <code class="bg-gray-100 dark:bg-gray-700 px-1 rounded">{{ asgn.clusterUid === '*' ? 'All clusters' : asgn.clusterUid }}</code>
                    </p>
                  </div>
                </div>
                <UButton
                  icon="i-lucide-trash-2"
                  size="xs"
                  color="error"
                  variant="ghost"
                  :loading="loading"
                  @click="removeRoleAssignment(asgn.roleName, asgn.clusterUid)"
                />
              </div>
            </div>
          </div>

          <USeparator />

          <!-- Add new assignment -->
          <div>
            <h4 class="text-sm font-semibold mb-3">Assign New Role</h4>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <UFormField label="Role Template">
                <USelectMenu
                  v-model="newAssignment.roleId"
                  :items="availableRoleTemplates.map(r => ({ label: r.displayName || r.name, value: r.id }))"
                  value-key="value"
                  label-key="label"
                  placeholder="Select a role..."
                  class="w-full"
                />
              </UFormField>
              <UFormField label="Cluster(s) — leave empty for all">
                <USelectMenu
                  v-model="newAssignment.clusterUids"
                  :items="clusterOptions"
                  value-key="value"
                  label-key="label"
                  multiple
                  placeholder="Select clusters (optional)..."
                  class="w-full"
                />
              </UFormField>
            </div>
            <div class="flex justify-end mt-4">
              <UButton
                label="Assign Role"
                icon="i-lucide-user-plus"
                :loading="loading"
                :disabled="!newAssignment.roleId"
                @click="addRoleAssignment"
              />
            </div>
          </div>

          <div class="flex justify-end pt-2 border-t border-gray-200 dark:border-gray-800">
            <UButton label="Close" color="neutral" variant="ghost" @click="activePanel = 'list'" />
          </div>
        </div>
      </div>
    </UPageCard>
  </div>
</template>
