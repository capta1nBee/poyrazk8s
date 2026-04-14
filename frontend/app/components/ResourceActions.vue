<script setup lang="ts">
interface Props {
  resourceName: string
  resourceNamespace?: string
  resourceKind: string
  resourceYaml?: string
}

defineProps<Props>()

const emit = defineEmits<{
  refresh: []
  viewYAML: []
  editYAML: []
  delete: []
  viewEvents: []
  copyName: []
  copyNamespace: []
  copyYAML: []
}>()

const toast = useToast()

const copyToClipboard = async (text: string, label: string) => {
  try {
    await navigator.clipboard.writeText(text)
    toast.add({
      title: 'Copied to clipboard',
      description: `${label} copied`,
      color: 'green'
    })
  } catch (error) {
    toast.add({
      title: 'Failed to copy',
      description: 'Could not copy to clipboard',
      color: 'red'
    })
  }
}
</script>

<template>
  <div class="flex gap-1">
    <!-- Refresh -->
    <UButton
      icon="i-lucide-refresh-cw"
      size="xs"
      color="neutral"
      variant="ghost"
      @click="emit('refresh')"
      title="Refresh"
    />

    <!-- View YAML -->
    <UButton
      icon="i-lucide-file-code"
      size="xs"
      color="blue"
      variant="ghost"
      @click="emit('viewYAML')"
      title="View YAML"
    />

    <!-- Edit YAML -->
    <UButton
      icon="i-lucide-pencil"
      size="xs"
      color="yellow"
      variant="ghost"
      @click="emit('editYAML')"
      title="Edit YAML"
    />

    <!-- View Events -->
    <UButton
      icon="i-lucide-bell"
      size="xs"
      color="purple"
      variant="ghost"
      @click="emit('viewEvents')"
      title="View Events"
    />

    <!-- Copy Menu -->
    <UDropdown
      :popper="{ placement: 'bottom-end' }"
      :items="[
        [{
          label: 'Copy Name',
          icon: 'i-lucide-copy',
          click: () => copyToClipboard(resourceName, 'Name')
        },
        resourceNamespace ? {
          label: 'Copy Namespace',
          icon: 'i-lucide-copy',
          click: () => copyToClipboard(resourceNamespace, 'Namespace')
        } : null,
        resourceYaml ? {
          label: 'Copy YAML',
          icon: 'i-lucide-copy',
          click: () => copyToClipboard(resourceYaml, 'YAML')
        } : null].filter(Boolean) as any
      ]"
    >
      <UButton
        icon="i-lucide-copy"
        size="xs"
        color="neutral"
        variant="ghost"
        title="Copy"
      />
    </UDropdown>

    <!-- Delete -->
    <UButton
      icon="i-lucide-trash-2"
      size="xs"
      color="red"
      variant="ghost"
      @click="emit('delete')"
      title="Delete"
    />
  </div>
</template>
