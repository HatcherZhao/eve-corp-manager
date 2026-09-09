/**
 * 使用前端同一套 RSA 加密、租户请求头和图形验证码流程验证本地真实账号登录。
 * 密码仅从环境变量读取，脚本不会输出密码、验证码或会话令牌。
 */
import { constants, createPublicKey, publicEncrypt } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'

const baseUrl = 'http://127.0.0.1:8000'
const username = process.env.EVE_LOGIN_USERNAME || 'zhaoyuqing_test@163.com'
const tenantName = process.env.EVE_LOGIN_TENANT_NAME || '穆塔根商人协会'
const clientId = 'ef51c9a3e9046c4f2ea45142c8a8344a'
const password = process.env.EVE_LOGIN_PASSWORD

if (!password) {
  throw new Error('缺少 EVE_LOGIN_PASSWORD，无法执行真实登录验证。')
}

const envFile = readFileSync(new URL('../../frontend/.env.local', import.meta.url), 'utf8')
const publicKeyBase64 = envFile.match(/^VITE_RSA_PUBLIC_KEY=(.+)$/m)?.[1]
if (!publicKeyBase64) {
  throw new Error('未找到前端 RSA 公钥配置。')
}

const captchaResponse = await fetch(`${baseUrl}/captcha/image`)
if (!captchaResponse.ok) {
  throw new Error(`获取验证码失败（HTTP ${captchaResponse.status}）。`)
}
const captchaPayload = await captchaResponse.json()
const captchaData = captchaPayload.data
if (!captchaData?.uuid || !captchaData.isEnabled) {
  throw new Error('服务未返回可用的图形验证码。')
}

const captchaValue = execFileSync('redis-cli', ['-n', '14', '--raw', 'GET', `CAPTCHA:${captchaData.uuid}`], {
  encoding: 'utf8',
}).trim()
if (!captchaValue || captchaValue === '(nil)') {
  throw new Error('验证码未写入 Redis db14。')
}
// RedisUtils 使用 JsonJacksonCodec 保存字符串，redis-cli 读取到的是 JSON 字符串字面量。
const captcha = JSON.parse(captchaValue)

const publicKey = createPublicKey({
  key: Buffer.from(publicKeyBase64, 'base64'),
  format: 'der',
  type: 'spki',
})
const encryptedPassword = publicEncrypt({ key: publicKey, padding: constants.RSA_PKCS1_PADDING }, Buffer.from(password))
  .toString('base64')
const loginResponse = await fetch(`${baseUrl}/auth/login`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    ...(tenantName ? { 'X-Tenant-Code': encodeURIComponent(tenantName) } : {}),
  },
  body: JSON.stringify({
    authType: 'ACCOUNT',
    clientId,
    username,
    password: encryptedPassword,
    captcha,
    uuid: captchaData.uuid,
  }),
})
const loginPayload = await loginResponse.json()
if (!loginResponse.ok || !loginPayload?.data?.token || !loginPayload.data.tenantId) {
  throw new Error(`登录失败：${loginPayload?.msg || `HTTP ${loginResponse.status}`}。`)
}

const sessionHeaders = {
  Authorization: `Bearer ${loginPayload.data.token}`,
  'X-Tenant-Id': String(loginPayload.data.tenantId),
}
const [userInfoResponse, routesResponse] = await Promise.all([
  fetch(`${baseUrl}/auth/user/info`, { headers: sessionHeaders }),
  fetch(`${baseUrl}/auth/user/route`, { headers: sessionHeaders }),
])
const [userInfoPayload, routesPayload] = await Promise.all([userInfoResponse.json(), routesResponse.json()])
if (!userInfoResponse.ok || !userInfoPayload?.data || !routesResponse.ok || !Array.isArray(routesPayload?.data)) {
  throw new Error('登录后的用户资料或动态路由请求失败。')
}

