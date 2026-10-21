import { execFileSync } from 'node:child_process'
import { mkdirSync, rmSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const frontendRequire = createRequire(resolve(root, 'frontend/package.json'))
const viteRequire = createRequire(frontendRequire.resolve('vite'))
const { build } = viteRequire('esbuild')
const outputDir = resolve(root, 'tmp/timezone-regression')
const bundled = resolve(outputDir, 'eveTime.mjs')
mkdirSync(outputDir, { recursive: true })

try {
  await build({
    entryPoints: [resolve(root, 'frontend/src/utils/eveTime.ts')],
    bundle: true,
    format: 'esm',
    platform: 'node',
    outfile: bundled,
  })

  const probe = `
    import assert from 'node:assert/strict'
    import { formatEveTime, formatEveDate, toEveIso, parseEveTime } from ${JSON.stringify(bundled)}
    assert.equal(formatEveTime('2026-10-21T04:00:00Z'), '2026-10-21 12:00')
    assert.equal(formatEveTime('2026-10-21T12:00:00+08:00'), '2026-10-21 12:00')
    assert.equal(formatEveDate('2026-10-21'), '2026-10-21')
    assert.equal(formatEveDate('2026-10-21', 'M/D'), '10/21')
    assert.equal(toEveIso('2026-10-21T12:00:00'), '2026-10-21T12:00:00+08:00')
    assert.equal(parseEveTime(undefined).isValid(), false)
    assert.equal(parseEveTime('not-a-time').isValid(), false)
    assert.equal(formatEveTime('2026-10-21T20:00:00Z'), '2026-10-22 04:00')
    assert.equal(parseEveTime('2026-10-21T20:00:00Z').day(), 4)
    assert.equal(parseEveTime('2026-10-21T04:00:00Z').diff(parseEveTime('2026-10-21T03:00:00Z'), 'hour'), 1)
    assert.equal(parseEveTime('2026-10-21T04:00:00Z').isAfter(parseEveTime('2026-10-21T03:59:59Z')), true)
  `

  for (const timezone of ['UTC', 'Asia/Shanghai', 'America/New_York']) {
    execFileSync(process.execPath, ['--input-type=module', '-e', probe], {
      env: { ...process.env, TZ: timezone },
      stdio: 'inherit',
    })
  }
  console.log('eveTime timezone regression passed: UTC, Asia/Shanghai, America/New_York')
} finally {
  rmSync(outputDir, { recursive: true, force: true })
}
