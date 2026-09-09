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

export interface EveMemberTracking {
  baseId?: string
  baseName?: string
  locationId?: string
  locationName?: string
  shipTypeId?: string
  shipTypeName?: string
  lastLogonAt?: string
  lastLogoffAt?: string
  sourceObservedAt?: string
  sourceExpiresAt?: string
}

/** 军团资产当前快照条目。 */
export interface EveCorporationAsset {
  itemId: string
  typeId: number
  typeName?: string
  itemName?: string
  locationId?: string
  locationName?: string
  locationType: 'station' | 'solar_system' | 'item' | 'other'
  locationFlag: string
  quantity: number
  singleton: boolean
  blueprintCopy?: boolean
  lastSeenAt: string
  sourceExpiresAt?: string
}

export interface EveCorporationAssetQuery extends PageQuery {
  keyword?: string
  locationType?: EveCorporationAsset['locationType']
}

/** 军团资产树节点；容器节点只在游戏资产确有子资产时出现。 */
export interface EveCorporationAssetTreeNode {
  key: string
  title: string
  kind: 'solar_system' | 'npc_station' | 'player_structure' | 'corporation_structure' | 'npc_structure' | 'space_asset' | 'corporation_office' | 'corporation_hangar' | 'asset_safety_package' | 'asset_safety_origin' | 'space_assets' | 'warehouse' | 'structure_compartment_group' | 'structure_compartment' | 'ship' | 'ship_compartment_group' | 'ship_compartment' | 'container' | 'asset' | 'unresolved'
  itemId?: string
  typeId?: number
  typeName?: string
  itemName?: string
  quantity?: number
  locationFlag?: string
  singleton?: boolean
  blueprintCopy?: boolean
  lastSeenAt?: string
  sourceExpiresAt?: string
  children: EveCorporationAssetTreeNode[]
}

/** 军团资产当前完整层级快照。 */
export interface EveCorporationAssetTree {
  nodes: EveCorporationAssetTreeNode[]
  assetCount: number
}

export interface EveAssetSyncResult {
  assetCount: number
  pageCount: number
  synchronizedAt: string
  sourceExpiresAt?: string
}

/** evedata.xlsx 导入后的数据库静态资料统计。 */
export interface EveStaticReferenceImportResult {
  sourceFileName: string
  sourceUpdatedAt: string
  typeCount: number
  locationCount: number
  imported: boolean
}

export interface EveStaticTypeReference {
  typeId: number
  typeName: string
  typeDescription?: string
  marketCategoryL1: string
  marketCategoryL2: string
  marketCategoryL3: string
  marketCategoryL4: string
  marketCategoryL5: string
  marketCategoryL6: string
  sourceUpdatedAt: string
}

export interface EveStaticTypeReferenceQuery extends PageQuery {
  keyword?: string
  marketCategoryL1?: string
}

export interface EveStaticLocationReference {
  referenceType: 'REGION' | 'CONSTELLATION' | 'SOLAR_SYSTEM' | 'NPC_STATION' | 'PUBLIC_STRUCTURE'
  referenceId: string
  referenceName: string
  solarSystemId?: string
  constellationId?: string
  regionId?: string
  securityStatus?: number
  sourceUpdatedAt: string
}

export interface EveStaticLocationReferenceQuery extends PageQuery {
  keyword?: string
  referenceType?: EveStaticLocationReference['referenceType']
}

export interface EveMember {
  id: string
  characterId: string
  characterName?: string
  organizationGroup?: string
  memberNote?: string
  status: 'ACTIVE' | 'LEFT'
  joinedAt?: string
  leftAt?: string
  lastSeenAt: string
  /** 无 eve:members:track:view 时服务端不会返回该字段。 */
  tracking?: EveMemberTracking
}

export interface EveMemberPageQuery extends PageQuery {
  keyword?: string
  status?: string
}

export interface EveMemberSyncResult {
  rosterCount: number
  trackingCount: number
  trackingSynchronized: boolean
  trackingUnavailableReason?: string
  synchronizedAt: string
  rosterSourceExpiresAt?: string
  trackingSourceExpiresAt?: string
}

