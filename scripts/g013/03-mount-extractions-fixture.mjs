const task = await taskSpace(41)
const page = task.page('p1')

const result = await page.evaluate(async () => {
  const resources = performance.getEntriesByType('resource').map((entry) => entry.name)
  const moduleUrl = (fragment) => resources.find((name) => name.includes(fragment))
  const vueUrl = moduleUrl('/node_modules/.vite/deps/vue.js')
  const arcoUrl = moduleUrl('/node_modules/.vite/deps/@arco-design_web-vue.js')
  const iconsUrl = moduleUrl('/node_modules/.vite/deps/@arco-design_web-vue_es_icon.js')
  if (!vueUrl || !arcoUrl || !iconsUrl) throw new Error('未找到已加载的 Vue/Arco 模块')

  const [{ createApp }, { default: ArcoVue }, { default: ArcoVueIcon }, { default: pinia }, { useUserStore }, httpModule] = await Promise.all([
    import(vueUrl),
    import(arcoUrl),
    import(iconsUrl),
    import('/src/stores/index.ts'),
    import('/src/stores/modules/user.ts'),
    import('/src/utils/http.ts'),
  ])

  const originalProfile = {
    status: 'READY',
    ores: [
      { typeId: 45490, typeName: '优质沥青矿', proportion: 0.352 },
      { typeId: 45501, typeName: '闪耀柯石英', proportion: 0.648 },
    ],
    sourceSentAt: '2026-10-22T04:05:00Z',
    updatedAt: '2026-10-22T04:06:00Z',
  }
  const refreshedProfile = {
    status: 'READY',
    ores: [
      { typeId: 45490, typeName: '优质沥青矿', proportion: 0.4 },
      { typeId: 45501, typeName: '闪耀柯石英', proportion: 0.6 },
    ],
    sourceSentAt: '2026-10-23T04:05:00Z',
    updatedAt: '2026-10-24T02:00:00Z',
  }
  const records = [
    {
      id: '101', structureId: '9001', structureName: '星海一号月矿堡', structureTypeName: '塔塔拉',
      moonId: '4001', moonName: '宁静之域 VIII - 卫星 3', solarSystemId: '3001', solarSystemName: '宁静之域',
      extractionStartAt: '2026-10-20T04:00:00Z', chunkArrivalAt: '2026-10-25T04:00:00Z', naturalDecayAt: '2026-10-27T04:00:00Z',
      status: 'ACTIVE', lastSeenAt: '2026-10-24T01:00:00Z', sourceExpiresAt: '2026-10-24T02:00:00Z', structureNote: '周末优先组织开采', oreProfile: originalProfile,
    },
    {
      id: '102', structureId: '9001', structureName: '星海一号月矿堡', structureTypeName: '塔塔拉',
      moonId: '4001', moonName: '宁静之域 VIII - 卫星 3', solarSystemId: '3001', solarSystemName: '宁静之域',
      extractionStartAt: '2026-09-20T04:00:00Z', chunkArrivalAt: '2026-09-25T04:00:00Z', naturalDecayAt: '2026-09-27T04:00:00Z',
      status: 'MISSING', lastSeenAt: '2026-09-25T01:00:00Z', sourceExpiresAt: '2026-09-25T02:00:00Z', structureNote: '周末优先组织开采', oreProfile: originalProfile,
    },
    {
      id: '201', structureId: '9002', structureName: '极光边境精炼厂', structureTypeName: '阿塔诺',
      moonId: '4002', moonName: '极光边境 IV - 卫星 7', solarSystemId: '3002', solarSystemName: '极光边境',
      extractionStartAt: '2026-10-21T05:00:00Z', chunkArrivalAt: '2026-10-26T05:00:00Z', naturalDecayAt: '2026-10-28T05:00:00Z',
      status: 'ACTIVE', lastSeenAt: '2026-10-24T01:00:00Z', sourceExpiresAt: '2026-10-24T02:00:00Z', oreProfile: { status: 'PENDING', ores: [] },
    },
  ]
  const fixture = { records, refreshCalls: 0, searchTerms: [], refreshed: false }
  window.__moonOreFixture = fixture

  httpModule.default.get = async (url, query = {}) => {
    if (url === '/eve/me/data-freshness') {
      return { data: [{ module: 'MOON_EXTRACTIONS', title: '月矿时间线', route: '/eve/extractions', status: 'FRESH', lastSuccessfulAt: '2026-10-24T01:00:00Z', retryable: true }] }
    }
    if (url === '/eve/extractions') {
      const keyword = String(query.keyword || '').trim().toLowerCase()
      fixture.searchTerms.push(keyword)
      const filtered = records.filter((record) => !keyword || [record.structureName, record.moonName, record.solarSystemName, ...record.oreProfile.ores.map((ore) => ore.typeName)]
        .some((value) => value.toLowerCase().includes(keyword)))
      return { data: { list: filtered.map((record) => ({ ...record, oreProfile: record.structureId === '9001' && fixture.refreshed ? refreshedProfile : record.oreProfile })), total: filtered.length } }
    }
    throw new Error(`未模拟 GET ${url}`)
  }
  httpModule.default.post = async (url) => {
    if (url.endsWith('/ore-profile/refresh')) {
      fixture.refreshCalls += 1
      fixture.refreshed = true
      return { data: { ...records[0], oreProfile: refreshedProfile } }
    }
    if (url === '/eve/extractions/sync') return { data: { message: 'fixture sync' } }
    throw new Error(`未模拟 POST ${url}`)
  }
  httpModule.default.put = async (url) => {
    if (url.endsWith('/note')) return { data: records[0] }
    throw new Error(`未模拟 PUT ${url}`)
  }

  window.__moonOreFixtureApp?.unmount()
  document.querySelectorAll('.arco-drawer-container, .arco-trigger-popup, .arco-message-list').forEach((node) => node.remove())
  const currentRoot = document.querySelector('#app')
  const fixtureRoot = document.createElement('div')
  fixtureRoot.id = 'app'
  currentRoot.replaceWith(fixtureRoot)

  const { default: ExtractionsPage } = await import('/src/views/eve/extractions/index.vue')
  const userStore = useUserStore(pinia)
  userStore.permissions = ['eve:extractions:manage', 'eve:extractions:sync']
  const app = createApp(ExtractionsPage)
  app.use(pinia)
  app.use(ArcoVue)
  app.use(ArcoVueIcon)
  app.mount(fixtureRoot)
  window.__moonOreFixtureApp = app
  window.__moonOreUserStore = userStore
  return { mounted: true, permissionCount: userStore.permissions.length }
})

await page.waitForSelector('loc=css:.eve-extractions-page__table-wrap', { state: 'visible' })
console.log(result)
console.log(await page.snapshot({ scope: 'full_page' }))
