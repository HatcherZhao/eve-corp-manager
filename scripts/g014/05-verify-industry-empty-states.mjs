import assert from 'node:assert/strict'

const task = await taskSpace(48)
const page = task.page('p1')
assert.equal(new URL(await page.url()).origin, 'http://127.0.0.1:5173')
assert.equal(await page.evaluate(() => !!window.__industryFixture), true)
await page.evaluate(() => { window.__industryFixture.empty = true })
const expected = {
  opportunities: '暂无利润分析结果',
  calculator: '选择条件后开始计算，即可查看成本与利润',
  plans: '暂无生产计划',
  config: '尚未保存生产配置',
  data: '尚未同步或导入工业配方',
}
for (const [name, text] of Object.entries(expected)) {
  await page.evaluate((name) => window.__industryFixture.router.push(`/eve/industry/${name}`), name)
  await page.waitForFunction((text) => document.querySelector('main')?.innerText.includes(text), text)
}
await page.click('loc=role:button[name="添加游戏内对照"]')
await page.waitForFunction(() => [...document.querySelectorAll('.industry-data-modal')].some((modal) => modal.getBoundingClientRect().width > 0))
await page.evaluate(() => Promise.all(document.getAnimations().filter((animation) => animation.effect?.getTiming().iterations !== Infinity).map((animation) => animation.finished.catch(() => undefined))))
await page.cdp('Emulation.setDeviceMetricsOverride', { width: 320, height: 1000, deviceScaleFactor: 1, mobile: false })
await page.waitForFunction(() => innerWidth === 320)
await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))))
const form = await page.evaluate(() => {
  const modal = [...document.querySelectorAll('.industry-data-modal')].find((modal) => modal.getBoundingClientRect().width > 0)
  const rect = modal.getBoundingClientRect()
  return { left: rect.left, right: rect.right, labels: [...modal.querySelectorAll('.arco-form-item-label')].map((label) => label.innerText), overflow: modal.scrollWidth - modal.clientWidth }
})
assert.ok(form.left >= 0 && form.right <= 321 && form.overflow <= 1, JSON.stringify(form))
assert.ok(form.labels.includes('材料效率（%）') && form.labels.includes('时间效率（%）'))
await page.screenshot({ path: '/Users/yuqingzhao/_workspace/_AI/eve-corp-manager/tmp/g014/game-comparison-320.png' })
await page.click('loc=role:button[name="Close"]')
await page.cdp('Emulation.clearDeviceMetricsOverride')
await page.evaluate(() => { window.__industryFixture.empty = false })
console.log({ emptyStates: Object.keys(expected), comparisonForm: form, realApiWrites: 0 })
console.log(await page.snapshot({ scope: 'full_page' }))
