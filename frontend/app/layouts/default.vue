<script setup lang="ts">
const clusterStore  = useClusterStore()
const permStore     = usePermissionsStore()
const open          = ref(false)
const cl            = () => { open.value = false }

// ── All possible nav items (each leaf has optional pageKey for permission check)
// pageKey=undefined → always visible (Dashboard, Clusters, Settings, etc.)
type NavItem = { label: string; icon?: string; to?: string; pageKey?: string; superadminOnly?: boolean; type?: string; defaultOpen?: boolean; children?: NavItem[]; onSelect?: () => void }

const ALL_NAV: NavItem[][] = [[
  { label: 'Dashboard', icon: 'i-lucide-layout-dashboard', to: '/', onSelect: cl },

  // ── Workloads ──────────────────────────────────────────────────────────────
  { label: 'Workloads', icon: 'i-lucide-box', type: 'trigger', defaultOpen: false, children: [
    { label: 'Pods',                   to: '/pods',                   pageKey: 'pods',                   onSelect: cl },
    { label: 'Deployments',            to: '/deployments',            pageKey: 'deployments',            onSelect: cl },
    { label: 'StatefulSets',           to: '/statefulsets',           pageKey: 'statefulsets',           onSelect: cl },
    { label: 'DaemonSets',             to: '/daemonsets',             pageKey: 'daemonsets',             onSelect: cl },
    { label: 'Jobs',                   to: '/jobs',                   pageKey: 'jobs',                   onSelect: cl },
    { label: 'CronJobs',               to: '/cronjobs',               pageKey: 'cronjobs',               onSelect: cl },
    { label: 'ReplicaSets',            to: '/replicasets',            pageKey: 'replicasets',            onSelect: cl },
    { label: 'ReplicationControllers', to: '/replicationcontrollers', pageKey: 'replicationcontrollers', onSelect: cl },
    { label: 'HPA',                  to: '/hpa',                   pageKey: 'hpa',                    onSelect: cl },
  ]},

  // ── Network ────────────────────────────────────────────────────────────────
  { label: 'Network', icon: 'i-lucide-network', type: 'trigger', defaultOpen: false, children: [
    { label: 'Services',       to: '/services',       pageKey: 'services',       onSelect: cl },
    { label: 'Ingresses',      to: '/ingresses',      pageKey: 'ingresses',      onSelect: cl },
    { label: 'Ingress Classes', to: '/ingressclasses', pageKey: 'ingressclasses', onSelect: cl },
    { label: 'Network Policies', to: '/networkpolicies', pageKey: 'network-policy', onSelect: cl },
    { label: 'Endpoint Slices', to: '/endpointslices', pageKey: 'endpointslices', onSelect: cl },
    { label: 'IP Addresses',   to: '/ipaddresses',    pageKey: 'ipaddresses',    onSelect: cl },
  ]},

  // ── Network Firewall ───────────────────────────────────────────────────────
  { label: 'Network Firewall', icon: 'i-lucide-shield-ellipsis', type: 'trigger', defaultOpen: false, children: [
    { label: 'Policy Generator', to: '/network-policy-generator', pageKey: 'network-policy-gen', onSelect: cl },
    { label: 'Flow Monitor',     to: '/network-monitor',          pageKey: 'network-monitor',    onSelect: cl },
    { label: 'Topology',         to: '/network-topology',         pageKey: 'network-topology',   onSelect: cl },
  ]},

  // ── Storage ────────────────────────────────────────────────────────────────
  { label: 'Storage', icon: 'i-lucide-hard-drive', type: 'trigger', defaultOpen: false, children: [
    { label: 'PersistentVolumeClaims', to: '/persistentvolumeclaims', pageKey: 'pvcs',             onSelect: cl },
    { label: 'PersistentVolumes',      to: '/persistentvolumes',      pageKey: 'persistentvolumes', onSelect: cl },
    { label: 'StorageClasses',         to: '/storageclasses',         pageKey: 'storageclasses',    onSelect: cl },
    { label: 'CSI Drivers',            to: '/csidrivers',             pageKey: 'csidrivers',        onSelect: cl },
    { label: 'CSI Nodes',              to: '/csinodes',               pageKey: 'csinodes',          onSelect: cl },
    { label: 'Volume Attachments',     to: '/volumeattachments',      pageKey: 'volumeattachments', onSelect: cl },
  ]},

  // ── Config ─────────────────────────────────────────────────────────────────
  { label: 'Config', icon: 'i-lucide-file-cog', type: 'trigger', defaultOpen: false, children: [
    { label: 'ConfigMaps', to: '/configmaps', pageKey: 'configmaps', onSelect: cl },
    { label: 'Secrets',    to: '/secrets',    pageKey: 'secrets',    onSelect: cl },
  ]},

  // ── Access Control ─────────────────────────────────────────────────────────
  { label: 'Access Control', icon: 'i-lucide-users-round', type: 'trigger', defaultOpen: false, children: [
    { label: 'ServiceAccounts',            to: '/serviceaccounts',             pageKey: 'serviceaccounts',             onSelect: cl },
    { label: 'ClusterRoles',               to: '/clusterroles',                pageKey: 'clusterroles',                onSelect: cl },
    { label: 'ClusterRoleBindings',        to: '/clusterrolebindings',         pageKey: 'clusterrolebindings',         onSelect: cl },
    { label: 'Roles',                      to: '/k8sroles',                    pageKey: 'k8sroles',                    onSelect: cl },
    { label: 'RoleBindings',               to: '/rolebindings',                pageKey: 'rolebindings',                onSelect: cl },
    { label: 'CertificateSigningRequests', to: '/certificatesigningrequests',  pageKey: 'certificatesigningrequests',  onSelect: cl },
  ]},

  // ── Monitoring ────────────────────────────────────────────────────────────
  { label: 'Monitoring', icon: 'i-lucide-activity', type: 'trigger', defaultOpen: false, children: [
    { label: 'Pod Metrics', icon: 'i-lucide-activity', to: '/pod-metrics', pageKey: 'pod-metrics', onSelect: cl },
  ]},

  // ── Cluster ────────────────────────────────────────────────────────────────
  { label: 'Cluster', icon: 'i-lucide-server', type: 'trigger', defaultOpen: false, children: [
    { label: 'Nodes',                  to: '/nodes',                    pageKey: 'nodes',                    onSelect: cl },
    { label: 'Namespaces',             to: '/namespaces',               pageKey: 'namespaces',               onSelect: cl },
    { label: 'Events',                 to: '/events',                   pageKey: 'events',                   onSelect: cl },
    { label: 'CRDs',                   to: '/customresourcedefinitions',pageKey: 'customresourcedefinitions', onSelect: cl },
    { label: 'Leases',                 to: '/leases',                   pageKey: 'leases',                   onSelect: cl },
    { label: 'Priority Classes',       to: '/priorityclasses',          pageKey: 'priorityclasses',          onSelect: cl },
    { label: 'Priority Level Configs', to: '/prioritylevelconfigurations', pageKey: 'prioritylevelconfigurations', onSelect: cl },
  ]},

  // ── Admission & Webhooks ───────────────────────────────────────────────────
  { label: 'Admission & Webhooks', icon: 'i-lucide-shield-alert', type: 'trigger', defaultOpen: false, children: [
    { label: 'Validating Webhooks',           to: '/validatingwebhookconfigurations',   pageKey: 'validatingwebhooks',               onSelect: cl },
    { label: 'Mutating Webhooks',             to: '/mutatingwebhookconfigurations',     pageKey: 'mutatingwebhooks',                 onSelect: cl },
    { label: 'Validating Admission Policies', to: '/validatingadmissionpolicies',       pageKey: 'validatingadmissionpolicies',      onSelect: cl },
    { label: 'Policy Bindings',               to: '/validatingadmissionpolicybindings', pageKey: 'validatingadmissionpolicybindings', onSelect: cl },
  ]},

  // ── Extensions ─────────────────────────────────────────────────────────────
  { label: 'Extensions', icon: 'i-lucide-plus-square', type: 'trigger', defaultOpen: false, children: [
    // App Market (Helm)
    { label: 'App Market', icon: 'i-lucide-shopping-bag', to: '/helm', pageKey: 'helm', onSelect: cl },
    // App Creator
    { label: 'App Creator', icon: 'i-lucide-rocket', type: 'trigger', defaultOpen: false, pageKey: 'appcreator', children: [
      { label: 'Applications',         to: '/appcreator',                       pageKey: 'appcreator', onSelect: cl },
      { label: 'Templates',            to: '/appcreator/templates',             pageKey: 'appcreator', onSelect: cl },
      { label: 'Git Connections',      to: '/appcreator/git-connections',       pageKey: 'appcreator', onSelect: cl },
      { label: 'Registry Connections', to: '/appcreator/registry-connections',  pageKey: 'appcreator', onSelect: cl },
    ]},
    // Federations
    { label: 'Federations', icon: 'i-lucide-globe-2', to: '/federations', pageKey: 'federations', onSelect: cl },
  ]},

  // ── Security ───────────────────────────────────────────────────────────────
  { label: 'Security', icon: 'i-lucide-shield-check', type: 'trigger', defaultOpen: false, children: [
    { label: 'Security Rules', to: '/security/rules',  pageKey: 'security',   onSelect: cl },
    { label: 'Alarms',         to: '/security/alarms', pageKey: 'security',   onSelect: cl },
    { label: 'Audit Logs',     to: '/admin/audit-logs', pageKey: 'audit-logs', onSelect: cl },
    { label: 'Vulnerability Scan', icon: 'i-lucide-bug', to: '/security/vulnerability-scan', pageKey: 'vulnerability-scan', onSelect: cl },
    { label: 'ClusterEye',         icon: 'i-lucide-scan-eye', to: '/security/cluster-eye', pageKey: 'security-eye', onSelect: cl },

  ]},


  { label: 'Backup', icon: 'i-lucide-archive', to: '/backups', pageKey: 'backups', onSelect: cl },

  // ── Reports ────────────────────────────────────────────────────────────────
  { label: 'Reports', icon: 'i-lucide-bar-chart-2', to: '/reports', pageKey: 'reports', onSelect: cl },
], [
  { label: 'Clusters',         icon: 'i-lucide-database',    to: '/clusters',    superadminOnly: true, onSelect: cl },
  { label: 'Role Management',  icon: 'i-lucide-shield-half', to: '/admin/roles', superadminOnly: true, onSelect: cl },
  { label: 'Settings',         icon: 'i-lucide-settings',    to: '/settings',    superadminOnly: true, onSelect: cl },
]]

