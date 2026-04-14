import { ref, computed, watch } from 'vue'

export interface MonitoringConfig {
  id: number
  clusterUid: string
  enableExecve: boolean
  enableOpen: boolean
  enableOpenat: boolean
  enableConnect: boolean
  enableBind: boolean
  enableUnlink: boolean
  enableUnlinkat: boolean
  enableWrite: boolean
  enableLink: boolean
  enableRename: boolean
  enableMkdir: boolean
  enableRmdir: boolean
  enableXattr: boolean
  enableClone: boolean
  enableFork: boolean
  updatedBy: string
  updatedAt: string
}

export const useMonitoringConfig = () => {
  const authStore = useAuthStore()
  const toast = useToast()

  const config = ref<MonitoringConfig | null>(null)
  const loading = ref(false)

  const headers = () => ({
    Authorization: `Bearer ${authStore.token}`
  })

  /**
   * Fetch monitoring configuration for a cluster
   */
  const fetchMonitoringConfig = async (clusterUid: string) => {
    loading.value = true
    try {
      const response = await $fetch('/api/security/monitoring-config', {
        method: 'GET',
        query: { clusterUid },
        headers: headers()
      })

      config.value = response
      return response
    } catch (error: any) {
      console.error('Error fetching monitoring config:', error)
      toast.add({
        title: 'Error',
        description: 'Failed to fetch monitoring configuration',
        color: 'error'
      })
      throw error
    } finally {
      loading.value = false
    }
  }

  /**
   * Update entire monitoring configuration
   */
  const updateMonitoringConfig = async (clusterUid: string, newConfig: Partial<MonitoringConfig>) => {
    loading.value = true
    try {
      const response = await $fetch('/api/security/monitoring-config', {
        method: 'PUT',
        query: { clusterUid },
        body: newConfig,
        headers: headers()
      })

      config.value = response

      toast.add({
        title: 'Success',
        description: 'Monitoring configuration updated',
        color: 'green'
      })

      return response
    } catch (error: any) {
      console.error('Error updating monitoring config:', error)
      toast.add({
        title: 'Error',
        description: 'Failed to update monitoring configuration',
        color: 'error'
      })
      throw error
    } finally {
      loading.value = false
    }
  }

  /**
   * Enable specific tracepoint
   */
  const enableTracepoint = async (clusterUid: string, tracepointType: string) => {
    loading.value = true
    try {
      const response = await $fetch(
        `/api/security/monitoring-config/tracepoint/${tracepointType}/enable`,
        {
          method: 'PATCH',
          query: { clusterUid },
          headers: headers()
        }
      )

      config.value = response

      toast.add({
        title: 'Success',
        description: `${tracepointType} tracepoint enabled`,
        color: 'green'
      })

      return response
    } catch (error: any) {
      console.error('Error enabling tracepoint:', error)
      toast.add({
        title: 'Error',
        description: `Failed to enable ${tracepointType} tracepoint`,
        color: 'error'
      })
      throw error
    } finally {
      loading.value = false
    }
  }

  /**
   * Disable specific tracepoint
   */
  const disableTracepoint = async (clusterUid: string, tracepointType: string) => {
    loading.value = true
    try {
      const response = await $fetch(
        `/api/security/monitoring-config/tracepoint/${tracepointType}/disable`,
        {
          method: 'PATCH',
          query: { clusterUid },
          headers: headers()
        }
      )

      config.value = response

      toast.add({
        title: 'Success',
        description: `${tracepointType} tracepoint disabled`,
        color: 'green'
      })

      return response
    } catch (error: any) {
      console.error('Error disabling tracepoint:', error)
      toast.add({
        title: 'Error',
        description: `Failed to disable ${tracepointType} tracepoint`,
        color: 'error'
      })
      throw error
    } finally {
      loading.value = false
    }
  }

  /**
   * Get active tracepoints
   */
  const getActiveTracepoints = computed(() => {
    if (!config.value) return []

    const active = []
    if (config.value.enableExecve) active.push('execve')
    if (config.value.enableOpen) active.push('open')
    if (config.value.enableOpenat) active.push('openat')
    if (config.value.enableConnect) active.push('connect')
    if (config.value.enableBind) active.push('bind')
    if (config.value.enableUnlink) active.push('unlink')
    if (config.value.enableUnlinkat) active.push('unlinkat')
    if (config.value.enableWrite) active.push('write')
    if (config.value.enableLink) active.push('link')
    if (config.value.enableRename) active.push('rename')
    if (config.value.enableMkdir) active.push('mkdir')
    if (config.value.enableRmdir) active.push('rmdir')
    if (config.value.enableXattr) active.push('xattr')
    if (config.value.enableClone) active.push('clone')
    if (config.value.enableFork) active.push('fork')

    return active
  })

  /**
   * Check if specific tracepoint is enabled
   */
  const isTracepointEnabled = (tracepointType: string): boolean => {
    if (!config.value) return false

    const tracepointMap: Record<string, keyof MonitoringConfig> = {
      execve: 'enableExecve',
      open: 'enableOpen',
      openat: 'enableOpenat',
      connect: 'enableConnect',
      bind: 'enableBind',
      unlink: 'enableUnlink',
      unlinkat: 'enableUnlinkat',
      write: 'enableWrite',
      link: 'enableLink',
      rename: 'enableRename',
      mkdir: 'enableMkdir',
      rmdir: 'enableRmdir',
      xattr: 'enableXattr',
      clone: 'enableClone',
      fork: 'enableFork'
    }

    const key = tracepointMap[tracepointType]
    if (!key) return false

    return (config.value[key] as boolean) || false
  }

  return {
    config: computed(() => config.value),
    loading: computed(() => loading.value),
    fetchMonitoringConfig,
    updateMonitoringConfig,
    enableTracepoint,
    disableTracepoint,
    getActiveTracepoints,
    isTracepointEnabled
  }
}