if (process.env.EVE_LOGIN_REFRESH_PERMISSIONS === 'true') {
  const refreshResponse = await fetch(`${baseUrl}/eve/permissions/refresh`, {
    method: 'POST',
    headers: sessionHeaders,
  })
  const refreshPayload = await refreshResponse.json()
  if (!refreshResponse.ok || !refreshPayload?.data) {
    throw new Error(`游戏权限刷新失败：${refreshPayload?.msg || `HTTP ${refreshResponse.status}`}。`)
  }
  console.log('真实游戏权限刷新验证通过。')
}

if (process.env.EVE_LOGIN_SYNC_ASSETS === 'true') {
  const syncResponse = await fetch(`${baseUrl}/eve/assets/sync`, {
    method: 'POST',
    headers: sessionHeaders,
  })
  const syncPayload = await syncResponse.json()
  const syncResult = syncPayload?.data
  if (!syncResponse.ok || !Number.isInteger(syncResult?.assetCount) || !Number.isInteger(syncResult?.pageCount)) {
    throw new Error(`资产同步失败：${syncPayload?.msg || `HTTP ${syncResponse.status}`}。`)
  }

  const assetsResponse = await fetch(`${baseUrl}/eve/assets?page=1&size=20`, { headers: sessionHeaders })
  const assetsPayload = await assetsResponse.json()
  const snapshot = assetsPayload?.data
  if (!assetsResponse.ok || !Array.isArray(snapshot?.list) || snapshot.total !== syncResult.assetCount) {
    throw new Error('资产快照读取失败或与本次完整同步数量不一致。')
  }
  const treeResponse = await fetch(`${baseUrl}/eve/assets/tree`, { headers: sessionHeaders })
  const treePayload = await treeResponse.json()
  const tree = treePayload?.data
  if (!treeResponse.ok || !Array.isArray(tree?.nodes) || tree.assetCount !== syncResult.assetCount) {
    throw new Error('资产树读取失败或资产总数与本次完整同步不一致。')
  }
  const countAssets = (nodes) => nodes.reduce((count, node) => count + (node.itemId ? 1 : 0) + countAssets(node.children || []), 0)
  if (countAssets(tree.nodes) !== syncResult.assetCount) {
    throw new Error('资产树节点数量与本次完整同步数量不一致。')
  }
  const flattenNodes = (nodes) => nodes.flatMap((node) => [node, ...flattenNodes(node.children || [])])
  const treeNodes = flattenNodes(tree.nodes)
  const untranslatedNpcStations = treeNodes.filter((node) => node.kind === 'station' && /\s-\sMoon\s/i.test(node.title))
  if (untranslatedNpcStations.length) {
    throw new Error(`资产树存在 ${untranslatedNpcStations.length} 个未本地化的 NPC 空间站名称。`)
  }
  const rawWarehouseCodes = /^(Hangar|CorpSAG[1-7]|AutoFit|HiSlot\d+|MedSlot\d+|LoSlot\d+|RigSlot\d+|ServiceSlot\d+|FighterTube\d+)$/
  if (treeNodes.some((node) => node.kind === 'warehouse' && rawWarehouseCodes.test(node.title))) {
    throw new Error('资产树存在未中文化的固定仓位名称。')
  }
  const corporationStructures = treeNodes.filter((node) => node.kind === 'corporation_structure')
  if (!corporationStructures.length) {
    throw new Error('资产树未识别到本军团玩家建筑。')
  }
  if (corporationStructures.some((node) => node.children.some((child) => child.title === '自动装配仓位'))) {
    throw new Error('玩家建筑的部署标记被错误展示为建筑内部仓位。')
  }
  const resolvedTypeCount = snapshot.list.filter((asset) => Boolean(asset.typeName)).length
  const resolvedLocationCount = snapshot.list.filter((asset) => Boolean(asset.locationName)).length
  console.log(`真实资产同步验证通过：${syncResult.assetCount} 条资产、${syncResult.pageCount} 页；资产树包含 ${tree.nodes.length} 个星系分组和 ${corporationStructures.length} 座本军团建筑；首批 ${snapshot.list.length} 条中已解析 ${resolvedTypeCount} 个类型、${resolvedLocationCount} 个位置。`)
}

