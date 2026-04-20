<script setup lang="ts">
const { $api } = useNuxtApp()
const toast = useToast()
const clusterStore = useClusterStore()
const confirmDialog = useConfirm()

// ── Types ─────────────────────────────────────────────────────────────────
interface RoleTemplate { id: number; name: string; displayName: string; description: string; color: string; isActive: boolean }
interface Permission { role: string; cluster: string; namespace: string; resource: string; action: string }
interface UserBinding { username: string; role: string; clusterUid: string; displayName?: string; email?: string }
interface ActionMeta { code: string; displayName: string; requiresWrite: boolean; isDangerous: boolean }
interface NameFilter { id: number; roleName: string; clusterUid: string; nsPattern: string; resourceKind: string; namePattern: string }

/**
 * page_tier from backend:
 *   1 = Namespace-scoped K8s resource  (Tier 1: cluster + namespace + name + actions)
 *   2 = Cluster-scoped K8s resource    (Tier 2: cluster + name + actions)
 *   3 = Feature/management page        (Tier 3: page access only)
 */
interface PageMeta {
  name: string; displayName: string; resourceKind: string; icon: string
  isNamespaceScoped: boolean; pageTier?: number; actions: ActionMeta[]
}

// ── Superadmin-only pages ─────────────────────────────────────────────────
// These pages are blocked for non-superadmins at both middleware and backend level.
// Even if assigned via a role, they are inaccessible. Show a badge as a warning.
const SUPERADMIN_ONLY_PAGES = new Set([
  'clusters',
  'roles',
  'audit-logs',
  'settings',
  'settings-mail',
  'settings-ldap',
  'settings-users',
  'settings-command-permissions',
  'settings-exec-sessions',
  'settings-helm-repos',
  'settings-runtime-security',
  'settings-pages-actions',
])

// ── Config from backend ───────────────────────────────────────────────────
const pageOptions = ref<PageMeta[]>([])
async function loadConfig() {
  try {
    const res = await $api.get<{ pages: PageMeta[] }>('/admin/roles/config')
    pageOptions.value = (res.data.pages ?? []).map(p => ({
      ...p,
      isNamespaceScoped: !!p.isNamespaceScoped,
      pageTier: p.pageTier ?? (p.resourceKind ? (p.isNamespaceScoped ? 1 : 2) : 3)
    }))
  } catch { /* silent */ }
}

// ── State ─────────────────────────────────────────────────────────────────
const roles = ref<RoleTemplate[]>([])
const selectedRole = ref<RoleTemplate | null>(null)
const permissions = ref<Permission[]>([])
const nameFilters = ref<NameFilter[]>([])
const userBindings = ref<UserBinding[]>([])
const loading = ref(false)
const activeTab = ref<'permissions' | 'users'>('permissions')

// Role CRUD form
const showRoleModal = ref(false)
const editingRole = ref<RoleTemplate | null>(null)
const roleForm = reactive({ name: '', displayName: '', description: '', color: '#6366f1' })

// ── Cluster-scope form (NEW: one form covers cluster + namespaces + pages) ─
interface PageEntry { pageKey: string; resourceKind: string; pageTier: number; namePattern: string; actions: string[] }

const showScopeModal = ref(false)
const nsPatternInput = ref('')
const scopeForm = reactive({
  clusterUid: '' as string,
  nsPatterns: [] as string[],   // shared namespace patterns for all Tier 1 pages
  pageEntries: [] as PageEntry[]
})

function resetScopeForm() {
  scopeForm.clusterUid = clusterStore.clusters[0]?.uid ?? ''
  scopeForm.nsPatterns = []
  scopeForm.pageEntries = []
  nsPatternInput.value = ''
}

function addNsPattern() {
  const v = nsPatternInput.value.trim()
  if (v && !scopeForm.nsPatterns.includes(v)) scopeForm.nsPatterns.push(v)
  nsPatternInput.value = ''
}
function removeNsPattern(ns: string) {
  const idx = scopeForm.nsPatterns.indexOf(ns)
  if (idx !== -1) scopeForm.nsPatterns.splice(idx, 1)
}

function addPageEntry() {
  scopeForm.pageEntries.push({ pageKey: '', resourceKind: '', pageTier: 3, namePattern: '', actions: [] })
}
function removePageEntry(i: number) { scopeForm.pageEntries.splice(i, 1) }

