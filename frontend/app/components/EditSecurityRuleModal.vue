<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import type { FormError } from '#ui/types'

interface Clause {
  id: string
  field: string
  operator: string
  value: any
}

interface ConditionGroup {
  id: string
  logic: 'any' | 'all'
  clauses: (Clause | ConditionGroup)[]
}

interface SecurityRule {
  id: number
  name: string
  description: string
  priority: string
  clusterName?: string
  cluster?: string
  enabled: boolean
  condition: any
  tags: string[]
  output?: string
}

const props = defineProps<{
  modelValue: boolean
  rule: SecurityRule
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'update': [data: any]
}>()

const isOpen = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const clusterStore = useClusterStore()

const form = ref<Partial<SecurityRule>>({
  name: '',
  description: '',
  priority: 'HIGH',
  clusterName: 'default-cluster',
  enabled: true,
  output: '',
  tags: []
})

const conditions = ref<ConditionGroup>({
  id: 'root',
  logic: 'all',
  clauses: [{ id: '1', field: '', operator: 'in', value: '' }]
})

const tagInput = ref('')
const activeTab = ref<'form' | 'preview'>('form')

const commonFields = [
  { label: 'Event Type', value: 'event.type' },
  { label: 'Process Name', value: 'proc.name' },
  { label: 'Command Line', value: 'proc.cmdline' },
  { label: 'File Path', value: 'file.path' },
  { label: 'Source IP', value: 'net.sip' },
  { label: 'Dest IP', value: 'net.dip' },
  { label: 'Dest Port', value: 'net.dport' },
  { label: 'Container ID', value: 'container.id' },
  { label: 'Pod Name', value: 'k8s.pod.name' },
  { label: 'Namespace', value: 'k8s.ns.name' },
  { label: 'User ID', value: 'user.uid' },
  { label: 'User Name', value: 'user.name' }
]

const operators = [
  { label: 'equals', value: '==', icon: 'i-lucide-equal' },
  { label: 'not equals', value: '!=', icon: 'i-lucide-not-equal' },
  { label: 'greater than', value: '>', icon: 'i-lucide-chevron-right' },
  { label: 'less than', value: '<', icon: 'i-lucide-chevron-left' },
  { label: 'greater than or equal', value: '>=', icon: 'i-lucide-arrow-right-to-line' },
  { label: 'less than or equal', value: '<=', icon: 'i-lucide-arrow-left-to-line' },
  { label: 'contains text', value: 'contains', icon: 'i-lucide-search' },
  { label: 'starts with', value: 'startswith', icon: 'i-lucide-arrow-down-from-line' },
  { label: 'ends with', value: 'endswith', icon: 'i-lucide-arrow-up-to-line' },
  { label: 'matches regex', value: 'regex', icon: 'i-lucide-regex' },
  { label: 'in (any of)', value: 'in', icon: 'i-lucide-list' },
  { label: 'not_in (none of)', value: 'not_in', icon: 'i-lucide-ban' },
  { label: 'exists', value: 'exists', icon: 'i-lucide-check-circle' }
]

const priorityOptions = [
  { label: 'CRITICAL', value: 'CRITICAL', icon: 'i-lucide-alert-triangle', color: 'red' },
  { label: 'HIGH', value: 'HIGH', icon: 'i-lucide-alert-circle', color: 'orange' },
  { label: 'MEDIUM', value: 'MEDIUM', icon: 'i-lucide-info', color: 'yellow' },
  { label: 'LOW', value: 'LOW', icon: 'i-lucide-circle', color: 'blue' }
]

const outputTemplates = [
  '%proc.name (%proc.cmdline)',
  '%file.path accessed by %proc.name in %k8s.pod.name',
  'Connection to %net.dip:%net.dport by %proc.name',
  '%proc.name executing from %file.path (ns: %k8s.ns.name)'
]

const getPriorityColor = (priority: string) => {
  const opt = priorityOptions.find(o => o.value === priority)
  return opt?.color || 'gray'
}

const eventTypes = ref<string[]>([
  'execve', 'open', 'openat', 'connect', 'bind', 'accept', 'unlink', 'unlinkat',
  'mkdir', 'rmdir', 'xattr', 'link', 'rename', 'clone', 'fork', 'ptrace', 'mount'
])

