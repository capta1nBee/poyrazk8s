import { usePermissions } from '~/composables/usePermissions'

// Page to permission mapping - maps URL paths to page keys in the database
const pagePermissionMap: Record<string, string> = {
  // Dashboard & Core
  '/': 'dashboard',
  '/dashboard': 'dashboard',
  '/clusters': 'clusters',
  '/backups': 'backups',
  '/admin/audit-logs': 'audit-logs',
  '/admin/roles': 'roles',

  // Workloads
  '/pods': 'pods',
  '/deployments': 'deployments',
  '/statefulsets': 'statefulsets',
  '/daemonsets': 'daemonsets',
  '/replicasets': 'replicasets',
  '/replicationcontrollers': 'replicationcontrollers',
  '/hpa': 'hpa',
  '/jobs': 'jobs',
  '/cronjobs': 'cronjobs',

  // Networking
  '/services': 'services',
  '/ingresses': 'ingresses',
  '/ingressclasses': 'ingressclasses',
  '/networkpolicies': 'network-policy',
  '/endpointslices': 'endpointslices',
  '/ipaddresses': 'ipaddresses',

  // Network Firewall
  '/network-policy-generator': 'network-policy-gen',
  '/network-monitor': 'network-monitor',
  '/network-topology': 'network-topology',

  // Monitoring
  '/pod-metrics': 'pod-metrics',

  // Storage
  '/persistentvolumes': 'persistentvolumes',
  '/persistentvolumeclaims': 'pvcs',
  '/storageclasses': 'storageclasses',
  '/volumeattachments': 'volumeattachments',
  '/csidrivers': 'csidrivers',
  '/csinodes': 'csinodes',

  // Config & Secrets
  '/configmaps': 'configmaps',
  '/secrets': 'secrets',
  '/namespaces': 'namespaces',

  // RBAC
  '/clusterroles': 'clusterroles',
  '/clusterrolebindings': 'clusterrolebindings',
  '/k8sroles': 'k8sroles',
  '/rolebindings': 'rolebindings',
  '/serviceaccounts': 'serviceaccounts',

  // Cluster Resources
  '/nodes': 'nodes',
  '/events': 'events',
  '/leases': 'leases',
  '/customresourcedefinitions': 'customresourcedefinitions',
  '/priorityclasses': 'priorityclasses',
  '/prioritylevelconfigurations': 'prioritylevelconfigurations',

  // Admission & Webhooks
  '/mutatingwebhookconfigurations': 'mutatingwebhooks',
  '/validatingwebhookconfigurations': 'validatingwebhooks',
  '/validatingadmissionpolicies': 'validatingadmissionpolicies',
  '/validatingadmissionpolicybindings': 'validatingadmissionpolicybindings',

  // Certificates & Security
  '/certificatesigningrequests': 'certificatesigningrequests',

  // Extensions
  '/helm': 'helm',
  '/helm/deploy': 'helm',
  '/appcreator': 'appcreator',
  '/appcreator/templates': 'appcreator',
  '/appcreator/git-connections': 'appcreator',
  '/appcreator/registry-connections': 'appcreator',
  '/federations': 'federations',

  // Security
  '/security/rules': 'security',
  '/security/alarms': 'security',
  '/security/vulnerability-scan': 'vulnerability-scan',
  '/security/cluster-eye': 'security-eye',

  // Settings (superadmin-only sub-pages use base 'settings' key)
  '/settings': 'settings',
  '/settings/users': 'settings',
  '/settings/roles': 'settings',
  '/settings/permissions': 'settings',
  '/settings/clusters': 'clusters',
  '/settings/ldap': 'settings',
  '/settings/members': 'settings',
  '/settings/mail': 'settings',
  '/settings/security': 'settings',
  '/settings/notifications': 'settings',
  '/settings/helm-repos': 'settings',
  '/settings/pages-actions': 'settings',
  '/settings/command-permissions': 'settings',
  '/settings/exec-sessions': 'settings',
}

// Superadmin-only routes: non-superadmins are always blocked (no Casbin workaround)
const superadminOnlyPrefixes = [
  '/clusters',
  '/admin/roles',
  '/settings',
]

export default defineNuxtRouteMiddleware(async (to: { path: string }) => {
  // Skip on server (use import.meta.server for Nuxt 3)
  if (import.meta.server) {
    return
  }

  const authStore = useAuthStore()

  // Public routes that don't require authentication
  const publicRoutes = ['/login']

  // If going to login page and already authenticated, redirect to home
  if (to.path === '/login' && authStore.isAuthenticated) {
    return navigateTo('/')
  }

  // Allow access to public routes
  if (publicRoutes.includes(to.path)) {
    return
  }

  // Initialize auth from localStorage if not already done
  if (!authStore.isAuthenticated) {
    authStore.initializeAuth()
  }

  // If authenticated but user profile is missing (e.g. after refresh), fetch it
  if (authStore.isAuthenticated && !authStore.user) {
    try {
      await authStore.fetchCurrentUser()
    } catch (error) {
      console.error('Failed to fetch user profile in middleware:', error)
    }
  }

  // Redirect to login if not authenticated
  if (!authStore.isAuthenticated) {
    return navigateTo('/login')
  }

  // Dashboard is accessible to all authenticated users
  if (to.path === '/' || to.path === '/dashboard') {
    return
  }

  // Superadmins can access all pages
  if (authStore.user?.isSuperadmin) {
    return
  }

  // Get required permission for this page
  const requiredPermission = pagePermissionMap[to.path]

  // If no specific permission required for this path, check by extracting page name from path
  // This ensures all pages go through permission check
  const pageName = requiredPermission || to.path.replace(/^\//, '').split('/')[0]

  // Skip empty page names (shouldn't happen)
  if (!pageName) {
    return
  }

  // Check page access using permissions composable
  const permissions = usePermissions()

  // Ensure permissions object is valid
  if (!permissions) {
    console.warn('Permissions composable returned undefined')
    return showError({
      statusCode: 403,
      message: 'Permission check failed'
    })
  }

  // Always load permissions if not loaded
  if (!permissions.permissionsLoaded?.value) {
    try {
      await permissions.fetchUserPages()
    } catch (error) {
      console.error('Failed to load permissions:', error)
      return showError({
        statusCode: 403,
        message: 'Yetki bilgileri yüklenemedi'
      })
    }
  }

  // Superadmin-only routes: block non-superadmins regardless of Casbin permissions
  const isSuperadminRoute = superadminOnlyPrefixes.some(prefix =>
    to.path === prefix || to.path.startsWith(prefix + '/')
  )
  if (isSuperadminRoute && !authStore.user?.isSuperadmin) {
    console.warn(`Superadmin-only route blocked for non-superadmin: ${to.path}`)
    return showError({
      statusCode: 403,
      message: 'Bu sayfaya erişim izniniz bulunmamaktadır.'
    })
  }

  // Check if user has access to this page
  const hasAccess = permissions.canAccessPage ? permissions.canAccessPage(pageName) : false

  if (!hasAccess) {
    console.warn(`Access denied to page: ${to.path} (requires: ${pageName})`, {
      userPages: permissions.userPages?.value,
      permissionsLoaded: permissions.permissionsLoaded?.value
    })
    return showError({
      statusCode: 403,
      message: `Bu sayfaya erişim izniniz bulunmamaktadır: ${to.path}`
    })
  }

})

