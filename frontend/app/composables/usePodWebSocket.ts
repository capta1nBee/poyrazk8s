export const usePodWebSocket = () => {
  const clusterStore = useClusterStore()
  const config = useRuntimeConfig()

  const getWebSocketUrl = (path: string) => {
    if (typeof window !== 'undefined' && window.location.protocol === 'https:') {
      // Page is on HTTPS. Browsers block ws:// from HTTPS pages (Mixed Content).
      // Connect to wss://<same-host>/ws/... so the request goes through the
      // existing SSL-terminating reverse proxy (nginx).
      // nginx MUST have a "location /ws/" block that proxies to the backend
      // with the Upgrade / Connection headers set (see nginx config note).
      return `wss://${window.location.host}${path}`
    }

    // HTTP environment (dev / internal): connect directly to the backend.
    const apiBase = config.public.apiBase || 'http://localhost:8080/api'
    const baseUrl = apiBase.replace('/api', '')
    return baseUrl.replace('http://', 'ws://') + path
  }

  /* ================= POD LOGS ================= */

  const connectPodLogs = (
    podName: string,
    namespace: string,
    container?: string,
    options: {
      follow?: boolean
      tail?: number
      onMessage?: (msg: string) => void
      onError?: (e: Event) => void
      onClose?: () => void
    } = {}
  ) => {
    const clusterUid = clusterStore.selectedCluster?.uid
    if (!clusterUid) throw new Error('No cluster selected')

    const ws = new WebSocket(getWebSocketUrl('/ws/pod-logs'))

    ws.onopen = () => {
      ws.send(JSON.stringify({
        clusterUid,
        namespace,
        podName,
        container: container || null,
        follow: options.follow ?? true,
        tail: options.tail ?? 100
      }))
    }

    ws.onmessage = e => options.onMessage?.(e.data)
    ws.onerror = e => options.onError?.(e)
    ws.onclose = () => options.onClose?.()

    return { close: () => ws.close() }
  }

  /* ================= POD EXEC ================= */

  const connectPodExec = (
    podName: string,
    namespace: string,
    container?: string,
    options: {
      onMessage?: (msg: string) => void
      onError?: (e: Event) => void
      onClose?: () => void
    } = {}
  ) => {
    const clusterUid = clusterStore.selectedCluster?.uid
    const authStore = useAuthStore()
    if (!clusterUid) throw new Error('No cluster selected')

    const ws = new WebSocket(getWebSocketUrl('/ws/pod-exec'))
    let ready = false

    ws.onopen = () => {
      ws.send(JSON.stringify({
        clusterUid,
        namespace,
        podName,
        container: container || null,
        token: authStore.token
      }))
      ready = true
    }

    ws.onmessage = e => options.onMessage?.(e.data)
    ws.onerror = e => options.onError?.(e)
    ws.onclose = () => options.onClose?.()

    return {
      send: (data: string) => {
        if (ready) ws.send(data)
      },
      close: () => ws.close()
    }
  }

  return { connectPodLogs, connectPodExec }
}
