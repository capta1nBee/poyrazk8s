<script setup lang="ts">
import type { FormSubmitEvent, FormError } from '#ui/types'

interface CreateRuleForm {
  name: string
  description: string
  ruleType: 'process' | 'network' | 'file' | 'file_access'
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  enabled: boolean
  condition: Record<string, any>
  tags: string[]
}

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'create': [data: any]
}>()

const isOpen = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const clusterStore = useClusterStore()
const toast = useToast()

const form = reactive<CreateRuleForm>({
  name: '',
  description: '',
  ruleType: 'process',
  priority: 'HIGH',
  enabled: true,
  condition: {},
  tags: []
})

const tagInput = ref('')

const priorities = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const ruleTypes = [
  { label: 'Process Execution', value: 'process' },
  { label: 'Network Activity', value: 'network' },
  { label: 'File Operations', value: 'file' },
  { label: 'File Access', value: 'file_access' }
]

const validate = (state: CreateRuleForm): FormError[] => {\n  const errors: FormError[] = []\n  if (!state.name?.trim()) errors.push({ path: 'name', message: 'Rule name is required' })\n  if (!state.description?.trim()) errors.push({ path: 'description', message: 'Description is required' })\n  if (!state.ruleType) errors.push({ path: 'ruleType', message: 'Rule type is required' })\n  if (!state.priority) errors.push({ path: 'priority', message: 'Priority is required' })\n  return errors\n}\n\nconst onSubmit = async (event: FormSubmitEvent<CreateRuleForm>) => {\n  try {\n    emit('create', {\n      ...form,\n      tags: form.tags.length > 0 ? form.tags : []\n    })\n    resetForm()\n    isOpen.value = false\n  } catch (error) {\n    console.error('Error submitting form:', error)\n  }\n}\n\nconst addTag = () => {\n  if (tagInput.value.trim() && !form.tags.includes(tagInput.value)) {\n    form.tags.push(tagInput.value.trim())\n    tagInput.value = ''\n  }\n}\n\nconst removeTag = (index: number) => {\n  form.tags.splice(index, 1)\n}\n\nconst resetForm = () => {\n  form.name = ''\n  form.description = ''\n  form.ruleType = 'process'\n  form.priority = 'HIGH'\n  form.enabled = true\n  form.condition = {}\n  form.tags = []\n  tagInput.value = ''\n}\n\nconst priorityItems = computed(() => [[...priorities.map(p => ({\n  label: p,\n  icon: form.priority === p ? 'i-lucide-check' : undefined,\n  onSelect: () => { form.priority = p as any }\n}))]])\n\nconst typeItems = computed(() => [[...ruleTypes.map(t => ({\n  label: t.label,\n  icon: form.ruleType === t.value ? 'i-lucide-check' : undefined,\n  onSelect: () => { form.ruleType = t.value }\n}))]])\n\nwatch(() => isOpen.value, (newVal) => {\n  if (!newVal) resetForm()\n})\n</script>\n\n<template>\n  <UModal\n    v-model:open=\"isOpen\"\n    title=\"Create Security Rule\"\n    description=\"Define a new security rule to detect threats\"\n    size=\"2xl\"\n  >\n    <template #body>\n      <UForm :state=\"form\" :validate=\"validate\" class=\"space-y-4\" @submit=\"onSubmit\">\n        <!-- Rule Name -->\n        <UFormField label=\"Rule Name\" name=\"name\" required>\n          <UInput\n            v-model=\"form.name\"\n            placeholder=\"e.g., Detect Reverse Shell\"\n            icon=\"i-lucide-file-text\"\n            size=\"md\"\n          />\n        </UFormField>\n\n        <!-- Description -->\n        <UFormField label=\"Description\" name=\"description\" required>\n          <UTextarea\n            v-model=\"form.description\"\n            placeholder=\"Describe what this rule detects...\"\n            :rows=\"2\"\n          />\n        </UFormField>\n\n        <!-- Rule Type & Priority Row -->\n        <div class=\"grid grid-cols-2 gap-4\">\n          <!-- Rule Type -->\n          <UFormField label=\"Rule Type\" name=\"ruleType\" required>\n            <UDropdownMenu\n              :items=\"typeItems\"\n              :popper=\"{ placement: 'bottom-start' }\"\n              :ui=\"{ content: 'w-(--reka-dropdown-menu-trigger-width)' }\"\n            >\n              <UButton\n                :label=\"ruleTypes.find(t => t.value === form.ruleType)?.label || 'Select type'\"\n                trailing-icon=\"i-lucide-chevron-down\"\n                color=\"neutral\"\n                variant=\"outline\"\n                block\n              />\n            </UDropdownMenu>\n          </UFormField>\n\n          <!-- Priority -->\n          <UFormField label=\"Priority\" name=\"priority\" required>\n            <UDropdownMenu\n              :items=\"priorityItems\"\n              :popper=\"{ placement: 'bottom-start' }\"\n              :ui=\"{ content: 'w-(--reka-dropdown-menu-trigger-width)' }\"\n            >\n              <UButton\n                :label=\"form.priority\"\n                trailing-icon=\"i-lucide-chevron-down\"\n                color=\"neutral\"\n                variant=\"outline\"\n                block\n              />\n            </UDropdownMenu>\n          </UFormField>\n        </div>\n\n        <!-- Tags -->\n        <UFormField label=\"Tags\" name=\"tags\">\n          <div class=\"space-y-2\">\n            <div class=\"flex gap-2\">\n              <UInput\n                v-model=\"tagInput\"\n                placeholder=\"Add a tag\"\n                icon=\"i-lucide-tag\"\n                @keydown.enter=\"addTag\"\n              />\n              <UButton\n                icon=\"i-lucide-plus\"\n                color=\"neutral\"\n                variant=\"outline\"\n                @click=\"addTag\"\n              />\n            </div>\n            <div v-if=\"form.tags.length > 0\" class=\"flex flex-wrap gap-2\">\n              <UBadge\n                v-for=\"(tag, idx) in form.tags\"\n                :key=\"idx\"\n                variant=\"solid\"\n                class=\"cursor-pointer\"\n                @click=\"removeTag(idx)\"\n              >\n                {{ tag }}\n                <UIcon name=\"i-lucide-x\" class=\"w-3 h-3 ml-1\" />\n              </UBadge>\n            </div>\n          </div>\n        </UFormField>\n\n        <!-- Enable Toggle -->\n        <div class=\"flex items-center gap-3 p-3 rounded-lg bg-gray-50 dark:bg-gray-900/30\">\n          <UToggle v-model=\"form.enabled\" />\n          <div>\n            <p class=\"text-sm font-medium\">Enable Rule Immediately</p>\n            <p class=\"text-xs text-gray-500 dark:text-gray-400\">Rule will start monitoring right away</p>\n          </div>\n        </div>\n\n        <!-- Help Text -->\n        <div class=\"bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3\">\n          <p class=\"text-xs text-blue-900 dark:text-blue-100\">\n            <strong>💡 Tip:</strong> Rules are evaluated against runtime security events. The agent will trigger alerts when matching conditions are detected.\n          </p>\n        </div>\n      </UForm>\n    </template>\n\n    <template #footer>\n      <div class=\"flex gap-2 justify-end\">\n        <UButton\n          label=\"Cancel\"\n          color=\"neutral\"\n          variant=\"ghost\"\n          @click=\"isOpen = false\"\n        />\n        <UButton\n          label=\"Create Rule\"\n          icon=\"i-lucide-plus\"\n          @click=\"onSubmit\"\n        />\n      </div>\n    </template>\n  </UModal>\n</template>
