export const useConfirm = () => {
  const isOpen = useState<boolean>('confirm_isOpen', () => false)
  const title = useState<string>('confirm_title', () => '')
  const description = useState<string>('confirm_description', () => '')
  const confirmLabel = useState<string>('confirm_label', () => 'Confirm')
  const cancelLabel = useState<string>('confirm_cancel_label', () => 'Cancel')
  const confirmColor = useState<string>('confirm_color', () => 'primary')
  const icon = useState<string>('confirm_icon', () => '')

  // Store the resolve function of the promise
  const resolvePromise = useState<(value: boolean) => void | null>('confirm_resolve', () => () => { return })

  const open = (options: {
    title: string
    description: string
    confirmLabel?: string
    cancelLabel?: string
    color?: string
    icon?: string
  }): Promise<boolean> => {
    title.value = options.title
    description.value = options.description
    confirmLabel.value = options.confirmLabel || 'Confirm'
    cancelLabel.value = options.cancelLabel || 'Cancel'
    confirmColor.value = options.color || 'primary'
    icon.value = options.icon || ''
    isOpen.value = true

    return new Promise((resolve) => {
      resolvePromise.value = resolve
    })
  }

  const confirm = () => {
    if (resolvePromise.value) {
      resolvePromise.value(true)
      // Do not set to null, just keep the function to avoid strict null checks if it stays in state, 
      // but logically it's done. Or use a specific no-op.
      resolvePromise.value = () => { }
    }
    isOpen.value = false
  }

  const cancel = () => {
    if (resolvePromise.value) {
      resolvePromise.value(false)
      resolvePromise.value = () => { }
    }
    isOpen.value = false
  }

  return {
    isOpen,
    title,
    description,
    confirmLabel,
    cancelLabel,
    confirmColor,
    icon,
    open,
    confirm,
    cancel
  }
}