function onPageEntryChange(entry: PageEntry) {
  const meta = pageOptions.value.find(p => p.name === entry.pageKey)
  if (!meta) return
  entry.resourceKind = meta.resourceKind ?? ''
  entry.pageTier = meta.pageTier ?? (meta.resourceKind ? (meta.isNamespaceScoped ? 1 : 2) : 3)
  entry.actions = []
  entry.namePattern = ''
}

// User form
const showUserModal = ref(false)
const userForm = reactive({ username: '', clusterUids: ['*'] as string[] })

// ── Computed ───────────────────────────────────────────────────────────────
const clusters = computed(() => clusterStore.clusters || [])

function actionsForPage(pageKey: string): ActionMeta[] {
  return pageOptions.value.find(p => p.name === pageKey)?.actions ?? []
}
function clusterName(uid: string): string {
  return clusters.value.find(c => c.uid === uid)?.name ?? uid
}

/**
 * Group permissions by cluster for the display panel.
 * Structure: { clusterUid, namespaceGroups: [{ns, resources: [{kind, actions}]}], pageAccess: string[] }
 */
const clusterScopes = computed(() => {
  type ResRow = { kind: string; actions: string[] }
  type NsGroup = { ns: string; resources: ResRow[] }
  type Scope = { clusterUid: string; nsGroups: Map<string, Map<string, Set<string>>>; pageAccess: Set<string> }

  const map = new Map<string, Scope>()
  const ensure = (uid: string) => {
    if (!map.has(uid)) map.set(uid, { clusterUid: uid, nsGroups: new Map(), pageAccess: new Set() })
    return map.get(uid)!
  }
  for (const p of permissions.value) {
    const scope = ensure(p.cluster)
    if (p.resource.startsWith('page:')) {
      scope.pageAccess.add(p.resource.substring(5))
    } else {
      if (!scope.nsGroups.has(p.namespace)) scope.nsGroups.set(p.namespace, new Map())
      const resMap = scope.nsGroups.get(p.namespace)!
      if (!resMap.has(p.resource)) resMap.set(p.resource, new Set())
      resMap.get(p.resource)!.add(p.action)
    }
  }
  return Array.from(map.values()).map(scope => ({
    clusterUid: scope.clusterUid,
    nsGroups: Array.from(scope.nsGroups.entries()).map(([ns, resMap]) => ({
      ns,
      resources: Array.from(resMap.entries()).map(([kind, acts]) => ({ kind, actions: Array.from(acts) }))
    })),
    pageAccess: Array.from(scope.pageAccess),
    nameFiltersForCluster: nameFilters.value.filter(f => f.clusterUid === scope.clusterUid)
  }))
})

// Total permission count for tab badge
const totalPermCount = computed(() => clusterScopes.value.length)

// ── Data loading ───────────────────────────────────────────────────────────
async function loadRoles() {
  loading.value = true
  try { roles.value = (await $api.get('/admin/roles')).data }
  catch { toast.add({ title: 'Roles could not be loaded', color: 'error' }) }
  finally { loading.value = false }
}

async function selectRole(role: RoleTemplate) {
  selectedRole.value = role
  const [pRes, uRes, fRes] = await Promise.all([
    $api.get(`/admin/roles/${role.id}/permissions`),
    $api.get(`/admin/roles/${role.id}/users`),
    $api.get(`/admin/roles/${role.id}/name-filters`)
  ])
  permissions.value = pRes.data ?? []
  userBindings.value = uRes.data ?? []
  nameFilters.value = fRes.data ?? []
}

// ── Role CRUD ─────────────────────────────────────────────────────────────
function openCreate() {
  editingRole.value = null
  Object.assign(roleForm, { name: '', displayName: '', description: '', color: '#6366f1' })
  showRoleModal.value = true
}
function openEdit(role: RoleTemplate) {
  editingRole.value = role
  Object.assign(roleForm, { name: role.name, displayName: role.displayName, description: role.description, color: role.color })
  showRoleModal.value = true
}
async function saveRole() {
  try {
    if (editingRole.value) { await $api.put(`/admin/roles/${editingRole.value.id}`, roleForm) }
    else { await $api.post('/admin/roles', roleForm) }
    toast.add({ title: 'Role saved', color: 'success' })
    showRoleModal.value = false
    await loadRoles()
  } catch { toast.add({ title: 'Role could not be saved', color: 'error' }) }
}
async function deleteRole(role: RoleTemplate) {
  if (!await confirmDialog.open({ title: `Delete "${role.displayName || role.name}"?`, description: 'This action cannot be undone.', confirmLabel: 'Delete', color: 'red' })) return
  try {
    await $api.delete(`/admin/roles/${role.id}`)
    toast.add({ title: 'Role deleted', color: 'success' })
    if (selectedRole.value?.id === role.id) selectedRole.value = null
    await loadRoles()
  } catch { toast.add({ title: 'Could not delete', color: 'error' }) }
}

