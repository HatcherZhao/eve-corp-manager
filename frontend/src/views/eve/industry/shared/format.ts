import type { EveIndustryOpportunity } from '@/apis/eve'

/** 将工业金额统一格式化为 ISK，缺失值不伪装为零。 */
export function formatIndustryIsk(value?: number) {
  return value === undefined || value === null
    ? '—'
    : `${Math.round(value).toLocaleString()} ISK`
}

/** 将小数比率转换为界面百分比。 */
export function formatIndustryPercent(value?: number) {
  return value === undefined || value === null ? '—' : `${(value * 100).toFixed(1)}%`
}

/** 将机会状态转换为用户可读名称。 */
export function industryStatusLabel(value: EveIndustryOpportunity['status']) {
  return value === 'EXECUTABLE' ? '可加入计划' : value === 'TRIAL' ? '仅供估算' : '暂不可用'
}

/** 返回与机会状态一致的 Arco 标签颜色。 */
export function industryStatusColor(value: EveIndustryOpportunity['status']) {
  return value === 'EXECUTABLE' ? 'green' : value === 'TRIAL' ? 'orange' : 'red'
}

const riskLabels: Record<string, string> = {
  DEMAND_UNAVAILABLE: '市场需求不足，无法推荐产量',
  ORDER_DEPTH_INSUFFICIENT: '市场挂单数量不足',
  MARKET_PRICE_PENDING: '相关物品行情正在后台按需准备，请稍后重新扫描',
  MARKET_PRICE_REFRESH_FAILED: '相关物品行情刷新失败，本次未使用旧报价',
  NO_HISTORY_VOLUME: '缺少历史成交量',
  ADJUSTED_PRICE_MISSING: '缺少计算安装费所需的官方参考价',
  SCI_MISSING: '缺少星系成本指数',
  TQ_FALLBACK_NOT_SERENITY_VERIFIED: '国际服配方，尚未核对国服数据',
  ACTIVITY_NOT_IN_EXECUTABLE_SCOPE: '该生产类型暂不支持加入计划',
}

/** 将风险代码转换为适合表格和详情展示的中文说明。 */
export function industryRiskLabel(tags: string[]) {
  return tags.map((tag) => riskLabels[tag] || `未识别的风险（${tag}）`).join('、') || '无已知风险'
}

/** 使用游戏内的中文工业活动名称，内部活动代码保持不变。 */
export function industryActivityLabel(value?: string) {
  const labels: Record<string, string> = {
    MANUFACTURING: '制造',
    REACTION: '反应',
    INVENTION: '发明',
    COPYING: '复制',
    RESEARCH_MATERIAL: '材料效率研究',
    RESEARCH_TIME: '时间效率研究',
  }
  return value ? labels[value] || `其他工业活动（${value}）` : '未指定活动'
}

/** 区分等待核对、已核对和存在差异，避免直接显示后台状态码。 */
export function industryValidationLabel(value: string) {
  const labels: Record<string, string> = { PENDING: '待核对', VALIDATED: '已校验', REJECTED: '未通过', PASSED: '一致', FAILED: '存在差异' }
  return labels[value] || '未知状态'
}

/** 将蓝图数据来源转换为玩家可识别的名称。 */
export function industrySourceLabel(value: string) {
  const labels: Record<string, string> = { SERENITY_EXPORT: '国服导出数据', TRANQUILITY_SDE: '国际服参考数据', CEVE_INDUSTRY_PUBLIC: '第三方公开蓝图' }
  return labels[value] || '其他数据来源'
}

/** 展示计划步骤的中文名称。 */
export function industryStepLabel(value: string) {
  const labels: Record<string, string> = { PURCHASE: '采购材料', PRODUCTION: '生产', SALES: '出售产品' }
  return labels[value] || '其他步骤'
}
