import { usePermissionsStore } from '~/stores/permissions'

/**
 * Casbin-backed permission composable.
 * - SUPERADMIN → all allowed instantly (no API call)
 * - Others     → POST /api/admin/roles/check (Casbin enforce)
 * API is backward-compatible: same method names as before.
 */
export const usePermissions = () => {
  const { $api }      = useNuxtApp()
  const permStore     = usePermissionsStore()
  const clusterStore  = useClusterStore()

  const clusterUid = computed(() => clusterStore.selectedCluster?.uid ?? '*')

  /** Single action check — calls Casbin via /api/admin/roles/check */
  const hasActionPermission = async (
    resourceKind: string,
    _resourceName: string,
    namespace: string | undefined,
    actionCode: string
  ): Promise<boolean> => {
    if (permStore.isSuperadmin) return true
    try {
      const res = await $api.post<{ allowed: boolean }>('/admin/roles/check', {
        clusterUid: clusterUid.value,
        namespace: namespace ?? '*',
        resourceKind,
        action: actionCode
      })
      return res.data.allowed === true
    } catch { return false }
  }

  /** Multiple action codes in parallel — no batch endpoint needed, runs in parallel */
  const hasActionPermissions = async (
    resourceKind: string,
    resourceName: string,
    namespace: string | undefined,
    actionCodes: string[]
  ): Promise<Record<string, boolean>> => {
    if (permStore.isSuperadmin)
      return Object.fromEntries(actionCodes.map(c => [c, true]))
    const entries = await Promise.all(
      actionCodes.map(async code => [code, await hasActionPermission(resourceKind, resourceName, namespace, code)] as [string, boolean])
    )
    return Object.fromEntries(entries)
  }

  /** Page access — uses local permStore (no extra API call) */
  const canAccessPage = (pageName: string): boolean => permStore.canAccess(pageName)

  const fetchUserPages = () => permStore.fetchMyPages()

  return {
    hasActionPermission,
    hasActionPermissions,
    canAccessPage,
    fetchUserPages,
    permissionsLoaded: computed(() => permStore.isLoaded),
    userPages:         computed(() => permStore.pages)
  }
}
