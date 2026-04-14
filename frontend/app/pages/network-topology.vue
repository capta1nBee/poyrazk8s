<script setup lang="ts">
import * as d3 from 'd3'
import type { NetworkTopology, TopologyNode, TopologyEdge } from '~/types/networkflow'
import { formatBytes } from '~/types/networkflow'

const clusterStore = useClusterStore()
const toast = useToast()

const {
  loading,
  error,
  topology,
  fetchTopology,
  fetchPodPolicyLabels,
} = useNetworkFlows()

const selectedCluster = computed(() => clusterStore.selectedCluster)
const selectedNamespace = computed(() => clusterStore.selectedNamespace)

// Visualization refs
const svgContainer = ref<HTMLDivElement | null>(null)
const svgElement = ref<SVGSVGElement | null>(null)

// View state
const viewMode = ref<'force' | 'hierarchical'>('force')
const showLabels = ref(true)
const showEdgeLabels = ref(false)
const highlightConnections = ref(true)
const showServiceClustering = ref(false)
const nodeSize = ref<'connections' | 'bytes' | 'fixed'>('connections')
const colorBy = ref<'type' | 'namespace' | 'group'>('type')

// Filter state
const selectedNodeTypes = ref<string[]>(['pod', 'service', 'external', 'node'])
const minConnections = ref(0)

// Pod label filter state
const selectedPodLabelKey = ref<string>('')
const podPolicyLabels = ref<string[]>([])

// Selected node for details
const selectedNode = ref<TopologyNode | null>(null)
const showNodeModal = ref(false)
const hoveredNode = ref<TopologyNode | null>(null)

// Zoom state
const currentZoom = ref(1)
const zoomBehavior = ref<d3.ZoomBehavior<SVGSVGElement, unknown> | null>(null)

// Node type options
const nodeTypeOptions = [
  { label: 'Pods', value: 'pod', icon: 'i-lucide-box', color: '#60A5FA' },
  { label: 'Services', value: 'service', icon: 'i-lucide-server', color: '#34D399' },
  { label: 'External', value: 'external', icon: 'i-lucide-globe', color: '#FBBF24' },
  { label: 'Nodes', value: 'node', icon: 'i-lucide-hard-drive', color: '#A78BFA' }
]

// Color scales
const typeColorScale = d3.scaleOrdinal<string>()
  .domain(['pod', 'service', 'external', 'node'])
  .range(['#60A5FA', '#34D399', '#FBBF24', '#A78BFA'])

const namespaceColorScale = d3.scaleOrdinal(d3.schemeTableau10)

// Traffic color scale (Heatmap)
const trafficColorScale = d3.scaleSequential(d3.interpolateTurbo)

// Pod label dropdown items
const podLabelDropdownItems = computed(() => {
  const allOption = [{
    label: 'All Labels',
    icon: selectedPodLabelKey.value === '' ? 'i-lucide-check' : undefined,
    onSelect: () => {
      selectedPodLabelKey.value = ''
    }
  }]

  if (!podPolicyLabels.value.length) return [allOption]

  const valueItems = podPolicyLabels.value.map(val => ({
    label: val,
    icon: selectedPodLabelKey.value === val ? 'i-lucide-check' : undefined,
    onSelect: () => {
      selectedPodLabelKey.value = val
    }
  }))

  return [allOption, valueItems]
})

// Display label for button
const podLabelButtonLabel = computed(() => {
  if (!selectedPodLabelKey.value) return 'Pod Labels'
  return selectedPodLabelKey.value
})

