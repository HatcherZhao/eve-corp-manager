const task = await taskSpace(48)
const page = task.page('p1')
if (new URL(await page.url()).origin !== 'http://127.0.0.1:5173') throw new Error('模拟验收仅允许在本地开发页面执行')
console.log(await page.evaluate(async () => {
  const resources = () => performance.getEntriesByType('resource').map((entry) => entry.name)
  const moduleUrl = (fragment) => resources().find((name) => name.includes(fragment))
  const [{ createApp, h }, { default: ArcoVue }, { default: ArcoVueIcon }, { createRouter, createMemoryHistory, RouterView }] = await Promise.all([
    import(moduleUrl('/node_modules/.vite/deps/vue.js')),
    import(moduleUrl('/node_modules/.vite/deps/@arco-design_web-vue.js')),
    import(moduleUrl('/node_modules/.vite/deps/@arco-design_web-vue_es_icon.js')),
    import(moduleUrl('/node_modules/.vite/deps/vue-router.js')),
  ])
  await import('/src/apis/eve/index.ts')
  const httpUrl = resources().filter((name) => name.includes('/src/utils/http.ts')).at(-1)
  const { default: http } = await import(httpUrl)
  const blueprint = { id: 'bp1', blueprintTypeId: 1001, blueprintName: '测试护卫舰蓝图', productTypeId: 2001, productName: '测试护卫舰', activity: 'MANUFACTURING', productQuantity: 1, baseTimeSeconds: 3600 }
  const config = { id: 'cfg1', name: '测试生产配置', productionSkillJson: '{"productionLines":11,"timeMultiplier":0.8,"extraSkill":5}', marketFeeJson: '{"salesTax":0.036,"brokerFee":0.01,"extraFee":0.02}', freshnessPolicyJson: '{"marketMinutes":30}', targetDays: 7, marketShareLimit: 0.1, enabled: true }
  const facility = { id: 'fac1', name: '测试制造设施', realityType: 'ACTUAL', systemId: '30000142', systemName: '吉他', facilityName: '测试工程建筑', materialMultiplier: 0.98, timeMultiplier: 0.8, costMultiplier: 0.95, manufacturingSci: 0.05, reactionSci: 0.03, facilityTax: 0.01, versionedSurcharge: 0.04, supportedActivitiesJson: '["MANUFACTURING","REACTION"]', onlineServicesJson: '["Manufacturing"]', rigTypeIdsJson: '[]', enabled: true }
  const opportunity = { id: 'opp1', blueprintId: 'bp1', blueprintName: blueprint.blueprintName, productName: blueprint.productName, activity: 'MANUFACTURING', facilityName: facility.name, systemName: facility.systemName, status: 'EXECUTABLE', recommendedRuns: 10, recommendedQuantity: 10, materialCost: 10000, installCost: 500, immediateProfit: 4000, sellOrderProfit: 6000, sellOrderRevenue: 16500, dailyProfit: 6000, hourlyProfit: 250, margin: 0.57, riskTags: [], riskScore: 0, calculatedAt: '2026-10-24T04:00:00Z' }
  const plan = { id: 'plan1', name: '测试护卫舰生产计划', productName: blueprint.productName, status: 'DRAFT', targetQuantity: 10, expectedProfit: 6000, totalMaterialCost: 10000, totalInstallCost: 500, updateTime: '2026-10-24T04:00:00Z' }
  const fixture = { writes: [], empty: false }
  window.__industryFixture = fixture
  const getData = (url) => {
    const list = (items) => fixture.empty ? [] : items
    if (url === '/eve/industry/static-blueprints') return list([blueprint])
    if (url === '/eve/industry/configs') return list([config])
    if (url === '/eve/industry/facilities') return list([facility])
    if (url === '/eve/industry/blueprints/overrides') return list([{ id: 'ov1', blueprintTypeId: 1001, blueprintKind: 'BPC', materialEfficiency: 10, timeEfficiency: 20, remainingRuns: 100 }])
    if (url === '/eve/industry/opportunities') return list([opportunity, { ...opportunity, id: 'opp2', status: 'TRIAL', riskTags: ['SCI_MISSING', 'DEMAND_UNAVAILABLE'], riskScore: 80 }])
    if (url === '/eve/industry/opportunities/opp1/materials' || url === '/eve/industry/opportunities/opp2/materials') return [{ materialName: '三钛合金', requiredQuantity: 1000, purchaseCost: 10000 }]
    if (url === '/eve/industry/plans') return list([plan])
    if (url === '/eve/industry/plans/plan1') return { plan, steps: [{ id: 's1', stepType: 'PURCHASE', title: '采购 三钛合金', status: 'PENDING', quantity: 1000, totalCost: 10000, sequenceNo: 1 }] }
    if (url === '/eve/industry/data-status') return { blueprintCount: fixture.empty ? 0 : 2, validatedBlueprintCount: fixture.empty ? 0 : 1, marketSnapshotCount: fixture.empty ? 0 : 20, message: fixture.empty ? '还没有可用的蓝图配方，请先在“数据与校验”同步公开蓝图或导入国服配方' : '已有可用蓝图配方，可选择生产配置和设施分析利润' }
    if (url === '/eve/industry/static-versions') return list([{ id: 'v1', source: 'CEVE_INDUSTRY_PUBLIC', serverVersion: '测试版本', validationStatus: 'VALIDATED', sha256: 'abcdef1234567890', downloadedAt: '2026-10-24T04:00:00Z' }])
    if (url === '/eve/industry/golden-cases') return list([{ id: 'g1', name: '国服制造对照', blueprintName: blueprint.blueprintName, runs: 10, materialEfficiency: 10, timeEfficiency: 20, validationStatus: 'PASSED', validationDetails: [] }])
    throw new Error(`未模拟 GET ${url}`)
  }
  http.get = async (url) => ({ data: getData(url) })
  http.put = async (url, payload) => { fixture.writes.push({ url, payload }); return { data: payload } }
  http.post = async (url, payload) => {
    fixture.writes.push({ url, payload })
    if (url === '/eve/industry/calculate') return { data: { durationSeconds: 3600, totalCost: 10500, materialCost: 10000, installationCost: 500, immediateRevenue: 14500, sellOrderRevenue: 16500, immediateProfit: 4000, sellOrderProfit: 6000, warnings: [], materials: [{ name: '三钛合金', quantity: 1000, averagePrice: 10, purchaseCost: 10000, marketComplete: true }] } }
    return { data: payload }
  }
  const modules = {
    opportunities: (await import('/src/views/eve/industry/opportunities/index.vue')).default,
    calculator: (await import('/src/views/eve/industry/calculator/index.vue')).default,
    plans: (await import('/src/views/eve/industry/plans/index.vue')).default,
    inventory: (await import('/src/views/eve/industry/inventory/index.vue')).default,
    config: (await import('/src/views/eve/industry/config/index.vue')).default,
    data: (await import('/src/views/eve/industry/data/index.vue')).default,
  }
  // Vite 热更新可能保留多个版本的请求模块，统一拦截，避免测试写入真实接口。
  for (const url of resources().filter((name) => name.includes('/src/utils/http.ts'))) {
    const { default: client } = await import(url)
    client.get = http.get
    client.post = http.post
    client.put = http.put
  }
  const router = createRouter({ history: createMemoryHistory(), routes: Object.keys(modules).map((name) => ({ path: `/eve/industry/${name}`, component: modules[name] })) })
  const root = document.querySelector('#app')
  root.__vue_app__?.unmount()
  root.replaceChildren()
  root.style.cssText = 'height:auto;min-height:100vh;padding:12px;box-sizing:border-box;'
  document.body.style.overflow = 'auto'
  const app = createApp({ render: () => h(RouterView) })
  app.use(router).use(ArcoVue).use(ArcoVueIcon)
  await router.push('/eve/industry/config')
  app.mount(root)
  fixture.router = router
  return { mounted: true }
}))
await page.waitForFunction(() => document.body.innerText.includes('测试生产配置'))
console.log(await page.snapshot({ scope: 'full_page' }))
