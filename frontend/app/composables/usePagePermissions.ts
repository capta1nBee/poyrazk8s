import { useActionMap } from '~/composables/useActionMap'

/**
 * Resource kinds that belong to Tier 3 pages (page-level access only, no action-level Casbin rules).
 * If the user has page-level access for the mapped page key, ALL actions on this resource kind
 * are considered allowed — matching the backend controller behaviour (checkPagePermissionOrThrow).
 */
const T3_PAGE_MAP: Record<string, string> = {
  AppCreator:         'appcreator',
  AppCreatorGit:      'appcreator',
  AppCreatorRegistry: 'appcreator',
  HelmRelease:        'helm',
  HelmRepository:     'helm',
  Backup:             'backups',
  Federation:         'federations',
  NetworkFlow:        'network-monitor',
  NetworkTopology:    'network-topology',
  // Security pages — T3 page-only access via page:security
  SecurityRule:       'security',
  SecurityAlert:      'security',
  SecurityAlarm:      'security',
  // Reports — T3 page-only access via page:reports
  // NOTE: VulnerabilityScan and ClusterEye are now T1 (namespace-scoped).
  //       They no longer use the T3 fast-path — Casbin action-level checks apply.
  Report:             'reports',
}

/**
 * Glob-style pattern matching for resource name filtering.
 * Supports * as wildcard. Case-insensitive.
 * Examples: "*kayit*" matches "test-kayit-pod", "prod-*" matches "prod-app"
 */
function matchesGlob(name: string, pattern: string): boolean {
  if (!pattern || pattern === '*') return true
  const escaped = pattern.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*')
  return new RegExp('^' + escaped + '$', 'i').test(name)
}

/**
 * usePagePermissions — real Casbin-backed permission check for a resource kind.
 *
 * Fetches all action permissions for the given resourceKind using the DB-driven
 * action map. Re-checks when selected cluster or namespace changes.
 * Superadmin users bypass all checks and always get true.
 *
 * Also loads name-pattern filters via /admin/roles/my-name-filters so that
 * filterByName() can be called to hide resources that don't match the user's
 * assigned name patterns.
 *
 * Usage:
 *   const { hasPermission, filterByName, loading } = usePagePermissions('Pod')
 *   const filteredPods = computed(() => filterByName(pods.value))
 */
/** Filter entry with both namespace and name pattern */
interface NameFilter {
  nsPattern: string
  namePattern: string
}

