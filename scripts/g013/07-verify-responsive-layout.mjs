const task = await taskSpace(41)
const page = task.page('p1')
const widths = [320, 375, 414, 768, 1024, 1280, 1440]
const results = []

for (const width of widths) {
  await page.cdp('Emulation.setDeviceMetricsOverride', {
    width,
    height: width <= 414 ? 780 : 960,
    deviceScaleFactor: 1,
    mobile: false,
  })
  await page.waitForFunction((expectedWidth) => window.innerWidth === expectedWidth, width)
  await page.mouse.move(1, 1, { label: '移开悬停提示' })
  await page.waitForTimeout(500)
  const layout = await page.evaluate(() => {
    const rect = (selector) => {
      const node = document.querySelector(selector)
      if (!node) return null
      const bounds = node.getBoundingClientRect()
      return { left: bounds.left, right: bounds.right, width: bounds.width }
    }
    const tableContainer = document.querySelector('.arco-table-container')
    return {
      viewport: window.innerWidth,
      documentClientWidth: document.documentElement.clientWidth,
      documentScrollWidth: document.documentElement.scrollWidth,
      bodyClientWidth: document.body.clientWidth,
      bodyScrollWidth: document.body.scrollWidth,
      main: rect('.eve-extractions-page'),
      drawer: rect('.arco-drawer'),
      filter: rect('.eve-extractions-page__filter-bar'),
      tableViewport: tableContainer ? { clientWidth: tableContainer.clientWidth, scrollWidth: tableContainer.scrollWidth } : null,
    }
  })
  const path = `/Users/yuqingzhao/_workspace/_AI/eve-corp-manager/tmp/g013/moon-ore-${width}.png`
  await page.screenshot({ path })
  results.push({ width, path, layout })
}

await page.cdp('Emulation.clearDeviceMetricsOverride')
console.log(results)
