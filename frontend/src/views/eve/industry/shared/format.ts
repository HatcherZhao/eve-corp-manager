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
  return value === 'EXECUTABLE' ? '可执行' : value === 'TRIAL' ? '可试算' : '不可执行'
}

/** 返回与机会状态一致的 Arco 标签颜色。 */
export function industryStatusColor(value: EveIndustryOpportunity['status']) {
  return value === 'EXECUTABLE' ? 'green' : value === 'TRIAL' ? 'orange' : 'red'
}

const riskLabels: Record<string, string> = {
  ORDER_DEPTH_INSUFFICIENT: '订单深度不足',
  NO_HISTORY_VOLUME: '无成交历史',
  ADJUSTED_PRICE_MISSING: '缺少调整价格',
  SCI_MISSING: '缺少星系成本指数',
  TQ_FALLBACK_NOT_SERENITY_VERIFIED: '国际服回退配方',
  ACTIVITY_NOT_IN_EXECUTABLE_SCOPE: '活动暂不支持正式执行',
}

/** 将风险代码转换为适合表格和详情展示的中文说明。 */
export function industryRiskLabel(tags: string[]) {
  return tags.map((tag) => riskLabels[tag] || tag).join('、') || '无'
}
