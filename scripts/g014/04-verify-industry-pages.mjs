import assert from 'node:assert/strict'

const task = await taskSpace(48)
const page = task.page('p1')
assert.equal(new URL(await page.url()).origin, 'http://127.0.0.1:5173')
assert.equal(await page.evaluate(() => !!window.__industryFixture), true)
if (await page.evaluate(() => [...document.querySelectorAll('.arco-modal')].some((modal) => modal.getBoundingClientRect().width > 0))) await page.click('loc=role:button[name="Close"]')
const summaries = []
for (const name of ['opportunities', 'calculator', 'plans', 'inventory', 'config', 'data']) {
  await page.evaluate((name) => window.__industryFixture.router.push(`/eve/industry/${name}`), name)
  await page.waitForSelector(`.industry-${name}-page`, { state: 'visible' })
  const expected = { opportunities: '测试护卫舰', calculator: '测试护卫舰', plans: '测试护卫舰生产计划', inventory: '材料费用构成', config: '测试生产配置', data: '国服制造对照' }[name]
  await page.waitForFunction((text) => document.querySelector('main')?.innerText.includes(text), expected)
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  const text = await page.evaluate(() => document.querySelector('main').innerText)
  assert.doesNotMatch(text, /Golden Case|\bSCI\b|\bMANUFACTURING\b|\bVALIDATED\b|\bPASSED\b|固化|后端快照/)
  if (name === 'calculator') {
    assert.match(text, /材料效率/)
    assert.match(text, /时间效率/)
    await page.click('loc=role:button[name="开始计算"]')
    await page.waitForFunction(() => document.querySelector('main').innerText.includes('直接卖出利润'))
  }
  for (const width of [320, 375, 414, 768, 1024, 1280, 1440]) {
    await page.cdp('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: false })
    await page.waitForFunction((expected) => innerWidth === expected, width)
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))))
    const layout = await page.evaluate(() => ({ width: innerWidth, scrollWidth: document.documentElement.scrollWidth, clippedLabels: [...document.querySelectorAll('main .arco-form-item-label')].filter((label) => label.getClientRects().length && label.scrollWidth > label.clientWidth + 1).map((label) => label.textContent) }))
    assert.ok(layout.scrollWidth <= width + 1, JSON.stringify({ page: name, ...layout }))
    assert.deepEqual(layout.clippedLabels, [], JSON.stringify({ page: name, ...layout }))
    if (width === 320 || width === 1440) await page.screenshot({ path: `/Users/yuqingzhao/_workspace/_AI/eve-corp-manager/tmp/g014/${name}-${width}.png` })
  }
  summaries.push({ page: name, widths: 7, horizontalOverflow: false })
}
await page.evaluate(() => window.__industryFixture.router.push('/eve/industry/plans'))
console.log({ pages: summaries })
await page.waitForSelector('.industry-plans-page tbody tr:not(.arco-table-tr-empty)', { state: 'visible' })
await page.click('.industry-plans-page tbody tr:not(.arco-table-tr-empty)')
await page.waitForSelector('.plan-detail__steps', { state: 'visible' })
assert.match(await page.evaluate(() => document.querySelector('.plan-detail__steps').innerText), /待开始 · 采购材料/)
await page.cdp('Emulation.setDeviceMetricsOverride', { width: 320, height: 1000, deviceScaleFactor: 1, mobile: false })
await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))))
await page.waitForFunction(() => {
  const rect = document.querySelector('.arco-drawer')?.getBoundingClientRect()
  return rect && rect.left >= -1 && rect.right <= innerWidth + 1
})
const drawer = await page.evaluate(() => {
  const rect = document.querySelector('.arco-drawer').getBoundingClientRect()
  return { left: rect.left, right: rect.right, width: innerWidth }
})
assert.ok(drawer.left >= -1 && drawer.right <= drawer.width + 1, JSON.stringify(drawer))
await page.screenshot({ path: '/Users/yuqingzhao/_workspace/_AI/eve-corp-manager/tmp/g014/plan-detail-320.png' })
console.log({ pages: summaries, planStepsInChinese: true, drawer })
console.log(await page.snapshot({ scope: 'full_page' }))