// Computed filtered topology
const filteredTopology = computed(() => {
  if (!topology.value) return { nodes: [], edges: [] }

  let filteredNodes = topology.value.nodes.filter(node => {
    if (!selectedNodeTypes.value.includes(node.type)) return false
    if (node.connectionCount < minConnections.value) return false
    return true
  })

  // Apply pod label filter: show matching pods AND their 1-hop connected neighbors
  if (selectedPodLabelKey.value) {
    const matchingPodIds = new Set<string>()
    filteredNodes.forEach(node => {
      if (node.type === 'pod' && node.podLabels) {
        for (const [key, value] of Object.entries(node.podLabels)) {
          if (`${key}=${value}` === selectedPodLabelKey.value) {
            matchingPodIds.add(node.id)
            break
          }
        }
      }
    })

    // Find all edges connected to matching pods
    const validEdges = topology.value.edges.filter(edge => {
      const sourceId = typeof edge.source === 'object' ? (edge.source as any).id : edge.source
      const targetId = typeof edge.target === 'object' ? (edge.target as any).id : edge.target
      return matchingPodIds.has(sourceId) || matchingPodIds.has(targetId)
    })

    // Keep nodes involved in these edges, plus the matching pods themselves
    const nodesInNetwork = new Set<string>(matchingPodIds)
    validEdges.forEach(edge => {
      const sourceId = typeof edge.source === 'object' ? (edge.source as any).id : edge.source
      const targetId = typeof edge.target === 'object' ? (edge.target as any).id : edge.target
      nodesInNetwork.add(sourceId)
      nodesInNetwork.add(targetId)
    })

    // ─── 2-Hop Expansion for Service Backends ───
    // If we included a Service node, we should also include its logical backend Pods
    topology.value.nodes.forEach(node => {
      if (node.type === 'service' && nodesInNetwork.has(node.id)) {
        topology.value.edges.forEach(edge => {
          if (edge.isLogical) {
            const sourceId = typeof edge.source === 'object' ? (edge.source as any).id : edge.source
            const targetId = typeof edge.target === 'object' ? (edge.target as any).id : edge.target
            if (sourceId === node.id) {
              nodesInNetwork.add(targetId)
            }
          }
        })
      }
    })

    filteredNodes = filteredNodes.filter(node => nodesInNetwork.has(node.id))
  }

  const nodeIds = new Set(filteredNodes.map(n => n.id))
  
  // Create fresh copies of edges to prevent D3 mutation issues across re-renders
  const filteredEdges = topology.value.edges
    .filter(edge => {
      const sourceId = typeof edge.source === 'object' ? (edge.source as any).id : edge.source
      const targetId = typeof edge.target === 'object' ? (edge.target as any).id : edge.target
      return nodeIds.has(sourceId) && nodeIds.has(targetId)
    })
    .map(edge => ({
      ...edge,
      // Reset source/target to string IDs so D3 simulation can link them fresh
      source: typeof edge.source === 'object' ? (edge.source as any).id : edge.source,
      target: typeof edge.target === 'object' ? (edge.target as any).id : edge.target
    }))

  const freshNodes = filteredNodes.map(node => ({ ...node }))

  // ─── Service Pod Clustering logic ───
  if (showServiceClustering.value) {
    const serviceToPods = new Map<string, string[]>()
    const podToService = new Map<string, string>()

    // Identify which pods belong to which service using logical edges
    topology.value.edges.forEach(edge => {
      if (edge.isLogical) {
        const sId = typeof edge.source === 'object' ? (edge.source as any).id : edge.source
        const tId = typeof edge.target === 'object' ? (edge.target as any).id : edge.target
        const serviceNode = topology.value?.nodes.find(n => n.id === sId)
        if (serviceNode?.type === 'service') {
          if (!serviceToPods.has(sId)) serviceToPods.set(sId, [])
          serviceToPods.get(sId)?.push(tId)
          podToService.set(tId, sId)
        }
      }
    })

    // Create clustered nodes
    const finalNodes: any[] = []
    const clusteredPodIds = new Set<string>()

    freshNodes.forEach(node => {
      if (node.type === 'service' && serviceToPods.has(node.id)) {
        const childPodIds = serviceToPods.get(node.id) || []
        const currentNamespacePods = childPodIds.filter(id => nodeIds.has(id))
        
        if (currentNamespacePods.length > 0) {
          // Merge this service with its pods
          node.label = `${node.name} (${currentNamespacePods.length} pods)`
          node.isCluster = true
          node.childPodIds = currentNamespacePods
          // Sum up connections and bytes from children
          currentNamespacePods.forEach(pid => {
            const pNode = freshNodes.find(n => n.id === pid)
            if (pNode) {
              node.connectionCount += pNode.connectionCount
              node.totalBytes += pNode.totalBytes
              clusteredPodIds.add(pid)
            }
          })
          finalNodes.push(node)
        } else {
          finalNodes.push(node)
        }
      } else if (node.type !== 'pod' || !podToService.has(node.id)) {
        finalNodes.push(node)
      } else if (!podToService.has(node.id)) {
        finalNodes.push(node)
      }
    })

    // Remap edges to point to cluster nodes
    const finalEdges = filteredEdges.map(edge => {
      const sId = edge.source as string
      const tId = edge.target as string
      return {
        ...edge,
        source: podToService.get(sId) || sId,
        target: podToService.get(tId) || tId
      }
    }).filter(e => e.source !== e.target) // Remove self-loops created by clustering

    return { nodes: finalNodes, edges: finalEdges }
  }

  return { nodes: freshNodes, edges: filteredEdges }
})

// Stats
const topologyStats = computed(() => {
  if (!filteredTopology.value) return null

  const nodes = filteredTopology.value.nodes
  const edges = filteredTopology.value.edges

  const byType = nodeTypeOptions.map(type => ({
    type: type.value,
    label: type.label,
    count: nodes.filter(n => n.type === type.value).length,
    color: type.color
  }))

  const totalBytes = edges.reduce((sum, e) => sum + (e.totalBytes || 0), 0)
  const totalFlows = edges.reduce((sum, e) => sum + (e.flowCount || 0), 0)

  return {
    nodeCount: nodes.length,
    edgeCount: edges.length,
    byType,
    totalBytes,
    totalFlows
  }
})

// Fetch data
const loadData = async () => {
  if (!selectedCluster.value?.uid) return

  try {
    const namespaceFilter = selectedNamespace.value && selectedNamespace.value !== 'all'
      ? [selectedNamespace.value]
      : undefined

    await fetchTopology(namespaceFilter)

    // Load pod policy-labels for current namespace
    if (namespaceFilter?.[0]) {
      podPolicyLabels.value = await fetchPodPolicyLabels(namespaceFilter[0])
    } else {
      podPolicyLabels.value = []
    }
    // Reset label selection if the previously selected label is no longer available
    if (selectedPodLabelKey.value && !podPolicyLabels.value.includes(selectedPodLabelKey.value)) {
      selectedPodLabelKey.value = ''
    }

    nextTick(() => {
      if (topology.value) {
        renderTopology()
      }
    })
  } catch (e: any) {
    toast.add({
      title: 'Failed to load topology',
      description: e.message,
      color: 'red'
    })
  }
}

