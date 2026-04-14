import { defineStore } from 'pinia'
import type { User, LoginRequest, AuthResponse } from '~/types/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as User | null,
    token: null as string | null,
    tokenExpiry: null as number | null,
    isAuthenticated: false
  }),

  getters: {
    isSuperadmin: (state) => state.user?.isSuperadmin || false,
    userRoles: (state) => state.user?.roles || [],
    username: (state) => state.user?.username || ''
  },

  actions: {
    async login(credentials: LoginRequest) {
      const { $api } = useNuxtApp()
      try {
        const response = await $api.post<AuthResponse>('/auth/login', credentials)
        this.setAuth(response.data)
        return response.data
      } catch (error) {
        throw error
      }
    },

    async fetchCurrentUser() {
      const { $api } = useNuxtApp()
      try {
        const response = await $api.get<AuthResponse>('/auth/me')
        this.user = {
          username: response.data.username,
          email: response.data.email,
          isSuperadmin: response.data.isSuperadmin,
          roles: response.data.roles
        }
      } catch (error) {
        this.logout()
        throw error
      }
    },

    setAuth(authData: AuthResponse) {
      this.user = {
        username: authData.username,
        email: authData.email,
        isSuperadmin: authData.isSuperadmin,
        roles: authData.roles
      }
      if (authData.token) {
        this.token = authData.token
        this.decodeToken(authData.token)
        if (import.meta.client) {
          localStorage.setItem('token', authData.token)
        }
      }
      this.isAuthenticated = true
    },

    decodeToken(token: string) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]))
        if (payload.exp) {
          this.tokenExpiry = payload.exp
        }
      } catch (e) {
        console.error('Failed to decode token:', e)
      }
    },

    logout() {
      // Clear ALL browser storage on client side FIRST
      if (import.meta.client) {
        // Clear localStorage completely
        localStorage.clear()

        // Clear sessionStorage
        sessionStorage.clear()

        // Clear all cookies
        const cookies = document.cookie.split(';')
        for (let cookie of cookies) {
          const eqPos = cookie.indexOf('=')
          const name = eqPos > -1 ? cookie.substr(0, eqPos).trim() : cookie.trim()
          if (name) {
            // Clear both root and potential paths
            document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/`
            document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain=${window.location.hostname}`
          }
        }
      }

      // Clear auth state
      this.user = null
      this.token = null
      this.isAuthenticated = false

      // Clear cluster store
      const clusterStore = useClusterStore()
      clusterStore.reset()
    },

    initializeAuth() {
      if (import.meta.client) {
        const token = localStorage.getItem('token')
        if (token) {
          this.token = token
          this.decodeToken(token)
          this.isAuthenticated = true
        }
      }
    },

    async refresh() {
      const { $api } = useNuxtApp()
      try {
        const response = await $api.post<AuthResponse>('/auth/refresh')
        this.setAuth(response.data)
        return response.data
      } catch (error) {
        throw error
      }
    },

    async extendSession() {
      await this.refresh()
    }
  }
})

