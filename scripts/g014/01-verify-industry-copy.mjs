import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'

const root = new URL('../../', import.meta.url)
const require = createRequire(new URL('frontend/package.json', root))
const ts = require('typescript')
async function loadTs(path) {
  const source = await readFile(new URL(path, root), 'utf8')
  const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } })
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
}

const format = await loadTs('frontend/src/views/eve/industry/shared/format.ts')
assert.equal(format.industryActivityLabel('MANUFACTURING'), '制造')
assert.equal(format.industryActivityLabel('REACTION'), '反应')
assert.equal(format.industryActivityLabel('INVENTION'), '发明')
assert.equal(format.industryStatusLabel('TRIAL'), '仅供估算')
assert.equal(format.industryStatusLabel('EXECUTABLE'), '可加入计划')
assert.equal(format.industryValidationLabel('PASSED'), '一致')
assert.equal(format.industryValidationLabel('FAILED'), '存在差异')
assert.equal(format.industryStepLabel('PURCHASE'), '采购材料')
assert.equal(format.industrySourceLabel('TRANQUILITY_SDE'), '国际服参考数据')
for (const tag of ['DEMAND_UNAVAILABLE', 'ORDER_DEPTH_INSUFFICIENT', 'NO_HISTORY_VOLUME', 'ADJUSTED_PRICE_MISSING', 'SCI_MISSING', 'TQ_FALLBACK_NOT_SERENITY_VERIFIED', 'ACTIVITY_NOT_IN_EXECUTABLE_SCOPE']) {
  assert.doesNotMatch(format.industryRiskLabel([tag]), /[A-Z_]/)
}
assert.match(format.industryRiskLabel(['NEW_RISK']), /未识别的风险/)
assert.equal(format.formatIndustryIsk(undefined), '—')
assert.equal(format.formatIndustryPercent(0.05), '5.0%')

const settings = await loadTs('frontend/src/views/eve/industry/shared/productionSettings.ts')
assert.deepEqual(settings.readIndustryProductionFields(), { productionLines: 1, skillTimePercent: 100, salesTaxPercent: 0, brokerFeePercent: 0 })
const skills = '{"productionLines":11,"timeMultiplier":0.8,"customSkill":5}'
const fees = '{"salesTax":0.036,"brokerFee":0.01,"customFee":0.05}'
const fields = settings.readIndustryProductionFields(skills, fees)
assert.equal(fields.skillTimePercent, 80)
assert.equal(fields.salesTaxPercent, 3.6)
assert.equal(JSON.parse(settings.writeIndustryProductionFields(fields, skills, fees).marketFeeJson).salesTax, 0.036)
const payload = settings.writeIndustryProductionFields({ ...fields, salesTaxPercent: 4 }, skills, fees)
assert.deepEqual(JSON.parse(payload.productionSkillJson), { productionLines: 11, timeMultiplier: 0.8, customSkill: 5 })
assert.deepEqual(JSON.parse(payload.marketFeeJson), { salesTax: 0.04, brokerFee: 0.01, customFee: 0.05 })
for (const value of ['[]', 'null', '42', 'broken', '{"timeMultiplier":"invalid"}']) {
  assert.throws(() => settings.readIndustryProductionFields(value, fees))
}
for (const value of [0, 1.5, Number.NaN]) {
  assert.throws(() => settings.writeIndustryProductionFields({ ...fields, productionLines: value }, skills, fees))
}

for (const page of ['calculator', 'config', 'data']) {
  const source = await readFile(new URL(`frontend/src/views/eve/industry/${page}/index.vue`, root), 'utf8')
  const template = source.split('<template>')[1]
  assert.match(template, /材料效率/)
  assert.match(template, /时间效率/)
  assert.doesNotMatch(template, /(?:label|title)="(?:ME|TE|ME \/ TE|流程数|制造 SCI|反应 SCI)"/)
  assert.doesNotMatch(template, /Golden Case|server version|adjusted price/)
  assert.doesNotMatch(template, /\{\{ item\.activity \}\}/)
}
console.log('工业中文术语、状态映射、费率单位和原配置保留检查通过')