// ── Filter nav items based on Casbin permissions + superadmin flag
function filterItems(items: NavItem[]): NavItem[] {
  return items.reduce<NavItem[]>((acc, item) => {
    // superadminOnly items: hide completely for non-superadmins
    if (item.superadminOnly && !permStore.isSuperadmin) return acc

    if (item.children) {
      const filteredChildren = filterItems(item.children)
      if (filteredChildren.length > 0) {
        acc.push({ ...item, children: filteredChildren })
      }
    } else {
      if (!item.pageKey || permStore.canAccess(item.pageKey)) {
        acc.push(item)
      }
    }
    return acc
  }, [])
}

const visibleLinks = computed(() => {
  if (!permStore.isLoaded) return [[], []]
  return ALL_NAV.map(section => filterItems(section))
})

const groups = computed(() => [
  { id: 'links', label: 'Go to', items: visibleLinks.value.flat() }
])

onMounted(async () => {
  await clusterStore.fetchClusters()
  clusterStore.initializeSelection()
  // Load pages for the initially selected cluster
  await permStore.fetchMyPages(clusterStore.selectedClusterUid ?? undefined)
})

// When the user switches clusters, reload sidebar pages for the new cluster
watch(
  () => clusterStore.selectedClusterUid,
  (newUid) => {
    permStore.fetchMyPages(newUid ?? undefined)
  }
)
</script>