// ── Permissions ───────────────────────────────────────────────────────────
const savingScopeLoading = ref(false)

async function saveClusterScope() {
  if (!selectedRole.value || !scopeForm.clusterUid || !scopeForm.pageEntries.length) return
  savingScopeLoading.value = true
  try {
    for (const entry of scopeForm.pageEntries) {
      if (!entry.pageKey) continue
      const nsList = entry.pageTier === 1 && scopeForm.nsPatterns.length > 0
        ? scopeForm.nsPatterns : ['*']

      for (const ns of nsList) {
        await $api.post(`/admin/roles/${selectedRole.value.id}/permissions`, {
          pageKey: entry.pageKey,
          clusterUid: scopeForm.clusterUid,
          namespacePattern: ns,
          resourceKind: entry.resourceKind || '*',
          actions: entry.pageTier === 3 ? [] : entry.actions
        })
        // Name filter per namespace pattern (Tier 1 & 2)
        if (entry.pageTier !== 3 && entry.namePattern && entry.namePattern !== '*') {
          await $api.post(`/admin/roles/${selectedRole.value.id}/name-filters`, {
            clusterUid: scopeForm.clusterUid,
            nsPattern: ns,
            resourceKind: entry.resourceKind,
            namePattern: entry.namePattern
          })
        }
      }
    }
    toast.add({ title: 'Scope saved', color: 'success' })
    showScopeModal.value = false
    await selectRole(selectedRole.value)
  } catch { toast.add({ title: 'Scope could not be saved', color: 'error' }) }
  finally { savingScopeLoading.value = false }
}

async function removeResourceRow(clusterUid: string, ns: string, kind: string, actions: string[]) {
  if (!selectedRole.value) return
  try {
    await Promise.all(actions.map(action =>
      $api.delete(`/admin/roles/${selectedRole.value!.id}/permissions/single`, {
        data: { cluster: clusterUid, namespace: ns, resource: kind, action }
      })
    ))
    // Also remove page access rule
    await $api.delete(`/admin/roles/${selectedRole.value.id}/permissions/single`, {
      data: { cluster: clusterUid, namespace: '*', resource: `page:${kind.toLowerCase()}`, action: 'access' }
    }).catch(() => { /* ignore if not found */ })
    await selectRole(selectedRole.value)
  } catch { toast.add({ title: 'Could not delete', color: 'error' }) }
}

async function removePageAccess(clusterUid: string, pageKey: string) {
  if (!selectedRole.value) return
  try {
    await $api.delete(`/admin/roles/${selectedRole.value.id}/permissions/single`, {
      data: { cluster: clusterUid, namespace: '*', resource: `page:${pageKey}`, action: 'access' }
    })
    await selectRole(selectedRole.value)
  } catch { toast.add({ title: 'Could not delete', color: 'error' }) }
}

async function clearPermissions() {
  if (!selectedRole.value) return
  if (!await confirmDialog.open({ title: 'Delete all permissions?', confirmLabel: 'Clear', color: 'red' })) return
  await $api.delete(`/admin/roles/${selectedRole.value.id}/permissions`)
  await selectRole(selectedRole.value)
}

async function removeNameFilter(filterId: number) {
  if (!selectedRole.value) return
  try {
    await $api.delete(`/admin/roles/${selectedRole.value.id}/name-filters/${filterId}`)
    await selectRole(selectedRole.value)
  } catch { toast.add({ title: 'Filter could not be deleted', color: 'error' }) }
}

// ── User assignments ───────────────────────────────────────────────────────
async function assignUser() {
  if (!selectedRole.value || !userForm.username) return
  try {
    await $api.post(`/admin/roles/${selectedRole.value.id}/users`, { username: userForm.username, clusterUids: userForm.clusterUids })
    toast.add({ title: 'User assigned', color: 'success' })
    showUserModal.value = false
    Object.assign(userForm, { username: '', clusterUids: ['*'] })
    await selectRole(selectedRole.value)
  } catch { toast.add({ title: 'User could not be assigned', color: 'error' }) }
}
async function removeUser(binding: UserBinding) {
  if (!selectedRole.value) return
  await $api.delete(`/admin/roles/${selectedRole.value.id}/users/${binding.username}`, { params: { clusterUid: binding.clusterUid } })
  await selectRole(selectedRole.value)
}

