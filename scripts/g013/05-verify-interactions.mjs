const task = await taskSpace(41)
const page = task.page('p1')

await page.fill('loc=css:input[placeholder="搜索精炼厂、月球、星系或矿种"]', '闪耀柯石英')
await page.click('loc=role:button[name="查询"]', { label: '按矿种查询' })
await page.waitForFunction(() => document.body.innerText.includes('共 2 条时间线'))
const searchResult = await page.evaluate(() => ({
  rows: [...document.querySelectorAll('tr')].filter((row) => row.querySelector('button')).length,
  pendingVisible: document.body.innerText.includes('极光边境精炼厂'),
  lastSearch: window.__moonOreFixture.searchTerms.at(-1),
}))

await page.click('loc=role:button[name="重置"]', { label: '重置矿种查询' })
await page.waitForFunction(() => document.body.innerText.includes('共 3 条时间线'))
await page.click('text="详情" >> nth=0', { label: '打开矿堡详情' })
await page.waitForSelector('text=月矿种类档案', { state: 'visible' })
const detailBefore = await page.evaluate(() => ({
  hasStatus: document.body.innerText.includes('已有档案'),
  hasRatioHeading: document.body.innerText.includes('产出比例'),
  hasSourceTime: document.body.innerText.includes('通知发送时间'),
  hasOldRatio: document.body.innerText.includes('35.2%'),
}))

await page.click('loc=role:button[name="刷新矿种"]', { label: '刷新矿种档案' })
await page.waitForFunction(() => window.__moonOreFixture.refreshCalls === 1 && document.body.innerText.includes('40%'))
const refreshResult = await page.evaluate(() => ({
  refreshCalls: window.__moonOreFixture.refreshCalls,
  tableFortyPercentCount: [...document.querySelectorAll('.eve-extractions-page__ore-tags')]
    .filter((node) => node.textContent.includes('优质沥青矿 40%')).length,
  tableSixtyPercentCount: [...document.querySelectorAll('.eve-extractions-page__ore-tags')]
    .filter((node) => node.textContent.includes('闪耀柯石英 60%')).length,
  drawerUpdated: [...document.querySelectorAll('.eve-extractions-page__ore-item')]
    .some((node) => node.textContent.includes('优质沥青矿') && node.textContent.includes('40%')),
}))

await page.evaluate(() => {
  window.__moonOreUserStore.permissions = []
})
await page.waitForFunction(() => document.querySelectorAll('[aria-label="刷新该矿堡矿种档案"]').length === 0
  && ![...document.querySelectorAll('button')].some((button) => button.textContent.includes('刷新矿种')))
const memberResult = await page.evaluate(() => ({
  tableRefreshButtons: document.querySelectorAll('[aria-label="刷新该矿堡矿种档案"]').length,
  drawerRefreshButtons: [...document.querySelectorAll('button')].filter((button) => button.textContent.includes('刷新矿种')).length,
}))

await page.evaluate(() => {
  window.__moonOreUserStore.permissions = ['eve:extractions:manage', 'eve:extractions:sync']
})

console.log({ searchResult, detailBefore, refreshResult, memberResult })
console.log(await page.snapshot({ scope: 'full_page' }))