// Render D3 topology
const renderTopology = () => {
  if (!svgContainer.value || !filteredTopology.value) return

  const container = svgContainer.value
  const width = container.clientWidth
  const height = container.clientHeight || 700

  // Clear previous
  d3.select(container).selectAll('svg').remove()

  const data = filteredTopology.value
  if (data.nodes.length === 0) return

  // Create SVG
  const svg = d3.select(container)
    .append('svg')
    .attr('width', '100%')
    .attr('height', '100%')
    .attr('viewBox', [0, 0, width, height])
    .attr('class', 'network-topology-svg')
    .attr('font-family', 'Inter, system-ui, sans-serif')
    .attr('font-size', '12px')

  svgElement.value = svg.node()

  // ─── Defs ───
  const defs = svg.append('defs')

  // Glow filter
  const glowFilter = defs.append('filter')
    .attr('id', 'glow')
    .attr('x', '-50%').attr('y', '-50%')
    .attr('width', '200%').attr('height', '200%')
  glowFilter.append('feGaussianBlur').attr('stdDeviation', '4').attr('result', 'coloredBlur')
  const feMerge = glowFilter.append('feMerge')
  feMerge.append('feMergeNode').attr('in', 'coloredBlur')
  feMerge.append('feMergeNode').attr('in', 'SourceGraphic')

  // Drop shadow filter for nodes
  const shadowFilter = defs.append('filter')
    .attr('id', 'shadow')
    .attr('x', '-30%').attr('y', '-30%')
    .attr('width', '160%').attr('height', '160%')
  shadowFilter.append('feDropShadow')
    .attr('dx', '0').attr('dy', '2')
    .attr('stdDeviation', '3')
    .attr('flood-color', 'rgba(0,0,0,0.4)')

  // ─── Arrow markers per node type ───
  const arrowDefs = [
    { id: 'arrow-pod',      color: '#60A5FA' },
    { id: 'arrow-service',  color: '#34D399' },
    { id: 'arrow-external', color: '#FBBF24' },
    { id: 'arrow-node',     color: '#A78BFA' },
    { id: 'arrow-default',  color: '#94a3b8' },
  ]

  arrowDefs.forEach(({ id, color }) => {
    defs.append('marker')
      .attr('id', id)
      .attr('viewBox', '0 -5 10 10')
      .attr('refX', 22)  // Brought closer to node edge
      .attr('refY', 0)
      .attr('markerWidth', 7)
      .attr('markerHeight', 7)
      .attr('markerUnits', 'userSpaceOnUse')
      .attr('orient', 'auto-start-reverse')
      .append('path')
      .attr('d', 'M0,-5L10,0L0,5L2,0Z') // Sharper swept-back arrow
      .attr('fill', color)
      .attr('fill-opacity', 0.95)
  })

  // ─── Edge gradient helper ───
  const edgeColorForTarget = (d: any): string => {
    const targetId = typeof d.target === 'object' ? d.target.id : d.target
    const targetNode = data.nodes.find(n => n.id === targetId)
    if (!targetNode) return '#94a3b8'
    return typeColorScale(targetNode.type)
  }

  const markerIdForTarget = (d: any): string => {
    const targetId = typeof d.target === 'object' ? d.target.id : d.target
    const targetNode = data.nodes.find(n => n.id === targetId)
    if (!targetNode) return 'arrow-default'
    return `arrow-${targetNode.type}`
  }

  // Create container group for zoom/pan
  const g = svg.append('g').attr('class', 'topology-container')

  // Setup zoom
  const zoom = d3.zoom<SVGSVGElement, unknown>()
    .scaleExtent([0.1, 4])
    .on('zoom', (event) => {
      g.attr('transform', event.transform)
      currentZoom.value = event.transform.k
    })

  svg.call(zoom)
  zoomBehavior.value = zoom

  // ─── Helpers ───
  const getNodeRadius = (d: TopologyNode) => {
    if (nodeSize.value === 'fixed') return 22
    if (nodeSize.value === 'bytes') return Math.max(16, Math.min(42, Math.log10(d.totalBytes + 1) * 5.5))
    return Math.max(16, Math.min(42, 12 + d.connectionCount * 2))
  }

  const getNodeColor = (d: TopologyNode) => {
    if (colorBy.value === 'type') return typeColorScale(d.type)
    if (colorBy.value === 'namespace') return namespaceColorScale(d.namespace || 'default')
    return typeColorScale(d.type)
  }

  // Prepare node & edge data
  const nodes: (TopologyNode & { x?: number; y?: number; fx?: number | null; fy?: number | null })[] =
    data.nodes.map(d => ({ ...d }))

  // ─── Edge Bundling (Optimize and Group Edges by Source-Target) ───
  const groupedEdgesMap = new Map()
  data.edges.forEach(e => {
    // Generate a direction-agnostic generic key pair for multi-edge offset indices
    // but keep direction-specific grouping for actual bundling 
    const sourceId = typeof e.source === 'object' ? (e.source as any).id : e.source
    const targetId = typeof e.target === 'object' ? (e.target as any).id : e.target
    const key = `${sourceId}::${targetId}`
    
    if (!groupedEdgesMap.has(key)) {
      groupedEdgesMap.set(key, {
        source: sourceId,
        target: targetId,
        flowCount: 0,
        totalBytes: 0,
        bundleCount: 0,
        isLogical: e.isLogical
      })
    }
    const bundle = groupedEdgesMap.get(key)
    bundle.flowCount += (e.flowCount || 0)
    bundle.totalBytes += (e.totalBytes || 0)
    bundle.bundleCount += 1
    if (e.isLogical) bundle.isLogical = true
  })

  let edges = Array.from(groupedEdgesMap.values()).map(d => ({
    ...d,
    source: d.source,
    target: d.target,
    index: 0
  }))

  // ─── Multi-edge Offset Pre-calculation ───
  const edgeIndexTracker = new Map()
  edges.forEach(e => {
    const sId = typeof e.source === 'object' ? (e.source as any).id : e.source
    const tId = typeof e.target === 'object' ? (e.target as any).id : e.target
    // Sort pair to get the same index key regardless of direction
    const pairKey = [sId, tId].sort().join('::')
    edgeIndexTracker.set(pairKey, (edgeIndexTracker.get(pairKey) || 0) + 1)
    e.index = edgeIndexTracker.get(pairKey)
  })

  // ─── Adjacency Map for O(1) hover highlighting ───
  const adjacencyMap = new Map<string, Set<string>>()
  edges.forEach(e => {
    const sId = typeof e.source === 'object' ? (e.source as any).id : e.source
    const tId = typeof e.target === 'object' ? (e.target as any).id : e.target
    if (!adjacencyMap.has(sId)) adjacencyMap.set(sId, new Set())
    if (!adjacencyMap.has(tId)) adjacencyMap.set(tId, new Set())
    adjacencyMap.get(sId)?.add(tId)
    adjacencyMap.get(tId)?.add(sId)
  })

  // ─── Flow Scales (Post-Bundling) ───
  const maxFlow = d3.max(edges, (e: any) => e.flowCount) || 1
  const edgeWidthScale = d3.scaleLinear().domain([0, maxFlow]).range([1.5, 6]).clamp(true)
  trafficColorScale.domain([0, maxFlow])

  // Force simulation - compact and stable configuration
  const simulation = d3.forceSimulation(nodes as any)
    .alphaDecay(0.05)
    .velocityDecay(0.4)
    .force('link', d3.forceLink(edges as any)
      .id((d: any) => d.id)
      .distance((d: any) => 80 + Math.sqrt(d.flowCount || 1))
      .strength(0.6))
    .force('charge', d3.forceManyBody().strength(-220))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collision', d3.forceCollide()
      .radius((d: any) => getNodeRadius(d) + 8)
      .iterations(2))
    .force('y', d3.forceY((d: any) => {
      if (viewMode.value !== 'hierarchical') return height / 2
      if (d.type === 'external') return height * 0.15
      if (d.type === 'service') return height * 0.45
      if (d.type === 'pod') return height * 0.85
      return height / 2
    }).strength(viewMode.value === 'hierarchical' ? 0.7 : 0))

  simulation.on('end', () => {
    simulation.stop()
  })

  // ─── Draw edges ───
  const edgeGroup = g.append('g').attr('class', 'edges')

  const edge = edgeGroup.selectAll('g')
    .data(edges)
    .join('g')
    .attr('class', 'edge-group')

  const edgeLine = edge.append('path')
    .attr('class', 'edge-line')
    .attr('fill', 'none')
    .attr('stroke', (d: any) => d.isLogical ? '#94a3b8' : trafficColorScale(d.flowCount || 0))
    .attr('stroke-width', (d: any) => d.isLogical ? 2 : edgeWidthScale(d.flowCount || 0))
    .attr('stroke-opacity', (d: any) => {
      if (d.isLogical) return 0.55
      return Math.max(0.15, 0.7 - (edges.length / 500))
    })
    .attr('stroke-dasharray', (d: any) => d.isLogical ? '6,4' : 'none')
    .attr('stroke-linecap', 'round')
    .attr('marker-end', (d: any) => `url(#${markerIdForTarget(d)})`)

  // Edge labels
  const edgeLabel = edge.append('text')
    .attr('class', 'edge-label')
    .attr('font-size', '10px')
    .attr('font-family', 'Inter, ui-sans-serif, sans-serif')
    .attr('fill', '#94a3b8')
    .attr('text-anchor', 'middle')
    .attr('dy', -6)
    .style('opacity', showEdgeLabels.value ? 1 : 0)
    .text((d: any) => {
      if (d.isLogical) return 'backend'
      return d.bundleCount > 1 
        ? `${d.bundleCount} connections` 
        : `${d.flowCount} flows · ${formatBytes(d.totalBytes)}`
    })

  // ─── Draw nodes ───
  const nodeGroup = g.append('g').attr('class', 'nodes')

  const node = nodeGroup.selectAll('g')
    .data(nodes)
    .join('g')
    .attr('class', 'node-group')
    .attr('cursor', 'pointer')
    .call(d3.drag<any, any>()
      .on('start', (event, d) => {
        if (!event.active) simulation.alphaTarget(0.3).restart()
        d.fx = d.x
        d.fy = d.y
      })
      .on('drag', (event, d) => {
        d.fx = event.x
        d.fy = event.y
      })
      .on('end', (event, d) => {
        if (!event.active) simulation.alphaTarget(0)
        d.fx = null
        d.fy = null
      }))

  // Outer ring (pulse/glow indicator)
  node.append('circle')
    .attr('class', 'node-ring')
    .attr('r', (d: any) => getNodeRadius(d) + 6)
    .attr('fill', 'none')
    .attr('stroke', (d: any) => getNodeColor(d))
    .attr('stroke-width', 1.5)
    .attr('stroke-opacity', 0.25)
    .attr('stroke-dasharray', '4 3')

  // Glow halo (shown on hover via CSS)
  node.append('circle')
    .attr('class', 'node-glow')
    .attr('r', (d: any) => getNodeRadius(d) + 8)
    .attr('fill', (d: any) => getNodeColor(d))
    .attr('fill-opacity', 0)

  // Main circle with gradient-like appearance using two circles
  node.append('circle')
    .attr('class', 'node-bg')
    .attr('r', (d: any) => getNodeRadius(d))
    .attr('fill', (d: any) => getNodeColor(d))
    .attr('fill-opacity', 0.15)
    .attr('stroke', (d: any) => getNodeColor(d))
    .attr('stroke-width', 2.5)
    .attr('filter', 'url(#shadow)')

  node.append('circle')
    .attr('class', 'node-circle')
    .attr('r', (d: any) => getNodeRadius(d) - 4)
    .attr('fill', (d: any) => getNodeColor(d))
    .attr('fill-opacity', 0.9)

  // Node icon
  node.append('text')
    .attr('class', 'node-icon')
    .attr('font-size', '14px')
    .attr('fill', '#fff')
    .attr('text-anchor', 'middle')
    .attr('dominant-baseline', 'central')
    .text((d: any) => {
      const icons: Record<string, string> = {
        'pod': '⬡',
        'service': '◎',
        'external': '◉',
        'node': '▣'
      }
      return icons[d.type] || '●'
    })

  // Node label
  node.append('text')
    .attr('class', 'node-label')
    .attr('font-size', '11px')
    .attr('font-weight', '600')
    .attr('font-family', 'Inter, ui-sans-serif, sans-serif')
    .attr('fill', '#e2e8f0')
    .attr('text-anchor', 'middle')
    .attr('dy', (d: any) => getNodeRadius(d) + 16)
    .style('opacity', showLabels.value ? 1 : 0)
    .text((d: any) => d.name.length > 22 ? d.name.substring(0, 20) + '…' : d.name)

  // Namespace badge
  node.append('text')
    .attr('class', 'node-namespace')
    .attr('font-size', '9px')
    .attr('font-family', 'Inter, ui-sans-serif, sans-serif')
    .attr('fill', '#94a3b8')
    .attr('text-anchor', 'middle')
    .attr('dy', (d: any) => getNodeRadius(d) + 28)
    .style('opacity', showLabels.value ? 0.8 : 0)
    .text((d: any) => d.namespace || '')

  // ─── Interactions ───
  node
    .on('mouseover', function (event, d: any) {
      hoveredNode.value = d

      if (highlightConnections.value) {
        node.style('opacity', 0.25)
        edge.style('opacity', 0.08)

        const connectedNodeIds = adjacencyMap.get(d.id) || new Set<string>()
        const reachableIds = new Set(connectedNodeIds)
        reachableIds.add(d.id)

        node.filter((n: any) => reachableIds.has(n.id)).style('opacity', 1)
        edge.filter((e: any) => {
          const sourceId = typeof e.source === 'object' ? e.source.id : e.source
          const targetId = typeof e.target === 'object' ? e.target.id : e.target
          return sourceId === d.id || targetId === d.id
        }).style('opacity', 1)

        d3.select(this).select('.node-glow').attr('fill-opacity', 0.2)
        d3.select(this).select('.node-ring').attr('stroke-opacity', 0.7)
      }
    })
    .on('mouseout', function () {
      hoveredNode.value = null

      if (highlightConnections.value) {
        node.style('opacity', 1)
        edge.style('opacity', 1)
        d3.select(this).select('.node-glow').attr('fill-opacity', 0)
        d3.select(this).select('.node-ring').attr('stroke-opacity', 0.25)
      }
    })
    .on('click', (event, d: any) => {
      selectedNode.value = d
      showNodeModal.value = true
    })

  // ─── Tick ───
  simulation.on('tick', () => {
    edgeLine.attr('d', (d: any) => {
      const sx = d.source.x, sy = d.source.y
      const tx = d.target.x, ty = d.target.y
      
      const r = getNodeRadius(d.target)
      const dx = tx - sx, dy = ty - sy
      const dist = Math.sqrt(dx * dx + dy * dy)
      
      // Calculate terminal point at node's edge to prevent arrow overlap
      const offsetX = (dx / dist) * r
      const offsetY = (dy / dist) * r
      
      const finalTx = tx - offsetX
      const finalTy = ty - offsetY

      if (d.isLogical) {
        return `M${sx},${sy}L${finalTx},${finalTy}`
      }

      // Dynamic arc offset depending on how many edges exist between nodes
      const dr = dist * (1 + (d.index || 1) * 0.15)
      return `M${sx},${sy}A${dr},${dr} 0 0,1 ${finalTx},${finalTy}`
    })

    edgeLabel.attr('transform', (d: any) => {
      const x = (d.source.x + d.target.x) / 2
      const y = (d.source.y + d.target.y) / 2
      return `translate(${x},${y})`
    })

    node.attr('transform', (d: any) => `translate(${d.x},${d.y})`)
  })

  // Initial zoom to fit
  setTimeout(() => {
    const bounds = g.node()?.getBBox()
    if (bounds && bounds.width > 0) {
      const scale = Math.min(
        0.85 / Math.max(bounds.width / width, bounds.height / height),
        2
      )
      const midX = bounds.x + bounds.width / 2
      const midY = bounds.y + bounds.height / 2
      const translate: [number, number] = [width / 2 - scale * midX, height / 2 - scale * midY]

      svg.transition()
        .duration(800)
        .call(zoom.transform, d3.zoomIdentity.translate(translate[0], translate[1]).scale(scale))
    }
  }, 600)
}

