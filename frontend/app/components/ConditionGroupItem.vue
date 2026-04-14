<template>
  <div :style="{ marginLeft: depth > 0 ? '16px' : '0' }" class="space-y-2">
    <!-- Group Header -->
    <div class="flex items-center gap-2 p-2.5 bg-blue-50 dark:bg-blue-900/20 rounded border border-blue-200 dark:border-blue-800">
      <button 
        @click="toggleLogic" 
        class="px-2 py-1 text-xs font-medium rounded bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-700 hover:bg-gray-100 dark:hover:bg-gray-700"
      >
        {{ localGroup.logic.toUpperCase() }}
      </button>
      <span class="text-xs text-gray-600 dark:text-gray-300">of:</span>
      <div class="flex-1"></div>
      <button @click="addClause" class="p-1 text-xs rounded hover:bg-white/50" title="Add Clause">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
        </svg>
      </button>
      <button @click="addGroup" class="p-1 text-xs rounded hover:bg-white/50" title="Add Group">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
        </svg>
      </button>
    </div>

    <!-- Clauses -->
    <div class="space-y-1">
      <template v-for="(clause, idx) in localGroup.clauses" :key="clause.id">
        <!-- Nested Group -->
        <ConditionGroupItem
          v-if="!isClause(clause)"
          :group="clause"
          :common-fields="commonFields"
          :operators="operators"
          :event-types="eventTypes"
          :pod-names="podNames"
          :namespaces="namespaces"
          :depth="(depth || 0) + 1"
          @update:group="updateNestedGroup(idx, $event)"
        />

        <!-- Clause Row -->
        <div v-else class="flex items-center gap-1.5 p-2 bg-white dark:bg-gray-800 rounded border border-gray-200 dark:border-gray-700 text-xs">
          <select 
            v-model="clause.field"
            @change="updateParent"
            class="px-2 py-1 rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-xs flex-1"
          >
            <option value="">Select Field</option>
            <option v-for="f in commonFields" :key="f.value" :value="f.value">{{ f.label }}</option>
          </select>

          <select 
            v-model="clause.operator"
            @change="updateParent"
            class="px-2 py-1 rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-xs w-24"
          >
            <option v-for="op in operators" :key="op.value" :value="op.value">{{ op.label }}</option>
          </select>

          <input 
            v-model="clause.value"
            @input="updateParent"
            :placeholder="['in', 'not_in'].includes(clause.operator) ? 'csv' : 'value'"
            :list="clause.field === 'event.type' ? 'event-types' : clause.field === 'k8s.pod.name' ? 'pod-names' : clause.field === 'k8s.ns.name' ? 'namespaces' : undefined"
            class="px-2 py-1 rounded border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-xs flex-1"
          />

          <!-- Event Type Help Popover -->
          <UPopover v-if="clause.field === 'event.type' && eventHelp[clause.value]" mode="hover">
            <UButton
              icon="i-lucide-info"
              size="xs"
              color="blue"
              variant="ghost"
              class="px-1"
            />
            <template #content>
              <div class="p-4 w-80 space-y-3">
                <div class="flex items-center gap-2 text-blue-600 dark:text-blue-400">
                  <UIcon name="i-lucide-lightbulb" class="w-5 h-5" />
                  <span class="font-bold text-sm uppercase">{{ clause.value }} Olayı</span>
                </div>
                <p class="text-xs text-gray-600 dark:text-gray-300 leading-relaxed italic">
                  {{ eventHelp[clause.value].desc }}
                </p>
                <div class="space-y-1.5">
                  <span class="text-[10px] uppercase font-bold text-gray-400">YAML Örneği</span>
                  <div class="bg-gray-900 rounded p-2 text-[10px] font-mono text-blue-300 overflow-x-auto whitespace-pre">
                    {{ eventHelp[clause.value].yaml }}
                  </div>
                </div>
              </div>
            </template>
          </UPopover>

          <button @click="removeClause(idx)" class="p-1 text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
          </button>
        </div>
      </template>
    </div>

    <!-- Datalists for autocomplete suggestions -->
    <datalist id="event-types">
      <option v-for="t in eventTypes" :key="t" :value="t" />
    </datalist>
    <datalist id="pod-names">
      <option v-for="p in podNames" :key="p" :value="p" />
    </datalist>
    <datalist id="namespaces">
      <option v-for="n in namespaces" :key="n" :value="n" />
    </datalist>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

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