const k8s = useKubernetes()
const podNames = ref<string[]>([])
const namespaces = ref<string[]>([])

const { $api } = useNuxtApp()

const loadClusterData = async () => {
  if (!clusterStore.selectedCluster?.uid) return
  try {
    // T3: fetch ALL namespaces unfiltered via /namespaces/for-page
    const nsRes = await $api.get<string[]>(`/k8s/${clusterStore.selectedCluster.uid}/namespaces/for-page`)
    namespaces.value = (nsRes.data ?? []).sort()

    const podList = await k8s.fetchPods('all').catch(() => k8s.fetchPods(clusterStore.selectedNamespace || 'default'))
    podNames.value = podList.map((p: any) => p.name || p.metadata?.name).filter(Boolean)
  } catch (error) {
    console.error('Failed to load cluster data for edit rule builder', error)
  }
}

watch(() => clusterStore.selectedCluster?.uid, () => {
  loadClusterData()
}, { immediate: true })

const isClause = (item: any): item is Clause => !item.logic

const buildBackendCondition = (group: ConditionGroup): any => {
  return {
    [group.logic]: group.clauses.map(clause => {
      if (isClause(clause)) {
        if (['in', 'not_in'].includes(clause.operator)) {
          const arr = Array.isArray(clause.value) 
            ? clause.value 
            : typeof clause.value === 'string' 
              ? clause.value.split(',').map((s: string) => s.trim()).filter((s: string) => s)
              : [clause.value]
          return {
            field: clause.field,
            [clause.operator]: arr
          }
        }
        else if (clause.operator === 'exists') {
          return {
            field: clause.field,
            exists: clause.value === 'true' || clause.value === true
          }
        }
        else {
          return {
            field: clause.field,
            op: clause.operator,
            value: clause.value
          }
        }
      } else {
        return buildBackendCondition(clause as ConditionGroup)
      }
    })
  }
}

const buildYaml = (): string => {
  return `name: "${form.value.name}"
description: "${form.value.description}"
priority: "${form.value.priority}"
cluster: "${clusterStore.selectedCluster?.uid || form.value.clusterName}"
enabled: ${form.value.enabled}
tags: ${JSON.stringify(form.value.tags || [])}
output: "${form.value.output}"
condition:
${JSON.stringify(buildBackendCondition(conditions.value), null, 2)
  .split('\n')
  .map(l => '  ' + l)
  .join('\n')}`
}

const yamlPreview = computed(() => buildYaml())

const addTag = () => {
  if (tagInput.value.trim() && !form.value.tags?.includes(tagInput.value)) {
    form.value.tags = [...(form.value.tags || []), tagInput.value.trim()]
    tagInput.value = ''
  }
}

const copyYaml = () => {
  navigator.clipboard.writeText(yamlPreview.value).then(() => {
    alert('YAML copied to clipboard!')
  })
}

const validate = (): FormError[] => {
  const errors: FormError[] = []
  if (!form.value.name?.trim()) errors.push({ path: 'name', message: 'Name required' })
  if (!form.value.description?.trim()) errors.push({ path: 'description', message: 'Description required' })
  if (!form.value.output?.trim()) errors.push({ path: 'output', message: 'Output required' })
  return errors
}

const onSubmit = () => {
  if (validate().length > 0) return
  emit('update', { 
    ...form.value, 
    condition: buildBackendCondition(conditions.value) 
  })
  isOpen.value = false
}