// Zoom controls
const zoomIn = () => {
  if (svgElement.value && zoomBehavior.value) {
    d3.select(svgElement.value).transition().duration(300).call(zoomBehavior.value.scaleBy, 1.3)
  }
}

const zoomOut = () => {
  if (svgElement.value && zoomBehavior.value) {
    d3.select(svgElement.value).transition().duration(300).call(zoomBehavior.value.scaleBy, 0.77)
  }
}

const resetZoom = () => {
  if (svgElement.value && zoomBehavior.value) {
    d3.select(svgElement.value).transition().duration(500).call(zoomBehavior.value.transform, d3.zoomIdentity)
  }
}

// ─── Watchers ───
watch([selectedCluster], () => {
  loadData()
}, { immediate: true })

watch([selectedNamespace], () => {
  loadData()
})

watch([selectedNodeTypes, minConnections, viewMode, nodeSize, colorBy, selectedPodLabelKey, showServiceClustering], () => {
  nextTick(() => renderTopology())
})

watch([showLabels, showEdgeLabels], () => {
  if (svgContainer.value) {
    d3.select(svgContainer.value)
      .selectAll('.node-label, .node-namespace')
      .style('opacity', showLabels.value ? 1 : 0)

    d3.select(svgContainer.value)
      .selectAll('.edge-label')
      .style('opacity', showEdgeLabels.value ? 1 : 0)
  }
})

