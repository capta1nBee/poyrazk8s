/**
 * useActionMap — DB-driven resource-kind → action-codes map.
 *
 * Module-level cache: fetched once per session, shared across all
 * ResourceActionMenu instances so only one HTTP call is made.
 *
 * Falls back to a minimal safe default if the fetch fails.
 */

import { ref, readonly } from 'vue'

const _actionMap = ref<Record<string, string[]>>({})
const _loaded = ref(false)
const _loading = ref(false)

/** Minimal fallback for any kind not yet in DB */
const DEFAULT_ACTIONS = ['view-yaml', 'edit-yaml', 'delete', 'refresh', 'events', 'show-labels', 'show-annotations']

export const useActionMap = () => {
  const { $api } = useNuxtApp()

  /**
   * Fetch the action map from the backend if not already loaded.
   * Safe to call multiple times — only one request will be made.
   */
  const ensureLoaded = async (): Promise<void> => {
    if (_loaded.value || _loading.value) return
    _loading.value = true
    try {
      const res = await $api.get<Record<string, string[]>>('/admin/roles/action-map')
      _actionMap.value = res.data ?? {}
      _loaded.value = true
    } catch (e) {
      console.warn('[useActionMap] Failed to load action map from backend, using defaults', e)
    } finally {
      _loading.value = false
    }
  }

  /**
   * Returns the list of action codes for a given K8s resource kind.
   * Make sure `ensureLoaded()` has been awaited before calling this.
   */
  const getActionCodesForKind = (kind: string): string[] => {
    return _actionMap.value[kind] ?? DEFAULT_ACTIONS
  }

  return {
    ensureLoaded,
    getActionCodesForKind,
    actionMap: readonly(_actionMap),
    isLoaded: readonly(_loaded),
  }
}
