<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import type { FormSubmitEvent } from '#ui/types'

const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const toast = useToast()

const loading = ref(false)
const saving = ref(false)

const state = reactive({
  // Process
  enableExecve: false,
  enableClone: false,
  enableFork: false,
  enablePtrace: false,
  enableMount: false,
  // Network
  enableConnect: false,
  enableBind: false,
  enableAccept: false,
  // File: open/write
  enableOpen: false,
  enableOpenat: false,
  enableWrite: false,
  // File: delete/link/rename
  enableUnlink: false,
  enableUnlinkat: false,
  enableLink: false,
  enableRename: false,
  // File: directory/meta
  enableMkdir: false,
  enableRmdir: false,
  enableXattr: false,
})

const loadConfig = async () => {
  if (!clusterStore.selectedCluster?.uid) return
  loading.value = true
  try {
    const response = await $api.get('/security/monitoring-config', {
      params: { clusterUid: clusterStore.selectedCluster.uid }
    })
    const d = response.data || {}
    // Map both snake_case (from agent) and camelCase (from UI DTO)
    state.enableExecve = d.enable_execve ?? d.enableExecve ?? false
    state.enableClone = d.enable_clone ?? d.enableClone ?? false
    state.enableFork = d.enable_fork ?? d.enableFork ?? false
    state.enablePtrace = d.enable_ptrace ?? d.enablePtrace ?? false
    state.enableMount = d.enable_mount ?? d.enableMount ?? false
    state.enableConnect = d.enable_connect ?? d.enableConnect ?? false
    state.enableBind = d.enable_bind ?? d.enableBind ?? false
    state.enableAccept = d.enable_accept ?? d.enableAccept ?? false
    state.enableOpen = d.enable_open ?? d.enableOpen ?? false
    state.enableOpenat = d.enable_openat ?? d.enableOpenat ?? false
    state.enableWrite = d.enable_write ?? d.enableWrite ?? false
    state.enableUnlink = d.enable_unlink ?? d.enableUnlink ?? false
    state.enableUnlinkat = d.enable_unlinkat ?? d.enableUnlinkat ?? false
    state.enableLink = d.enable_link ?? d.enableLink ?? false
    state.enableRename = d.enable_rename ?? d.enableRename ?? false
    state.enableMkdir = d.enable_mkdir ?? d.enableMkdir ?? false
    state.enableRmdir = d.enable_rmdir ?? d.enableRmdir ?? false
    state.enableXattr = d.enable_xattr ?? d.enableXattr ?? false
  } catch (error: any) {
    if (error.response?.status !== 404) {
      toast.add({ title: 'Error loading config', description: error.response?.data?.message || error.message, color: 'red' })
    }
  } finally {
    loading.value = false
  }
}

const onSubmit = async (_: FormSubmitEvent<any>) => {
  if (!clusterStore.selectedCluster?.uid) {
    toast.add({ title: 'Error', description: 'No cluster selected', color: 'red' })
    return
  }
  saving.value = true
  try {
    // Backend MonitoringConfig entity uses camelCase fields
    const payload = {
      enableExecve: state.enableExecve,
      enableClone: state.enableClone,
      enableFork: state.enableFork,
      enablePtrace: state.enablePtrace,
      enableMount: state.enableMount,
      enableConnect: state.enableConnect,
      enableBind: state.enableBind,
      enableAccept: state.enableAccept,
      enableOpen: state.enableOpen,
      enableOpenat: state.enableOpenat,
      enableWrite: state.enableWrite,
      enableUnlink: state.enableUnlink,
      enableUnlinkat: state.enableUnlinkat,
      enableLink: state.enableLink,
      enableRename: state.enableRename,
      enableMkdir: state.enableMkdir,
      enableRmdir: state.enableRmdir,
      enableXattr: state.enableXattr,
    }
    await $api.put('/security/monitoring-config', payload, {
      params: { clusterUid: clusterStore.selectedCluster.uid }
    })
    toast.add({ title: 'Success', description: 'Runtime Security configuration saved. Agent will pick up changes within 30s.', color: 'green' })
  } catch (error: any) {
    toast.add({ title: 'Failed to save', description: error.response?.data?.message || 'Unexpected error', color: 'red' })
  } finally {
    saving.value = false
  }
}

watch(() => clusterStore.selectedCluster?.uid, () => { loadConfig() })
onMounted(() => { loadConfig() })
</script>

