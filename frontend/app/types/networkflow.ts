// Network Flow Types

export type FlowType =
  | 'pod-to-pod'
  | 'pod-to-service'
  | 'pod-to-external'
  | 'pod-to-nodeport'
  | 'pod-to-node'
  | 'external-to-pod'
  | 'service-to-pod'
  | 'node-to-pod'
  | 'node-to-external'
  | 'external-to-node'
  | 'cni-internal'
  | 'unknown';

export interface NetworkFlow {
  id: number;
  flowId: string;
  clusterUid: string;
  flowType: FlowType | string;
  timestamp: string;
  source: SourceInfo;
  destination: DestinationInfo;
  network: NetworkInfo;
  l7?: L7Info;
  nodeName?: string;
}

export interface SourceInfo {
  podName?: string;
  namespace?: string;
  kind?: string;
  ip: string;
  port: number;
  podLabels?: Record<string, string>;
  nodeName?: string;
  ingress?: boolean;
  egress?: boolean;
  drop?: boolean;
  pass?: boolean;
}

export interface DestinationInfo {
  podName?: string;
  namespace?: string;
  kind?: string;
  ip: string;
  port: number;
  podLabels?: Record<string, string>;
  nodeName?: string;
  ingress?: boolean;
  egress?: boolean;
  drop?: boolean;
  pass?: boolean;
  service?: ServiceInfo;
}

export interface ServiceInfo {
  name: string;
  namespace: string;
  labels?: Record<string, string>;
  backendPodName?: string;
  backendPodNamespace?: string;
  backendPodLabels?: Record<string, string>;
}

export interface NetworkInfo {
  protocol: string;
  tcpFlags?: number;
  bytes?: number;
  interfaceName?: string;
  direction?: string;
}

export interface L7Info {
  protocol?: string;
  method?: string;
  host?: string;
  url?: string;
  path?: string;
  statusCode?: number;
  contentType?: string;
}

// Filter types
export interface NetworkFlowFilter {
  clusterId?: number;
  flowTypes?: FlowType[];
  sourceNamespaces?: string[];
  destinationNamespaces?: string[];
  sourcePodName?: string;
  destinationPodName?: string;
  protocols?: string[];
  sourceIp?: string;
  destinationIp?: string;
  sourcePort?: number;
  destinationPort?: number;
  startTime?: string;
  endTime?: string;
  l7Method?: string;
  l7Path?: string;
  serviceName?: string;
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortDesc?: boolean;
}

// Filter options
export interface NetworkFlowFilterOptions {
  flowTypes: string[];
  sourceNamespaces: string[];
  destinationNamespaces: string[];
  protocols: string[];
  sourcePods: string[];
  destinationPods: string[];
}

// Statistics
export interface NetworkFlowStats {
  totalFlows: number;
  totalBytes: number;
  byFlowType: StatItem[];
  byProtocol: StatItem[];
  bySourceNamespace: StatItem[];
  byDestinationNamespace: StatItem[];
  timeSeries?: TimeSeriesItem[];
}

export interface StatItem {
  key: string;
  count: number;
  bytes: number;
  percentage: number;
}

export interface TimeSeriesItem {
  timestamp: string;
  count: number;
  bytes: number;
  byFlowType?: Record<string, number>;
}

// Topology types
export interface NetworkTopology {
  nodes: TopologyNode[];
  edges: TopologyEdge[];
}

export interface TopologyNode {
  id: string;
  name: string;
  namespace?: string;
  type: 'pod' | 'service' | 'external' | 'node';
  label: string;
  connectionCount: number;
  totalBytes: number;
  group: string;
  podLabels?: Record<string, string>;
}

export interface TopologyEdge {
  id: number;
  source: string;
  target: string;
  protocol?: string;
  port?: number;
  flowCount: number;
  totalBytes: number;
  lastSeen: string;
  label: string;
  weight: number;
  isLogical?: boolean;
}

// Paginated response
export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

// Flow type colors and labels
export const FLOW_TYPE_CONFIG: Record<FlowType, { label: string; color: string; bgColor: string }> = {
  'pod-to-pod': { label: 'Pod → Pod', color: 'text-blue-600', bgColor: 'bg-blue-100' },
  'pod-to-service': { label: 'Pod → Service', color: 'text-green-600', bgColor: 'bg-green-100' },
  'pod-to-external': { label: 'Pod → External', color: 'text-orange-600', bgColor: 'bg-orange-100' },
  'pod-to-nodeport': { label: 'Pod → NodePort', color: 'text-purple-600', bgColor: 'bg-purple-100' },
  'pod-to-node': { label: 'Pod → Node', color: 'text-indigo-600', bgColor: 'bg-indigo-100' },
  'external-to-pod': { label: 'External → Pod', color: 'text-red-600', bgColor: 'bg-red-100' },
  'service-to-pod': { label: 'Service → Pod', color: 'text-teal-600', bgColor: 'bg-teal-100' },
  'node-to-pod': { label: 'Node → Pod', color: 'text-cyan-600', bgColor: 'bg-cyan-100' },
  'node-to-external': { label: 'Node → External', color: 'text-amber-600', bgColor: 'bg-amber-100' },
  'external-to-node': { label: 'External → Node', color: 'text-rose-600', bgColor: 'bg-rose-100' },
  'cni-internal': { label: 'CNI Internal', color: 'text-gray-500', bgColor: 'bg-gray-100' },
  'unknown': { label: 'Unknown', color: 'text-gray-400', bgColor: 'bg-gray-50' }
};

// Protocol colors
export const PROTOCOL_COLORS: Record<string, string> = {
  'TCP': 'text-blue-600',
  'UDP': 'text-green-600',
  'ICMP': 'text-orange-600',
  'HTTP': 'text-purple-600',
  'HTTPS': 'text-indigo-600',
  'gRPC': 'text-pink-600'
};

// Utility functions
export function formatBytes(bytes?: number): string {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i++;
  }
  return `${size.toFixed(1)} ${units[i]}`;
}

export function formatFlowDirection(flow: NetworkFlow): string {
  const src = flow.source.podName || flow.source.ip;
  const dst = flow.destination.podName || flow.destination.ip;
  return `${src}:${flow.source.port} → ${dst}:${flow.destination.port}`;
}

export function getFlowTypeLabel(flowType: FlowType): string {
  return FLOW_TYPE_CONFIG[flowType]?.label || flowType;
}

export function getFlowTypeColor(flowType: FlowType): string {
  return FLOW_TYPE_CONFIG[flowType]?.color || 'text-gray-500';
}

export function getFlowTypeBgColor(flowType: FlowType): string {
  return FLOW_TYPE_CONFIG[flowType]?.bgColor || 'bg-gray-100';
}
