export interface UserResponse {
  id: number
  username: string
  email: string
  authType: string
  isActive: boolean
  isSuperadmin: boolean
  roles: string[]
  createdAt: string
  updatedAt: string
}

export interface CreateUserRequest {
  username: string
  email: string
  password?: string
  authType: string
  isActive: boolean
  isSuperadmin: boolean
  roles: string[]
}

export interface UpdateUserRequest {
  username?: string
  email?: string
  password?: string
  isActive?: boolean
  isSuperadmin?: boolean
  roles?: string[]
}

export interface Assignment {
  cluster: string
  namespace: string
  resources: Resource[]
}

export interface Resource {
  kind: string
  namePattern: string
  actions: string[]
}

export interface UIPermissions {
  pages: string[]
  features: string[]
}

export interface AssignPermissionsRequest {
  userId?: number
  assignments: Assignment[]
  uiPermissions: UIPermissions
  roles: string[]
}

export interface UserPermissionsResponse {
  userId: number
  username: string
  subject: {
    type: string
    name: string
  }
  assignments: Assignment[]
  uiPermissions: UIPermissions
  roles: string[]
}

export const useUsers = () => {
  const { $api } = useNuxtApp()

  const getAllUsers = async (): Promise<UserResponse[]> => {
    const response = await $api.get('/admin/users')
    return response.data
  }

  const getUserById = async (id: number): Promise<UserResponse> => {
    const response = await $api.get(`/admin/users/${id}`)
    return response.data
  }

  const createUser = async (request: CreateUserRequest): Promise<UserResponse> => {
    const response = await $api.post('/admin/users', request)
    return response.data
  }

  const updateUser = async (id: number, request: UpdateUserRequest): Promise<UserResponse> => {
    const response = await $api.put(`/admin/users/${id}`, request)
    return response.data
  }

  const deleteUser = async (id: number): Promise<void> => {
    await $api.delete(`/admin/users/${id}`)
  }

  const assignPermissions = async (id: number, request: AssignPermissionsRequest): Promise<UserPermissionsResponse> => {
    const response = await $api.post(`/admin/users/${id}/permissions`, request)
    return response.data
  }

  const getUserPermissions = async (id: number): Promise<UserPermissionsResponse> => {
    const response = await $api.get(`/admin/users/${id}/permissions`)
    return response.data
  }

  return {
    getAllUsers,
    getUserById,
    createUser,
    updateUser,
    deleteUser,
    assignPermissions,
    getUserPermissions
  }
}

