import { fileURLToPath } from 'node:url'
import { mkdir } from 'node:fs/promises'

const task = await taskSpace('EVE 多军团授权验收 20261026')
const page = task.page('p1')
const widths = [320, 375, 414, 768, 1024, 1280, 1440]
const results = []
const root = fileURLToPath(new URL('../../tmp/g020', import.meta.url))
await mkdir(root, { recursive: true })

await page.goto('http://127.0.0.1:5173/agent-multi-corp-ui.html?authorize=synthetic-public-invitation')
await page.waitForSelector('loc=css:.eve-agents__invitation label.arco-checkbox', { state: 'visible' })
for (const width of widths) {
  await page.cdp('Emulation.setDeviceMetricsOverride', { width, height: 960, deviceScaleFactor: 1, mobile: false })
  await page.waitForFunction(value => window.innerWidth === value, width)
  const layout = await page.evaluate(() => {
    const dialog = document.querySelector('.arco-modal').getBoundingClientRect()
    return { viewport: innerWidth, documentWidth: document.documentElement.scrollWidth, dialogLeft: dialog.left, dialogRight: dialog.right }
  })
  if (layout.documentWidth > width + 1 || layout.dialogLeft < 0 || layout.dialogRight > width + 1) {
    throw new Error(`确认弹窗发生横向溢出：${JSON.stringify(layout)}`)
  }
  await page.screenshot({ path: `${root}/agent-invitation-${width}.png` })
  results.push({ state: 'invitation', ...layout })
}

await page.click('loc=role:button[name="Close"]')
await page.waitForSelector('loc=css:.arco-modal', { state: 'hidden' })
for (const width of widths) {
  await page.cdp('Emulation.setDeviceMetricsOverride', { width, height: 960, deviceScaleFactor: 1, mobile: false })
  await page.waitForFunction(value => window.innerWidth === value, width)
  const layout = await page.evaluate(() => ({ viewport: innerWidth, documentWidth: document.documentElement.scrollWidth }))
  if (layout.documentWidth > width + 1) throw new Error(`页面发生横向溢出：${JSON.stringify(layout)}`)
  await page.screenshot({ path: `${root}/agent-main-${width}.png` })
  results.push({ state: 'applications', ...layout })
}
await page.cdp('Emulation.clearDeviceMetricsOverride')
console.log(results)