<template>
  <div class="space-y-6">
    <UPageCard
      title="Runtime Security Agent"
      description="Configure eBPF kernel tracepoints. Changes take effect within 30 seconds (next agent poll cycle)."
    >
      <!-- No cluster selected -->
      <div v-if="!clusterStore.selectedCluster" class="p-4 bg-yellow-50 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-300 rounded-lg flex gap-3">
        <UIcon name="i-lucide-alert-triangle" class="w-5 h-5 flex-shrink-0 mt-0.5" />
        <p class="text-sm">Please select a cluster from the top navigation to manage its runtime security configuration.</p>
      </div>

      <UForm v-else :state="state" class="space-y-6" @submit="onSubmit">

        <!-- Process Tracepoints -->
        <div class="space-y-3">
          <div class="pb-1.5 border-b border-gray-200 dark:border-gray-800">
            <h3 class="text-sm font-semibold text-gray-900 dark:text-white flex items-center gap-1.5">
              <UIcon name="i-lucide-cpu" class="w-4 h-4" /> Process Events
            </h3>
            <p class="text-xs text-gray-400 mt-0.5">Monitor process lifecycle — spawn, clone, fork</p>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div v-for="tp in [
              { key: 'enableExecve', label: 'execve', desc: 'New process execution' },
              { key: 'enableClone', label: 'clone', desc: 'Thread/process cloning' },
              { key: 'enableFork', label: 'fork', desc: 'Process forking' },
              { key: 'enablePtrace', label: 'ptrace', desc: 'Process debugging/tracing' },
              { key: 'enableMount', label: 'mount', desc: 'FS mount/umount' },
            ]" :key="tp.key" class="flex items-center justify-between p-3 rounded-lg border border-gray-200 dark:border-gray-800 bg-gray-50 dark:bg-gray-900/50">
              <div>
                <p class="text-sm font-medium font-mono text-gray-900 dark:text-white">{{ tp.label }}</p>
                <p class="text-xs text-gray-400">{{ tp.desc }}</p>
              </div>
              <UCheckbox v-model="(state as any)[tp.key]" />
            </div>
          </div>
        </div>

        <!-- Network Tracepoints -->
        <div class="space-y-3">
          <div class="pb-1.5 border-b border-gray-200 dark:border-gray-800">
            <h3 class="text-sm font-semibold text-gray-900 dark:text-white flex items-center gap-1.5">
              <UIcon name="i-lucide-globe" class="w-4 h-4" /> Network Events
            </h3>
            <p class="text-xs text-gray-400 mt-0.5">Track outbound connections and port bindings</p>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div v-for="tp in [
              { key: 'enableConnect', label: 'connect', desc: 'Outbound TCP/UDP connections' },
              { key: 'enableAccept', label: 'accept', desc: 'Inbound connection acceptance' },
              { key: 'enableBind', label: 'bind', desc: 'Socket port binding / listening' },
            ]" :key="tp.key" class="flex items-center justify-between p-3 rounded-lg border border-gray-200 dark:border-gray-800 bg-gray-50 dark:bg-gray-900/50">
              <div>
                <p class="text-sm font-medium font-mono text-gray-900 dark:text-white">{{ tp.label }}</p>
                <p class="text-xs text-gray-400">{{ tp.desc }}</p>
              </div>
              <UCheckbox v-model="(state as any)[tp.key]" />
            </div>
          </div>
        </div>

        <!-- File Tracepoints -->
        <div class="space-y-3">
          <div class="pb-1.5 border-b border-gray-200 dark:border-gray-800">
            <h3 class="text-sm font-semibold text-gray-900 dark:text-white flex items-center gap-1.5">
              <UIcon name="i-lucide-folder-open" class="w-4 h-4" /> File System Events
              <UBadge label="Higher Overhead" color="warning" variant="subtle" size="xs" class="ml-1" />
            </h3>
            <p class="text-xs text-gray-400 mt-0.5">Monitor file open, write, delete, and metadata operations</p>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
            <div v-for="tp in [
              { key: 'enableOpen', label: 'open', desc: 'File open (legacy syscall)' },
              { key: 'enableOpenat', label: 'openat', desc: 'File open relative to fd' },
              { key: 'enableWrite', label: 'write', desc: 'File writes (very high volume)' },
              { key: 'enableUnlink', label: 'unlink', desc: 'File deletion' },
              { key: 'enableUnlinkat', label: 'unlinkat', desc: 'File deletion (fd-relative)' },
              { key: 'enableLink', label: 'link', desc: 'Hard link creation' },
              { key: 'enableRename', label: 'rename', desc: 'File rename / move' },
              { key: 'enableMkdir', label: 'mkdir', desc: 'Directory creation' },
              { key: 'enableRmdir', label: 'rmdir', desc: 'Directory removal' },
              { key: 'enableXattr', label: 'xattr', desc: 'Extended attribute modification' },
            ]" :key="tp.key" class="flex items-center justify-between p-3 rounded-lg border border-gray-200 dark:border-gray-800 bg-gray-50 dark:bg-gray-900/50">
              <div>
                <p class="text-sm font-medium font-mono text-gray-900 dark:text-white">{{ tp.label }}</p>
                <p class="text-xs text-gray-400">{{ tp.desc }}</p>
              </div>
              <UCheckbox v-model="(state as any)[tp.key]" />
            </div>
          </div>
        </div>

        <!-- Actions -->
        <div class="flex items-center gap-3 pt-4 border-t border-gray-200 dark:border-gray-800">
          <span class="text-sm text-gray-400 flex-1">
            <UIcon name="i-lucide-server" class="w-4 h-4 inline-block mr-1 align-text-bottom" />
            <strong class="text-gray-700 dark:text-gray-200">{{ clusterStore.selectedCluster.name || clusterStore.selectedCluster.uid }}</strong>
          </span>
          <UButton type="button" label="Discard" color="neutral" variant="ghost" :disabled="saving || loading" @click="loadConfig" />
          <UButton type="submit" label="Save Configuration" icon="i-lucide-save" :loading="saving" :disabled="loading" />
        </div>
      </UForm>
    </UPageCard>
  </div>
</template>
