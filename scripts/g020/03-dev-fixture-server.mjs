import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createServer } from '../../frontend/node_modules/vite/dist/node/index.js'

const root = fileURLToPath(new URL('../../frontend', import.meta.url))
const fixture = new URL('./02-agent-multi-corp-ui.html', import.meta.url)
const fixturePath = '/agent-multi-corp-ui.html'

// 合成验收页仅在本地开发中提供，不进入业务路由或生产构建。
const server = await createServer({
  root,
  configFile: `${root}/vite.config.ts`,
  server: { host: '127.0.0.1', port: 5173, strictPort: true },
  plugins: [{
    name: 'eve-agent-synthetic-fixture',
    configureServer(vite) {
      vite.middlewares.use(async (request, response, next) => {
        const url = new URL(request.url, 'http://127.0.0.1')
        if (url.pathname !== fixturePath || url.searchParams.has('html-proxy')) return next()
        try {
          const source = await readFile(fixture, 'utf8')
          response.setHeader('Content-Type', 'text/html; charset=utf-8')
          response.end(await vite.transformIndexHtml(fixturePath, source))
        } catch (error) {
          next(error)
        }
      })
    },
  }],
})
await server.listen()
server.printUrls()
console.log(`合成授权验收：http://127.0.0.1:5173${fixturePath}?authorize=synthetic-public-invitation`)
