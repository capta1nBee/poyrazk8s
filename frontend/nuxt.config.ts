// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  ssr: false,

  modules: [
    '@nuxt/eslint',
    '@nuxt/ui',
    '@vueuse/nuxt',
    '@pinia/nuxt',
    '@nuxt/fonts'
  ],
  fonts: {
    // Use only local @fontsource packages — no external requests
    providers: {
      google: false,
    },
    defaults: {
      weights: [400, 500, 600, 700],
    },
  },
  icon: {
    serverBundle: 'local',
    fallbackToApi: false,
    clientBundle: {
      scan: true
    }
  },

  devtools: {
    enabled: true
  },

  css: ['~/assets/css/main.css'],

  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || 'http://localhost:8080/api'
    }
  },
  experimental: {
    // oxc property removed
  },

  routeRules: {
    '/api/**': {
      cors: true,
      proxy: {
        to: 'http://localhost:8080/api/**'
      }
    }
  },

  compatibilityDate: '2024-07-11',

  eslint: {
    config: {
      stylistic: {
        commaDangle: 'never',
        braceStyle: '1tbs'
      }
    }
  }
})
