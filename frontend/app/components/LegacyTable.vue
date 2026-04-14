<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import * as XLSX from 'xlsx'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'

defineOptions({ inheritAttrs: false })

/* ---------------- PROPS ---------------- */
type LegacyColumn = {
  key?: string
  id?: string
  label?: string
  sortable?: boolean
  filterable?: boolean
}

const props = withDefaults(defineProps<{
  rows: any[]
  columns: LegacyColumn[]
  loading?: boolean
}>(), {
  rows: () => [],
  columns: () => [],
  loading: false
})

const attrs = useAttrs()
const slots = useSlots()

/* ---------------- STATE ---------------- */
const q = ref('')
const page = ref(1)
const pageCount = ref(10)

const sortKey = ref<string | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
const filters = reactive<Record<string, string>>({})

watch(q, () => (page.value = 1))

/* ---------------- DATA PIPELINE ---------------- */
const processedRows = computed(() => {
  let data = [...props.rows]

  // Global search
  if (q.value) {
    const s = q.value.toLowerCase()
    data = data.filter(row =>
      Object.values(row).some(v =>
        String(v).toLowerCase().includes(s)
      )
    )
  }

  // Column filters
  for (const key in filters) {
    if (!filters[key]) continue
    data = data.filter(row =>
      String(row[key] ?? '')
        .toLowerCase()
        .includes(filters[key].toLowerCase())
    )
  }

  // Sorting
  if (sortKey.value) {
    data.sort((a, b) => {
      const A = a[sortKey.value!]
      const B = b[sortKey.value!]
      if (A === B) return 0
      return sortDir.value === 'asc'
        ? A > B ? 1 : -1
        : A < B ? 1 : -1
    })
  }

  return data
})

const paginatedRows = computed(() => {
  const start = (page.value - 1) * pageCount.value
  return processedRows.value.slice(start, start + pageCount.value)
})

/* ---------------- COLUMNS ---------------- */
const normalizedColumns = computed<TableColumn<any>[]>(() =>
  props.columns
    .filter(col => col != null)
    .map(col => {
      const key = String(col.key ?? col.id ?? '')
      return {
        id: key,
        accessorKey: key,
        header: col.label ?? key
      }
    })
)

/* ---------------- SORT ---------------- */
const toggleSort = (key: string) => {
  if (sortKey.value === key) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortDir.value = 'asc'
  }
}

/* ---------------- EXPORT ---------------- */
const exportExcel = () => {
  const ws = XLSX.utils.json_to_sheet(processedRows.value)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Data')
  XLSX.writeFile(wb, 'table.xlsx')
}

const exportPDF = () => {
  const doc = new jsPDF()
  const validColumns = props.columns.filter(c => c != null)
  autoTable(doc, {
    head: [validColumns.map(c => c.label ?? c.key)],
    body: processedRows.value.map(row =>
      validColumns.map(c => row[c.key ?? c.id ?? ''])
    )
  })
  doc.save('table.pdf')
}

/* ---------------- LEGACY SLOT SUPPORT ---------------- */
const legacySlotColumnIds = computed(() =>
  props.columns
    .map(c => String(c.key ?? c.id ?? ''))
    .filter(id => id && slots[`${id}-data`])
)

const headerSlotNames = computed(() =>
  props.columns
    .filter(col => col != null)
    .map(col => `${String(col.key ?? col.id)}-header`)
)

const cellSlotNames = computed(() =>
  legacySlotColumnIds.value.map(id => `${id}-cell`)
)

const columnsWithSlotNames = computed(() =>
  props.columns
    .filter(col => col != null)
    .map((col, index) => ({
      ...col,
      slotName: headerSlotNames.value[index]
    }))
)

const legacySlotsWithNames = computed(() =>
  legacySlotColumnIds.value.map((id, index) => ({
    id,
    slotName: cellSlotNames.value[index]
  }))
)

/* ---------------- PAGE SIZE OPTIONS ---------------- */
const pageSizeOptions = [
  { label: '10 rows', value: 10 },
  { label: '25 rows', value: 25 },
  { label: '50 rows', value: 50 },
  { label: '100 rows', value: 100 }
]
</script>

