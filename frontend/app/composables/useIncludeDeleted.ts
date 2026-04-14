/**
 * Composable for managing includeDeleted checkbox state
 * Provides reactive state and helper functions
 */
export const useIncludeDeleted = () => {
  const includeDeleted = ref(false)

  const toggleIncludeDeleted = () => {
    includeDeleted.value = !includeDeleted.value
  }

  return {
    includeDeleted,
    toggleIncludeDeleted
  }
}