const parseBackendCondition = (cond: any): ConditionGroup => {
  if (!cond || typeof cond !== 'object') {
    return cond?.id ? cond : { id: 'root', logic: 'all', clauses: [{ id: '1', field: '', operator: 'in', value: '' }] }
  }
  
  const logicKeys = Object.keys(cond).filter(k => k === 'all' || k === 'any')
  if (logicKeys.length === 0) {
    if (cond.id) return cond
    return { id: 'root', logic: 'all', clauses: [{ id: '1', field: '', operator: 'in', value: '' }] }
  }
  
  const logic = logicKeys[0] as 'all' | 'any'
  const items = cond[logic] || []
  
  const parseItems = (children: any[]): any[] => {
    return (children || []).map(item => {
      const subLogicKeys = Object.keys(item).filter(k => k === 'all' || k === 'any')
      if (subLogicKeys.length > 0) {
        const subLogic = subLogicKeys[0] as 'all' | 'any'
        return {
          id: `g${Date.now()}${Math.random()}`,
          logic: subLogic,
          clauses: parseItems(item[subLogic])
        }
      } else {
        let operator = '=='
        let value: any = ''
        
        if ('in' in item) { operator = 'in'; value = Array.isArray(item.in) ? item.in.join(', ') : item.in }
        else if ('not_in' in item) { operator = 'not_in'; value = Array.isArray(item.not_in) ? item.not_in.join(', ') : item.not_in }
        else if ('exists' in item) { operator = 'exists'; value = item.exists }
        else if ('op' in item) { operator = item.op; value = item.value }
        else {
          const knownOps = ['!=', '>', '<', '>=', '<=', 'contains', 'startswith', 'endswith', 'regex']
          for (const op of knownOps) {
            if (op in item) {
               operator = op; value = item[op]; break;
            }
          }
        }
        
        return {
          id: `c${Date.now()}${Math.random()}`,
          field: item.field || '',
          operator,
          value
        }
      }
    })
  }
  
  return {
    id: `root`,
    logic,
    clauses: parseItems(items).length > 0 ? parseItems(items) : [{ id: '1', field: '', operator: 'in', value: '' }]
  }
}

watch(() => isOpen.value, (newValue) => {
  if (newValue && props.rule) {
    form.value = {
      ...props.rule,
      tags: props.rule.tags || []
    }
    
    if (props.rule.condition) {
      if (typeof props.rule.condition === 'string') {
        try {
          conditions.value = parseBackendCondition(JSON.parse(props.rule.condition))
        } catch {
          conditions.value = { id: 'root', logic: 'all', clauses: [{ id: '1', field: '', operator: 'in', value: '' }] }
        }
      } else {
        conditions.value = parseBackendCondition(JSON.parse(JSON.stringify(props.rule.condition)))
      }
    } else {
      conditions.value = { id: 'root', logic: 'all', clauses: [{ id: '1', field: '', operator: 'in', value: '' }] }
    }
    
    activeTab.value = 'form'
  }
})
</script>