<template>
  <div class="flex flex-col gap-4">
    <!-- TOOLBAR -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 bg-neutral-50 dark:bg-neutral-900 rounded-xl border border-neutral-200 dark:border-neutral-800">
      <!-- Search -->
      <div class="flex items-center gap-3 flex-1">
        <UInput
          v-model="q"
          icon="i-lucide-search"
          placeholder="Search..."
          size="sm"
          class="w-full sm:max-w-xs"
          :ui="{
            base: 'bg-white dark:bg-neutral-800 border-neutral-200 dark:border-neutral-700'
          }"
        />
        <span class="text-xs text-neutral-400 hidden sm:inline">
          {{ processedRows.length }} results
        </span>
      </div>

      <!-- Actions -->
      <div class="flex items-center gap-2">
        <UButton 
          size="sm" 
          icon="i-lucide-file-spreadsheet" 
          color="neutral"
          variant="outline"
          @click="exportExcel"
        >
          <span class="hidden sm:inline">Excel</span>
        </UButton>
        <UButton 
          size="sm" 
          icon="i-lucide-file-text" 
          color="neutral"
          variant="outline"
          @click="exportPDF"
        >
          <span class="hidden sm:inline">PDF</span>
        </UButton>
      </div>
    </div>

    <!-- COLUMN FILTERS -->
    <div 
      v-if="columns.filter(col => col != null && col.filterable).length > 0" 
      class="grid grid-cols-2 md:grid-cols-4 gap-2 px-1"
    >
      <UInput
        v-for="col in columns.filter(col => col != null && col.filterable)"
        :key="col.key ?? col.id"
        v-model="filters[String(col.key ?? col.id)]"
        size="xs"
        :placeholder="`Filter ${col.label ?? col.key}`"
        :ui="{
          base: 'bg-white dark:bg-neutral-800 border-neutral-200 dark:border-neutral-700'
        }"
      />
    </div>

    <!-- TABLE WRAPPER -->
    <div class="bg-white dark:bg-neutral-900 rounded-xl border border-neutral-200 dark:border-neutral-800 overflow-hidden shadow-sm">
      <UTable
        v-bind="attrs"
        :data="paginatedRows"
        :columns="normalizedColumns"
        :loading="loading"
        :ui="{
          base: 'min-w-full',
          thead: 'bg-neutral-50 dark:bg-neutral-800/50',
          th: 'px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-neutral-500 dark:text-neutral-400',
          td: 'px-4 py-3 text-sm text-neutral-700 dark:text-neutral-300',
          tbody: 'divide-y divide-neutral-100 dark:divide-neutral-800',
          tr: 'hover:bg-neutral-50 dark:hover:bg-neutral-800/30 transition-colors'
        }"
      >
        <!-- SORTABLE HEADERS -->
        <template
          v-for="colWithSlot in columnsWithSlotNames"
          :key="`header-${colWithSlot.key ?? colWithSlot.id}`"
          v-slot:[colWithSlot.slotName]=""
        >
          <button
            class="flex items-center gap-1.5 font-semibold text-neutral-600 dark:text-neutral-300 hover:text-primary-600 dark:hover:text-primary-400 transition-colors"
            @click="toggleSort(String(colWithSlot.key ?? colWithSlot.id))"
          >
            {{ colWithSlot.label ?? colWithSlot.key }}
            <UIcon
              v-if="sortKey === String(colWithSlot.key ?? colWithSlot.id)"
              :name="sortDir === 'asc' ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
              class="w-3.5 h-3.5 text-primary-500"
            />
            <UIcon
              v-else
              name="i-lucide-chevrons-up-down"
              class="w-3.5 h-3.5 opacity-30"
            />
          </button>
        </template>

        <!-- LEGACY CELL SLOTS -->
        <template
          v-for="slotInfo in legacySlotsWithNames"
          :key="`cell-${slotInfo.id}`"
          v-slot:[slotInfo.slotName]="ctx"
        >
          <slot
            :name="`${slotInfo.id}-data`"
            :row="ctx.row.original"
            :value="ctx.getValue()"
          >
            {{ ctx.getValue() ?? '-' }}
          </slot>
        </template>

        <template #empty>
          <div class="py-12 text-center">
            <UIcon name="i-lucide-inbox" class="w-12 h-12 mx-auto text-neutral-300 dark:text-neutral-600 mb-3" />
            <p class="text-sm font-medium text-neutral-500 dark:text-neutral-400">No data found</p>
            <p class="text-xs text-neutral-400 dark:text-neutral-500 mt-1">Try adjusting your search or filters</p>
          </div>
        </template>
      </UTable>
    </div>

    <!-- PAGINATION -->
    <div class="flex flex-col sm:flex-row items-center justify-between gap-3 px-1">
      <div class="flex items-center gap-2">
        <span class="text-xs text-neutral-500 dark:text-neutral-400">Show</span>
        <USelectMenu
          v-model="pageCount"
          :items="pageSizeOptions"
          value-key="value"
          size="xs"
          class="w-24"
        />
      </div>

      <div class="flex items-center gap-4">
        <span class="text-xs text-neutral-500 dark:text-neutral-400">
          Page {{ page }} of {{ Math.ceil(processedRows.length / pageCount) || 1 }}
        </span>
        <UPagination
          v-model:page="page"
          :total="processedRows.length"
          :page-count="pageCount"
          size="sm"
          :ui="{
            base: 'gap-1',
            rounded: 'rounded-lg'
          }"
        />
      </div>
    </div>
  </div>
</template>
