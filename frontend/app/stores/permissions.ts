import { defineStore } from 'pinia'

export const usePermissionsStore = defineStore('permissions', {
  state: () => ({
    loaded: false,
    isSuperadmin: false,
    pages: new Set<string>(),
    /** The clusterUid that was used for the last fetchMyPages call */
    loadedForCluster: null as string | null
  }),

  getters: {
    /** Returns true if user can access the given page key */
    canAccess: (state) => (pageKey: string): boolean => {
      if (state.isSuperadmin) return true
      return state.pages.has(pageKey)
    },
    /** Whether any permission data has been loaded */
    isLoaded: (state) => state.loaded
  },

  actions: {
    /**
     * Fetch accessible pages for a specific cluster.
     * If clusterUid changes, automatically reloads even if already loaded.
     * Pass clusterUid="*" or undefined for initial load (all clusters).
     */
    async fetchMyPages(clusterUid?: string) {
      const uid = clusterUid || '*'
      // Skip if already loaded for the same cluster
      if (this.loaded && this.loadedForCluster === uid) return
      const { $api } = useNuxtApp()
      try {
        const params = uid !== '*' ? `?clusterUid=${encodeURIComponent(uid)}` : ''
        const res = await $api.get<{ pages: string[], isSuperadmin: boolean }>(
          `/admin/roles/my-pages${params}`
        )
        this.pages = new Set(res.data.pages || [])
        this.isSuperadmin = res.data.isSuperadmin ?? false
        this.loadedForCluster = uid
      } catch {
        // On error keep empty — will deny access gracefully
        this.pages = new Set()
        this.isSuperadmin = false
        this.loadedForCluster = uid
      } finally {
        this.loaded = true
      }
    },

    reset() {
      this.loaded = false
      this.isSuperadmin = false
      this.pages = new Set()
      this.loadedForCluster = null
    }
  }
})