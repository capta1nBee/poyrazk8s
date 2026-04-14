<script setup lang="ts">
import { ref, onMounted } from 'vue'

const { $api } = useNuxtApp()
const toast = useToast()

const federations = ref<any[]>([])
const loading = ref(false)
const showForm = ref(false)
const selectedFederation = ref<any>(null)

const fetchFederations = async () => {
    loading.value = true
    try {
        const { data } = await $api.get('/federations')
        federations.value = data
        if (selectedFederation.value) {
            selectedFederation.value = data.find((f: any) => f.id === selectedFederation.value.id) || null
        }
    } catch (e: any) {
        toast.add({ title: 'Error', description: 'Federasyonlar yüklenemedi', color: 'red' })
    } finally {
        loading.value = false
    }
}

// DELETE MODAL LOGIC (FAIL-SAFE VUE3 TELEPORT)
const isDelModalOpen = ref(false)
const delStepNum = ref(1)
const fedIdTarget = ref<number | null>(null)
const optMember = ref(false)
const optMaster = ref(false)

const openDeleteModal = (id: number) => {
    fedIdTarget.value = id
    delStepNum.value = 1
    optMember.value = false
    optMaster.value = false
    isDelModalOpen.value = true
}

const nextDelStep = (confirm: boolean) => {
    optMember.value = confirm
    delStepNum.value = 2
}

const finalDeleteAction = async (confirmMaster: boolean) => {
    optMaster.value = confirmMaster
    if (!fedIdTarget.value) {
        isDelModalOpen.value = false
        return
    }
    
    loading.value = true
    try {
        await $api.delete(`/federations/${fedIdTarget.value}`, {
            params: {
                removeFromMembers: optMember.value,
                removeFromMaster: optMaster.value
            }
        })
        toast.add({ title: 'Success', description: 'Federasyon başarıyla silindi', color: 'green' })
        isDelModalOpen.value = false
        await fetchFederations()
        if (selectedFederation.value?.id === fedIdTarget.value) selectedFederation.value = null
    } catch (e: any) {
        toast.add({ title: 'Error', description: 'Silme işlemi başarısız oldu', color: 'red' })
    } finally {
        loading.value = false
    }
}

const onSave = async (payload: any) => {
    try {
        if (payload.id) {
            await $api.put(`/federations/${payload.id}`, payload)
        } else {
            await $api.post('/federations', payload)
        }
        toast.add({ title: 'Success', description: 'Kaydedildi', color: 'green' })
        showForm.value = false
        fetchFederations()
    } catch (e: any) {
        toast.add({ title: 'Error', description: 'Hata oluştu', color: 'red' })
    }
}

onMounted(() => {
    fetchFederations()
})
</script>