// Resize handler
onMounted(() => {
  const handleResize = () => {
    if (topology.value) renderTopology()
  }
  window.addEventListener('resize', handleResize)
  onUnmounted(() => window.removeEventListener('resize', handleResize))
})
</script>

<template>
  <UDashboardPanel id="network-topology">
    <template #header>
      <UDashboardNavbar title="Network Topology">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="primary"
            variant="soft"
            :loading="loading"
            @click="loadData"
          >
            Refresh
          </UButton>
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar class="border-b border-gray-200 dark:border-gray-800">
        <template #left>
          <div class="flex items-center gap-2 flex-wrap">
            <!-- Namespace Selector (T3: show all namespaces unfiltered) -->
            <NamespaceSelector for-page />

            <div class="h-6 w-px bg-gray-200 dark:bg-gray-700" />

            <!-- Pod Label Filter -->
            <UDropdownMenu
              :items="podLabelDropdownItems"
              :ui="{ content: 'max-h-72 overflow-y-auto min-w-44' }"
            >
              <UButton
                color="neutral"
                variant="soft"
                icon="i-lucide-tag"
                trailing-icon="i-lucide-chevron-down"
                size="sm"
              >
                {{ podLabelButtonLabel }}
                <UBadge
                  v-if="selectedPodLabelKey"
                  color="primary"
                  size="xs"
                  class="ml-1"
                >
                  1
                </UBadge>
              </UButton>
            </UDropdownMenu>

            <div class="h-6 w-px bg-gray-200 dark:bg-gray-700" />

            <!-- Node Type Filter -->
            <UPopover>
              <UButton color="neutral" variant="soft" icon="i-lucide-filter" size="sm">
                Node Types
                <UBadge v-if="selectedNodeTypes.length < 4" color="primary" size="xs" class="ml-1">
                  {{ selectedNodeTypes.length }}
                </UBadge>
              </UButton>
              <template #content>
                <div class="p-3 space-y-2 w-48">
                  <div v-for="type in nodeTypeOptions" :key="type.value" class="flex items-center gap-2">
                    <UCheckbox
                      :model-value="selectedNodeTypes.includes(type.value)"
                      @update:model-value="(v: boolean) => {
                        if (v) selectedNodeTypes.push(type.value)
                        else selectedNodeTypes = selectedNodeTypes.filter(t => t !== type.value)
                      }"
                    />
                    <div class="flex items-center gap-2">
                      <div class="w-3 h-3 rounded-full ring-1 ring-white/20" :style="{ backgroundColor: type.color }" />
                      <span class="text-sm">{{ type.label }}</span>
                    </div>
                  </div>
                </div>
              </template>
            </UPopover>

            <div class="h-6 w-px bg-gray-200 dark:bg-gray-700" />

            <!-- View Options -->
            <UPopover>
              <UButton color="neutral" variant="soft" icon="i-lucide-settings-2" size="sm">
                View Options
              </UButton>
              <template #content>
                <div class="p-4 space-y-4 w-64">
                  <!-- Node Size -->
                  <div>
                    <label class="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Node Size By</label>
                    <div class="flex gap-1.5">
                      <UButton
                        size="xs"
                        :color="nodeSize === 'connections' ? 'primary' : 'neutral'"
                        :variant="nodeSize === 'connections' ? 'solid' : 'soft'"
                        @click="nodeSize = 'connections'"
                      >
                        Connections
                      </UButton>
                      <UButton
                        size="xs"
                        :color="nodeSize === 'bytes' ? 'primary' : 'neutral'"
                        :variant="nodeSize === 'bytes' ? 'solid' : 'soft'"
                        @click="nodeSize = 'bytes'"
                      >
                        Traffic
                      </UButton>
                      <UButton
                        size="xs"
                        :color="nodeSize === 'fixed' ? 'primary' : 'neutral'"
                        :variant="nodeSize === 'fixed' ? 'solid' : 'soft'"
                        @click="nodeSize = 'fixed'"
                      >
                        Fixed
                      </UButton>
                    </div>
                  </div>

                  <!-- Color By -->
                  <div>
                    <label class="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Color By</label>
                    <div class="flex gap-1.5">
                      <UButton
                        size="xs"
                        :color="colorBy === 'type' ? 'primary' : 'neutral'"
                        :variant="colorBy === 'type' ? 'solid' : 'soft'"
                        @click="colorBy = 'type'"
                      >
                        Type
                      </UButton>
                      <UButton
                        size="xs"
                        :color="colorBy === 'namespace' ? 'primary' : 'neutral'"
                        :variant="colorBy === 'namespace' ? 'solid' : 'soft'"
                        @click="colorBy = 'namespace'"
                      >
                        Namespace
                      </UButton>
                    </div>
                  </div>

                  <!-- Toggles -->
                  <div class="space-y-2.5">
                    <UCheckbox v-model="showLabels" label="Show Node Labels" />
                    <UCheckbox v-model="showEdgeLabels" label="Show Edge Labels" />
                    <UCheckbox v-model="highlightConnections" label="Highlight on Hover" />
                    <UCheckbox v-model="showServiceClustering" label="Service Pod Clustering" />
                  </div>

                  <!-- Min Connections -->
                  <div>
                    <label class="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
                      Min Connections: <span class="text-primary font-bold">{{ minConnections }}</span>
                    </label>
                    <input
                      v-model.number="minConnections"
                      type="range"
                      min="0"
                      max="20"
                      class="w-full accent-primary"
                    />
                  </div>
                </div>
              </template>
            </UPopover>
          </div>
        </template>

        <template #right>
          <!-- Stats -->
          <div v-if="topologyStats" class="flex items-center gap-3 text-sm">
            <div class="flex items-center gap-1.5 bg-blue-500/10 dark:bg-blue-500/20 px-2.5 py-1 rounded-full">
              <UIcon name="i-lucide-circle-dot" class="text-blue-400 w-3.5 h-3.5" />
              <span class="font-bold text-blue-500 dark:text-blue-400">{{ topologyStats.nodeCount }}</span>
              <span class="text-gray-500 text-xs">nodes</span>
            </div>
            <div class="flex items-center gap-1.5 bg-slate-500/10 dark:bg-slate-500/20 px-2.5 py-1 rounded-full">
              <UIcon name="i-lucide-arrow-right" class="text-slate-400 w-3.5 h-3.5" />
              <span class="font-bold text-slate-600 dark:text-slate-300">{{ topologyStats.edgeCount }}</span>
              <span class="text-gray-500 text-xs">edges</span>
            </div>
            <div class="flex items-center gap-1.5 bg-emerald-500/10 dark:bg-emerald-500/20 px-2.5 py-1 rounded-full">
              <UIcon name="i-lucide-activity" class="text-emerald-400 w-3.5 h-3.5" />
              <span class="font-bold text-emerald-600 dark:text-emerald-400">{{ formatBytes(topologyStats.totalBytes) }}</span>
            </div>
            <div class="flex items-center gap-1.5 bg-violet-500/10 dark:bg-violet-500/20 px-2.5 py-1 rounded-full">
              <UIcon name="i-lucide-zap" class="text-violet-400 w-3.5 h-3.5" />
              <span class="font-bold text-violet-600 dark:text-violet-400">{{ topologyStats.totalFlows }}</span>
              <span class="text-gray-500 text-xs">flows</span>
            </div>
          </div>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="relative w-full h-full min-h-[700px] topology-bg">
        <!-- Grid background dots (decorative) -->
        <div class="absolute inset-0 topology-grid pointer-events-none" />

        <!-- Loading -->
        <div v-if="loading" class="absolute inset-0 flex items-center justify-center bg-white/60 dark:bg-slate-950/70 z-20 backdrop-blur-sm">
          <div class="flex flex-col items-center gap-4">
            <div class="relative">
              <UIcon name="i-lucide-loader-2" class="animate-spin h-10 w-10 text-primary" />
            </div>
            <p class="text-sm text-gray-400 font-medium tracking-wide">Building topology graph…</p>
          </div>
        </div>

        <!-- Empty State -->
        <div v-else-if="!topology || topology.nodes.length === 0" class="absolute inset-0 flex items-center justify-center">
          <div class="flex flex-col items-center gap-4 text-center">
            <div class="p-6 bg-gray-100 dark:bg-slate-800 rounded-2xl ring-1 ring-gray-200 dark:ring-slate-700">
              <UIcon name="i-lucide-network" class="h-12 w-12 text-gray-400" />
            </div>
            <div>
              <p class="text-xl font-semibold text-gray-900 dark:text-gray-100">No topology data</p>
              <p class="text-sm text-gray-500 mt-1">Select a namespace or wait for network activity</p>
            </div>
            <UButton color="primary" icon="i-lucide-refresh-cw" @click="loadData">
              Refresh
            </UButton>
          </div>
        </div>

        <!-- Topology Visualization -->
        <div
          ref="svgContainer"
          class="absolute inset-0 w-full h-full"
        />

        <!-- Zoom Controls -->
        <div class="absolute bottom-6 right-6 flex flex-col gap-1.5 z-10">
          <UButton
            icon="i-lucide-plus"
            color="neutral"
            variant="solid"
            size="sm"
            class="rounded-xl shadow-xl ring-1 ring-gray-200 dark:ring-slate-700 bg-white dark:bg-slate-800"
            @click="zoomIn"
          />
          <UButton
            icon="i-lucide-minus"
            color="neutral"
            variant="solid"
            size="sm"
            class="rounded-xl shadow-xl ring-1 ring-gray-200 dark:ring-slate-700 bg-white dark:bg-slate-800"
            @click="zoomOut"
          />
          <UButton
            icon="i-lucide-maximize-2"
            color="neutral"
            variant="solid"
            size="sm"
            class="rounded-xl shadow-xl ring-1 ring-gray-200 dark:ring-slate-700 bg-white dark:bg-slate-800"
            @click="resetZoom"
          />
        </div>

        <!-- Zoom Level -->
        <div class="absolute bottom-6 left-6 glass-card rounded-xl px-3.5 py-2 text-sm z-10 shadow-xl">
          <span class="text-gray-400 text-xs">ZOOM</span>
          <span class="font-mono font-bold ml-2 text-gray-700 dark:text-gray-200">{{ (currentZoom * 100).toFixed(0) }}%</span>
        </div>

        <!-- Legend -->
        <div class="absolute top-6 left-6 glass-card rounded-2xl shadow-xl p-4 z-10 min-w-52">
          <div class="flex items-center gap-2 mb-3">
            <UIcon name="i-lucide-layers" class="w-4 h-4 text-primary" />
            <h4 class="font-bold text-gray-900 dark:text-gray-100 text-sm uppercase tracking-wide">Legend</h4>
          </div>
          <div class="space-y-2">
            <div v-for="type in nodeTypeOptions" :key="type.value" class="flex items-center gap-2.5">
              <div
                class="w-4 h-4 rounded-full shadow-sm ring-2 ring-white/20 flex-shrink-0"
                :style="{ backgroundColor: type.color }"
              />
              <span class="text-sm text-gray-600 dark:text-gray-300 flex-1">{{ type.label }}</span>
              <span v-if="topologyStats" class="text-xs font-mono font-bold text-gray-400">
                {{ topologyStats.byType.find(t => t.type === type.value)?.count || 0 }}
              </span>
            </div>
            <div class="flex items-center gap-2.5 pt-2 mt-1 border-t border-gray-100 dark:border-slate-800/50">
              <div class="w-5 h-0 border-t-2 border-dashed border-gray-400 opacity-60 flex-shrink-0" />
              <span class="text-[11px] text-gray-500 uppercase font-medium">Service Backend</span>
            </div>
          </div>
          <div class="mt-4 pt-3 border-t border-gray-200/50 dark:border-slate-700/50 space-y-1 text-xs text-gray-400">
            <p class="flex items-center gap-1.5">
              <UIcon name="i-lucide-move" class="w-3 h-3" /> Drag nodes to rearrange
            </p>
            <p class="flex items-center gap-1.5">
              <UIcon name="i-lucide-zoom-in" class="w-3 h-3" /> Scroll to zoom
            </p>
            <p class="flex items-center gap-1.5">
              <UIcon name="i-lucide-mouse-pointer-2" class="w-3 h-3" /> Click for details
            </p>
          </div>
        </div>

        <!-- Hovered Node Info -->
        <Transition
          enter-active-class="transition duration-200 ease-out"
          enter-from-class="opacity-0 translate-y-3 scale-95"
          enter-to-class="opacity-100 translate-y-0 scale-100"
          leave-active-class="transition duration-150 ease-in"
          leave-from-class="opacity-100 translate-y-0 scale-100"
          leave-to-class="opacity-0 translate-y-3 scale-95"
        >
          <div v-if="hoveredNode" class="absolute top-6 right-6 glass-card rounded-2xl shadow-xl p-4 z-10 min-w-64 max-w-xs">
            <div class="flex items-center gap-3 mb-3">
              <div
                class="w-9 h-9 rounded-xl flex items-center justify-center text-white text-sm font-bold shadow-lg"
                :style="{ backgroundColor: typeColorScale(hoveredNode.type) }"
              >
                {{ hoveredNode.type[0].toUpperCase() }}
              </div>
              <div class="min-w-0">
                <p class="font-bold text-gray-900 dark:text-gray-100 truncate">{{ hoveredNode.name }}</p>
                <p class="text-xs text-gray-400 capitalize">{{ hoveredNode.type }}</p>
              </div>
            </div>
            <div class="space-y-1.5 text-sm">
              <div v-if="hoveredNode.namespace" class="flex justify-between items-center">
                <span class="text-gray-400">Namespace</span>
                <span class="font-medium text-xs bg-primary/10 text-primary px-2 py-0.5 rounded-full">{{ hoveredNode.namespace }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-gray-400">Connections</span>
                <span class="font-bold text-gray-700 dark:text-gray-200">{{ hoveredNode.connectionCount }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-gray-400">Traffic</span>
                <span class="font-mono font-medium text-emerald-500">{{ formatBytes(hoveredNode.totalBytes) }}</span>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </template>
  </UDashboardPanel>

  <!-- Node Details Modal -->
  <UModal v-model:open="showNodeModal" @close="selectedNode = null; showNodeModal = false">
    <template #content>
      <UCard v-if="selectedNode" class="w-full max-w-lg">
        <template #header>
          <div class="flex items-center gap-3">
            <div
              class="w-12 h-12 rounded-2xl flex items-center justify-center text-white font-bold text-lg shadow-lg"
              :style="{ backgroundColor: typeColorScale(selectedNode.type) }"
            >
              {{ selectedNode.type[0].toUpperCase() }}
            </div>
            <div>
              <h3 class="text-lg font-bold">{{ selectedNode.name }}</h3>
              <p class="text-sm text-gray-400 capitalize flex items-center gap-1.5">
                <span
                  class="inline-block w-2 h-2 rounded-full"
                  :style="{ backgroundColor: typeColorScale(selectedNode.type) }"
                />
                {{ selectedNode.type }}
              </p>
            </div>
          </div>
        </template>

        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-3">
            <div class="bg-blue-50 dark:bg-blue-500/10 p-4 rounded-xl text-center ring-1 ring-blue-200/50 dark:ring-blue-500/20">
              <div class="text-2xl font-bold text-blue-600 dark:text-blue-400">{{ selectedNode.connectionCount }}</div>
              <div class="text-xs text-gray-500 mt-1 font-medium uppercase tracking-wide">Connections</div>
            </div>
            <div class="bg-emerald-50 dark:bg-emerald-500/10 p-4 rounded-xl text-center ring-1 ring-emerald-200/50 dark:ring-emerald-500/20">
              <div class="text-xl font-bold text-emerald-600 dark:text-emerald-400">{{ formatBytes(selectedNode.totalBytes) }}</div>
              <div class="text-xs text-gray-500 mt-1 font-medium uppercase tracking-wide">Total Traffic</div>
            </div>
          </div>

          <div class="space-y-0 divide-y divide-gray-100 dark:divide-slate-800 text-sm">
            <div class="flex justify-between py-2.5">
              <span class="text-gray-400">ID</span>
              <span class="font-mono text-xs text-gray-600 dark:text-gray-300 max-w-[60%] truncate text-right">{{ selectedNode.id }}</span>
            </div>
            <div v-if="selectedNode.namespace" class="flex justify-between py-2.5">
              <span class="text-gray-400">Namespace</span>
              <span class="font-medium text-xs bg-primary/10 text-primary px-2 py-0.5 rounded-full">{{ selectedNode.namespace }}</span>
            </div>
            <div class="flex justify-between py-2.5">
              <span class="text-gray-400">Group</span>
              <span class="font-medium">{{ selectedNode.group }}</span>
            </div>
            <div class="flex justify-between py-2.5">
              <span class="text-gray-400">Label</span>
              <span class="font-medium max-w-[60%] truncate text-right">{{ selectedNode.label }}</span>
            </div>
          </div>
        </div>

        <template #footer>
          <div class="flex justify-end">
            <UButton color="neutral" variant="soft" @click="showNodeModal = false">Close</UButton>
          </div>
        </template>
      </UCard>
    </template>
  </UModal>
</template>

<style>
/* ─── Main SVG cursor ─── */
.network-topology-svg {
  cursor: grab;
}
.network-topology-svg:active {
  cursor: grabbing;
}

/* ─── Background ─── */
.topology-bg {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
}
.dark .topology-bg {
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
}

/* ─── Subtle dot grid ─── */
.topology-grid {
  background-image: radial-gradient(circle, #cbd5e1 1px, transparent 1px);
  background-size: 28px 28px;
  opacity: 0.35;
}
.dark .topology-grid {
  background-image: radial-gradient(circle, #334155 1px, transparent 1px);
  opacity: 0.4;
}

/* ─── Glass card ─── */
.glass-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(203, 213, 225, 0.6);
}
.dark .glass-card {
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(51, 65, 85, 0.7);
}

/* ─── Node / Edge transition ─── */
.node-group {
  transition: opacity 0.2s ease;
}
.edge-group {
  transition: opacity 0.2s ease;
}
.node-circle, .node-bg {
  transition: r 0.2s ease;
}

/* ─── SVG text dark mode ─── */
.dark .node-label {
  fill: #e2e8f0;
}
.dark .node-namespace {
  fill: #94a3b8;
}
.dark .edge-label {
  fill: #64748b;
}

/* ─── Edge flow animation (optional, applied to edge-line via JS if desired) ─── */
@keyframes flowDash {
  to {
    stroke-dashoffset: -24;
  }
}
.edge-line-animated {
  stroke-dasharray: 8 6;
  animation: flowDash 1.2s linear infinite;
}
</style>