const props = defineProps<{
  group: ConditionGroup
  commonFields: any[]
  operators: any[]
  depth?: number
  eventTypes?: string[]
  podNames?: string[]
  namespaces?: string[]
}>()

const emit = defineEmits<{
  'update:group': [value: ConditionGroup]
}>()

const eventHelp: Record<string, { desc: string, yaml: string }> = {
  execve: {
    desc: 'Yeni bir program çalıştırma (process execution).',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: execve\n    - field: proc.name\n      ==: curl'
  },
  open: {
    desc: 'Dosya açma işlemi (geleneksel syscall).',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: open\n    - field: file.path\n      startswith: /etc/'
  },
  openat: {
    desc: 'Bir dizine göre göreceli dosya açma (modern syscall).',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: openat\n    - field: file.path\n      ==: /etc/shadow'
  },
  connect: {
    desc: 'Dışarıya yönelik ağ bağlantısı denemesi.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: connect\n    - field: net.dport\n      ==: 443'
  },
  bind: {
    desc: 'Ağ portu dinleme veya bağlama işlemi.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: bind\n    - field: net.dport\n      ==: 8080'
  },
  accept: {
    desc: 'Gelen ağ bağlantısı isteğini kabul etme.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: accept\n    - field: net.sip\n      ==: 10.0.0.1'
  },
  unlink: {
    desc: 'Dosya silme işlemi.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: unlink\n    - field: file.path\n      contains: .log'
  },
  unlinkat: {
    desc: 'Dizine göre göreceli dosya silme.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: unlinkat\n    - field: file.path\n      startswith: /tmp/'
  },
  mkdir: {
    desc: 'Dizin oluşturma işlemi.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: mkdir\n    - field: file.path\n      startswith: /root/'
  },
  rmdir: {
    desc: 'Dizin silme işlemi.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: rmdir\n    - field: file.path\n      ==: /var/www'
  },
  xattr: {
    desc: 'Dosya genişletilmiş nitelik (extended attribute) değiştirme.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: xattr\n    - field: file.path\n      ==: /usr/bin'
  },
  link: {
    desc: 'Hard link oluşturma işlemi.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: link\n    - field: file.path\n      contains: /tmp/'
  },
  rename: {
    desc: 'Dosya adını değiştirme veya taşıma işlemi.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: rename\n    - field: file.path\n      startswith: /home/'
  },
  clone: {
    desc: 'Yeni thread veya process klonlama işlemi.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: clone\n    - field: proc.name\n      ==: bash'
  },
  fork: {
    desc: 'Process çatallama (forking) işlemi.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: fork\n    - field: proc.name\n      ==: python3'
  },
  ptrace: {
    desc: 'Process hata ayıklama veya izleme (güvenlik açıklarında sık kullanılır).',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: ptrace\n    - field: user.uid\n      ==: 0'
  },
  mount: {
    desc: 'Dosya sistemi bağlama (mount) veya ayırma (umount) işlemi.',
    yaml: 'condition:\n  all:\n    - field: event.type\n      ==: mount\n    - field: file.path\n      startswith: /mnt/'
  }
}

const localGroup = ref<ConditionGroup>(JSON.parse(JSON.stringify(props.group || { id: 'root', logic: 'all', clauses: [] })))

watch(() => props.group, (newVal) => {
  if (JSON.stringify(newVal) !== JSON.stringify(localGroup.value)) {
    localGroup.value = JSON.parse(JSON.stringify(newVal))
  }
}, { deep: true })

const updateParent = () => {
  emit('update:group', JSON.parse(JSON.stringify(localGroup.value)))
}

const isClause = (item: any) => !item.logic

const addClause = () => {
  localGroup.value.clauses.push({
    id: `c${Date.now()}`,
    field: '',
    operator: 'in',
    value: ''
  })
  updateParent()
}

const addGroup = () => {
  localGroup.value.clauses.push({
    id: `g${Date.now()}`,
    logic: 'any',
    clauses: [{ id: `c${Date.now()}`, field: '', operator: 'in', value: '' }]
  })
  updateParent()
}

const toggleLogic = () => {
  localGroup.value.logic = localGroup.value.logic === 'any' ? 'all' : 'any'
  updateParent()
}

const removeClause = (idx: number) => {
  localGroup.value.clauses.splice(idx, 1)
  updateParent()
}

const updateNestedGroup = (idx: number, nestedGroup: ConditionGroup) => {
  localGroup.value.clauses[idx] = nestedGroup
  updateParent()
}
</script>

<style scoped>
button { transition: all 0.2s ease; }
button:hover { transform: scale(1.1); }
</style>
