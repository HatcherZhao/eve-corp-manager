import dayjs, { type Dayjs } from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'

dayjs.extend(utc)
dayjs.extend(timezone)

/** 系统展示和用户输入统一使用北京时间，避免浏览器时区影响军团运营时间。 */
export const EVE_TIMEZONE = 'Asia/Shanghai'

const hasOffset = /(?:Z|[+-]\d{2}:?\d{2})$/i

/** 解析服务端瞬时值；兼容历史无时区值，按北京时间解释。 */
export function parseEveTime(value?: string | number | Date): Dayjs {
  if (value === undefined || value === null || value === '') return dayjs('invalid')
  if (value instanceof Date || typeof value === 'number') return dayjs(value).tz(EVE_TIMEZONE)
  if (!dayjs(value).isValid()) return dayjs('invalid')
  return hasOffset.test(value) ? dayjs(value).tz(EVE_TIMEZONE) : dayjs.tz(value, EVE_TIMEZONE)
}

/** 获取当前北京时间。 */
export function nowEveTime() {
  return dayjs().tz(EVE_TIMEZONE)
}

/** 将日期选择器生成的北京时间转换为带偏移的 ISO 字符串。 */
export function toEveIso(value?: string) {
  return value ? dayjs.tz(value, EVE_TIMEZONE).format('YYYY-MM-DDTHH:mm:ssZ') : undefined
}

export function formatEveTime(value?: string | number | Date, pattern = 'YYYY-MM-DD HH:mm') {
  const parsed = parseEveTime(value)
  return parsed.isValid() ? parsed.format(pattern) : '—'
}

export function formatEveDate(value?: string | number | Date, pattern = 'YYYY-MM-DD') {
  if (!value) return '—'
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) return dayjs(value).format(pattern)
  return formatEveTime(value, pattern)
}
