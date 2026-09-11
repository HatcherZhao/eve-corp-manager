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
  dataFreshness: EveDataFreshness[]
}

export interface EveDataFreshness {
  module: 'ASSETS' | 'STRUCTURES' | 'MOON_EXTRACTIONS' | 'MINING_LEDGER' | 'MEMBER_ROSTER' | 'MEMBER_TRACKING'
  title: string
  route: string
  status: 'FRESH' | 'STALE' | 'SYNC_FAILED' | 'NO_DATA'
  lastSuccessfulAt?: string
  sourceExpiresAt?: string
  lastFailureAt?: string
  failureCode?: string
  retryable: boolean
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
  kind: 'solar_system' | 'npc_station' | 'player_structure' | 'corporation_structure' | 'npc_structure' | 'space_asset' | 'corporation_office' | 'corporation_hangar' | 'asset_safety_package' | 'asset_safety_origin' | 'space_assets' | 'warehouse' | 'structure_storage_group' | 'structure_corporation_hangar_group' | 'structure_fitting_group' | 'structure_storage' | 'structure_fitting_slot' | 'ship' | 'ship_storage_group' | 'ship_fitting_group' | 'ship_storage' | 'ship_fitting_slot' | 'container' | 'asset' | 'unresolved'
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

/** 军团玩家建筑当前快照。 */
export interface EveCorporationStructure {
  structureId: string
  typeId: number
  typeName?: string
  structureName?: string
  solarSystemId: string
  solarSystemName?: string
  state: string
  fuelExpiresAt?: string
  stateTimerStartAt?: string
  stateTimerEndAt?: string
  unanchorsAt?: string
  services: Array<{ name: string, state: string }>
  status: 'ACTIVE' | 'MISSING'
  lastSeenAt: string
  sourceExpiresAt?: string
  /** 同一月矿堡长期展示的军团内部备注。 */
  structureNote?: string
  note?: string
}

export interface EveCorporationStructureQuery extends PageQuery {
  keyword?: string
  state?: string
}

export interface EveStructureSyncResult {
  structureCount: number
  pageCount: number
  synchronizedAt: string
  sourceExpiresAt?: string
}

/** 国服观察者采矿账本的单条日期聚合记录。 */
export interface EveMiningLedger {
  id: string
  observerId: string
  observerName?: string
  characterId: string
  characterName?: string
  recordedCorporationId: string
  typeId: number
  typeName?: string
  recordedAt: string
  quantity: number
  lastSeenAt: string
  sourceExpiresAt?: string
}

export interface EveMiningLedgerQuery extends PageQuery {
  observerId?: string
  characterId?: string
  typeId?: number
  fromDate?: string
  toDate?: string
  keyword?: string
}

/** 当前筛选条件下由服务端聚合的采矿账本统计。 */
export interface EveMiningLedgerSummary {
  quantity: number
  entryCount: number
  observerCount: number
  characterCount: number
  mineralTypeCount: number
  latestRecordedAt?: string
  latestSynchronizedAt?: string
}

/** 月矿开采统计页面的服务端聚合结果。 */
export interface EveMiningAnalytics {
  corporation: {
    name?: string
    ticker?: string
    quantity: number
    entryCount: number
    observerCount: number
    characterCount: number
    mineralTypeCount: number
  }
  timeline: Array<{
    recordedAt: string
    quantity: number
    entryCount: number
  }>
  observers: Array<{
    observerName?: string
    quantity: number
    entryCount: number
  }>
  characters: Array<{
    characterName?: string
    quantity: number
    entryCount: number
  }>
}

export interface EveMiningSyncResult {
  observerCount: number
  ledgerCount: number
  pageCount: number
  synchronizedAt: string
  sourceExpiresAt?: string
}

/** 国服月矿提取时间线。 */
export interface EveMoonExtraction {
  id: string
  structureId: string
  structureName?: string
  structureTypeName?: string
  moonId: string
  moonName?: string
  solarSystemId?: string
  solarSystemName?: string
  extractionStartAt: string
  chunkArrivalAt: string
  naturalDecayAt: string
  status: 'ACTIVE' | 'MISSING'
  lastSeenAt: string
  sourceExpiresAt?: string
  note?: string
}

export interface EveMoonExtractionQuery extends PageQuery {
  keyword?: string
  timeline?: 'UPCOMING' | 'ARRIVED'
}

export interface EveMoonExtractionSyncResult {
  extractionCount: number
  pageCount: number
  synchronizedAt: string
  sourceExpiresAt?: string
}

export interface EveMoonExtractionNoteReq {
  note?: string
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
  marketCategoryL2?: string
  marketCategoryL3?: string
  marketCategoryL4?: string
  marketCategoryL5?: string
  marketCategoryL6?: string
  unclassified?: boolean
}

/** 游戏市场左侧导航使用的物品分类树节点。 */
export interface EveStaticTypeCategoryNode {
  name: string
  path: string[]
  directTypeCount: number
  typeCount: number
  unclassified: boolean
  children: EveStaticTypeCategoryNode[]
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
  hierarchyType?: 'REGION' | 'CONSTELLATION' | 'SOLAR_SYSTEM'
  hierarchyId?: string
}

/** 星图式位置导航使用的星域、星座和星系树节点。 */
export interface EveStaticLocationTreeNode {
  referenceType: 'REGION' | 'CONSTELLATION' | 'SOLAR_SYSTEM'
  referenceId: string
  referenceName: string
  locationCount: number
  children: EveStaticLocationTreeNode[]
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

/** 游戏内邮件收件人类型。 */
export type EveMailRecipientType = 'character' | 'corporation' | 'alliance' | 'mailing_list'

/** 游戏内邮件发件人或收件人的已解析展示信息。 */
export interface EveGameMailParty {
  id?: string
  type: EveMailRecipientType
  name?: string
}

/** 提交给本站、将在服务端解析为游戏 ID 的邮件收件人。 */
export interface EveMailRecipientInput {
  recipientName: string
  recipientType: Exclude<EveMailRecipientType, 'mailing_list'>
}

/** 当前授权角色收件箱中的邮件摘要。 */
export interface EveGameMailSummary {
  mailId: string
  from: EveGameMailParty
  subject: string
  sentAt: string
  read: boolean
  labels: number[]
  recipients: EveGameMailParty[]
  bodyAvailable: boolean
  lastSeenAt: string
  sourceExpiresAt?: string
}

/** 游戏内邮箱的原生分类；名称和未读数均由国服返回。 */
export interface EveGameMailLabel {
  labelId: number
  name: string
  unreadCount: number
}

export interface EveGameMailQuery extends PageQuery {
  keyword?: string
  labelId?: number
}

/** 包含正文与收件人的游戏内邮件详情。 */
export interface EveGameMailDetail {
  mailId: string
  from: EveGameMailParty
  recipients: EveGameMailParty[]
  subject: string
  body: string
  sentAt: string
  read: boolean
  labels: number[]
  bodySynchronizedAt?: string
  bodySourceExpiresAt?: string
}

export interface EveGameMailSyncResult {
  mailCount: number
  synchronizedAt: string
  sourceExpiresAt?: string
}

/** 手动同步请求已交给服务端队列，实际读取受上游缓存与限流策略控制。 */
export interface EveSyncRequestResult {
  accepted: boolean
  message: string
}

export interface EveGameMailSendReq {
  recipients: EveMailRecipientInput[]
  subject: string
  body: string
  approvedCost?: number
}

export interface EveGameMailSendResult {
  mailId: string
  status: string
  sentAt: string
}

/** 游戏通知的产品分类。ALL 表示不按分类筛选。 */
export type EveGameNotificationCategory = 'ALL' | 'CORPORATION_MEMBER' | 'STRUCTURE_ASSET_SAFETY' | 'WAR_SOVEREIGNTY' | 'MOON_INDUSTRY' | 'OTHER'

/** 当前授权角色收到的一条游戏通知。 */
export interface EveGameNotification {
  notificationId: string
  read: boolean
  category: Exclude<EveGameNotificationCategory, 'ALL'>
  type?: string
  senderName?: string
  senderType?: string
  content?: string
  summary?: string
  details?: EveGameNotificationDetailItem[]
  sentAt?: string
  lastSeenAt?: string
  sourceExpiresAt?: string
}

/** 游戏通知详情中已脱敏并转换为中文的字段。 */
export interface EveGameNotificationDetailItem {
  label: string
  value: string
}

export interface EveGameNotificationQuery extends PageQuery {
  category?: EveGameNotificationCategory
  keyword?: string
}

export function getWorkspace() {
  return http.get<Workspace>('/eve/workspace')
}

export function getEveContext() {
  return http.get<EveContext>('/eve/me/context')
}

/** 查询当前军团各数据模块的同步新鲜度与脱敏失败分类。 */
export function getEveDataFreshness() {
  return http.get<EveDataFreshness[]>('/eve/me/data-freshness')
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

/** 请求后台同步当前军团完整资产快照。 */
export function syncEveCorporationAssets() {
  return http.post<EveSyncRequestResult>('/eve/assets/sync')
}

/** 导出当前筛选条件下的军团资产快照。 */
export function exportEveCorporationAssets(query: Pick<EveCorporationAssetQuery, 'keyword' | 'locationType'>) {
  return http.download('/eve/assets/export', query)
}

/** 查询当前军团建筑的已发布快照。 */
export function getEveCorporationStructures(query: EveCorporationStructureQuery) {
  return http.get<PageRes<EveCorporationStructure[]>>('/eve/structures', query)
}

/** 请求后台同步当前军团建筑。 */
export function syncEveCorporationStructures() {
  return http.post<EveSyncRequestResult>('/eve/structures/sync')
}

/** 查询当前军团已发布的观察者采矿账本。 */
export function getEveMiningLedger(query: EveMiningLedgerQuery) {
  return http.get<PageRes<EveMiningLedger[]>>('/eve/mining', query)
}

/** 汇总当前筛选条件下的观察者采矿账本。 */
export function getEveMiningLedgerSummary(query: Omit<EveMiningLedgerQuery, 'page' | 'size'>) {
  return http.get<EveMiningLedgerSummary>('/eve/mining/summary', query)
}

/** 查询当前军团月矿开采的时间、建筑和成员聚合统计。 */
export function getEveMiningAnalytics(query: Omit<EveMiningLedgerQuery, 'page' | 'size'>) {
  return http.get<EveMiningAnalytics>('/eve/mining/analytics', query)
}

/** 请求后台同步当前军团的完整采矿账本。 */
export function syncEveMiningLedger() {
  return http.post<EveSyncRequestResult>('/eve/mining/sync')
}

/** 查询当前军团已发布的月矿情报时间线。 */
export function getEveMoonExtractions(query: EveMoonExtractionQuery) {
  return http.get<PageRes<EveMoonExtraction[]>>('/eve/extractions', query)
}

/** 请求后台读取并原子发布当前军团完整月矿时间线。 */
export function syncEveMoonExtractions() {
  return http.post<EveSyncRequestResult>('/eve/extractions/sync')
}

/** 保存月矿情报的简短备注。 */
export function saveEveMoonExtractionNote(extractionId: string, data: EveMoonExtractionNoteReq) {
  return http.put<EveMoonExtraction>(`/eve/extractions/${extractionId}/note`, data)
}

/** 用新的 evedata.xlsx 原子更新数据库中的 EVE 静态资料。 */
export function importEveStaticReference(file: FormData) {
  return http.post<EveStaticReferenceImportResult>('/eve/reference/import', file)
}

/** 分页查询 evedata.xlsx 中的物品类型资料。 */
export function getEveStaticTypes(query: EveStaticTypeReferenceQuery) {
  return http.get<PageRes<EveStaticTypeReference[]>>('/eve/reference/types', query)
}

/** 查询由 evedata.xlsx 生成的完整游戏市场分类树。 */
export function getEveStaticTypeCategories() {
  return http.get<EveStaticTypeCategoryNode[]>('/eve/reference/types/categories')
}

/** 导出当前筛选条件下的物品类型资料。 */
export function exportEveStaticTypes(query: Pick<EveStaticTypeReferenceQuery, 'keyword' | 'marketCategoryL1' | 'marketCategoryL2' | 'marketCategoryL3' | 'marketCategoryL4' | 'marketCategoryL5' | 'marketCategoryL6' | 'unclassified'>) {
  return http.download('/eve/reference/types/export', query)
}

/** 分页查询 evedata.xlsx 中的星域、星座、星系与建筑位置资料。 */
export function getEveStaticLocations(query: EveStaticLocationReferenceQuery) {
  return http.get<PageRes<EveStaticLocationReference[]>>('/eve/reference/locations', query)
}

/** 查询由 evedata.xlsx 生成的星域、星座和星系导航树。 */
export function getEveStaticLocationTree() {
  return http.get<EveStaticLocationTreeNode[]>('/eve/reference/locations/tree')
}

/** 导出当前筛选条件下的位置资料。 */
export function exportEveStaticLocations(query: Pick<EveStaticLocationReferenceQuery, 'keyword' | 'referenceType' | 'hierarchyType' | 'hierarchyId'>) {
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

/** 请求后台同步名册与可用的追踪资源。 */
export function syncEveMembers() {
  return http.post<EveSyncRequestResult>('/eve/members/sync')
}

/** 查询当前登录用户授权角色的游戏内收件箱。 */
export function getEveGameMails(query: EveGameMailQuery) {
  return http.get<PageRes<EveGameMailSummary[]>>('/eve/mail', query)
}

/** 查询当前授权角色在游戏内的收件箱、已发送和自定义分类。 */
export function getEveGameMailLabels() {
  return http.get<EveGameMailLabel[]>('/eve/mail/labels')
}

/** 请求后台同步当前登录用户授权角色的最近邮件。 */
export function syncEveGameMails() {
  return http.post<EveSyncRequestResult>('/eve/mail/sync')
}

/** 查询当前登录用户授权角色的单封游戏内邮件正文。 */
export function getEveGameMail(mailId: string) {
  return http.get<EveGameMailDetail>(`/eve/mail/${mailId}`)
}

/** 将当前授权角色的游戏内邮件标记为已读。 */
export function markEveGameMailRead(mailId: string) {
  return http.put<EveGameMailDetail>(`/eve/mail/${mailId}/read`)
}

/** 按游戏内名称预校验邮件收件人；只查询，不会发送邮件。 */
export function resolveEveGameMailRecipient(data: EveMailRecipientInput) {
  return http.post<EveGameMailParty>('/eve/mail/recipients/resolve', data)
}

/** 以当前登录用户的授权角色立即发送游戏内邮件。 */
export function sendEveGameMail(data: EveGameMailSendReq) {
  return http.post<EveGameMailSendResult>('/eve/mail/send', data)
}

/** 查询当前授权角色已同步的游戏通知。 */
export function getEveGameNotifications(query: EveGameNotificationQuery) {
  return http.get<PageRes<EveGameNotification[]>>('/eve/notifications', query)
}

/** 请求后台同步当前授权角色的游戏通知。 */
export function syncEveGameNotifications() {
  return http.post<EveSyncRequestResult>('/eve/notifications/sync')
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
