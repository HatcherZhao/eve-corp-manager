/** 本站图片缓存入口；开发环境经 Vite 代理转发，生产环境由 API 网关转发。 */
const EVE_IMAGE_BASE_URL = `${String(import.meta.env.VITE_API_PREFIX || '').replace(/\/$/, '')}/eve/images`

/** 返回角色游戏肖像地址；无有效角色 ID 时交由调用方显示本站默认头像。 */
export function getEveCharacterPortraitUrl(characterId?: string | number) {
  const id = String(characterId || '').trim()
  return /^\d+$/.test(id) ? `${EVE_IMAGE_BASE_URL}/characters/${id}.jpg` : undefined
}

/** 返回军团游戏徽标地址；无有效军团 ID 时交由调用方显示占位图标。 */
export function getEveCorporationLogoUrl(corporationId?: string | number) {
  const id = String(corporationId || '').trim()
  return /^\d+$/.test(id) ? `${EVE_IMAGE_BASE_URL}/corporations/${id}.png` : undefined
}

/** 返回 EVE 物品类型图标；舰船、建筑与普通物品共用类型 ID 图片服务。 */
export function getEveTypeIconUrl(typeId?: string | number) {
  const id = String(typeId || '').trim()
  return /^\d+$/.test(id) ? `${EVE_IMAGE_BASE_URL}/types/${id}.png` : undefined
}
