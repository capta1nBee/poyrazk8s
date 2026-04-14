<script setup lang="ts">
const { $api } = useNuxtApp()
const toast = useToast()
const authStore = useAuthStore()
const confirmDialog = useConfirm()

if (!authStore.user?.isSuperadmin) {
  throw createError({ statusCode: 403, message: 'Access denied' })
}

const pages = ref<any[]>([])
const actions = ref<any[]>([])
const loading = ref(false)
const selectedPage = ref<any>(null)

const showPageModal = ref(false)
const showActionModal = ref(false)
const editingPage = ref<any>(null)
const editingAction = ref<any>(null)

const pageForm = ref({
  name: '',
  displayName: '',
  description: '',
  resourceKind: '',
  icon: '',
  isActive: true,
  isNamespaceScoped: false,
  pageTier: 3 // 1=NS+Name+Action, 2=Name+Action, 3=Page-only
})

const actionForm = ref({
  pageId: null as number | null,
  name: '',
  displayName: '',
  description: '',
  actionCode: '',
  resourceKind: '',
  requiresWrite: false,
  isDangerous: false,
  icon: '',
  isActive: true
})

const fetchPages = async () => {
  loading.value = true
  try {
    const res = await $api.get('/admin/pages-actions/pages') // Fixed path with /api
    pages.value = res.data
  } catch (e: any) {
    toast.add({ title: 'Failed to fetch pages', description: e.message, color: 'error' })
  } finally {
    loading.value = false
  }
}

const fetchActions = async (pageId?: number) => {
  if (!pageId) return
  try {
    const res = await $api.get(`/admin/pages-actions/actions/page/${pageId}`) // Fixed path with /api
    actions.value = res.data
  } catch (e: any) {
    toast.add({ title: 'Failed to fetch actions', description: e.message, color: 'error' })
  }
}

const selectPage = async (page: any) => {
  selectedPage.value = page
  await fetchActions(page.id)
}

const openPageModal = (page?: any) => {
  editingPage.value = page
  pageForm.value = page
    ? { ...page }
    : { name: '', displayName: '', description: '', resourceKind: '', icon: '', isActive: true, isNamespaceScoped: false, pageTier: 3 }
  showPageModal.value = true
}

const openActionModal = (action?: any, pageId?: number) => {
  editingAction.value = action
  actionForm.value = action
    ? { ...action }
    : {
        pageId: pageId || selectedPage.value?.id,
        name: '',
        displayName: '',
        description: '',
        actionCode: '',
        resourceKind: selectedPage.value?.resourceKind || '',
        requiresWrite: false,
        isDangerous: false,
        icon: '',
        isActive: true
      }
  showActionModal.value = true
}

const savePage = async () => {
  try {
    editingPage.value
      ? await $api.put(`/admin/pages-actions/pages/${editingPage.value.id}`, pageForm.value)
      : await $api.post('/admin/pages-actions/pages', pageForm.value)

    toast.add({ title: 'Page saved', color: 'success' })
    showPageModal.value = false
    await fetchPages()
  } catch (e: any) {
    toast.add({ title: 'Failed to save page', description: e.message, color: 'error' })
  }
}

const saveAction = async () => {
  try {
    editingAction.value
      ? await $api.put(`/admin/pages-actions/actions/${editingAction.value.id}`, actionForm.value)
      : await $api.post('/admin/pages-actions/actions', actionForm.value)

    toast.add({ title: 'Action saved', color: 'success' })
    showActionModal.value = false
    if (selectedPage.value) fetchActions(selectedPage.value.id)
  } catch (e: any) {
    toast.add({ title: 'Failed to save action', description: e.message, color: 'error' })
  }
}

const deletePage = async (page: any) => {
  if (!await confirmDialog.open({
    title: 'Delete Page',
    description: `Delete page "${page.displayName}"?`,
    confirmLabel: 'Delete',
    color: 'red'
  })) return
  try {
    await $api.delete(`/admin/pages-actions/pages/${page.id}`)
    toast.add({ title: 'Page deleted', color: 'success' })
    if (selectedPage.value?.id === page.id) selectedPage.value = null
    fetchPages()
  } catch (e: any) {
    toast.add({ title: 'Failed to delete page', description: e.message, color: 'error' })
  }
}

const deleteAction = async (action: any) => {
  if (!await confirmDialog.open({
    title: 'Delete Action',
    description: `Delete action "${action.displayName}"?`,
    confirmLabel: 'Delete',
    color: 'red'
  })) return
  try {
    await $api.delete(`/admin/pages-actions/actions/${action.id}`)
    toast.add({ title: 'Action deleted', color: 'success' })
    if (selectedPage.value) fetchActions(selectedPage.value.id)
  } catch (e: any) {
    toast.add({ title: 'Failed to delete action', description: e.message, color: 'error' })
  }
}

