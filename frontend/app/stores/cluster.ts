import { defineStore } from 'pinia'
import type { Cluster } from '~/types/cluster'

export const useClusterStore = defineStore('cluster', {
  state: () => ({
    clusters: [] as Cluster[],
    selectedCluster: null as Cluster | null,
    selectedNamespace: (typeof window !== 'undefined' && localStorage.getItem('selectedNamespace')) || 'default'
  }),

  getters: {
    activeClusters: (state) => state.clusters.filter(c => c.isActive),
    selectedClusterId: (state) => state.selectedCluster?.id || null,
    selectedClusterUid: (state) => state.selectedCluster?.uid || null,
    selectedClusterName: (state) => state.selectedCluster?.name || ''
  },

  actions: {
    async fetchClusters() {
      const { $api } = useNuxtApp()
      try {
        const response = await $api.get<Cluster[]>('/clusters')
        this.clusters = response.data
        if (response.data.length > 0 && !this.selectedCluster) {
          this.selectedCluster = response.data[0]
        }
      } catch (error) {
        console.error('Failed to fetch clusters:', error)
        throw error
      }
    },

    selectCluster(cluster: Cluster) {
      this.selectedCluster = cluster
      if (import.meta.client) {
        localStorage.setItem('selectedClusterId', cluster.id.toString())
      }
    },

    selectNamespace(namespace: string) {
      this.selectedNamespace = namespace
      if (import.meta.client) {
        localStorage.setItem('selectedNamespace', namespace)
      }
    },

    initializeSelection() {
      if (import.meta.client) {
        const clusterId = localStorage.getItem('selectedClusterId')
        const namespace = localStorage.getItem('selectedNamespace')

        if (clusterId && this.clusters.length > 0) {
          const cluster = this.clusters.find(c => c.id === parseInt(clusterId))
          if (cluster) {
            this.selectedCluster = cluster
          }
        }

        if (namespace) {
          this.selectedNamespace = namespace
        }
      }
    },

    reset() {
      this.clusters = []
      this.selectedCluster = null
      this.selectedNamespace = 'default'
      if (import.meta.client) {
        localStorage.removeItem('selectedClusterId')
        localStorage.removeItem('selectedNamespace')
      }
    }
  }
})

