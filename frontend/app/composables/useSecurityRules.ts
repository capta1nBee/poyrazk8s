import { ref, computed } from 'vue'
import { useAsyncData } from '#app'

export interface SecurityRule {
  id: number
  name: string
  description: string
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  enabled: boolean
  ruleType: string
  condition: any
  tags: string[]
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface SecurityAlert {
  id: number
  clusterUid: string
  eventType: string
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
  ruleName: string
  ruleDescription: string
  output: string
  namespaceName: string
  podName: string
  isAcknowledged: boolean
  acknowledgedBy: string
  resolvedBy: string
  resolved: boolean
  createdAt: string
}

export const useSecurityRules = () => {
  const authStore = useAuthStore()
  const toast = useToast()

  const headers = () => ({
    Authorization: `Bearer ${authStore.token}`
  })

  /**
   * Fetch rules for a cluster
   */
  const fetchRules = async (clusterUid: string, page: number = 1, pageSize: number = 10, search?: string, priority?: string) => {
    try {
      const query: any = {
        clusterUid,
        page: page - 1,
        size: pageSize
      }

      if (search) query.searchTerm = search
      if (priority) query.priority = priority

      const response = await $fetch('/api/security/rules', {
        method: 'GET',
        query,
        headers: headers()
      })

      return response
    } catch (error: any) {
      console.error('Error fetching rules:', error)
      throw error
    }
  }

  /**
   * Get single rule by ID
   */
  const getRule = async (ruleId: number) => {
    try {
      const response = await $fetch(`/api/security/rules/${ruleId}`, {
        method: 'GET',
        headers: headers()
      })

      return response
    } catch (error: any) {
      console.error('Error fetching rule:', error)
      throw error
    }
  }

  /**
   * Create new rule
   */
  const createRule = async (clusterUid: string, ruleData: Partial<SecurityRule>) => {
    try {
      const response = await $fetch('/api/security/rules', {
        method: 'POST',
        body: {
          ...ruleData,
          clusterUid
        },
        headers: headers()
      })

      toast.add({
        title: 'Success',
        description: 'Rule created successfully',
        color: 'green'
      })

      return response
    } catch (error: any) {
      console.error('Error creating rule:', error)
      toast.add({
        title: 'Error',
        description: error.message || 'Failed to create rule',
        color: 'error'
      })
      throw error
    }
  }

  /**
   * Update rule
   */
  const updateRule = async (ruleId: number, ruleData: Partial<SecurityRule>) => {
    try {
      const response = await $fetch(`/api/security/rules/${ruleId}`, {
        method: 'PUT',
        body: ruleData,
        headers: headers()
      })

      toast.add({
        title: 'Success',
        description: 'Rule updated successfully',
        color: 'green'
      })

      return response
    } catch (error: any) {
      console.error('Error updating rule:', error)
      toast.add({
        title: 'Error',
        description: error.message || 'Failed to update rule',
        color: 'error'
      })
      throw error
    }
  }

  /**
   * Delete rule
   */
  const deleteRule = async (ruleId: number) => {
    try {
      await $fetch(`/api/security/rules/${ruleId}`, {
        method: 'DELETE',
        headers: headers()
      })

      toast.add({
        title: 'Success',
        description: 'Rule deleted successfully',
        color: 'green'
      })
    } catch (error: any) {
      console.error('Error deleting rule:', error)
      toast.add({
        title: 'Error',
        description: error.message || 'Failed to delete rule',
        color: 'error'
      })
      throw error
    }
  }

  /**
   * Toggle rule enabled/disabled status
   */
  const toggleRule = async (ruleId: number) => {
    try {
      const response = await $fetch(`/api/security/rules/${ruleId}/toggle`, {
        method: 'PATCH',
        headers: headers()
      })

      toast.add({
        title: 'Success',
        description: 'Rule toggled successfully',
        color: 'green'
      })

      return response
    } catch (error: any) {
      console.error('Error toggling rule:', error)
      toast.add({
        title: 'Error',
        description: error.message || 'Failed to toggle rule',
        color: 'error'
      })
      throw error
    }
  }

  /**
   * Fetch alerts for a cluster
   */
  const fetchAlerts = async (clusterUid: string, page: number = 1, pageSize: number = 20, filters?: any) => {
    try {
      const query: any = {
        clusterUid,
        page: page - 1,
        size: pageSize,
        ...filters
      }

      const response = await $fetch('/api/security/alerts/search', {
        method: 'GET',
        query,
        headers: headers()
      })

      return response
    } catch (error: any) {
      console.error('Error fetching alerts:', error)
      throw error
    }
  }

  /**
   * Get pending alerts
   */
  const getPendingAlerts = async (clusterUid: string) => {
    try {
      const response = await $fetch('/api/security/alerts/pending', {
        method: 'GET',
        query: { clusterUid },
        headers: headers()
      })

      return response
    } catch (error: any) {
      console.error('Error fetching pending alerts:', error)
      throw error
    }
  }

  /**
   * Get recent alerts
   */
  const getRecentAlerts = async (clusterUid: string, limit: number = 10) => {
    try {
      const response = await $fetch('/api/security/alerts/recent', {
        method: 'GET',
        query: { clusterUid, limit },
        headers: headers()
      })

      return response
    } catch (error: any) {
      console.error('Error fetching recent alerts:', error)
      throw error
    }
  }

  /**
   * Get single alert
   */
  const getAlert = async (alertId: number) => {
    try {
      const response = await $fetch(`/api/security/alerts/${alertId}`, {
        method: 'GET',
        headers: headers()
      })

      return response
    } catch (error: any) {
      console.error('Error fetching alert:', error)
      throw error
    }
  }

  /**
   * Acknowledge alert
   */
  const acknowledgeAlert = async (alertId: number, note: string = '') => {
    try {
      const response = await $fetch(`/api/security/alerts/${alertId}/acknowledge`, {
        method: 'PATCH',
        body: { note },
        headers: headers()
      })

      toast.add({
        title: 'Success',
        description: 'Alert acknowledged',
        color: 'green'
      })

      return response
    } catch (error: any) {
      console.error('Error acknowledging alert:', error)
      toast.add({
        title: 'Error',
        description: error.message || 'Failed to acknowledge alert',
        color: 'error'
      })
      throw error
    }
  }

  /**
   * Resolve alert
   */
  const resolveAlert = async (alertId: number, note: string = '') => {
    try {
      const response = await $fetch(`/api/security/alerts/${alertId}/resolve`, {
        method: 'PATCH',
        body: { note },
        headers: headers()
      })

      toast.add({
        title: 'Success',
        description: 'Alert resolved',
        color: 'green'
      })

      return response
    } catch (error: any) {
      console.error('Error resolving alert:', error)
      toast.add({
        title: 'Error',
        description: error.message || 'Failed to resolve alert',
        color: 'error'
      })
      throw error
    }
  }

  /**
   * Get security statistics
   */
  const getSecurityStats = async (clusterUid: string) => {
    try {
      const rulesStats = await $fetch('/api/security/rules/stats', {
        method: 'GET',
        query: { clusterUid },
        headers: headers()
      })

      const alertsStats = await $fetch('/api/security/alerts/stats', {
        method: 'GET',
        query: { clusterUid },
        headers: headers()
      })

      return {
        rules: rulesStats,
        alerts: alertsStats
      }
    } catch (error: any) {
      console.error('Error fetching security stats:', error)
      throw error
    }
  }

  /**
   * Get alerts by pod
   */
  const getAlertsByPod = async (clusterUid: string, podName: string, page: number = 1, pageSize: number = 20) => {
    try {
      const response = await $fetch(`/api/security/alerts/pod/${podName}`, {
        method: 'GET',
        query: {
          clusterUid,
          page: page - 1,
          size: pageSize
        },
        headers: headers()
      })

      return response
    } catch (error: any) {
      console.error('Error fetching pod alerts:', error)
      throw error
    }
  }

  /**
   * Get alerts by namespace
   */
  const getAlertsByNamespace = async (clusterUid: string, namespace: string, page: number = 1, pageSize: number = 20) => {
    try {
      const response = await $fetch(`/api/security/alerts/namespace/${namespace}`, {
        method: 'GET',
        query: {
          clusterUid,
          page: page - 1,
          size: pageSize
        },
        headers: headers()
      })

      return response
    } catch (error: any) {
      console.error('Error fetching namespace alerts:', error)
      throw error
    }
  }

  return {
    // Rules
    fetchRules,
    getRule,
    createRule,
    updateRule,
    deleteRule,
    toggleRule,

    // Alerts
    fetchAlerts,
    getPendingAlerts,
    getRecentAlerts,
    getAlert,
    acknowledgeAlert,
    resolveAlert,
    getAlertsByPod,
    getAlertsByNamespace,

    // Stats
    getSecurityStats
  }
}
