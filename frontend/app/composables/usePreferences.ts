export interface AppFont {
  name: string
  label: string
  family: string
  description: string
  style: 'modern' | 'classic' | 'technical' | 'elegant' | 'minimal'
}

export const AVAILABLE_FONTS: AppFont[] = [
  {
    name: 'outfit',
    label: 'Outfit',
    family: '"Outfit", system-ui, sans-serif',
    description: 'Modern & geometric',
    style: 'modern'
  },
  {
    name: 'inter',
    label: 'Inter',
    family: '"Inter", system-ui, sans-serif',
    description: 'Versatile & readable',
    style: 'minimal'
  },
  {
    name: 'jetbrains-mono',
    label: 'JetBrains Mono',
    family: '"JetBrains Mono", monospace',
    description: 'Developer-focused',
    style: 'technical'
  },
  {
    name: 'plus-jakarta',
    label: 'Plus Jakarta Sans',
    family: '"Plus Jakarta Sans", system-ui, sans-serif',
    description: 'Premium & elegant',
    style: 'elegant'
  },
  {
    name: 'dm-sans',
    label: 'DM Sans',
    family: '"DM Sans", system-ui, sans-serif',
    description: 'Geometric & bold',
    style: 'classic'
  }
]

export interface UserPreferences {
  theme: 'light' | 'dark' | 'system'
  font: string
  sidebarCollapsed: boolean
  compactMode: boolean
}

const DEFAULT_PREFERENCES: UserPreferences = {
  theme: 'dark',
  font: 'inter',
  sidebarCollapsed: false,
  compactMode: false
}

const STORAGE_KEY = 'poyraz-preferences'

export const usePreferences = () => {
  // Safe reactive state using ref instead of useState to avoid SSR issues
  const preferences = ref<UserPreferences>({ ...DEFAULT_PREFERENCES })
  const initialized = ref(false)

  // Load preferences from localStorage
  const loadPreferences = () => {
    if (!import.meta.client) return
    
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        const parsed = JSON.parse(stored)
        preferences.value = { ...DEFAULT_PREFERENCES, ...parsed }
      }
      initialized.value = true
    } catch (error) {
      console.error('Failed to load preferences:', error)
    }
  }

  // Save preferences to localStorage
  const savePreferences = () => {
    if (!import.meta.client) return
    
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences.value))
    } catch (error) {
      console.error('Failed to save preferences:', error)
    }
  }

  // Apply font to document
  const applyFont = (fontName: string) => {
    if (!import.meta.client) return
    
    const font = AVAILABLE_FONTS.find(f => f.name === fontName)
    if (font) {
      document.documentElement.style.setProperty('--font-sans', font.family)
      document.body.style.fontFamily = font.family
    }
  }

  // Computed properties
  const currentFont = computed(() => {
    return AVAILABLE_FONTS.find(f => f.name === preferences.value.font) || AVAILABLE_FONTS[0]
  })

  // Actions
  const setFont = (fontName: string) => {
    preferences.value.font = fontName
    applyFont(fontName)
    savePreferences()
  }

  const setTheme = (theme: 'light' | 'dark' | 'system') => {
    preferences.value.theme = theme
    if (import.meta.client) {
      try {
        const colorMode = useColorMode()
        colorMode.preference = theme
      } catch (e) {
        console.warn('Could not set color mode:', e)
      }
    }
    savePreferences()
  }

  const toggleCompactMode = () => {
    preferences.value.compactMode = !preferences.value.compactMode
    savePreferences()
  }

  const toggleSidebarCollapsed = () => {
    preferences.value.sidebarCollapsed = !preferences.value.sidebarCollapsed
    savePreferences()
  }

  // Initialize preferences
  const initializePreferences = () => {
    if (initialized.value) return
    
    loadPreferences()
    applyFont(preferences.value.font)
    
    // Sync theme
    if (import.meta.client && preferences.value.theme) {
      try {
        const colorMode = useColorMode()
        colorMode.preference = preferences.value.theme
      } catch (e) {
        console.warn('Could not sync color mode:', e)
      }
    }
  }

  // Auto-initialize on client
  if (import.meta.client && !initialized.value) {
    loadPreferences()
  }

  return {
    preferences: readonly(preferences),
    currentFont,
    availableFonts: AVAILABLE_FONTS,
    setFont,
    setTheme,
    toggleCompactMode,
    toggleSidebarCollapsed,
    initializePreferences,
    loadPreferences,
    savePreferences
  }
}
