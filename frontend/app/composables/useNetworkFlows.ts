import type {
  NetworkFlow,
  NetworkFlowFilter,
  NetworkFlowFilterOptions,
  NetworkFlowStats,
  NetworkTopology,
  PaginatedResponse
} from '~/types/networkflow';

export function useNetworkFlows() {
  const config = useRuntimeConfig();
  const baseUrl = config.public.apiBase || 'http://localhost:8080';
  const clusterStore = useClusterStore();

  const clusterUid = computed(() => clusterStore.selectedCluster?.uid);

  const loading = ref(false);
  const error = ref<string | null>(null);

  const flows = ref<NetworkFlow[]>([]);
  const totalFlows = ref(0);
  const totalPages = ref(0);
  const currentPage = ref(0);

  const stats = ref<NetworkFlowStats | null>(null);
  const filterOptions = ref<NetworkFlowFilterOptions | null>(null);
  const topology = ref<NetworkTopology | null>(null);

  // Get auth token (check cookie first, then localStorage)
  function getAuthHeader() {
    let token = useCookie('token').value;
    if (!token && import.meta.client) {
      token = localStorage.getItem('token');
    }
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  // Fetch flows with filters
  async function fetchFlows(filter: NetworkFlowFilter = {}) {
    if (!clusterUid.value) throw new Error('No cluster selected');

    loading.value = true;
    error.value = null;

    try {
      const params = new URLSearchParams();

      if (filter.flowTypes?.length) params.append('flowTypes', filter.flowTypes.join(','));
      if (filter.sourceNamespaces?.length) params.append('sourceNamespaces', filter.sourceNamespaces.join(','));
      if (filter.destinationNamespaces?.length) params.append('destinationNamespaces', filter.destinationNamespaces.join(','));
      if (filter.sourcePodName) params.append('sourcePodName', filter.sourcePodName);
      if (filter.destinationPodName) params.append('destinationPodName', filter.destinationPodName);
      if (filter.protocols?.length) params.append('protocols', filter.protocols.join(','));
      if (filter.sourceIp) params.append('sourceIp', filter.sourceIp);
      if (filter.destinationIp) params.append('destinationIp', filter.destinationIp);
      if (filter.sourcePort) params.append('sourcePort', String(filter.sourcePort));
      if (filter.destinationPort) params.append('destinationPort', String(filter.destinationPort));
      if (filter.startTime) params.append('startTime', filter.startTime);
      if (filter.endTime) params.append('endTime', filter.endTime);
      if (filter.l7Method) params.append('l7Method', filter.l7Method);
      if (filter.l7Path) params.append('l7Path', filter.l7Path);
      if (filter.serviceName) params.append('serviceName', filter.serviceName);
      params.append('page', String(filter.page || 0));
      params.append('pageSize', String(filter.pageSize || 50));
      params.append('sortBy', filter.sortBy || 'timestamp');
      params.append('sortDesc', String(filter.sortDesc !== false));

      const response = await $fetch<PaginatedResponse<NetworkFlow>>(
        `${baseUrl}/network/${clusterUid.value}/flows?${params.toString()}`,
        {
          headers: getAuthHeader()
        }
      );

      flows.value = response.content;
      totalFlows.value = response.totalElements;
      totalPages.value = response.totalPages;
      currentPage.value = response.number;

      return response;
    } catch (e: any) {
      error.value = e.message || 'Failed to fetch flows';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  // Search flows with POST body
  async function searchFlows(filter: NetworkFlowFilter) {
    if (!clusterUid.value) throw new Error('No cluster selected');

    loading.value = true;
    error.value = null;

    try {
      const response = await $fetch<PaginatedResponse<NetworkFlow>>(
        `${baseUrl}/network/${clusterUid.value}/flows/search`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            ...getAuthHeader()
          },
          body: filter
        }
      );

      flows.value = response.content;
      totalFlows.value = response.totalElements;
      totalPages.value = response.totalPages;
      currentPage.value = response.number;

      return response;
    } catch (e: any) {
      error.value = e.message || 'Failed to search flows';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  // Get flow statistics
  async function fetchStats(startTime?: string, endTime?: string) {
    if (!clusterUid.value) throw new Error('No cluster selected');

    loading.value = true;
    error.value = null;

    try {
      const params = new URLSearchParams();
      if (startTime) params.append('startTime', startTime);
      if (endTime) params.append('endTime', endTime);

      const response = await $fetch<NetworkFlowStats>(
        `${baseUrl}/network/${clusterUid.value}/flows/stats?${params.toString()}`,
        {
          headers: getAuthHeader()
        }
      );

      stats.value = response;
      return response;
    } catch (e: any) {
      error.value = e.message || 'Failed to fetch stats';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  // Get filter options
  async function fetchFilterOptions() {
    if (!clusterUid.value) throw new Error('No cluster selected');

    try {
      const response = await $fetch<NetworkFlowFilterOptions>(
        `${baseUrl}/network/${clusterUid.value}/flows/filter-options`,
        {
          headers: getAuthHeader()
        }
      );

      filterOptions.value = response;
      return response;
    } catch (e: any) {
      error.value = e.message || 'Failed to fetch filter options';
      throw e;
    }
  }

  // Get network topology
  async function fetchTopology(namespaces?: string[]) {
    if (!clusterUid.value) throw new Error('No cluster selected');

    loading.value = true;
    error.value = null;

    try {
      const params = new URLSearchParams();
      if (namespaces?.length) params.append('namespaces', namespaces.join(','));

      const response = await $fetch<NetworkTopology>(
        `${baseUrl}/network/${clusterUid.value}/topology?${params.toString()}`,
        {
          headers: getAuthHeader()
        }
      );

      topology.value = response;
      return response;
    } catch (e: any) {
      error.value = e.message || 'Failed to fetch topology';
      throw e;
    } finally {
      loading.value = false;
    }
  }

  // Get distinct pod policy-label values (network.policy.policy-labels) for a namespace
  async function fetchPodPolicyLabels(namespace: string): Promise<string[]> {
    if (!clusterUid.value) return [];
    if (!namespace || namespace === 'all') return [];

    try {
      const response = await $fetch<string[]>(
        `${baseUrl}/k8s/${clusterUid.value}/namespaces/${namespace}/pods/policy-labels`,
        {
          headers: getAuthHeader()
        }
      );
      return Array.isArray(response) ? response : [];
    } catch (e: any) {
      console.error('Failed to fetch pod policy labels:', e.message);
      return [];
    }
  }

  // Export flows
  async function exportFlows(startTime?: string, endTime?: string, limit: number = 1000) {
    if (!clusterUid.value) throw new Error('No cluster selected');

    try {
      const params = new URLSearchParams();
      if (startTime) params.append('startTime', startTime);
      if (endTime) params.append('endTime', endTime);
      params.append('limit', String(limit));

      const response = await $fetch<NetworkFlow[]>(
        `${baseUrl}/network/${clusterUid.value}/flows/export?${params.toString()}`,
        {
          headers: getAuthHeader()
        }
      );

      return response;
    } catch (e: any) {
      error.value = e.message || 'Failed to export flows';
      throw e;
    }
  }

  // Download flows as JSON file
  function downloadFlowsAsJson(data: NetworkFlow[], filename: string = 'network-flows.json') {
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  return {
    // State
    loading,
    error,
    flows,
    totalFlows,
    totalPages,
    currentPage,
    stats,
    filterOptions,
    topology,

    // Actions
    fetchFlows,
    searchFlows,
    fetchStats,
    fetchFilterOptions,
    fetchTopology,
    fetchPodPolicyLabels,
    exportFlows,
    downloadFlowsAsJson
  };
}