onMounted(fetchPages)
</script>

<template>
  <UDashboardPanel>
    <template #header>
      <UDashboardNavbar title="Page & Action Management">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="grid grid-cols-12 gap-6 h-full p-4">

        <!-- PAGES -->
        <UCard class="col-span-12 lg:col-span-4 flex flex-col h-full overflow-hidden">
          <template #header>
            <div class="flex justify-between items-center">
              <h3 class="font-semibold">Pages</h3>
              <UButton size="sm" icon="i-lucide-plus" @click="openPageModal()">New</UButton>
            </div>
          </template>

          <div class="flex-1 overflow-y-auto divide-y dark:divide-gray-800">
            <div
              v-for="p in pages"
              :key="p.id"
              @click="selectPage(p)"
              class="p-3 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-900 group flex justify-between items-center transition-colors"
              :class="selectedPage?.id === p.id && 'bg-primary-50 dark:bg-primary-900/40 border-l-2 border-primary-500'"
            >
              <div>
                <div class="font-medium flex items-center gap-2">
                  <UIcon v-if="p.icon" :name="p.icon" class="w-4 h-4 text-gray-500" />
                  {{ p.displayName }}
                  <UBadge v-if="p.pageTier === 1" label="T1" size="xs" color="blue" variant="soft" />
                  <UBadge v-else-if="p.pageTier === 2" label="T2" size="xs" color="violet" variant="soft" />
                  <UBadge v-else label="T3" size="xs" color="gray" variant="soft" />
                </div>
                <div class="text-[10px] text-gray-500 font-mono">{{ p.name }}<span v-if="p.resourceKind"> · {{ p.resourceKind }}</span></div>
              </div>
              <div class="opacity-0 group-hover:opacity-100 flex gap-1 transition-opacity">
                <UButton size="xs" icon="i-lucide-edit-2" variant="ghost" color="neutral" @click.stop="openPageModal(p)" />
                <UButton size="xs" icon="i-lucide-trash-2" color="red" variant="ghost" @click.stop="deletePage(p)" />
              </div>
            </div>
          </div>
        </UCard>

        <!-- ACTIONS -->
        <UCard class="col-span-12 lg:col-span-8 flex flex-col h-full overflow-hidden">
          <template #header>
            <div class="flex justify-between items-center">
              <h3 class="font-semibold flex items-center gap-2">
                Actions
                <UBadge v-if="selectedPage" size="sm" color="neutral" variant="subtle">
                  {{ selectedPage.displayName }}
                </UBadge>
              </h3>
              <UButton
                v-if="selectedPage"
                size="sm"
                icon="i-lucide-plus"
                @click="openActionModal(undefined, selectedPage.id)"
              >
                New Action
              </UButton>
            </div>
          </template>

          <div v-if="!selectedPage" class="flex-1 flex flex-col items-center justify-center text-gray-400 gap-2">
            <UIcon name="i-lucide-mouse-pointer-2" class="w-10 h-10 opacity-20" />
            <p>Select a page from the left to manage its actions</p>
          </div>

          <div v-else class="flex-1 overflow-y-auto p-4 space-y-3">
            <div
              v-for="a in actions"
              :key="a.id"
              class="border dark:border-gray-800 rounded-lg p-4 flex justify-between items-center hover:bg-gray-50 dark:hover:bg-gray-900 border-l-4"
              :class="a.isDangerous ? 'border-l-red-500' : 'border-l-blue-500'"
            >
              <div class="flex items-start gap-3">
                <div class="bg-gray-100 dark:bg-gray-800 p-2 rounded">
                  <UIcon :name="a.icon || 'i-lucide-zap'" class="w-5 h-5 text-gray-600" />
                </div>
                <div>
                  <div class="font-semibold flex items-center gap-2">
                    {{ a.displayName }}
                    <UBadge v-if="a.requiresWrite" color="orange" size="xs" variant="subtle">Write Required</UBadge>
                  </div>
                  <div class="text-xs font-mono text-gray-500">{{ a.actionCode }}</div>
                  <div v-if="a.description" class="text-xs text-gray-400 mt-1 italic">{{ a.description }}</div>
                </div>
              </div>
              <div class="flex gap-2">
                <UButton size="sm" icon="i-lucide-edit" variant="ghost" color="neutral" @click.stop="openActionModal(a)" />
                <UButton size="sm" icon="i-lucide-trash-2" color="red" variant="ghost" @click.stop="deleteAction(a)" />
              </div>
            </div>
            
            <div v-if="actions.length === 0" class="text-center py-10 text-gray-400 border-2 border-dashed border-gray-100 dark:border-gray-800 rounded-lg">
              No actions defined for this page.
            </div>
          </div>
        </UCard>

      </div>

      <!-- PAGE MODAL -->
      <UModal v-model:open="showPageModal" :title="editingPage ? 'Edit Page' : 'Create Page'">
        <template #body>
          <UForm :state="pageForm" class="space-y-4">
            <UFormField label="Page Name (Machine)" name="name" required>
              <UInput v-model="pageForm.name" placeholder="e.g., node-details" />
            </UFormField>
            
            <UFormField label="Display Name" name="displayName" required>
              <UInput v-model="pageForm.displayName" placeholder="e.g., Node Details" />
            </UFormField>
            
            <UFormField label="Resource Kind" name="resourceKind" required>
              <UInput v-model="pageForm.resourceKind" placeholder="e.g., Node" />
            </UFormField>
            
            <UFormField label="Icon" name="icon">
              <UInput v-model="pageForm.icon" placeholder="e.g., i-lucide-box" />
            </UFormField>

            <UFormField label="Description" name="description">
              <UTextarea v-model="pageForm.description" placeholder="Short description..." />
            </UFormField>

            <div class="grid grid-cols-2 gap-4">
              <UFormField label="Permission Tier" name="pageTier" hint="1=NS+Name+Action, 2=Name+Action, 3=Page-only">
                <USelectMenu
                  v-model="pageForm.pageTier"
                  :items="[{label:'Tier 1 — Namespace + Name + Action',value:1},{label:'Tier 2 — Name + Action (cluster-scoped)',value:2},{label:'Tier 3 — Page access only',value:3}]"
                  value-key="value" label-key="label"
                  class="w-full"
                />
              </UFormField>
              <UFormField label="Namespace-Scoped?" name="isNamespaceScoped">
                <div class="flex items-center gap-2 mt-2">
                  <UToggle v-model="pageForm.isNamespaceScoped" />
                  <span class="text-sm text-gray-600">{{ pageForm.isNamespaceScoped ? 'Yes' : 'No' }}</span>
                </div>
              </UFormField>
            </div>

            <UFormField label="Status" name="isActive">
              <UToggle v-model="pageForm.isActive" />
              <span class="ml-2 text-sm text-gray-600">{{ pageForm.isActive ? 'Active' : 'Inactive' }}</span>
            </UFormField>
          </UForm>
        </template>
        <template #footer>
          <div class="flex justify-end gap-2 w-full">
            <UButton color="neutral" variant="ghost" @click="showPageModal = false">Cancel</UButton>
            <UButton @click="savePage" :loading="loading">{{ editingPage ? 'Update' : 'Create' }}</UButton>
          </div>
        </template>
      </UModal>

      <!-- ACTION MODAL -->
      <UModal v-model:open="showActionModal" :title="editingAction ? 'Edit Action' : 'Create Action'">
        <template #body>
          <UForm :state="actionForm" class="space-y-4">
            <div class="grid grid-cols-2 gap-4">
              <UFormField label="Name" name="name" required>
                <UInput v-model="actionForm.name" placeholder="delete-pod" />
              </UFormField>
              <UFormField label="Action Code" name="actionCode" required>
                <UInput v-model="actionForm.actionCode" placeholder="DELETE" />
              </UFormField>
            </div>

            <UFormField label="Display Name" name="displayName" required>
              <UInput v-model="actionForm.displayName" placeholder="Delete Pod" />
            </UFormField>

            <UFormField label="Resource Kind" name="resourceKind" required>
              <UInput v-model="actionForm.resourceKind" placeholder="Pod" />
            </UFormField>

            <UFormField label="Icon" name="icon">
              <UInput v-model="actionForm.icon" placeholder="i-lucide-trash" />
            </UFormField>

            <UFormField label="Description" name="description">
              <UTextarea v-model="actionForm.description" placeholder="What does this action do?" />
            </UFormField>

            <div class="flex gap-6">
              <UFormField label="Write Required" name="requiresWrite">
                <UToggle v-model="actionForm.requiresWrite" />
              </UFormField>
              
              <UFormField label="Is Dangerous" name="isDangerous">
                <UToggle v-model="actionForm.isDangerous" />
              </UFormField>

              <UFormField label="Active" name="isActive">
                <UToggle v-model="actionForm.isActive" />
              </UFormField>
            </div>
          </UForm>
        </template>
        <template #footer>
          <div class="flex justify-end gap-2 w-full">
            <UButton color="neutral" variant="ghost" @click="showActionModal = false">Cancel</UButton>
            <UButton @click="saveAction">{{ editingAction ? 'Update' : 'Create' }}</UButton>
          </div>
        </template>
      </UModal>
    </template>
  </UDashboardPanel>
</template>

