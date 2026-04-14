export interface MailConfig {
  'mail.enabled': string
  'mail.smtp.host': string
  'mail.smtp.port': string
  'mail.smtp.username': string
  'mail.smtp.password': string
  'mail.smtp.use.tls': string
  'mail.from.address': string
  'mail.from.name': string
}

export interface LDAPConfig {
  'ldap.enabled': string
  'ldap.server.url': string
  'ldap.server.port': string
  'ldap.bind.dn': string
  'ldap.bind.password': string
  'ldap.user.search.base': string
  'ldap.user.search.filter': string
  'ldap.group.search.base': string
  'ldap.group.search.filter': string
  'ldap.use.ssl': string
}

export interface LDAPStatus {
  enabled: boolean
  connected: boolean
}

export interface TestResult {
  success: boolean
  message: string
}

export interface LDAPUser {
  username: string
  email: string
  displayName: string
}

export const useSettings = () => {
  const { $api } = useNuxtApp()

  // Mail Configuration
  const getMailConfig = async (): Promise<MailConfig> => {
    const response = await $api.get<MailConfig>('/admin/settings/mail')
    return response.data
  }

  const updateMailConfig = async (config: Partial<MailConfig>): Promise<void> => {
    await $api.post('/admin/settings/mail', config)
  }

  const testMailConnection = async (): Promise<TestResult> => {
    const response = await $api.post<TestResult>('/admin/settings/mail/test')
    return response.data
  }

  // LDAP Configuration
  const getLDAPConfig = async (): Promise<LDAPConfig> => {
    const response = await $api.get<LDAPConfig>('/admin/ldap/config')
    return response.data
  }

  const updateLDAPConfig = async (config: Partial<LDAPConfig>): Promise<void> => {
    await $api.post('/admin/ldap/config', config)
  }

  const testLDAPConnection = async (): Promise<TestResult> => {
    const response = await $api.post<TestResult>('/admin/ldap/test')
    return response.data
  }

  const getLDAPStatus = async (): Promise<LDAPStatus> => {
    const response = await $api.get<LDAPStatus>('/admin/ldap/status')
    return response.data
  }

  const enableLDAP = async (enabled: boolean): Promise<void> => {
    await $api.post('/admin/ldap/enable', { enabled })
  }

  const syncLDAPUsers = async (): Promise<{ message: string; count: number }> => {
    const response = await $api.post<{ message: string; count: number }>('/admin/ldap/sync-users')
    return response.data
  }

  const searchLDAPUsers = async (query: string = '', limit: number = 20): Promise<LDAPUser[]> => {
    const response = await $api.get<LDAPUser[]>('/admin/ldap/search-users', {
      params: { query, limit }
    })
    return response.data
  }

  return {
    // Mail
    getMailConfig,
    updateMailConfig,
    testMailConnection,
    
    // LDAP
    getLDAPConfig,
    updateLDAPConfig,
    testLDAPConnection,
    getLDAPStatus,
    enableLDAP,
    syncLDAPUsers,
    searchLDAPUsers
  }
}

