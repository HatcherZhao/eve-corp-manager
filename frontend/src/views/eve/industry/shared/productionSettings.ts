export interface IndustryProductionFields {
  productionLines: number
  skillTimePercent: number
  salesTaxPercent: number
  brokerFeePercent: number
}

/** 保留配置中的额外字段，拒绝把损坏的数据静默改写为空对象。 */
function readObject(value: string | undefined): Record<string, unknown> {
  const parsed: unknown = JSON.parse(value || '{}')
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('生产配置格式不正确，请联系管理员检查原始数据')
  }
  return parsed as Record<string, unknown>
}

/** 兼容历史数字字符串，但不把无效值伪装成默认配置。 */
function readNumber(settings: Record<string, unknown>, key: string, fallback: number) {
  const value = settings[key]
  if (value === undefined) return fallback
  const number = typeof value === 'number' || (typeof value === 'string' && value.trim()) ? Number(value) : Number.NaN
  if (!Number.isFinite(number)) throw new Error('生产配置中存在无效数值，请联系管理员检查原始数据')
  return number
}

/** 避免百分数换算把 3.6 显示成 3.5999999999999996。 */
function percentNumber(value: number) {
  return Number(value.toPrecision(15))
}

/** 将后台小数费率转换为玩家表单中的百分数。 */
export function readIndustryProductionFields(productionSkillJson?: string, marketFeeJson?: string): IndustryProductionFields {
  const skills = readObject(productionSkillJson)
  const fees = readObject(marketFeeJson)
  return {
    productionLines: readNumber(skills, 'productionLines', 1),
    skillTimePercent: percentNumber(readNumber(skills, 'timeMultiplier', 1) * 100),
    salesTaxPercent: percentNumber(readNumber(fees, 'salesTax', 0) * 100),
    brokerFeePercent: percentNumber(readNumber(fees, 'brokerFee', 0) * 100),
  }
}

/** 仅更新常用字段，百分数转回原接口的小数单位，保留其他配置。 */
export function writeIndustryProductionFields(fields: IndustryProductionFields, productionSkillJson?: string, marketFeeJson?: string) {
  if (!Object.values(fields).every((value) => typeof value === 'number' && Number.isFinite(value))
    || !Number.isInteger(fields.productionLines) || fields.productionLines < 1 || fields.skillTimePercent <= 0
    || fields.salesTaxPercent < 0 || fields.salesTaxPercent > 100 || fields.brokerFeePercent < 0 || fields.brokerFeePercent > 100) {
    throw new Error('请填写有效的生产线数量、耗时比例和市场费率')
  }
  return {
    productionSkillJson: JSON.stringify({ ...readObject(productionSkillJson), productionLines: fields.productionLines, timeMultiplier: percentNumber(fields.skillTimePercent / 100) }),
    marketFeeJson: JSON.stringify({ ...readObject(marketFeeJson), salesTax: percentNumber(fields.salesTaxPercent / 100), brokerFee: percentNumber(fields.brokerFeePercent / 100) }),
  }
}