onMounted(async () => {
  await Promise.all([clusterStore.fetchClusters(), loadRoles(), loadConfig()])
})
</script>

<template>
  <UDashboardPanel>
    <UDashboardNavbar title="Role Management">
      <template #right>
        <UButton icon="i-lucide-plus" color="primary" @click="openCreate">New Role</UButton>
      </template>
    </UDashboardNavbar>

    <div class="flex h-full overflow-hidden">
      <!-- ── Left: Role list ──────────────────────────────────────────── -->
      <div class="w-64 border-r border-gray-200 dark:border-gray-800 flex flex-col overflow-y-auto flex-shrink-0">
        <div class="p-2 space-y-0.5">
          <div v-if="loading" class="py-8 text-center text-xs text-gray-400">Loading…</div>
          <div v-else-if="!roles.length" class="py-8 text-center text-xs text-gray-400">No roles yet</div>
          <div
            v-for="role in roles" :key="role.id"
            class="group flex items-center justify-between rounded-lg px-3 py-2 cursor-pointer transition-all"
            :class="selectedRole?.id === role.id
              ? 'bg-primary-50 dark:bg-primary-900/20 border border-primary-300 dark:border-primary-700'
              : 'hover:bg-gray-50 dark:hover:bg-gray-800/50 border border-transparent'"
            @click="selectRole(role)"
          >
            <div class="flex items-center gap-2 min-w-0">
              <div class="w-2.5 h-2.5 rounded-full flex-shrink-0" :style="`background:${role.color}`" />
              <div class="min-w-0">
                <p class="text-sm font-medium truncate">{{ role.displayName || role.name }}</p>
                <p class="text-xs text-gray-400 truncate">{{ role.name }}</p>
              </div>
            </div>
            <div class="flex gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
              <UButton v-if="role.name !== 'SUPERADMIN'" size="xs" variant="ghost" icon="i-lucide-pencil" @click.stop="openEdit(role)" />
              <UButton v-if="role.name !== 'SUPERADMIN'" size="xs" variant="ghost" color="error" icon="i-lucide-trash-2" @click.stop="deleteRole(role)" />
            </div>
          </div>
        </div>
      </div>

      <!-- ── Right: Role detail ──────────────────────────────────────── -->
      <div class="flex-1 overflow-y-auto">
        <!-- Empty state -->
        <div v-if="!selectedRole" class="flex flex-col items-center justify-center h-full text-gray-400 gap-3">
          <UIcon name="i-lucide-shield" class="w-14 h-14 opacity-20" />
          <p class="text-sm">Select a role from the left panel</p>
        </div>

        <template v-else>
          <!-- Role header -->
          <div class="px-6 pt-5 pb-3 border-b border-gray-200 dark:border-gray-800 flex items-center gap-3">
            <div class="w-3 h-3 rounded-full" :style="`background:${selectedRole.color}`" />
            <div>
              <h2 class="text-base font-semibold">{{ selectedRole.displayName || selectedRole.name }}</h2>
              <p class="text-xs text-gray-400">{{ selectedRole.description || selectedRole.name }}</p>
            </div>
          </div>

          <!-- Tabs -->
          <div class="flex gap-0 border-b border-gray-200 dark:border-gray-800 px-6">
            <button
              v-for="tab in [{ id: 'permissions', label: 'Permissions', icon: 'i-lucide-key', count: totalPermCount },
                             { id: 'users', label: 'Users', icon: 'i-lucide-users', count: userBindings.length }]"
              :key="tab.id"
              class="flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-all -mb-px"
              :class="activeTab === tab.id
                ? 'border-primary-500 text-primary-600 dark:text-primary-400'
                : 'border-transparent text-gray-500 hover:text-gray-700'"
              @click="activeTab = tab.id as 'permissions' | 'users'"
            >
              <UIcon :name="tab.icon" class="w-3.5 h-3.5" />
              {{ tab.label }}
              <span class="text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 rounded-full px-1.5 py-0.5 font-mono">{{ tab.count }}</span>
            </button>
          </div>

          <!-- ── PERMISSIONS TAB ──────────────────────────────────────── -->
          <div v-if="activeTab === 'permissions'" class="p-6 space-y-4">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-sm font-medium">Cluster Scopes</p>
                <p class="text-xs text-gray-400 mt-0.5">Namespace patterns and page permissions per cluster</p>
              </div>
              <div class="flex gap-2">
                <UButton v-if="clusterScopes.length" size="xs" variant="outline" color="error" icon="i-lucide-trash-2" @click="clearPermissions">Clear All</UButton>
                <UButton size="xs" icon="i-lucide-plus" color="primary" @click="() => { resetScopeForm(); showScopeModal = true }">Add Scope</UButton>
              </div>
            </div>

            <!-- Cluster scope cards -->
            <div v-if="clusterScopes.length" class="space-y-4">
              <div
                v-for="scope in clusterScopes" :key="scope.clusterUid"
                class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 overflow-hidden"
              >
                <!-- Cluster header -->
                <div class="flex items-center gap-2 px-4 py-2.5 bg-gray-50 dark:bg-gray-800/60 border-b border-gray-100 dark:border-gray-700">
                  <UIcon name="i-lucide-server" class="w-4 h-4 text-gray-500" />
                  <span class="text-sm font-semibold">{{ clusterName(scope.clusterUid) }}</span>
                  <span class="text-xs text-gray-400 font-mono">({{ scope.clusterUid }})</span>
                </div>

                <div class="p-3 space-y-3">
                  <!-- Namespace-scoped resource groups (Tier 1) -->
                  <div v-if="scope.nsGroups.length" class="space-y-2">
                    <div
                      v-for="nsGroup in scope.nsGroups" :key="nsGroup.ns"
                      class="rounded-lg border border-blue-100 dark:border-blue-900/30 overflow-hidden"
                    >
                      <!-- Namespace tag -->
                      <div class="flex items-center gap-2 px-3 py-1.5 bg-blue-50 dark:bg-blue-950/20">
                        <UIcon name="i-lucide-layers" class="w-3.5 h-3.5 text-blue-400" />
                        <span class="text-xs font-mono font-medium text-blue-700 dark:text-blue-300">{{ nsGroup.ns }}</span>
                      </div>
                      <!-- Resources in this namespace -->
                      <div class="divide-y divide-gray-100 dark:divide-gray-800">
                        <div
                          v-for="res in nsGroup.resources" :key="res.kind"
                          class="flex items-center justify-between px-3 py-2"
                        >
                          <div class="flex items-center gap-2 flex-wrap">
                            <UBadge :label="res.kind" color="primary" variant="soft" size="sm" />
                            <UBadge
                              v-for="a in res.actions" :key="a" :label="a" size="xs" variant="outline"
                              :color="['delete','force-delete','drain'].includes(a) ? 'error' : ['exec','exec-terminal'].includes(a) ? 'warning' : 'neutral'"
                            />
                            <!-- Name filter tags for this resource+cluster -->
                            <span
                              v-for="f in scope.nameFiltersForCluster.filter(f => f.resourceKind === res.kind && f.nsPattern === nsGroup.ns)"
                              :key="f.id"
                              class="text-xs px-2 py-0.5 rounded-full bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300 font-mono flex items-center gap-1"
                            >
                              <UIcon name="i-lucide-filter" class="w-3 h-3" />{{ f.namePattern }}
                              <button class="ml-0.5 hover:text-red-500" @click="removeNameFilter(f.id)">×</button>
                            </span>
                          </div>
                          <UButton size="xs" variant="ghost" color="error" icon="i-lucide-trash-2"
                            @click="removeResourceRow(scope.clusterUid, nsGroup.ns, res.kind, res.actions)" />
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Tier 3 page access -->
                  <div v-if="scope.pageAccess.length">
                    <p class="text-xs text-gray-400 mb-1.5 flex items-center gap-1">
                      <UIcon name="i-lucide-layout" class="w-3.5 h-3.5" />Page Access (Tier 3)
                    </p>
                    <div class="flex flex-wrap gap-1.5">
                      <div
                        v-for="pk in scope.pageAccess" :key="pk"
                        class="flex items-center gap-1 px-2 py-0.5 rounded-full bg-gray-100 dark:bg-gray-800 text-xs"
                      >
                        <span class="font-mono text-gray-600 dark:text-gray-300">{{ pk }}</span>
                        <button class="text-gray-400 hover:text-red-500 ml-0.5" @click="removePageAccess(scope.clusterUid, pk)">×</button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-else class="rounded-lg border border-dashed border-gray-200 dark:border-gray-700 py-12 flex flex-col items-center text-gray-400 gap-2">
              <UIcon name="i-lucide-lock-open" class="w-8 h-8 opacity-30" />
              <p class="text-sm">No scopes defined yet</p>
              <UButton size="xs" variant="outline" icon="i-lucide-plus" @click="() => { resetScopeForm(); showScopeModal = true }">Add Scope</UButton>
            </div>
          </div>

          <!-- ── USERS TAB ─────────────────────────────────────────────── -->
          <div v-if="activeTab === 'users'" class="p-6">
            <div class="flex items-center justify-between mb-4">
              <p class="text-sm text-gray-500">Users assigned to this role</p>
              <UButton size="xs" icon="i-lucide-user-plus" color="primary" @click="showUserModal = true">Assign User</UButton>
            </div>
            <div v-if="userBindings.length" class="space-y-1.5">
              <div
                v-for="(b, i) in userBindings" :key="i"
                class="flex items-center justify-between rounded-lg px-3 py-2 bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700"
              >
                <div class="flex items-center gap-2">
                  <UAvatar :alt="b.username" size="sm" />
                  <div>
                    <p class="text-sm font-medium">{{ b.displayName || b.username }}</p>
                    <p class="text-xs text-gray-400 flex items-center gap-1">
                      <UIcon name="i-lucide-server" class="w-3 h-3" />
                      {{ b.clusterUid === '*' ? 'All clusters' : b.clusterUid }}
                    </p>
                  </div>
                </div>
                <UButton size="xs" variant="ghost" color="error" icon="i-lucide-user-minus" @click="removeUser(b)" />
              </div>
            </div>
            <div v-else class="flex flex-col items-center justify-center py-16 text-gray-400 gap-2">
              <UIcon name="i-lucide-user-x" class="w-10 h-10 opacity-20" />
              <p class="text-sm">No users assigned to this role</p>
            </div>
          </div>
        </template>
      </div>
    </div>
  </UDashboardPanel>

  <!-- ── ROLE CREATE/EDIT MODAL ────────────────────────────────────────── -->
  <UModal v-model:open="showRoleModal" :title="editingRole ? 'Edit Role' : 'Create New Role'">
    <template #body>
      <div class="space-y-4 p-1">
        <UFormField label="Role Name (slug)" required hint="Cannot be changed">
          <UInput v-model="roleForm.name" placeholder="deployment_viewer" :disabled="!!editingRole" class="w-full font-mono" />
        </UFormField>
        <UFormField label="Display Name">
          <UInput v-model="roleForm.displayName" placeholder="Deployment Viewer" class="w-full" />
        </UFormField>
        <UFormField label="Description">
          <UTextarea v-model="roleForm.description" placeholder="Purpose of this role..." rows="2" class="w-full" />
        </UFormField>
        <UFormField label="Color">
          <input type="color" v-model="roleForm.color" class="h-9 w-16 rounded-md border border-gray-200 dark:border-gray-700 cursor-pointer" />
        </UFormField>
        <div class="flex justify-end gap-2 pt-2">
          <UButton variant="ghost" @click="showRoleModal = false">Cancel</UButton>
          <UButton color="primary" @click="saveRole">Save</UButton>
        </div>
      </div>
    </template>
  </UModal>

  <!-- ── CLUSTER-SCOPE MODAL ──────────────────────────────────────────── -->
  <UModal v-model:open="showScopeModal" title="Add Cluster Scope" :ui="{ width: 'sm:max-w-3xl' }">
    <template #body>
      <div class="space-y-5 p-1">

        <!-- Cluster selector -->
        <UFormField label="Cluster" required>
          <USelectMenu
            v-model="scopeForm.clusterUid"
            :items="[{ label: 'All Clusters (*)', value: '*' }, ...clusters.map(c => ({ label: c.name, value: c.uid ?? c.name }))]"
            value-key="value" label-key="label"
            placeholder="Select cluster…"
            class="w-full"
          />
        </UFormField>

        <!-- Namespace patterns (shared across all Tier 1 pages in this scope) -->
        <UFormField label="Namespace Patterns" hint="Applies only to Tier 1 (namespace-scoped) pages. Defaults to '*' if left empty.">
          <div class="space-y-2">
            <div class="flex gap-2">
              <UInput
                v-model="nsPatternInput"
                placeholder="prod-*, test, *staging*"
                class="flex-1 font-mono text-sm"
                @keydown.enter.prevent="addNsPattern"
              />
              <UButton icon="i-lucide-plus" size="sm" variant="outline" @click="addNsPattern">Add</UButton>
            </div>
            <div v-if="scopeForm.nsPatterns.length" class="flex flex-wrap gap-1.5">
              <span
                v-for="ns in scopeForm.nsPatterns" :key="ns"
                class="flex items-center gap-1 px-2.5 py-1 rounded-full bg-blue-50 dark:bg-blue-950/30 text-blue-700 dark:text-blue-300 text-xs font-mono border border-blue-200 dark:border-blue-800"
              >
                <UIcon name="i-lucide-layers" class="w-3 h-3" />
                {{ ns }}
                <button class="ml-0.5 hover:text-red-500 leading-none" @click="removeNsPattern(ns)">×</button>
              </span>
            </div>
            <p v-else class="text-xs text-gray-400 italic">
              No namespace patterns added — <code class="font-mono">*</code> will be used for Tier 1 pages
            </p>
          </div>
        </UFormField>

        <!-- Page / resource entries -->
        <div>
          <div class="flex items-center justify-between mb-2">
            <div>
              <p class="text-sm font-medium">Page / Resource Permissions</p>
              <p class="text-xs text-gray-400">Select pages to include in this scope and configure their permissions</p>
            </div>
            <UButton size="xs" icon="i-lucide-plus" variant="outline" @click="addPageEntry">Add Page</UButton>
          </div>

          <!-- Empty state -->
          <div v-if="!scopeForm.pageEntries.length" class="rounded-lg border border-dashed border-gray-200 dark:border-gray-700 py-8 flex flex-col items-center text-gray-400 gap-2">
            <UIcon name="i-lucide-file-plus" class="w-7 h-7 opacity-30" />
            <p class="text-xs">Click "Add Page" to add pages to this scope</p>
          </div>

          <!-- Page entry cards -->
          <div class="space-y-3">
            <div
              v-for="(entry, i) in scopeForm.pageEntries" :key="i"
              class="rounded-xl border border-gray-200 dark:border-gray-700 p-3 space-y-3 bg-gray-50/50 dark:bg-gray-800/30"
            >
              <!-- Row: page selector + tier badge + remove button -->
              <div class="flex items-start gap-2">
                <USelectMenu
                  v-model="entry.pageKey"
                  :items="pageOptions.map(p => ({ label: p.displayName || p.name, value: p.name, icon: p.icon, suffix: SUPERADMIN_ONLY_PAGES.has(p.name) ? '🔒 Superadmin Only' : undefined }))"
                  value-key="value" label-key="label"
                  placeholder="Page Selector…"
                  class="flex-1"
                  @update:model-value="onPageEntryChange(entry)"
                />
                <UBadge
                  v-if="entry.pageKey"
                  :label="entry.pageTier === 1 ? 'Tier 1' : entry.pageTier === 2 ? 'Tier 2' : 'Tier 3'"
                  :color="entry.pageTier === 1 ? 'blue' : entry.pageTier === 2 ? 'violet' : 'neutral'"
                  size="sm"
                  class="mt-1.5 flex-shrink-0"
                />
                <!-- Superadmin-only warning badge -->
                <UBadge
                  v-if="entry.pageKey && SUPERADMIN_ONLY_PAGES.has(entry.pageKey)"
                  label="Superadmin Only"
                  color="error"
                  variant="subtle"
                  size="sm"
                  icon="i-lucide-lock"
                  class="mt-1.5 flex-shrink-0"
                />
                <UButton size="xs" variant="ghost" color="error" icon="i-lucide-trash-2" class="flex-shrink-0 mt-0.5" @click="removePageEntry(i)" />
              </div>

              <!-- Name pattern (Tier 1 + Tier 2 only) -->
              <UFormField
                v-if="entry.pageKey && entry.pageTier !== 3"
                label="Name Pattern"
                hint="Optional — leave blank to allow access to all resources"
              >
                <UInput
                  v-model="entry.namePattern"
                  placeholder="*record*, prod-001, * (all)"
                  class="w-full font-mono text-sm"
                />
              </UFormField>

              <!-- Action checkboxes (Tier 1 + Tier 2) -->
              <div v-if="entry.pageKey && entry.pageTier !== 3">
                <div class="flex items-center justify-between mb-1.5">
                  <p class="text-xs font-medium text-gray-600 dark:text-gray-400">Permissions</p>
                  <div v-if="actionsForPage(entry.pageKey).length" class="flex gap-3">
                    <button class="text-xs text-primary-600 dark:text-primary-400 hover:underline" @click="entry.actions = actionsForPage(entry.pageKey).map(a => a.code)">Select All</button>
                    <button class="text-xs text-gray-400 hover:underline" @click="entry.actions = []">Clear</button>
                  </div>
                </div>
                <div v-if="actionsForPage(entry.pageKey).length" class="flex flex-wrap gap-1.5">
                  <label
                    v-for="action in actionsForPage(entry.pageKey)" :key="action.code"
                    class="flex items-center gap-1 cursor-pointer rounded-md px-2.5 py-1 text-xs font-medium border transition-all select-none"
                    :class="entry.actions.includes(action.code)
                      ? 'bg-primary-50 border-primary-400 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                      : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:border-gray-400'"
                  >
                    <input type="checkbox" :value="action.code" v-model="entry.actions" class="sr-only" />
                    {{ action.displayName }}
                    <span v-if="action.isDangerous" class="ml-0.5 text-red-500" title="Dangerous">⚠</span>
                    <span v-else-if="action.requiresWrite" class="ml-0.5 text-amber-500" title="Requires write">✎</span>
                  </label>
                </div>
                <p v-else class="text-xs text-gray-400 italic">No actions defined for this page</p>
              </div>

              <!-- Tier 3 info note -->
              <div
                v-if="entry.pageKey && entry.pageTier === 3"
                class="rounded-lg bg-blue-50 dark:bg-blue-950/20 border border-blue-100 dark:border-blue-900/30 px-3 py-2 text-xs text-blue-700 dark:text-blue-300 flex items-center gap-1.5"
              >
                <UIcon name="i-lucide-info" class="w-3.5 h-3.5 flex-shrink-0" />
                Only page access will be granted — no namespace, name, or action restrictions applied.
              </div>
            </div>
          </div>
        </div>

        <!-- Footer actions -->
        <div class="flex justify-end gap-2 pt-2 border-t border-gray-100 dark:border-gray-700">
          <UButton variant="ghost" @click="showScopeModal = false">Cancel</UButton>
          <UButton
            color="primary"
            :loading="savingScopeLoading"
            :disabled="!scopeForm.clusterUid || !scopeForm.pageEntries.length"
            icon="i-lucide-save"
            @click="saveClusterScope"
          >
            Save
          </UButton>
        </div>

      </div>
    </template>
  </UModal>

  <!-- ── ASSIGN USER MODAL ─────────────────────────────────────────────── -->
  <UModal v-model:open="showUserModal" title="Assign User">
    <template #body>
      <div class="space-y-4 p-1">
        <UFormField label="Username" required>
          <UInput v-model="userForm.username" placeholder="username" class="w-full" autofocus />
        </UFormField>
        <UFormField label="Cluster(s)" hint="Which clusters should this role be active on?">
          <div class="mt-1 rounded-lg border border-gray-200 dark:border-gray-700 divide-y divide-gray-100 dark:divide-gray-700 overflow-hidden">
            <label class="flex items-center gap-2 px-3 py-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800/50">
              <input type="checkbox" value="*" v-model="userForm.clusterUids" class="rounded" />
              <UIcon name="i-lucide-globe" class="w-4 h-4 text-gray-400" />
              <span class="text-sm font-medium">All Clusters</span>
            </label>
            <label v-for="c in clusters" :key="c.uid" class="flex items-center gap-2 px-3 py-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800/50">
              <input type="checkbox" :value="c.uid" v-model="userForm.clusterUids" class="rounded" />
              <UIcon name="i-lucide-server" class="w-4 h-4 text-gray-400" />
              <span class="text-sm">{{ c.name }}</span>
            </label>
          </div>
        </UFormField>
        <div class="flex justify-end gap-2 pt-2">
          <UButton variant="ghost" @click="showUserModal = false">Cancel</UButton>
          <UButton color="primary" :disabled="!userForm.username || !userForm.clusterUids.length" @click="assignUser">Assign</UButton>
        </div>
      </div>
    </template>
  </UModal>
</template>
