/** Matches the new RoleTemplate entity from the Casbin RBAC system */
export interface Role {
  id: number
  name: string
  displayName?: string
  description?: string
  color?: string
  isActive?: boolean
  createdAt?: string
}

export const useRoles = () => {
  const { $api } = useNuxtApp()

  const getAllRoles = async (): Promise<Role[]> => {
    const response = await $api.get('/admin/roles')
    return response.data
  }

  const getRoleById = async (id: number): Promise<Role> => {
    const response = await $api.get(`/admin/roles/${id}`)
    return response.data
  }

  const createRole = async (role: Partial<Role>): Promise<Role> => {
    const response = await $api.post('/admin/roles', role)
    return response.data
  }

  const deleteRole = async (id: number): Promise<void> => {
    await $api.delete(`/admin/roles/${id}`)
  }

  return {
    getAllRoles,
    getRoleById,
    createRole,
    deleteRole
  }
}

