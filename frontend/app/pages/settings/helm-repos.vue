<script setup lang="ts">
// Helm Repository Management Page
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold">Helm Repository Management</h3>
        <ClusterSelector />
      </div>
    </template>
    <HelmRepositoryManagement />
  </UCard>
</template>