<template>
  <UModal 
    v-model:open="isOpen" 
    title="Edit Security Rule"
    size="xl"
    :ui="{ base: 'relative z-50', width: 'w-full max-w-5xl' }"
  >
    <template #body>
      <!-- Tabs -->
      <div class="flex gap-4 border-b mb-4">
        <button
          @click="activeTab = 'form'"
          :class="[
            'px-4 py-2 text-sm font-medium border-b-2 transition',
            activeTab === 'form' 
              ? 'border-blue-500 text-blue-600' 
              : 'border-transparent text-gray-600 hover:text-gray-900'
          ]"
        >
          Rule Configuration
        </button>
        <button
          @click="activeTab = 'preview'"
          :class="[
            'px-4 py-2 text-sm font-medium border-b-2 transition',
            activeTab === 'preview' 
              ? 'border-blue-500 text-blue-600' 
              : 'border-transparent text-gray-600 hover:text-gray-900'
          ]"
        >
          YAML Preview
        </button>
      </div>

      <!-- Form Tab -->
      <div v-show="activeTab === 'form'" class="space-y-6">
        <!-- Basic Info -->
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-semibold mb-2">Rule Name *</label>
            <UInput disabled v-model="form.name" placeholder="Enter rule name" size="md" />
          </div>
          <div>
            <label class="block text-sm font-semibold mb-2">Description *</label>
            <UTextarea v-model="form.description" placeholder="What does this rule detect?" :rows="2" />
          </div>
        </div>

        <!-- Configuration -->
        <div class="grid grid-cols-3 gap-4">
          <!-- Priority -->
          <div>
            <label class="block text-sm font-semibold mb-2">Priority *</label>
            <UDropdownMenu
              :items="[priorityOptions.map(opt => ({
                label: opt.label,
                icon: opt.icon,
                click: () => form.priority = opt.value
              }))]"
              :content="{ align: 'start' }"
            >
              <UButton
                :label="form.priority || 'Select Priority'"
                :icon="priorityOptions.find(o => o.value === form.priority)?.icon"
                :color="getPriorityColor(form.priority || 'HIGH')"
                trailing-icon="i-lucide-chevron-down"
                variant="outline"
                block
              />
            </UDropdownMenu>
          </div>

          <!-- Cluster Display -->
          <div>
            <label class="block text-sm font-semibold mb-2">Cluster</label>
            <div class="px-3 py-2 rounded border border-gray-300 dark:border-gray-600 bg-gray-100 dark:bg-gray-700 text-xs flex items-center gap-2">
              <UIcon name="i-lucide-server" class="w-4 h-4" />
              <span class="text-gray-700 dark:text-gray-300 font-medium">{{ clusterStore.selectedCluster?.uid || clusterStore.selectedCluster?.name || 'No Cluster Selected' }}</span>
            </div>
          </div>

          <!-- Enable -->
          <div class="flex items-end">
            <label class="flex items-center gap-2 mb-2 w-full">
              <UToggle v-model="form.enabled" />
              <span class="text-sm font-medium">{{ form.enabled ? 'Enabled' : 'Disabled' }}</span>
            </label>
          </div>
        </div>

        <!-- Output -->
        <div>
          <label class="block text-sm font-semibold mb-2">Output Message</label>
          <div class="flex gap-2">
            <UInput 
              v-model="form.output" 
              placeholder="%proc.name accessed %file.path" 
              class="flex-1"
            />
            <UDropdownMenu :items="[outputTemplates.map(t => ({ label: t, onSelect: () => form.output = t }))]">
              <UButton icon="i-lucide-sparkles" variant="outline" />
            </UDropdownMenu>
          </div>
        </div>

        <!-- Conditions Editor -->
        <div class="border-t pt-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-sm font-semibold">Detection Conditions</h3>
          </div>

          <!-- Conditions -->
          <div class="space-y-3 border p-4 rounded-lg bg-gray-50 dark:bg-gray-800/30 min-h-[200px]">
            <ConditionGroupItem 
              :group="conditions"
              @update:group="conditions = $event"
              :common-fields="commonFields"
              :operators="operators"
              :event-types="eventTypes"
              :pod-names="podNames"
              :namespaces="namespaces"
            />
          </div>
        </div>

        <!-- Tags -->
        <div class="border-t pt-6">
          <label class="block text-sm font-semibold mb-3">Tags</label>
          <div class="flex gap-2 mb-3">
            <UInput 
              v-model="tagInput" 
              placeholder="Add a tag" 
              @keydown.enter="addTag"
            />
            <UButton icon="i-lucide-plus" @click="addTag" />
          </div>
          <div class="flex flex-wrap gap-2">
            <UBadge 
              v-for="(tag, i) in form.tags || []" 
              :key="i"
              variant="solid"
              class="cursor-pointer"
              @click="form.tags?.splice(i, 1)"
            >
              {{ tag }} <UIcon name="i-lucide-x" class="w-3 h-3 ml-1" />
            </UBadge>
          </div>
        </div>
      </div>

      <!-- Preview Tab -->
      <div v-show="activeTab === 'preview'" class="space-y-4">
        <div class="bg-gray-900 dark:bg-gray-950 text-gray-100 p-4 rounded-lg overflow-auto max-h-96">
          <pre class="text-xs font-mono whitespace-pre-wrap break-words">{{ yamlPreview }}</pre>
        </div>
        <div class="flex gap-2">
          <UButton
            icon="i-lucide-copy"
            label="Copy YAML"
            @click="copyYaml"
            color="blue"
          />
        </div>
      </div>
    </template>

    <template #footer>
      <div class="flex gap-2 justify-end">
        <UButton label="Cancel" color="neutral" @click="isOpen = false" />
        <UButton 
          label="Update Rule" 
          :disabled="!form.description?.trim()"
          @click="onSubmit"
        />
      </div>
    </template>
  </UModal>
</template>