export const usePagePermissions = (resourceKind?: string) => {
  const authStore = useAuthStore()
  const permStore = usePermissionsStore()
  const clusterStore = useClusterStore()
  const { $api } = useNuxtApp()
  const actionMapComp = useActionMap()

  const _loading = ref(true)
  const _permissions = ref<Record<string, boolean>>({})
  const _namePatterns = ref<string[]>(['*']) // backward compat
  const _nsPatterns = ref<string[]>(['*'])   // namespace patterns
  const _filters = ref<NameFilter[]>([])     // full filter entries (ns+name)
  // true when no actions are registered for this resourceKind in the action map
  // (Tier 3 / page-only pages). In this case hasPermission always returns true —
  // page access is already enforced by the sidebar/middleware.
  const _pageOnly = ref(false)

  // Superadmin check: use both auth store user flag AND permissions store (loaded from /my-pages)
  const _isSuperadmin = () => !!(authStore.user?.isSuperadmin || permStore.isSuperadmin)

  const loadPermissions = async () => {
    if (_isSuperadmin()) {
      _loading.value = false
      _pageOnly.value = false
      _namePatterns.value = ['*']
      _nsPatterns.value = ['*']
      _filters.value = []
      return
    }
    if (!resourceKind) { _loading.value = false; return }

    _loading.value = true
    _pageOnly.value = false
    try {
      // ── T3 fast-path: if resource kind is mapped to a T3 page AND user has page access,
      // grant all actions immediately — no Casbin action-level calls needed.
      const t3PageKey = T3_PAGE_MAP[resourceKind]
      if (t3PageKey && permStore.canAccess(t3PageKey)) {
        _pageOnly.value = true
        _loading.value = false
        return
      }

      await actionMapComp.ensureLoaded()
      const actionCodes = actionMapComp.getActionCodesForKind(resourceKind)

      // If no actions are registered for this resourceKind in the DB, it is a Tier 3
      // (page-only) resource. Page access is enforced by the sidebar/middleware, so
      // hasPermission always returns true — no API calls needed.
      if (actionCodes.length === 0) {
        _pageOnly.value = true
        _loading.value = false
        return
      }

      const clusterUid = clusterStore.selectedCluster?.uid ?? '*'
      // Always use '*' for the permission check so "All Namespaces" view works.
      // Actual namespace restriction is enforced in filterByName below.
      const namespace = clusterStore.selectedNamespace || '*'

      // Load action permissions and name filters in parallel
      const [entries, nameFilterRes] = await Promise.all([
        Promise.all(
          actionCodes.map(async (code: string) => {
            try {
              const res = await $api.post<{ allowed: boolean }>('/admin/roles/check', {
                clusterUid, namespace, resourceKind, action: code
              })
              return [code, res.data?.allowed === true] as [string, boolean]
            } catch {
              return [code, false] as [string, boolean]
            }
          })
        ),
        $api.get<{ patterns: string[], nsPatterns: string[], filters: NameFilter[] }>(
          '/admin/roles/my-name-filters', { params: { resourceKind, clusterUid } }
        ).catch(() => ({ data: { patterns: ['*'], nsPatterns: ['*'], filters: [] } }))
      ])

      _permissions.value = Object.fromEntries(entries)
      _namePatterns.value = nameFilterRes.data?.patterns?.length ? nameFilterRes.data.patterns : ['*']
      _nsPatterns.value   = nameFilterRes.data?.nsPatterns?.length ? nameFilterRes.data.nsPatterns : ['*']
      _filters.value      = nameFilterRes.data?.filters ?? []
    } catch (e) {
      console.warn('[usePagePermissions] failed', e)
      _permissions.value = {}
      _namePatterns.value = ['*']
      _nsPatterns.value = ['*']
      _filters.value = []
    } finally {
      _loading.value = false
    }
  }

  const hasPermission = (action: string): boolean => {
    if (_isSuperadmin()) return true
    // Tier 3 / page-only: no actions registered → page access already enforced by middleware
    if (_pageOnly.value) return true
    if (_loading.value) return false
    return _permissions.value[action] === true
  }

  /**
   * Filter a list of items by the user's effective name AND namespace patterns.
   * An item is visible if it matches ANY filter entry (nsPattern AND namePattern).
   * If filters are empty (wildcard/superadmin) → returns all items unchanged.
   *
   * @param items     - list of resources
   * @param nameField - field containing the resource name (default: 'name')
   * @param nsField   - field containing the namespace (default: 'namespace')
   */
  const filterByName = <T extends Record<string, any>>(
    items: T[], nameField = 'name', nsField = 'namespace'
  ): T[] => {
    if (_isSuperadmin()) return items
    // If we have full filter entries (ns+name), use them for precise matching
    if (_filters.value.length > 0) {
      return items.filter(item => {
        const name = String(item[nameField] ?? '')
        const ns   = String(item[nsField]   ?? '')
        return _filters.value.some((f: NameFilter) =>
          matchesGlob(ns, f.nsPattern) && matchesGlob(name, f.namePattern)
        )
      })
    }
    // Fallback: name-only filter (no namespace restriction)
    const patterns = _namePatterns.value
    if (!patterns.length || patterns.includes('*')) return items
    return items.filter(item => {
      const name = String(item[nameField] ?? '')
      return patterns.some((p: string) => matchesGlob(name, p))
    })
  }

  onMounted(() => loadPermissions())
  watch(
    [() => clusterStore.selectedCluster?.uid, () => clusterStore.selectedNamespace],
    () => loadPermissions()
  )

  return {
    permissions:  computed(() => _permissions.value),
    namePatterns: computed(() => _namePatterns.value),
    nsPatterns:   computed(() => _nsPatterns.value),
    filters:      computed(() => _filters.value),
    loading:      computed(() => _loading.value),
    hasPermission,
    filterByName,
    hasAnyPermission:  (...actions: string[]) => actions.some(a => hasPermission(a)),
    hasAllPermissions: (...actions: string[]) => actions.every(a => hasPermission(a)),
    loadPermissions
  }
}
