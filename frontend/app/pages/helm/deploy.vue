<script setup lang="ts">
import { ref, computed } from 'vue'

const { $api } = useNuxtApp()
const clusterStore = useClusterStore()
const router = useRouter()

const form = ref({
    releaseName: '',
    deployName: '',
    namespace: 'default',
    chartName: '',
    chartVersion: '',
    customValues: ''
})

const isDeploying = ref(false)
const selectedCluster = computed(() => clusterStore.selectedCluster)

const onSubmit = async () => {
    isDeploying.value = true
    try {
        await $api.post(`/k8s/${selectedCluster.value?.uid}/helm/deploy`, {
            releaseName: form.value.releaseName,
            deployName: form.value.deployName || form.value.releaseName,
            namespace: form.value.namespace,
            chartName: form.value.chartName,
            chartVersion: form.value.chartVersion,
            customValuesYaml: form.value.customValues
        })
        router.push('/helm')
    } catch (err) {
        console.error('Deployment failed', err)
    } finally {
        isDeploying.value = false
    }
}
</script>

<template>
  <UDashboardPage>
    <UDashboardPanel grow>
      <UDashboardNavbar title="Deploy New Chart">
        <template #left>
            <UButton icon="i-lucide-arrow-left" color="gray" variant="ghost" to="/helm" />
        </template>
      </UDashboardNavbar>

      <UDashboardPanelContent>
        <div class="max-w-2xl mx-auto py-8">
            <UCard>
                <form @submit.prevent="onSubmit" class="space-y-6">
                    <UFormGroup label="Release Name" required>
                        <UInput v-model="form.releaseName" placeholder="my-release" required />
                    </UFormGroup>
                    
                    <UFormGroup label="Chart Name" required>
                        <UInput v-model="form.chartName" placeholder="bitnami/nginx" required />
                    </UFormGroup>
                    
                    <UFormGroup label="Chart Version (Optional)">
                        <UInput v-model="form.chartVersion" placeholder="1.0.0" />
                    </UFormGroup>
                    
                    <UFormGroup label="Namespace" required>
                        <UInput v-model="form.namespace" placeholder="default" required />
                    </UFormGroup>
                    
                    <UFormGroup label="Custom Values (YAML / JSON)">
                        <UTextarea v-model="form.customValues" placeholder="# Your helm values here" rows="10" class="font-mono text-sm" />
                    </UFormGroup>
                    
                    <div class="flex justify-end gap-3">
                        <UButton color="gray" variant="ghost" to="/helm">Cancel</UButton>
                        <UButton type="submit" color="primary" :loading="isDeploying">Deploy</UButton>
                    </div>
                </form>
            </UCard>
        </div>
      </UDashboardPanelContent>
    </UDashboardPanel>
  </UDashboardPage>
</template>
