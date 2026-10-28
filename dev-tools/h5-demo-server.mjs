#!/usr/bin/env node
/**
 * H5 临时演示静态服务器。
 *
 * 背景：uni-app 的 `pnpm dev:h5` 是 watch 模式，会和其它 uni 进程抢写 src/pages.json，
 * 临时给外地同事演示时不够稳定，也慢（按需编译）。本脚本改为：
 *   - 服务 `pnpm build:h5` 产出的静态包（默认 dist/build/h5）；
 *   - 同源代理 `/api/*` 到本机后端（等价 Vite dev 的 rewrite：去掉 `/api` 前缀）；
 *   - history 路由的 SPA fallback（未知路径回 index.html）。
 * 再配合 ./dev-tools/dev-public-tunnel.sh 映射成 https 域名即可给外部访问。
 *
 * 用法（仓库根目录）：
 *   node dev-tools/h5-demo-server.mjs                 # 默认 8890 端口
 *   node dev-tools/h5-demo-server.mjs --port 8890 --api http://127.0.0.1:9999
 *   node dev-tools/h5-demo-server.mjs --dir aryn-mall-uniapp/dist/build/h5
 */
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function parseArgs(argv) {
  const opts = {
    port: 8890,
    dir: path.join(ROOT, 'aryn-mall-uniapp', 'dist', 'build', 'h5'),
    api: 'http://127.0.0.1:9999',
  }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--port')
      opts.port = Number(argv[++i])
    else if (a === '--dir')
      opts.dir = path.resolve(argv[++i])
    else if (a === '--api')
      opts.api = argv[++i]
    else if (a === '-h' || a === '--help') {
      console.log('用法: node dev-tools/h5-demo-server.mjs [--port 8890] [--dir <h5产物目录>] [--api http://127.0.0.1:9999]')
      process.exit(0)
    }
    else {
      console.error(`未知参数: ${a}`)
      process.exit(1)
    }
  }
  return opts
}

const opts = parseArgs(process.argv.slice(2))
const apiTarget = new URL(opts.api)
const indexFile = path.join(opts.dir, 'index.html')

if (!fs.existsSync(indexFile)) {
  console.error(`[h5-demo] 找不到 ${indexFile}`)
  console.error('[h5-demo] 请先在 aryn-mall-uniapp 执行: pnpm build:h5:demo')
  process.exit(1)
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
}

function sendFile(res, filePath, status = 200) {
  fs.readFile(filePath, (err, buf) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
      res.end('Not Found')
      return
    }
    res.writeHead(status, {
      'Content-Type': MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream',
      'Cache-Control': 'no-cache',
    })
    res.end(buf)
  })
}

function proxyApi(req, res, url) {
  // 与 Vite dev proxy 一致：/api/xxx -> http://127.0.0.1:9999/xxx
  const targetPath = url.pathname.replace(/^\/api/, '') || '/'
  const headers = { ...req.headers, host: apiTarget.host }
  const proxyReq = http.request({
    hostname: apiTarget.hostname,
    port: apiTarget.port || 80,
    path: targetPath + url.search,
    method: req.method,
    headers,
  }, (proxyRes) => {
    res.writeHead(proxyRes.statusCode || 502, proxyRes.headers)
    proxyRes.pipe(res)
  })
  proxyReq.on('error', (error) => {
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end(`api proxy error: ${error.message}`)
  })
  req.pipe(proxyReq)
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)

  if (url.pathname.startsWith('/api/')) {
    proxyApi(req, res, url)
    return
  }

  let pathname
  try {
    pathname = decodeURIComponent(url.pathname)
  }
  catch {
    res.writeHead(400)
    res.end('Bad Request')
    return
  }

  const rel = pathname.replace(/^\/+/, '')
  const filePath = path.join(opts.dir, rel)
  if (filePath !== opts.dir && !filePath.startsWith(opts.dir + path.sep)) {
    res.writeHead(403)
    res.end('Forbidden')
    return
  }

  if (rel && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    sendFile(res, filePath)
    return
  }

  // history 路由 / 目录 -> index.html
  sendFile(res, indexFile)
})

server.listen(opts.port, '127.0.0.1', () => {
  console.log(`[h5-demo] serving ${opts.dir}`)
  console.log(`[h5-demo] http://127.0.0.1:${opts.port}/  (api -> ${opts.api}, strip /api prefix)`)
  console.log('[h5-demo] 对外暴露: ./dev-tools/dev-public-tunnel.sh ' + opts.port + ' --detach --no-host-rewrite')
})
