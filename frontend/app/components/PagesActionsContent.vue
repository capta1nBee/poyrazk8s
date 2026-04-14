<script setup lang="ts">
const { $api } = useNuxtApp()
const toast = useToast()
const confirmDialog = useConfirm()

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
  isActive: true
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
    const response = await $api.get('/admin/pages-actions/pages')
    pages.value = response.data
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch pages',
      description: error.message,
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

const fetchActions = async (pageId?: number) => {
  if (!pageId) return
  
  try {
    const response = await $api.get(`/admin/pages-actions/actions/page/${pageId}`)
    actions.value = response.data
  } catch (error: any) {
    toast.add({
      title: 'Failed to fetch actions',
      description: error.message,
      color: 'error'
    })
  }
}

const openPageModal = (page?: any) => {
  editingPage.value = page
  if (page) {
    pageForm.value = {
      name: page.name,
      displayName: page.displayName,
      description: page.description || '',
      resourceKind: page.resourceKind || '',
      icon: page.icon || '',
      isActive: page.isActive !== false
    }
  } else {
    pageForm.value = {
      name: '',
      displayName: '',
      description: '',
      resourceKind: '',
      icon: '',
      isActive: true
    }
  }
  showPageModal.value = true
}

const openActionModal = (action?: any, pageId?: number) => {
  editingAction.value = action
  if (action) {
    actionForm.value = {
      pageId: action.pageId,
      name: action.name,
      displayName: action.displayName,
      description: action.description || '',
      actionCode: action.actionCode,
      resourceKind: action.resourceKind || '',
      requiresWrite: action.requiresWrite || false,
      isDangerous: action.isDangerous || false,
      icon: action.icon || '',
      isActive: action.isActive !== false
    }
  } else {
    actionForm.value = {
      pageId: pageId || selectedPage.value?.id || null,
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
  }
  showActionModal.value = true
}

const savePage = async () => {
  try {
    if (editingPage.value) {
      await $api.put(`/admin/pages-actions/pages/${editingPage.value.id}`, pageForm.value)
      toast.add({ title: 'Page updated', color: 'success' })
    } else {
      await $api.post('/admin/pages-actions/pages', pageForm.value)
      toast.add({ title: 'Page created', color: 'success' })
    }
    showPageModal.value = false
    await fetchPages()
  } catch (error: any) {
    toast.add({
      title: 'Failed to save page',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  }
}

const saveAction = async () => {
  try {
    if (editingAction.value) {
      await $api.put(`/admin/pages-actions/actions/${editingAction.value.id}`, actionForm.value)
      toast.add({ title: 'Action updated', color: 'success' })
    } else {
      await $api.post('/admin/pages-actions/actions', actionForm.value)
      toast.add({ title: 'Action created', color: 'success' })
    }
    showActionModal.value = false
    if (selectedPage.value) {
      await fetchActions(selectedPage.value.id)
    }
  } catch (error: any) {
    toast.add({
      title: 'Failed to save action',
      description: error.response?.data?.message || error.message,
      color: 'error'
    })
  }
}

const deletePage = async (page: any) => {
  if (!await confirmDialog.open({
    title: 'Delete Page',
    description: `Are you sure you want to delete page "${page.displayName}"?`,
    confirmLabel: 'Delete',
    color: 'red'
  })) return
  
  try {
    await $api.delete(`/admin/pages-actions/pages/${page.id}`)
    toast.add({ title: 'Page deleted', color: 'success' })
    await fetchPages()
    if (selectedPage.value?.id === page.id) {
      selectedPage.value = null
      actions.value = []
    }
  } catch (error: any) {
    toast.add({
      title: 'Failed to delete page',
      description: error.message,
      color: 'error'
    })
  }
}

const deleteAction = async (action: any) => {
  if (!await confirmDialog.open({
    title: 'Delete Action',
    description: `Are you sure you want to delete action "${action.displayName}"?`,
    confirmLabel: 'Delete',
    color: 'red'
  })) return
  
  try {
    await $api.delete(`/admin/pages-actions/actions/${action.id}`)
    toast.add({ title: 'Action deleted', color: 'success' })
    if (selectedPage.value) {
      await fetchActions(selectedPage.value.id)
    }
  } catch (error: any) {
    toast.add({
      title: 'Failed to delete action',
      description: error.message,
      color: 'error'
    })
  }
}

const selectPage = async (page: any) => {
  selectedPage.value = page
  await fetchActions(page.id)
}

onMounted(() => {
  fetchPages()
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
    <!-- Pages Section -->
    <UCard>
      <template #header>
        <div class="flex items-center justify-between">
          <h3 class="text-lg font-semibold">Pages</h3>
          <UButton
            icon="i-lucide-plus"
            label="New Page"
            @click="openPageModal()"
          />
        </div>
      </template>

      <div v-if="loading" class="p-8 flex justify-center">
        <UIcon name="i-lucide-loader-2" class="animate-spin text-3xl text-primary-500" />
      </div>

      <div v-else class="space-y-2">
        <div
          v-for="page in pages"
          :key="page.id"
          :class="[
            'p-4 border rounded-lg cursor-pointer transition-colors',
            selectedPage?.id === page.id
              ? 'border-primary-500 bg-primary-50 dark:bg-primary-950'
              : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
          ]"
          @click="selectPage(page)"
        >
          <div class="flex items-center justify-between">
            <div>
              <div class="font-semibold">{{ page.displayName }}</div>
              <div class="text-sm text-gray-600 dark:text-gray-400">{{ page.name }}</div>
              <div v-if="page.resourceKind" class="text-xs text-gray-500 mt-1">
                Resource: {{ page.resourceKind }}
              </div>
            </div>
            <div class="flex gap-2">
              <UButton
                icon="i-lucide-edit"
                color="neutral"
                variant="ghost"
                size="xs"
                @click.stop="openPageModal(page)"
              />
              <UButton
                icon="i-lucide-trash-2"
                color="red"
                variant="ghost"
                size="xs"
                @click.stop="deletePage(page)"
              />
            </div>
          </div>
          <UBadge
            :color="page.isActive ? 'green' : 'gray'"
            variant="subtle"
            class="mt-2"
          >
            {{ page.isActive ? 'Active' : 'Inactive' }}
          </UBadge>
        </div>
      </div>
    </UCard>

    <!-- Actions Section -->
    <UCard>
      <template #header>
        <div class="flex items-center justify-between">
          <h3 class="text-lg font-semibold">
            Actions
            <span v-if="selectedPage" class="text-sm text-gray-500">
              - {{ selectedPage.displayName }}
            </span>
          </h3>
          <UButton
            v-if="selectedPage"
            icon="i-lucide-plus"
            label="New Action"
            @click="openActionModal(undefined, selectedPage.id)"
          />
        </div>
      </template>

      <div v-if="!selectedPage" class="p-8 text-center text-gray-500">
        Select a page to view its actions
      </div>

      <div v-else class="space-y-2">
        <div
          v-for="action in actions"
          :key="action.id"
          class="p-4 border border-gray-200 dark:border-gray-700 rounded-lg"
        >
          <div class="flex items-center justify-between">
            <div>
              <div class="font-semibold">{{ action.displayName }}</div>
              <div class="text-sm text-gray-600 dark:text-gray-400">{{ action.actionCode }}</div>
              <div v-if="action.description" class="text-xs text-gray-500 mt-1">
                {{ action.description }}
              </div>
              <div class="flex gap-2 mt-2">
                <UBadge
                  v-if="action.requiresWrite"
                  color="orange"
                  variant="subtle"
                  size="xs"
                >
                  Write
                </UBadge>
                <UBadge
                  v-if="action.isDangerous"
                  color="red"
                  variant="subtle"
                  size="xs"
                >
                  Dangerous
                </UBadge>
                <UBadge
                  :color="action.isActive ? 'green' : 'gray'"
                  variant="subtle"
                  size="xs"
                >
                  {{ action.isActive ? 'Active' : 'Inactive' }}
                </UBadge>
              </div>
            </div>
            <div class="flex gap-2">
              <UButton
                icon="i-lucide-edit"
                color="neutral"
                variant="ghost"
                size="xs"
                @click="openActionModal(action)"
              />
              <UButton
                icon="i-lucide-trash-2"
                color="red"
                variant="ghost"
                size="xs"
                @click="deleteAction(action)"
              />
            </div>
          </div>
        </div>
      </div>
    </UCard>
  </div>

  <!-- Page Modal -->
  <UModal v-model:open="showPageModal">
    <UCard>
      <template #header>
        <h3 class="text-lg font-semibold">
          {{ editingPage ? 'Edit Page' : 'Create Page' }}
        </h3>
      </template>

      <UForm :state="pageForm" class="space-y-4">
        <UFormField label="Name" name="name" required>
          <UInput v-model="pageForm.name" :disabled="!!editingPage" />
        </UFormField>

        <UFormField label="Display Name" name="displayName" required>
          <UInput v-model="pageForm.displayName" />
        </UFormField>

        <UFormField label="Description" name="description">
          <UInput v-model="pageForm.description" />
        </UFormField>

        <UFormField label="Resource Kind" name="resourceKind">
          <UInput v-model="pageForm.resourceKind" placeholder="e.g., Pod, Deployment" />
        </UFormField>

        <UFormField label="Icon" name="icon">
          <UInput v-model="pageForm.icon" placeholder="e.g., i-lucide-box" />
        </UFormField>

        <UFormField label="Active" name="isActive">
          <UCheckbox v-model="pageForm.isActive" />
        </UFormField>
      </UForm>

      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton label="Cancel" color="neutral" variant="ghost" @click="showPageModal = false" />
          <UButton label="Save" color="primary" @click="savePage" />
        </div>
      </template>
    </UCard>
  </UModal>

  <!-- Action Modal -->
  <UModal v-model:open="showActionModal">
    <UCard>
      <template #header>
        <h3 class="text-lg font-semibold">
          {{ editingAction ? 'Edit Action' : 'Create Action' }}
        </h3>
      </template>

      <UForm :state="actionForm" class="space-y-4">
        <UFormField label="Page" name="pageId" required>
          <USelect
            v-model="actionForm.pageId"
            :options="pages.map(p => ({ value: p.id, label: p.displayName }))"
            :disabled="!!editingAction"
          />
        </UFormField>

        <UFormField label="Name" name="name" required>
          <UInput v-model="actionForm.name" />
        </UFormField>

        <UFormField label="Display Name" name="displayName" required>
          <UInput v-model="actionForm.displayName" />
        </UFormField>

        <UFormField label="Action Code" name="actionCode" required>
          <UInput v-model="actionForm.actionCode" placeholder="e.g., exec, logs, scale" />
          <template #hint>
            This code is used in permission checks
          </template>
        </UFormField>

        <UFormField label="Description" name="description">
          <UInput v-model="actionForm.description" />
        </UFormField>

        <UFormField label="Resource Kind" name="resourceKind">
          <UInput v-model="actionForm.resourceKind" placeholder="e.g., Pod, Deployment" />
        </UFormField>

        <UFormField label="Requires Write" name="requiresWrite">
          <UCheckbox v-model="actionForm.requiresWrite" />
        </UFormField>

        <UFormField label="Is Dangerous" name="isDangerous">
          <UCheckbox v-model="actionForm.isDangerous" />
        </UFormField>

        <UFormField label="Icon" name="icon">
          <UInput v-model="actionForm.icon" placeholder="e.g., i-lucide-terminal" />
        </UFormField>

        <UFormField label="Active" name="isActive">
          <UCheckbox v-model="actionForm.isActive" />
        </UFormField>
      </UForm>

      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton label="Cancel" color="neutral" variant="ghost" @click="showActionModal = false" />
          <UButton label="Save" color="primary" @click="saveAction" />
        </div>
      </template>
    </UCard>
  </UModal>
</template>