if (process.env.EVE_LOGIN_VALIDATE_REFERENCE === 'true') {
  const [typesResponse, locationsResponse, exportResponse] = await Promise.all([
    fetch(`${baseUrl}/eve/reference/types?page=1&size=5&keyword=${encodeURIComponent('强制者')}`, { headers: sessionHeaders }),
    fetch(`${baseUrl}/eve/reference/locations?page=1&size=5&referenceType=SOLAR_SYSTEM`, { headers: sessionHeaders }),
    fetch(`${baseUrl}/eve/reference/types/export?keyword=${encodeURIComponent('强制者')}`, { headers: sessionHeaders }),
  ])
  const [typesPayload, locationsPayload, exportText] = await Promise.all([
    typesResponse.json(),
    locationsResponse.json(),
    exportResponse.text(),
  ])
  if (!typesResponse.ok || !Array.isArray(typesPayload?.data?.list) || typesPayload.data.total < 1) {
    throw new Error('静态物品资料列表读取失败。')
  }
  if (!locationsResponse.ok || !Array.isArray(locationsPayload?.data?.list) || locationsPayload.data.total < 1) {
    throw new Error('静态位置资料列表读取失败。')
  }
  // Fetch 的 UTF-8 解码会移除 BOM；下载响应仍携带 BOM 供 Excel 自动识别编码。
  if (!exportResponse.ok || !exportText.startsWith('物品类型ID,')) {
    throw new Error('静态物品资料导出失败。')
  }
  const referenceGroup = routesPayload.data
    .flatMap((item) => item.children || [])
    .find((item) => item.path === '/eve/reference')
  if (!referenceGroup?.children?.some((item) => item.path === '/eve/reference/types')
      || !referenceGroup.children.some((item) => item.path === '/eve/reference/locations')) {
    throw new Error('基础信息动态菜单未返回物品资料和位置资料。')
  }
  if (process.env.EVE_LOGIN_VALIDATE_REFERENCE_IMPORT === 'true') {
    const workbook = new FormData()
    workbook.append('file', new Blob([readFileSync(new URL('../../docs/evedata.xlsx', import.meta.url))], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    }), 'evedata.xlsx')
    const importResponse = await fetch(`${baseUrl}/eve/reference/import`, {
      method: 'POST',
      headers: sessionHeaders,
      body: workbook,
    })
    const importPayload = await importResponse.json()
    const importResult = importPayload?.data
    const importedTypeCount = importResult?.typeCount ?? importResult?.type_count
    const importedLocationCount = importResult?.locationCount ?? importResult?.location_count
    if (!importResponse.ok || importedTypeCount !== 28433 || importedLocationCount !== 15190) {
      throw new Error(`静态资料批量更新失败：${JSON.stringify(importResult) || importPayload?.msg || `HTTP ${importResponse.status}`}。`)
    }
    console.log('静态资料批量更新验证通过：物品与位置资料已原子替换。')
  }
  console.log(`静态资料页面验证通过：${typesPayload.data.total} 条物品匹配、${locationsPayload.data.total} 条星系资料，导出文件可用。`)
}

if (process.env.EVE_LOGIN_ROUTE_DEBUG === 'true') {
  const summarize = (routes) => routes.map(({ title, path, children }) => ({
    title,
    path,
    children: Array.isArray(children) ? summarize(children) : [],
  }))
  console.log(`动态路由：${JSON.stringify(summarize(routesPayload.data))}`)
}

console.log(`真实登录及页面初始化验证通过：租户 ${loginPayload.data.tenantId}，用户资料和 ${routesPayload.data.length} 条顶级路由均可读取。`)