export interface EveMemberSyncRun {
  id: string
  resource: 'ROSTER' | 'TRACKING'
  status: 'SUCCEEDED' | 'FAILED' | 'SKIPPED'
  recordCount: number
  sourceExpiresAt?: string
  failureCode?: string
  startedAt: string
  finishedAt?: string
}

export interface EveMemberOperationAudit {
  id: string
  rosterMemberId?: string
  targetUserId?: string
  eventType: string
  summary: string
  actorUserId: string
  actorUsername?: string
  occurredAt: string
}

export interface EveMemberOrganizationReq {
  organizationGroup?: string
  memberNote?: string
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

/** 查询当前军团最近完整资产快照。 */
export function getEveCorporationAssets(query: EveCorporationAssetQuery) {
  return http.get<PageRes<EveCorporationAsset[]>>('/eve/assets', query)
}

/** 查询按星系、空间站或建筑、仓库与实际容器组织的完整资产树。 */
export function getEveCorporationAssetTree() {
  return http.get<EveCorporationAssetTree>('/eve/assets/tree')
}

/** 同步当前军团完整资产快照。 */
export function syncEveCorporationAssets() {
  return http.post<EveAssetSyncResult>('/eve/assets/sync')
}

/** 用新的 evedata.xlsx 原子更新数据库中的 EVE 静态资料。 */
export function importEveStaticReference(file: FormData) {
  return http.post<EveStaticReferenceImportResult>('/eve/reference/import', file)
}

/** 分页查询 evedata.xlsx 中的物品类型资料。 */
export function getEveStaticTypes(query: EveStaticTypeReferenceQuery) {
  return http.get<PageRes<EveStaticTypeReference[]>>('/eve/reference/types', query)
}

/** 导出当前筛选条件下的物品类型资料。 */
export function exportEveStaticTypes(query: Pick<EveStaticTypeReferenceQuery, 'keyword' | 'marketCategoryL1'>) {
  return http.download('/eve/reference/types/export', query)
}

/** 分页查询 evedata.xlsx 中的星域、星座、星系与建筑位置资料。 */
export function getEveStaticLocations(query: EveStaticLocationReferenceQuery) {
  return http.get<PageRes<EveStaticLocationReference[]>>('/eve/reference/locations', query)
}

/** 导出当前筛选条件下的位置资料。 */
export function exportEveStaticLocations(query: Pick<EveStaticLocationReferenceQuery, 'keyword' | 'referenceType'>) {
  return http.download('/eve/reference/locations/export', query)
}

/** 查询当前军团完整游戏成员名册。 */
export function getEveMembers(query: EveMemberPageQuery) {
  return http.get<PageRes<EveMember[]>>('/eve/members', query)
}

/** 查询当前军团单个游戏成员。 */
export function getEveMember(characterId: string) {
  return http.get<EveMember>(`/eve/members/${characterId}`)
}

/** 更新成员在本军团内的分组与备注。 */
export function updateEveMemberOrganization(characterId: string, data: EveMemberOrganizationReq) {
  return http.put<EveMember>(`/eve/members/${characterId}/organization`, data)
}

/** 查询成员名册、追踪资源的最近同步批次。 */
export function getEveMemberSyncRuns(limit = 20) {
  return http.get<EveMemberSyncRun[]>('/eve/members/sync-runs', { limit })
}

/** 查询最近成员分组与备注调整记录。 */
export function getEveMemberOrganizationAudit(limit = 20) {
  return http.get<EveMemberOperationAudit[]>('/eve/members/activities', { limit })
}

/** 导出当前筛选条件下的成员资料。 */
export function exportEveMembers(query: Pick<EveMemberPageQuery, 'keyword' | 'status'>) {
  return http.download('/eve/members/export', query)
}

/** 查询最近成员业务角色调整记录。 */
export function getEveMemberRoleAudit(limit = 20) {
  return http.get<EveMemberOperationAudit[]>('/eve/rbac/audit', { limit })
}

/** 手动同步名册与可用的追踪资源。 */
export function syncEveMembers() {
  return http.post<EveMemberSyncResult>('/eve/members/sync')
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
