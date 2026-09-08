import http from '@/utils/http'

export type EveCapabilityStatus =
  | 'AVAILABLE'
  | 'MISSING_SITE_PERMISSION'
  | 'MISSING_SCOPE'
  | 'MISSING_GAME_ROLE'
  | 'AUTH_EXPIRED'
  | 'NO_DATA_SOURCE'

export interface EveCapability {
  key: string
  title: string
  description: string
  status: EveCapabilityStatus
  requiredScope?: string
  requiredGameRole?: string
}

export interface EveContext {
  tenantId: string
  character?: { id: string, characterId: string, name: string }
  corporation?: { corporationId: string, name: string, ticker: string }
  derivedIdentity: string
  authorizationStatus: EveCapabilityStatus
  capabilities: EveCapability[]
  permissionFreshness?: {
    lastCheckedAt?: string
    snapshotCapturedAt?: string
    cacheExpiresAt?: string
    cacheExpiryEstimated: boolean
  }
}

export interface Workspace {
  server: string
  authorizationStatus: string
  capabilities: Array<Pick<EveCapability, 'key' | 'title' | 'description' | 'status'>>
}

export interface EveAuthorizationStart { authorizationUri: string }

export interface EveRegistrationCallbackResult {
  credential: string
  characterName: string
  corporationName: string
  ceo: boolean
  director: boolean
}

export interface EveRegistrationCompleteReq {
  credential: string
  username: string
  password: string
  confirmPassword: string
  clientId: string
}

export interface EveRegistrationCompleteResult {
  token: string
  tenantId: string
  userId: string
  tenantCreated: boolean
}

export interface EvePasswordRecoveryCallbackResult {
  credential: string
  characterName: string
}

export interface EvePasswordRecoveryCompleteReq {
  credential: string
  password: string
  confirmPassword: string
}

export interface EvePermissionRefreshResult {
  status: 'REFRESHED' | 'COOLDOWN' | 'REAUTHORIZATION_REQUIRED' | 'AUTHORIZATION_DISABLED' | 'UPSTREAM_UNAVAILABLE' | 'MEMBERSHIP_INVALID'
  requestedAt?: string
  upstreamCheckedAt?: string
  sourceExpiresAt?: string
  sourceExpiryEstimated: boolean
  lastRoleChangedAt?: string
  nextSuggestedRefreshAt?: string
  missingScopes: string[]
  reauthorizationPath?: string
  addedGameRoles: Array<{ sourceScope: string, role: string }>
  removedGameRoles: Array<{ sourceScope: string, role: string }>
  derivedIdentityChange?: { before?: string, after?: string }
  capabilityChanges: Array<{
    key: string
    before?: EveCapabilityStatus
    after?: EveCapabilityStatus
  }>
}

export interface EvePermissionNode {
  key: string
  displayName: string
  code: string
  description: string
  owned: boolean
  known: boolean
  sourceScope: string
  capabilities: string[]
  checkedAt?: string
  dataSource: string
}

export interface EvePermissionTree {
  status: 'READY' | 'CHARACTER_NOT_BOUND' | 'SNAPSHOT_UNAVAILABLE' | 'AUTHORIZATION_UNAVAILABLE' | 'AUTHORIZATION_INACTIVE'
  message: string
  character?: { characterRefId: string, characterId: string, name: string, corporationId: string }
  gamePermissions?: {
    ceo?: EvePermissionNode
    groups: Array<{ key: string, displayName: string, sourceScope: string, children: EvePermissionNode[] }>
    checkedAt?: string
    sourceExpiresAt?: string
    sourceExpiryEstimated: boolean
    dataSource: string
  }
  siteRoles?: {
    roles: Array<{
      id: string
      code: string
      name: string
      description: string
      source: string
      derived: boolean
      system: boolean
      dataScope: string
      permissions: string[]
    }>
    effectivePermissions: string[]
    checkedAt?: string
    dataSource: string
  }
  scopes?: {
    authorizationStatus: string
    items: Array<{
      key: string
      scope: string
      displayName: string
      owned: boolean
      identityRequired: boolean
      capabilities: string[]
    }>
    updatedAt?: string
    dataSource: string
  }
}

export interface EveRbacOverview {
  permissions: Array<{ permission: string, name: string }>
  roles: Array<{ id: string, name: string, code: string, description?: string, permissions: string[] }>
  members: Array<{
    id: string
    username: string
    nickname: string
    derivedIdentity: 'OWNER' | 'ADMIN' | 'MEMBER' | 'NONE'
    businessRoleIds: string[]
  }>
}

export interface EveBusinessRoleReq {
  name: string
  description?: string
  permissions: string[]
}

export function getWorkspace() {
  return http.get<Workspace>('/eve/workspace')
}

export function getEveContext() {
  return http.get<EveContext>('/eve/me/context')
}

export function startEveRegistration() {
  return http.post<EveAuthorizationStart>('/eve/registration/start')
}

export function verifyEveRegistrationCallback(callbackUrl: string) {
  return http.post<EveRegistrationCallbackResult>('/eve/registration/callback', { callbackUrl })
}

export function completeEveRegistration(data: EveRegistrationCompleteReq) {
  return http.post<EveRegistrationCompleteResult>('/eve/registration/complete', data)
}

export function startEvePasswordRecovery() {
  return http.post<EveAuthorizationStart>('/eve/password-recovery/start')
}

export function verifyEvePasswordRecoveryCallback(callbackUrl: string) {
  return http.post<EvePasswordRecoveryCallbackResult>('/eve/password-recovery/callback', { callbackUrl })
}

export function completeEvePasswordRecovery(data: EvePasswordRecoveryCompleteReq) {
  return http.post<void>('/eve/password-recovery/complete', data)
}

export function startEveCharacterBinding() {
  return http.post<EveAuthorizationStart>('/eve/characters/binding/start')
}

export function completeEveCharacterBinding(callbackUrl: string) {
  return http.post<EveContext>('/eve/characters/binding/callback', { callbackUrl })
}

export function getEvePermissionTree() {
  return http.get<EvePermissionTree>('/eve/permissions/tree')
}

export function refreshEvePermissions() {
  return http.post<EvePermissionRefreshResult>('/eve/permissions/refresh')
}

export function startEveReauthorization() {
  return http.post<EveAuthorizationStart>('/eve/permissions/reauthorization/start')
}

export function completeEveReauthorization(callbackUrl: string) {
  return http.post<EvePermissionRefreshResult>('/eve/permissions/reauthorization/callback', { callbackUrl })
}

export function getEveRbacOverview() {
  return http.get<EveRbacOverview>('/eve/rbac/overview')
}

export function createEveBusinessRole(data: EveBusinessRoleReq) {
  return http.post<string>('/eve/rbac/roles', data)
}

export function updateEveBusinessRole(roleId: string, data: EveBusinessRoleReq) {
  return http.put<void>(`/eve/rbac/roles/${roleId}`, data)
}

export function deleteEveBusinessRole(roleId: string) {
  return http.del<void>(`/eve/rbac/roles/${roleId}`)
}

export function assignEveMemberRoles(userId: string, roleIds: string[]) {
  return http.put<void>(`/eve/rbac/members/${userId}/roles`, { roleIds })
}
