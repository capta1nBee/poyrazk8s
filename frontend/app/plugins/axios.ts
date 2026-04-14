import axios from 'axios'

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig()

  const api = axios.create({
    baseURL: config.public.apiBase as string,
    headers: {
      'Content-Type': 'application/json'
    }
  })

  // Request interceptor
  api.interceptors.request.use(
    (config) => {
      if (import.meta.client) {
        const token = localStorage.getItem('token')
        if (token) {
          config.headers.Authorization = `Bearer ${token}`
        }
      }
      return config
    },
    (error) => {
      return Promise.reject(error)
    }
  )

  // Response interceptor
  api.interceptors.response.use(
    (response) => response,
    async (error) => {
      if (error.response?.status === 401 && import.meta.client) {
        const authStore = useAuthStore()
        authStore.logout()
        await navigateTo('/login')
      }
      return Promise.reject(error)
    }
  )

  return {
    provide: {
      api
    }
  }
})