<template>
  <div class="h-full flex flex-col bg-gray-50 dark:bg-black overflow-hidden relative">
    
    <UDashboardPanel id="federations" class="flex-1 overflow-hidden">
      <template #header>
        <UDashboardNavbar title="Federation Management">
          <template #leading><UDashboardSidebarCollapse /></template>
          <template #right>
            <UButton v-if="!showForm" icon="i-lucide-plus" label="New Federation" color="black" @click="showForm = true; selectedFederation = null" />
            <UButton v-else icon="i-lucide-x" label="Cancel" color="neutral" variant="ghost" @click="showForm = false" />
            <UButton icon="i-lucide-refresh-cw" :loading="loading" color="neutral" variant="ghost" @click="fetchFederations" />
          </template>
        </UDashboardNavbar>
      </template>

      <template #body>
        <!-- Form Overlay overlay -->
        <div v-if="showForm" class="p-6 max-w-4xl mx-auto w-full h-full overflow-y-auto bg-white dark:bg-gray-900 z-10">
          <FederationForm :federation="selectedFederation" @save="onSave" @cancel="showForm = false" />
        </div>

        <div v-else class="flex flex-col lg:flex-row h-full overflow-hidden">
           <!-- LEFT SIDE: LIST -->
           <div class="w-full lg:w-[400px] border-r border-gray-200 dark:border-gray-800 flex flex-col bg-white dark:bg-gray-900 overflow-hidden shrink-0">
                <div class="p-4 border-b border-gray-100 dark:border-gray-800 flex justify-between items-center">
                    <span class="font-black text-[10px] uppercase tracking-[0.2em] text-gray-400">Inventory</span>
                    <span class="text-xs bg-gray-100 dark:bg-gray-800 px-2 py-0.5 rounded-full">{{ federations.length }}</span>
                </div>
                <div class="flex-1 overflow-y-auto p-4 space-y-3">
                   <div v-for="fed in federations" :key="fed.id" 
                        class="p-5 border rounded-2xl transition-all relative group shadow-sm"
                        :class="[selectedFederation?.id === fed.id ? 'border-primary-500 ring-2 ring-primary-500/20 bg-primary-50/10' : 'border-gray-100 dark:border-gray-800 bg-white dark:bg-gray-950 hover:border-primary-200']">
                        
                        <div class="cursor-pointer" @click="selectedFederation = fed">
                            <div class="flex items-center gap-3 mb-4">
                                <div class="w-10 h-10 rounded-xl bg-primary-50 dark:bg-primary-900/20 flex items-center justify-center text-primary-500">
                                    <UIcon name="i-lucide-network" class="w-6 h-6" />
                                </div>
                                <div class="min-w-0">
                                    <div class="font-bold text-gray-900 dark:text-white truncate">{{ fed.name }}</div>
                                    <div class="text-[10px] text-gray-400 uppercase font-black tracking-widest mt-0.5">{{ fed.masterClusterName }}</div>
                                </div>
                            </div>
                            
                            <div class="flex items-center justify-between">
                                <UBadge :color="fed.status === 'Success' ? 'green' : (fed.status === 'Pending' ? 'yellow' : 'red')" variant="soft" size="xs" class="font-bold">
                                    {{ fed.status }}
                                </UBadge>
                                <div class="text-[10px] text-gray-400 font-bold uppercase">{{ fed.members?.length || 0 }} Clusters</div>
                            </div>
                        </div>

                        <!-- Action Overlay on Hover -->
                        <div class="absolute top-4 right-4 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <UButton icon="i-lucide-edit" variant="soft" color="gray" size="xs" @click.stop.prevent="selectedFederation = fed; showForm = true" />
                            <UButton icon="i-lucide-trash" variant="soft" color="red" size="xs" @click.stop.prevent="openDeleteModal(fed.id)" />
                        </div>
                   </div>

                   <div v-if="!loading && federations.length === 0" class="text-center py-20 opacity-30">
                        <UIcon name="i-lucide-inbox" class="w-12 h-12 mx-auto mb-2" />
                        <p class="font-bold">Empty</p>
                   </div>
                </div>
           </div>

           <!-- RIGHT SIDE: DETAIL -->
           <div class="flex-1 bg-gray-50 dark:bg-gray-950 overflow-hidden flex flex-col">
                <div v-if="selectedFederation" class="flex-1 flex flex-col overflow-hidden">
                    <div class="p-6 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 flex justify-between items-center shadow-sm">
                        <div class="flex items-center gap-4">
                            <div class="p-3 bg-primary-50 dark:bg-primary-900/20 rounded-2xl text-primary-500">
                                <UIcon name="i-lucide-layout-dashboard" class="w-8 h-8" />
                            </div>
                            <div>
                                <h2 class="text-3xl font-black text-gray-900 dark:text-white tracking-tighter">{{ selectedFederation.name }}</h2>
                                <p class="text-xs font-bold text-gray-400 uppercase tracking-widest mt-1">Federation Overview & Metrics</p>
                            </div>
                        </div>
                        <UButton icon="i-lucide-trash" color="red" label="DELETE FEDERATION" variant="solid" class="font-black" size="md" @click="openDeleteModal(selectedFederation.id)" />
                    </div>
                    <div class="flex-1 overflow-y-auto p-8">
                        <FederationDashboard :federation="selectedFederation" @refresh="fetchFederations" />
                    </div>
                </div>
                <div v-else class="h-full flex flex-col items-center justify-center text-center p-12 opacity-20">
                    <UIcon name="i-lucide-mouse-pointer-2" class="w-24 h-24 mb-6 animate-bounce" />
                    <h3 class="text-4xl font-black uppercase tracking-tighter">Selection Required</h3>
                </div>
           </div>
        </div>
      </template>
    </UDashboardPanel>

    <!-- 
      FAIL-SAFE MODAL USING VUE 3 TELEPORT 
      This bypasses any UI library component state issues by using raw Vue 3 Teleport to body.
    -->
    <Teleport to="body">
        <div v-if="isDelModalOpen" class="fixed inset-0 z-[99999] flex items-center justify-center p-4">
            <!-- Backdrop -->
            <div class="absolute inset-0 bg-gray-900/80 backdrop-blur-sm" @click="isDelModalOpen = false"></div>
            
            <!-- Modal Body -->
            <div class="relative bg-white dark:bg-gray-900 rounded-3xl shadow-[0_0_50px_rgba(0,0,0,0.5)] w-full max-w-md overflow-hidden border border-white/10 animate-scale-in">
                <div class="p-5 border-b border-gray-100 dark:border-gray-800 flex justify-between items-center bg-gray-50/50 dark:bg-gray-800/50">
                    <span class="text-[10px] font-black uppercase tracking-[0.3em] text-gray-400">Security Verification &middot; Step {{ delStepNum }} / 2</span>
                    <button @click="isDelModalOpen = false" class="p-1 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-full transition-colors">
                        <UIcon name="i-lucide-x" class="w-5 h-5 text-gray-500" />
                    </button>
                </div>
                
                <div class="p-10 text-center">
                    <div v-if="delStepNum === 1" class="animate-fade-in">
                        <div class="w-24 h-24 bg-blue-100/50 dark:bg-blue-900/30 text-blue-500 rounded-3xl flex items-center justify-center mx-auto mb-8 shadow-inner">
                            <UIcon name="i-lucide-network" class="w-12 h-12" />
                        </div>
                        <h3 class="text-2xl font-black mb-3 tracking-tighter">Member Cluster Cleanup?</h3>
                        <p class="text-sm text-gray-500 font-medium px-4">Would you like to recursively remove the federated resources and their dependencies from all <b>Member clusters</b>?</p>
                        
                        <div class="grid grid-cols-2 gap-4 mt-10">
                            <button @click="nextDelStep(false)" class="bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 py-4 rounded-2xl font-black text-xs uppercase tracking-widest transition-all active:scale-95 text-gray-600 dark:text-gray-300">
                                Keep Them
                            </button>
                            <button @click="nextDelStep(true)" class="bg-primary-500 hover:bg-primary-600 text-white py-4 rounded-2xl font-black text-xs uppercase tracking-widest transition-all shadow-lg shadow-primary-500/30 active:scale-95">
                                Delete Them
                            </button>
                        </div>
                    </div>

                    <div v-else class="animate-fade-in">
                        <div class="w-24 h-24 bg-red-100/50 dark:bg-red-900/30 text-red-500 rounded-3xl flex items-center justify-center mx-auto mb-8 shadow-inner animate-pulse">
                            <UIcon name="i-lucide-alert-octagon" class="w-12 h-12" />
                        </div>
                        <h3 class="text-2xl font-black mb-3 tracking-tighter text-red-600">Master Cluster Destruction?</h3>
                        <p class="text-sm text-gray-500 font-medium px-4">Permanently remove the original federation configuration and related resources from the <b>Master cluster</b>? <span class="text-red-500 underline font-bold">This is irreversible.</span></p>
                        
                        <div class="grid grid-cols-2 gap-4 mt-10">
                            <button @click="finalDeleteAction(false)" :disabled="loading" class="bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 py-4 rounded-2xl font-black text-xs uppercase tracking-widest transition-all active:scale-95 text-gray-600 dark:text-gray-300 disabled:opacity-50">
                                Cancel
                            </button>
                            <button @click="finalDeleteAction(true)" :disabled="loading" class="bg-red-500 hover:bg-red-600 text-white py-4 rounded-2xl font-black text-xs uppercase tracking-widest transition-all shadow-lg shadow-red-500/30 active:scale-95 disabled:opacity-50 flex items-center justify-center gap-2">
                                <UIcon v-if="loading" name="i-lucide-loader-2" class="w-4 h-4 animate-spin" />
                                <span>{{ loading ? 'Deleting...' : 'Destroy All' }}</span>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </Teleport>
  </div>
</template>

<style scoped>
.animate-scale-in {
    animation: scaleIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.animate-fade-in {
    animation: fadeIn 0.4s ease-out;
}
@keyframes scaleIn {
    from { opacity: 0; transform: scale(0.9) translateY(20px); }
    to { opacity: 1; transform: scale(1) translateY(0); }
}
@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}
</style>