<template>
  <UDashboardGroup unit="rem">
    <UDashboardSidebar
      id="default"
      v-model:open="open"
      collapsible
      resizable
      class="bg-white dark:bg-neutral-900 border-r border-gray-200 dark:border-gray-800"
      :ui="{
        container: 'flex flex-col h-full',
        header: 'sticky top-0 z-10 bg-white dark:bg-neutral-900',
        body: 'flex-1 overflow-hidden p-0',
        footer: 'sticky bottom-0 z-10 bg-white dark:bg-neutral-900 border-t border-gray-100 dark:border-gray-800'
      }"
    >

      <!-- ================= HEADER ================= -->
      <template #header="{ collapsed }">
        <div class="px-3 py-2 border-b border-gray-100 dark:border-gray-800">
          <!-- COLLAPSED -->
          <div v-if="collapsed" class="flex justify-center">
            <img src="~/assets/logo/logo.svg" class="w-6 h-6" alt="Logo" />
          </div>

          <!-- EXPANDED -->
          <div v-else class="flex flex-col gap-3">
            <div class="flex items-center gap-3">
              <img src="~/assets/logo/logo.svg" class="w-10 h-10" alt="Logo" />
              <div>
                <div class="text-sm font-semibold text-neutral-900 dark:text-neutral-100">
                  Poyraz
                </div>
                <div class="text-[10px] text-neutral-400 uppercase tracking-wider">
                  Kubernetes
                </div>
              </div>
            </div>

            
          </div>
        </div>
      </template>

      <!-- ================= BODY ================= -->
      <template #default="{ collapsed }">
        <div class="flex-1 overflow-y-auto px-4 py-1 custom-scrollbar">
          <UNavigationMenu
            :collapsed="collapsed"
            :items="visibleLinks[0]"
            orientation="vertical"
            tooltip
          />

          <div class="mt-auto pt-4">
            <div v-if="!collapsed" class="h-px bg-gray-100 dark:bg-gray-800 mb-3" />
            <UNavigationMenu
              :collapsed="collapsed"
              :items="visibleLinks[1]"
              orientation="vertical"
              tooltip
            />
          </div>
        </div>
      </template>

      <!-- ================= FOOTER ================= -->
      <template #footer="{ collapsed }">
        <div class="px-2 py-2 w-full">
          <div v-if="!collapsed" class="mb-2">
            <UDashboardSearchButton
              :collapsed="false"
              class="w-full justify-start text-neutral-500 dark:text-neutral-400"
            />
          </div>
        <div v-if="!collapsed" class="mb-2">
            <GlobalContextBar :collapsed="false" />
          </div>
          
          <UserMenu :collapsed="collapsed" />
        </div>
      </template>

    </UDashboardSidebar>

    <UDashboardSearch :groups="groups" />

    <div class="flex-1 overflow-y-auto bg-gray-50 dark:bg-gray-950">
      <slot />
    </div>

    <NotificationsSlideover />
  </UDashboardGroup>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: transparent;
}
.custom-scrollbar:hover::-webkit-scrollbar-thumb {
  background-color: rgba(156, 163, 175, 0.4);
}
</style>
