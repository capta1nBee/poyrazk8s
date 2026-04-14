import type { Pod, Deployment, Service, Job, CronJob, Node, StatefulSet, DaemonSet } from '~/types/kubernetes'
import yaml from 'js-yaml'

export const useKubernetes = () => {
  const { $api } = useNuxtApp()
  const clusterStore = useClusterStore()
  const toast = useToast()

  const clusterUid = computed(() => clusterStore.selectedCluster?.uid)
  const namespace = computed(() => clusterStore.selectedNamespace)

  // Pods
  const fetchPods = async (ns?: string, includeDeleted: boolean = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get<Pod[]>(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods`, {
      params: { includeDeleted }
    })
    return response.data
  }

  const restartPod = async (podName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods/${podName}/restart`)
    toast.add({ title: 'Pod restarted successfully', color: 'success' })
  }

  const deletePod = async (podName: string, force = false, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    if (force) {
      await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods/${podName}/delete-force`)
    } else {
      await $api.delete(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods/${podName}`)
    }
    toast.add({ title: 'Pod deleted successfully', color: 'success' })
  }

  const forceDeletePod = async (podName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods/${podName}/delete-force`)
    toast.add({ title: 'Pod force deleted successfully', color: 'success' })
  }

  const getPodYaml = async (podName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods/${podName}/yaml`)
    return response.data
  }

  const getPodPreviousLogs = async (podName: string, container?: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods/${podName}/logs/previous`, {
      params: { container }
    })
    return response.data
  }

  const getPodContainers = async (podName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods/${podName}/containers`)
    return response.data
  }

  const createPortForward = async (podName: string, localPort: number, podPort: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods/${podName}/port-forward`, null, {
      params: { localPort, podPort }
    })
    return response.data
  }

  // Deployments
  const fetchDeployments = async (ns?: string, includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get<Deployment[]>(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments`, {
      params: { includeDeleted }
    })
    return response.data
  }

  const scaleDeployment = async (deploymentName: string, replicas: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments/${deploymentName}/scale`, {
      replicas
    })
    toast.add({ title: 'Deployment scaled successfully', color: 'success' })
  }

  const rollbackDeployment = async (deploymentName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments/${deploymentName}/rollback`)
    toast.add({ title: 'Deployment rollback initiated', color: 'success' })
  }

  const getDeploymentHistory = async (deploymentName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments/${deploymentName}/history`)
    return response.data
  }

  const rollbackToRevision = async (deploymentName: string, revision: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments/${deploymentName}/rollback/${revision}`)
    toast.add({ title: 'Deployment rolled back to revision', color: 'success' })
  }

  const getDeploymentRevisionDetails = async (deploymentName: string, revision: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments/${deploymentName}/history/${revision}`)
    return response.data
  }

  const rollbackDeploymentToRevision = async (deploymentName: string, revision: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments/${deploymentName}/rollback/${revision}`)
    toast.add({ title: `Deployment rolled back to revision ${revision}`, color: 'success' })
  }

  const getDeploymentPods = async (deploymentName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments/${deploymentName}/pods`)
    return response.data
  }

  const restartDeployment = async (deploymentName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments/${deploymentName}/restart`)
    toast.add({ title: 'Deployment restart initiated', color: 'success' })
  }

  const pauseDeployment = async (deploymentName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments/${deploymentName}/pause`)
    toast.add({ title: 'Deployment paused', color: 'success' })
  }

  const resumeDeployment = async (deploymentName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/deployments/${deploymentName}/resume`)
    toast.add({ title: 'Deployment resumed', color: 'success' })
  }

  // Services
  const fetchServices = async (ns?: string, includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get<Service[]>(`/k8s/${clusterUid.value}/namespaces/${targetNs}/services`, {
      params: { includeDeleted }
    })
    return response.data
  }

  const exposeService = async (serviceName: string, type: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/services/${serviceName}/expose`, null, {
      params: { type }
    })
    toast.add({ title: 'Service exposed successfully', color: 'success' })
  }

  const changeServiceType = async (serviceName: string, newType: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/services/${serviceName}/change-type`, {
      type: newType
    })
    toast.add({ title: 'Service type changed successfully', color: 'success' })
    return response.data
  }

  const updateServicePorts = async (serviceName: string, ports: any[], ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/services/${serviceName}/update-ports`, ports)
    toast.add({ title: 'Service ports updated successfully', color: 'success' })
    return response.data
  }

  const getServiceEndpoints = async (serviceName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/services/${serviceName}/endpoints`)
    return response.data
  }

  // Ingresses
  const fetchIngresses = async (ns?: string, includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    if (targetNs && targetNs !== 'all') {
      const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/ingresses`, {
        params: { includeDeleted }
      })
      return response.data
    } else {
      const response = await $api.get(`/k8s/${clusterUid.value}/ingresses`, {
        params: { includeDeleted }
      })
      return response.data
    }
  }

  // Jobs
  const fetchJobs = async (ns?: string, includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get<Job[]>(`/k8s/${clusterUid.value}/namespaces/${targetNs}/jobs`, {
      params: { includeDeleted }
    })
    return response.data
  }

  const rerunJob = async (jobName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    // Note: JobController doesn't have rerun endpoint, but we can delete and recreate
    // For now, we'll use delete as terminate
    await $api.delete(`/k8s/${clusterUid.value}/namespaces/${targetNs}/jobs/${jobName}`)
    toast.add({ title: 'Job terminated (rerun requires manual recreation)', color: 'success' })
  }

  const terminateJob = async (jobName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.delete(`/k8s/${clusterUid.value}/namespaces/${targetNs}/jobs/${jobName}`)
    toast.add({ title: 'Job terminated successfully', color: 'success' })
  }

  const getJobHistory = async (cronJobName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/cronjobs/${cronJobName}/jobs`)
    return response.data
  }

  const deleteCompletedJobs = async (ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.delete(`/k8s/${clusterUid.value}/namespaces/${targetNs}/jobs/completed`)
    toast.add({ title: 'Completed jobs deleted successfully', color: 'success' })
  }

  // CronJobs
  const fetchCronJobs = async (ns?: string, includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get<CronJob[]>(`/k8s/${clusterUid.value}/namespaces/${targetNs}/cronjobs`, {
      params: { includeDeleted }
    })
    return response.data
  }

  const runCronJob = async (cronJobName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/cronjobs/${cronJobName}/run`)
    toast.add({ title: 'CronJob triggered successfully', color: 'success' })
  }

  const suspendCronJob = async (cronJobName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/cronjobs/${cronJobName}/suspend`)
    toast.add({ title: 'CronJob suspended successfully', color: 'success' })
  }

  const resumeCronJob = async (cronJobName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/cronjobs/${cronJobName}/resume`)
    toast.add({ title: 'CronJob resumed successfully', color: 'success' })
  }

  // Nodes
  const fetchNodes = async (includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const response = await $api.get<Node[]>(`/k8s/${clusterUid.value}/nodes`, {
      params: { includeDeleted }
    })
    return response.data
  }

  const cordonNode = async (nodeName: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    await $api.post(`/k8s/${clusterUid.value}/nodes/${nodeName}/cordon`)
    toast.add({ title: 'Node cordoned successfully', color: 'success' })
  }

  const uncordonNode = async (nodeName: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    await $api.post(`/k8s/${clusterUid.value}/nodes/${nodeName}/uncordon`)
    toast.add({ title: 'Node uncordoned successfully', color: 'success' })
  }

  const drainNode = async (nodeName: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    await $api.post(`/k8s/${clusterUid.value}/nodes/${nodeName}/drain`)
    toast.add({ title: 'Node drained successfully', color: 'success' })
  }

  // Namespaces
  const fetchNamespaces = async (includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces`, {
      params: { includeDeleted }
    })
    return response.data
  }

  // Get authorized namespaces for current user
  const fetchAuthorizedNamespaces = async () => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/authorized`)
    return response.data
  }

  // StatefulSets
  const fetchStatefulSets = async (ns?: string, includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get<StatefulSet[]>(`/k8s/${clusterUid.value}/namespaces/${targetNs}/statefulsets`, {
      params: { includeDeleted }
    })
    return response.data
  }

  const scaleStatefulSet = async (statefulSetName: string, replicas: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/statefulsets/${statefulSetName}/scale`, {
      replicas
    })
    toast.add({ title: 'StatefulSet scaled successfully', color: 'success' })
  }

  const restartStatefulSet = async (statefulSetName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/statefulsets/${statefulSetName}/restart`)
    toast.add({ title: 'StatefulSet restarted successfully', color: 'success' })
  }

  const deleteStatefulSetPod = async (statefulSetName: string, ordinal: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.delete(`/k8s/${clusterUid.value}/namespaces/${targetNs}/statefulsets/${statefulSetName}/pods/${ordinal}`)
    toast.add({ title: 'StatefulSet pod deleted successfully', color: 'success' })
  }

  const getStatefulSetHistory = async (statefulSetName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/statefulsets/${statefulSetName}/history`)
    return response.data
  }

  const getStatefulSetRevisionDetails = async (statefulSetName: string, revision: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/statefulsets/${statefulSetName}/history/${revision}`)
    return response.data
  }

  const rollbackStatefulSetToRevision = async (statefulSetName: string, revision: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/statefulsets/${statefulSetName}/rollback/${revision}`)
    toast.add({ title: `StatefulSet rolled back to revision ${revision}`, color: 'success' })
  }

  // DaemonSets
  const fetchDaemonSets = async (ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get<DaemonSet[]>(`/k8s/${clusterUid.value}/namespaces/${targetNs}/daemonsets`)
    return response.data
  }

  const restartDaemonSet = async (daemonSetName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/daemonsets/${daemonSetName}/restart`)
    toast.add({ title: 'DaemonSet restarted successfully', color: 'success' })
  }

  const pauseDaemonSet = async (daemonSetName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/daemonsets/${daemonSetName}/pause`)
    toast.add({ title: 'DaemonSet paused successfully', color: 'success' })
  }

  const resumeDaemonSet = async (daemonSetName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/daemonsets/${daemonSetName}/resume`)
    toast.add({ title: 'DaemonSet resumed successfully', color: 'success' })
  }

  const getDaemonSetHistory = async (daemonSetName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/daemonsets/${daemonSetName}/history`)
    return response.data
  }

  const getDaemonSetRevisionDetails = async (daemonSetName: string, revision: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/daemonsets/${daemonSetName}/history/${revision}`)
    return response.data
  }

  const rollbackDaemonSetToRevision = async (daemonSetName: string, revision: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/daemonsets/${daemonSetName}/rollback/${revision}`)
    toast.add({ title: `DaemonSet rolled back to revision ${revision}`, color: 'success' })
  }

  // ReplicaSets
  const fetchReplicaSets = async (ns?: string, includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/replicasets`, {
      params: { includeDeleted }
    })
    return response.data
  }

  // HPAs
  const fetchHpas = async (ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/hpas`)
    return response.data
  }

  // Events
  const fetchEvents = async (ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/events`)
    return response.data
  }

  // ConfigMaps
  const fetchConfigMaps = async (ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/configmaps`)
    return response.data
  }

  const getConfigMapData = async (configMapName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/configmaps/${configMapName}/data`)
    return response.data
  }

  const deleteConfigMap = async (configMapName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.delete(`/k8s/${clusterUid.value}/namespaces/${targetNs}/configmaps/${configMapName}`)
    toast.add({ title: 'ConfigMap deleted successfully', color: 'success' })
  }

  // Secrets
  const fetchSecrets = async (ns?: string, includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/secrets`, {
      params: { includeDeleted }
    })
    return response.data
  }

  const getSecretData = async (secretName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/secrets/${secretName}/data`)
    return response.data
  }

  const deleteSecret = async (secretName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.delete(`/k8s/${clusterUid.value}/namespaces/${targetNs}/secrets/${secretName}`)
    toast.add({ title: 'Secret deleted successfully', color: 'success' })
  }

  // PersistentVolumes
  const fetchPersistentVolumes = async () => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const response = await $api.get(`/k8s/${clusterUid.value}/persistentvolumes`)
    return response.data
  }

  const deletePersistentVolume = async (pvName: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    await $api.delete(`/k8s/${clusterUid.value}/persistentvolumes/${pvName}`)
    toast.add({ title: 'PersistentVolume deleted successfully', color: 'success' })
  }

  // PersistentVolumeClaims
  const fetchPersistentVolumeClaims = async (ns?: string, includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/persistentvolumeclaims`, {
      params: { includeDeleted }
    })
    return response.data
  }

  const deletePersistentVolumeClaim = async (pvcName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    await $api.delete(`/k8s/${clusterUid.value}/namespaces/${targetNs}/persistentvolumeclaims/${pvcName}`)
    toast.add({ title: 'PersistentVolumeClaim deleted successfully', color: 'success' })
  }

  const resizePVC = async (pvcName: string, newSize: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/persistentvolumeclaims/${pvcName}/resize`, {
      size: newSize
    })
    toast.add({ title: 'PVC resized successfully', color: 'success' })
    return response.data
  }

  const getPVCUsage = async (pvcName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/persistentvolumeclaims/${pvcName}/usage`)
    return response.data
  }

  // Helper for generic paths
  const getResourcePath = (kind: string) => {
    const map: Record<string, string> = {
      Pod: 'pods',
      Deployment: 'deployments',
      StatefulSet: 'statefulsets',
      DaemonSet: 'daemonsets',
      Job: 'jobs',
      CronJob: 'cronjobs',
      Service: 'services',
      Ingress: 'ingresses',
      ConfigMap: 'configmaps',
      Secret: 'secrets',
      PersistentVolumeClaim: 'persistentvolumeclaims',
      PersistentVolume: 'persistentvolumes',
      Node: 'nodes',
      Namespace: 'namespaces',
      Event: 'events',
      EndpointSlice: 'endpointslices',
      NetworkPolicy: 'networkpolicies',
      Lease: 'leases',
      IngressClass: 'ingressclasses',
      PriorityClass: 'priorityclasses',
      IPAddress: 'ipaddresses',
      ValidatingAdmissionPolicy: 'validatingadmissionpolicies',
      StorageClass: 'storageclasses',
      HorizontalPodAutoscaler: 'hpas',
    }
    return map[kind] || kind.toLowerCase() + 's'
  }

  // Generic fetch for any resource
  const fetchResources = async <T>(kind: string, ns?: string, includeDeleted = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const pathBase = getResourcePath(kind)

    // Determine if namespaced. 
    // This list must be comprehensive for new resources.
    const nonNamespacedKinds = [
      'Node', 'PersistentVolume', 'Namespace',
      'ClusterRole', 'ClusterRoleBinding',
      'MutatingWebhookConfiguration', 'ValidatingWebhookConfiguration',
      'CertificateSigningRequest', 'CSIDriver', 'CSINode',
      'CustomResourceDefinition', 'IngressClass', 'IPAddress',
      'PriorityClass', 'PriorityLevelConfiguration',
      'ValidatingAdmissionPolicy', 'ValidatingAdmissionPolicyBinding', 'VolumeAttachment',
      'StorageClass'
    ]

    const isNamespaced = !nonNamespacedKinds.includes(kind)
    const targetNs = ns || ((isNamespaced) ? namespace.value : undefined)

    let url = `/k8s/${clusterUid.value}`
    if (isNamespaced && targetNs) url += `/namespaces/${targetNs}`
    url += `/${pathBase}`

    const response = await $api.get<T[]>(url, {
      params: { includeDeleted }
    })
    return response.data
  }

  // Cluster-scoped resource kinds (not namespaced)
  const clusterScopedKinds = [
    'Node', 'PersistentVolume', 'Namespace',
    'ClusterRole', 'ClusterRoleBinding',
    'MutatingWebhookConfiguration', 'ValidatingWebhookConfiguration',
    'CertificateSigningRequest', 'CSIDriver', 'CSINode',
    'CustomResourceDefinition', 'IngressClass', 'IPAddress',
    'PriorityClass', 'PriorityLevelConfiguration',
    'ValidatingAdmissionPolicy', 'ValidatingAdmissionPolicyBinding', 'VolumeAttachment'
  ]

  const isClusterScoped = (kind: string) => clusterScopedKinds.includes(kind)

  // Generic Actions
  const getResourceYAML = async (kind: string, name: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const isNamespaced = !isClusterScoped(kind)
    const targetNs = ns || ((isNamespaced) ? namespace.value : undefined)

    let url = `/k8s/${clusterUid.value}/resources/${kind}`
    if (isNamespaced && targetNs) {
      url += `/namespaces/${targetNs}/${name}/yaml`
    } else {
      url += `/${name}/yaml`
    }

    const response = await $api.get<{ yaml: string }>(url)
    return response.data.yaml
  }

  const applyResourceYAML = async (kind: string, name: string, yaml: string, ns?: string, dryRun = false) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const isNamespaced = !isClusterScoped(kind)
    const targetNs = ns || ((isNamespaced) ? namespace.value : undefined)

    let url = `/k8s/${clusterUid.value}/resources/${kind}`
    if (isNamespaced && targetNs) {
      url += `/namespaces/${targetNs}/${name}/yaml`
    } else {
      // Cluster-scoped resources
      url += `/${name}/yaml`
    }

    const response = await $api.put<{ success: boolean; message: string }>(url, {
      yaml,
      dryRun: dryRun.toString()
    })

    if (response.data.success) {
      toast.add({ title: `${kind} applied successfully`, color: 'success' })
    } else {
      toast.add({ title: `Failed to apply ${kind}`, description: response.data.message, color: 'error' })
      throw new Error(response.data.message)
    }

    return response.data
  }

  const updateResource = async (kind: string, name: string, data: any, ns?: string) => {
    // For YAML updates, convert to YAML and use apply endpoint
    // This maintains backward compatibility but uses apply under the hood
    const yamlString = yaml.dump(data)
    return await applyResourceYAML(kind, name, yamlString, ns, false)
  }

  // Get resource details (including labels and annotations)
  const getResourceDetails = async (kind: string, name: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const isNamespaced = !isClusterScoped(kind)
    const targetNs = ns || ((isNamespaced) ? namespace.value : undefined)

    let url = `/k8s/${clusterUid.value}/resources/${kind}`
    if (isNamespaced && targetNs) {
      url += `/namespaces/${targetNs}/${name}/details`
    } else {
      url += `/${name}/details`
    }

    const response = await $api.get<{
      labels?: Record<string, string>
      annotations?: Record<string, string>
      [key: string]: any
    }>(url)
    return response.data
  }

  // Pod Actions
  const getPodLogs = async (podName: string, container?: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods/${podName}/logs`, {
      params: { container }
    })
    return response.data
  }

  // Related workloads (workloads that reference a specific ConfigMap or Secret)
  const fetchRelatedWorkloads = async (
    resourceKind: 'ConfigMap' | 'Secret',
    resourceName: string,
    ns?: string
  ): Promise<{ kind: string; name: string }[]> => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get<{ kind: string; name: string }[]>(
      `/k8s/${clusterUid.value}/namespaces/${targetNs}/related-workloads`,
      { params: { kind: resourceKind, name: resourceName } }
    )
    return response.data
  }

  // Deployment Actions
  const rolloutRestart = async (kind: string, name: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const pathBase = getResourcePath(kind)
    const targetNs = ns || namespace.value
    // Assuming backend supports /{kind}/{name}/restart for Deployment, StatefulSet, DaemonSet
    await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/${pathBase}/${name}/restart`)
    toast.add({ title: `${kind} restart initiated`, color: 'success' })
  }

  const scaleResource = async (kind: string, name: string, replicas: number, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const pathBase = getResourcePath(kind)
    const targetNs = ns || namespace.value
    if (kind === 'Deployment') {
      await scaleDeployment(name, replicas, targetNs)
    } else if (kind === 'StatefulSet') {
      await scaleStatefulSet(name, replicas, targetNs)
    } else {
      await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/${pathBase}/${name}/scale`, {
        replicas
      })
      toast.add({ title: `${kind} scaled successfully`, color: 'success' })
    }
  }

  // Ingress Actions
  const updateIngressRules = async (ingressName: string, rules: any[], ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/ingresses/${ingressName}/update-rules`, rules)
    toast.add({ title: 'Ingress rules updated successfully', color: 'success' })
    return response.data
  }

  const updateIngressTLS = async (ingressName: string, tls: any[], ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.post(`/k8s/${clusterUid.value}/namespaces/${targetNs}/ingresses/${ingressName}/update-tls`, tls)
    toast.add({ title: 'Ingress TLS updated successfully', color: 'success' })
    return response.data
  }

  const testIngressRoute = async (ingressName: string, path: string = '/', ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/ingresses/${ingressName}/test-route`, {
      params: { path }
    })
    return response.data
  }

  // Node Actions
  const getNodeMetrics = async (nodeName: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const response = await $api.get(`/k8s/${clusterUid.value}/nodes/${nodeName}/metrics`)
    return response.data
  }

  const getPodMetrics = async (podName: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const targetNs = ns || namespace.value
    try {
      const response = await $api.get(`/k8s/${clusterUid.value}/namespaces/${targetNs}/pods/${podName}/metrics`)
      return response.data
    } catch (error: any) {
      // If metrics endpoint doesn't exist, return empty object
      console.warn('Metrics endpoint not available:', error.message)
      return {}
    }
  }

  const deleteResource = async (kind: string, name: string, force = false, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const pathBase = getResourcePath(kind)
    const isNamespaced = !['Node', 'PersistentVolume', 'Namespace'].includes(kind)
    const targetNs = ns || ((isNamespaced) ? namespace.value : undefined)

    let url = `/k8s/${clusterUid.value}`
    if (isNamespaced && targetNs) url += `/namespaces/${targetNs}`
    url += `/${pathBase}/${name}`

    await $api.delete(url, { params: { force } })
    toast.add({ title: `${kind} deleted successfully`, color: 'success' })
  }

  const getResourceEvents = async (kind: string, name: string, ns?: string) => {
    if (!clusterUid.value) throw new Error('No cluster selected')
    const isNamespaced = !isClusterScoped(kind)
    const targetNs = ns || ((isNamespaced) ? namespace.value : undefined)

    let url = `/k8s/${clusterUid.value}`
    if (isNamespaced && targetNs) {
      url += `/namespaces/${targetNs}`
    }
    url += `/resources/${kind}/${name}/events`

    const response = await $api.get<any[]>(url)
    return response.data
  }

  return {
    getResourceYAML,
    applyResourceYAML,
    updateResource,
    getResourceDetails,
    getResourceEvents,
    deleteResource,
    getPodLogs,
    getPodMetrics,
    getPodYaml,
    getPodPreviousLogs,
    getPodContainers,
    createPortForward,
    forceDeletePod,
    rolloutRestart,
    scaleResource,
    fetchPods,
    restartPod,
    deletePod,
    fetchDeployments,
    scaleDeployment,
    restartDeployment,
    pauseDeployment,
    resumeDeployment,
    rollbackDeployment,
    getDeploymentHistory,
    rollbackToRevision,
    getDeploymentPods,
    fetchServices,
    exposeService,
    changeServiceType,
    updateServicePorts,
    getServiceEndpoints,
    fetchIngresses,
    fetchJobs,
    rerunJob,
    terminateJob,
    getJobHistory,
    deleteCompletedJobs,
    fetchCronJobs,
    runCronJob,
    suspendCronJob,
    resumeCronJob,
    fetchNamespaces,
    fetchAuthorizedNamespaces,
    fetchNodes,
    cordonNode,
    uncordonNode,
    drainNode,
    getNodeMetrics,
    fetchStatefulSets,
    scaleStatefulSet,
    restartStatefulSet,
    deleteStatefulSetPod,
    fetchDaemonSets,
    restartDaemonSet,
    pauseDaemonSet,
    resumeDaemonSet,
    fetchConfigMaps,
    getConfigMapData,
    deleteConfigMap,
    fetchSecrets,
    getSecretData,
    deleteSecret,
    fetchPersistentVolumes,
    deletePersistentVolume,
    fetchPersistentVolumeClaims,
    deletePersistentVolumeClaim,
    resizePVC,
    getPVCUsage,
    updateIngressRules,
    updateIngressTLS,
    testIngressRoute,
    getDeploymentRevisionDetails,
    rollbackDeploymentToRevision,
    getStatefulSetHistory,
    getStatefulSetRevisionDetails,
    rollbackStatefulSetToRevision,
    getDaemonSetHistory,
    getDaemonSetRevisionDetails,
    rollbackDaemonSetToRevision,
    fetchEvents,
    fetchReplicaSets,
    fetchResources,
    fetchRelatedWorkloads,
    fetchHpas
  }
}

