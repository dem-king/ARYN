#!/usr/bin/env node
/**
 * 国内演示统一网关：一个端口同时提供 H5、管理端、接口与图片，供国内内网穿透使用。
 *
 * 路由约定：
 *   /                 -> H5（aryn-mall-uniapp/dist/build/h5，history 回退 index.html）
 *   /admin/           -> 管理端（apps/web-ele/dist-cn，需用 /admin/ 为 base 构建）
 *   /api/*            -> 反代 127.0.0.1:9999，去掉 /api 前缀（与 Vite dev proxy 一致）
 *   /boot/*           -> 反代 127.0.0.1:9999，保留路径（上传文件、图片）
 *   /message          -> 反代 127.0.0.1:9999（在线消息，支持 WebSocket 升级）
 *   /__health         -> 网关与后端探活
 *
 * 用法（仓库根目录）：
 *   node dev-tools/demo-gateway.mjs
 *   node dev-tools/demo-gateway.mjs --port 8890 --backend http://127.0.0.1:9999
 *   node dev-tools/demo-gateway.mjs --admin-dir aryn-mall-ui/apps/web-ele/dist-cn
 *
 * 说明：
 *   - 零依赖，只用 Node 内置模块；
 *   - 文本资源按需 gzip 并缓存，带 ETag/304，弱网下首屏明显更快；
 *   - 只监听 127.0.0.1，公网入口交给内网穿透客户端（frpc/natapp/cpolar）。
 */
import http from 'node:http'
import https from 'node:https'
import net from 'node:net'
import fs from 'node:fs'
import path from 'node:path'
import zlib from 'node:zlib'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function parseArgs(argv) {
  const opts = {
    host: '127.0.0.1',
    port: 8890,
    h5Dir: path.join(ROOT, 'aryn-mall-uniapp', 'dist', 'build', 'h5'),
    adminDir: path.join(ROOT, 'aryn-mall-ui', 'apps', 'web-ele', 'dist-cn'),
    backend: 'http://127.0.0.1:9999',
    adminBase: '/admin',
    verbose: false,
  }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--host')
      opts.host = argv[++i]
    else if (a === '--port')
      opts.port = Number(argv[++i])
    else if (a === '--h5-dir')
      opts.h5Dir = path.resolve(argv[++i])
    else if (a === '--admin-dir')
      opts.adminDir = path.resolve(argv[++i])
    else if (a === '--backend')
      opts.backend = argv[++i]
    else if (a === '--admin-base')
      opts.adminBase = argv[++i]
    else if (a === '--verbose')
      opts.verbose = true
    else if (a === '-h' || a === '--help') {
      console.log('用法: node dev-tools/demo-gateway.mjs [--port 8890] [--h5-dir <dir>] [--admin-dir <dir>] [--backend http://127.0.0.1:9999] [--admin-base /admin] [--verbose]')
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
const ADMIN_BASE = `/${opts.adminBase.replace(/^\/+|\/+$/g, '')}`
const BACKEND = new URL(opts.backend)
const BACKEND_MOD = BACKEND.protocol === 'https:' ? https : http
const BACKEND_PORT = BACKEND.port || (BACKEND.protocol === 'https:' ? 443 : 80)

const H5_INDEX = path.join(opts.h5Dir, 'index.html')
const ADMIN_INDEX = path.join(opts.adminDir, 'index.html')

if (!fs.existsSync(H5_INDEX)) {
  console.error(`[gateway] 找不到 H5 产物: ${H5_INDEX}`)
  console.error('[gateway] 请先在 aryn-mall-uniapp 执行: pnpm build:h5:demo')
  process.exit(1)
}
let adminReady = true
if (!fs.existsSync(ADMIN_INDEX)) {
  adminReady = false
  console.warn(`[gateway] 警告: 找不到管理端产物 ${ADMIN_INDEX}`)
  console.warn('[gateway] 运行 ./dev-tools/build-cn-bundles.sh 后即可访问 ' + ADMIN_BASE + '/')
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.xml': 'application/xml; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.avif': 'image/avif',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
  '.wasm': 'application/wasm',
  '.mp4': 'video/mp4',
  '.webm': 'video/webm',
  '.pdf': 'application/pdf',
}
const COMPRESSIBLE = new Set([
  '.html',
  '.js',
  '.mjs',
  '.css',
  '.json',
  '.map',
  '.txt',
  '.xml',
  '.svg',
])

const gzipCache = new Map()

function log(...args) {
  console.log(`[gateway] ${args.join(' ')}`)
}

function mimeOf(file) {
  return MIME[path.extname(file).toLowerCase()] || 'application/octet-stream'
}

function safeJoin(root, rel) {
  const target = path.resolve(root, rel)
  if (target === root || target.startsWith(`${root}${path.sep}`))
    return target
  return null
}

function cacheControlFor(relPath) {
  if (relPath.endsWith('.html'))
    return 'no-cache'
  // Vite 产物带内容哈希，可长期缓存
  if (relPath.includes('/assets/') || /[-.][\w-]{8,}\.(?:js|css|woff2?)$/i.test(relPath))
    return 'public, max-age=31536000, immutable'
  return 'public, max-age=3600'
}

function sendBuffer(req, res, file, buffer, stat, status = 200) {
  const ext = path.extname(file).toLowerCase()
  const headers = {
    'Content-Type': mimeOf(file),
    'Cache-Control': cacheControlFor(file.split(path.sep).join('/')),
    'X-Content-Type-Options': 'nosniff',
  }
  const etag = `W/"${stat.size.toString(16)}-${Math.floor(stat.mtimeMs).toString(16)}"`
  headers.ETag = etag
  if (req.headers['if-none-match'] === etag) {
    res.writeHead(304, headers)
    res.end()
    return
  }

  let body = buffer
  const compressible = COMPRESSIBLE.has(ext) && buffer.length >= 1024
  if (compressible)
    headers.Vary = 'Accept-Encoding'
  const acceptsGzip = String(req.headers['accept-encoding'] || '').includes('gzip')
  if (acceptsGzip && compressible) {
    const cached = gzipCache.get(file)
    if (cached && cached.mtimeMs === stat.mtimeMs && cached.size === stat.size) {
      body = cached.body
    }
    else {
      body = zlib.gzipSync(buffer, { level: 6 })
      if (gzipCache.size > 200)
        gzipCache.clear()
      gzipCache.set(file, { mtimeMs: stat.mtimeMs, size: stat.size, body })
    }
    headers['Content-Encoding'] = 'gzip'
  }
  headers['Content-Length'] = body.length
  res.writeHead(status, headers)
  if (req.method === 'HEAD')
    res.end()
  else
    res.end(body)
}

function serveStatic(req, res, rootDir, indexPath, relPath, fallbackDir = '') {
  let rel = relPath.replace(/^\/+/, '')
  let file = safeJoin(rootDir, rel)
  if (!file) {
    res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('Forbidden')
    return true
  }

  try {
    if (fs.existsSync(file) && fs.statSync(file).isDirectory()) {
      file = path.join(file, 'index.html')
    }
    if (fs.existsSync(file) && fs.statSync(file).isFile()) {
      const stat = fs.statSync(file)
      const buf = fs.readFileSync(file)
      sendBuffer(req, res, file, buf, stat)
      return true
    }
  }
  catch (error) {
    log(`静态文件读取失败 ${file}: ${error.message}`)
  }

  // 管理端源码里有硬编码的 /static/xxx 图标，挂到 /admin/ 后会请求站点根目录；
  // 根目录找不到时回退到管理端产物，避免装修组件图标 404（H5 自己的 static 优先）。
  if (fallbackDir && rel.startsWith('static/')) {
    try {
      const fallbackFile = safeJoin(fallbackDir, rel)
      if (fallbackFile && fs.existsSync(fallbackFile) && fs.statSync(fallbackFile).isFile()) {
        const stat = fs.statSync(fallbackFile)
        sendBuffer(req, res, fallbackFile, fs.readFileSync(fallbackFile), stat)
        return true
      }
    }
    catch (error) {
      log(`管理端静态文件回退失败 ${rel}: ${error.message}`)
    }
  }

  // 只对页面导航做 SPA 回退，缺失的 js/css/图片仍然 404，避免把 HTML 当脚本返回
  const wantsHtml = String(req.headers.accept || '').includes('text/html')
  const lastSegment = rel.split('/').pop() || ''
  const looksLikeAsset = lastSegment.includes('.')
  if ((req.method === 'GET' || req.method === 'HEAD') && wantsHtml && !looksLikeAsset) {
    const stat = fs.statSync(indexPath)
    sendBuffer(req, res, indexPath, fs.readFileSync(indexPath), stat)
    return true
  }

  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
  res.end('Not Found')
  return true
}

function backendHeaders(req) {
  const headers = { ...req.headers }
  headers.host = BACKEND.host
  headers['x-forwarded-host'] = req.headers.host || ''
  headers['x-forwarded-proto'] = req.headers['x-forwarded-proto']
    || (req.socket.encrypted ? 'https' : 'http')
  if (req.socket.remoteAddress)
    headers['x-forwarded-for'] = req.socket.remoteAddress
  // 由网关自己处理连接管理，避免把 hop-by-hop 头透传给上游
  delete headers.connection
  delete headers['proxy-connection']
  delete headers['keep-alive']
  delete headers['transfer-encoding']
  delete headers.upgrade
  return headers
}

function proxyRequest(req, res, url, stripPrefix = '') {
  const targetPath = (stripPrefix
    ? url.pathname.slice(stripPrefix.length)
    : url.pathname) || '/'
  const init = {
    hostname: BACKEND.hostname,
    port: BACKEND_PORT,
    path: targetPath + url.search,
    method: req.method,
    headers: backendHeaders(req),
  }
  const proxyReq = BACKEND_MOD.request(init, (proxyRes) => {
    const headers = { ...proxyRes.headers }
    for (const key of ['connection', 'keep-alive', 'transfer-encoding', 'upgrade', 'proxy-authenticate', 'proxy-authorization', 'te', 'trailer'])
      delete headers[key]
    // 后端如果返回绝对地址重定向，改写成当前入口域名，避免浏览器被带回 127.0.0.1
    if (headers.location && headers.location.startsWith(BACKEND.origin)) {
      const proto = req.headers['x-forwarded-proto'] || (req.socket.encrypted ? 'https' : 'http')
      headers.location = `${proto}://${req.headers.host}${headers.location.slice(BACKEND.origin.length)}`
    }
    res.writeHead(proxyRes.statusCode || 502, headers)
    proxyRes.pipe(res)
  })
  proxyReq.on('error', (error) => {
    log(`反代失败 ${req.method} ${url.pathname}: ${error.message}`)
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
    }
    res.end(`后端不可用: ${error.message}`)
  })
  req.on('aborted', () => proxyReq.destroy())
  req.pipe(proxyReq)
}

let healthCache = { at: 0, status: 0 }
function probeBackend() {
  const now = Date.now()
  if (now - healthCache.at < 5000)
    return Promise.resolve(healthCache)
  return new Promise((resolve) => {
    const req = BACKEND_MOD.request({
      hostname: BACKEND.hostname,
      port: BACKEND_PORT,
      path: '/boot/',
      method: 'GET',
      timeout: 3000,
    }, (res) => {
      res.resume()
      healthCache = { at: Date.now(), status: res.statusCode || 0 }
      resolve(healthCache)
    })
    req.on('timeout', () => req.destroy(new Error('timeout')))
    req.on('error', () => {
      healthCache = { at: Date.now(), status: 0 }
      resolve(healthCache)
    })
    req.end()
  })
}

async function handleHealth(res) {
  const health = await probeBackend()
  const payload = {
    ok: health.status > 0,
    backend: opts.backend,
    backendStatus: health.status,
    h5: fs.existsSync(H5_INDEX),
    admin: adminReady,
    adminBase: ADMIN_BASE,
    time: new Date().toISOString(),
  }
  const body = JSON.stringify(payload, null, 2)
  res.writeHead(health.status > 0 ? 200 : 503, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  })
  res.end(body)
}

const server = http.createServer((req, res) => {
  let url
  try {
    url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
  }
  catch {
    res.writeHead(400)
    res.end('Bad Request')
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
  if (pathname.includes('\0')) {
    res.writeHead(400)
    res.end('Bad Request')
    return
  }

  if (opts.verbose)
    log(`${req.method} ${pathname}${url.search}`)

  if (pathname === '/__health') {
    handleHealth(res)
    return
  }

  if (pathname === '/api' || pathname.startsWith('/api/')) {
    proxyRequest(req, res, url, '/api')
    return
  }
  if (pathname === '/boot' || pathname.startsWith('/boot/')) {
    proxyRequest(req, res, url)
    return
  }
  if (pathname === '/message' || pathname.startsWith('/message/')) {
    proxyRequest(req, res, url)
    return
  }

  if (pathname === ADMIN_BASE) {
    res.writeHead(301, { Location: `${ADMIN_BASE}/` })
    res.end()
    return
  }
  if (pathname === `${ADMIN_BASE}/` || pathname.startsWith(`${ADMIN_BASE}/`)) {
    if (!adminReady) {
      res.writeHead(503, { 'Content-Type': 'text/plain; charset=utf-8' })
      res.end('管理端产物不存在，请先运行 ./dev-tools/build-cn-bundles.sh')
      return
    }
    const rel = pathname.slice(ADMIN_BASE.length).replace(/^\/+/, '')
    serveStatic(req, res, opts.adminDir, ADMIN_INDEX, rel)
    return
  }

  serveStatic(req, res, opts.h5Dir, H5_INDEX, pathname, adminReady ? opts.adminDir : '')
})

// 在线消息走了 WebSocket：把升级请求原样转发给后端（/message 或 /api 前缀）
server.on('upgrade', (req, socket, head) => {
  let url
  try {
    url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
  }
  catch {
    socket.destroy()
    return
  }
  let targetPath = url.pathname
  if (targetPath === '/api' || targetPath.startsWith('/api/'))
    targetPath = targetPath.slice(4) || '/'
  else if (!(targetPath === '/message' || targetPath.startsWith('/message/'))) {
    socket.destroy()
    return
  }
  targetPath += url.search

  const upstream = net.connect(BACKEND_PORT, BACKEND.hostname, () => {
    const lines = [`${req.method} ${targetPath} HTTP/1.1`]
    for (const [key, value] of Object.entries(req.headers)) {
      if (key.toLowerCase() === 'host')
        continue
      lines.push(`${key}: ${Array.isArray(value) ? value.join(', ') : value}`)
    }
    lines.push(`host: ${BACKEND.host}`, '', '')
    upstream.write(lines.join('\r\n'))
    if (head && head.length)
      upstream.write(head)
    upstream.pipe(socket)
    socket.pipe(upstream)
  })
  upstream.on('error', (error) => {
    log(`WebSocket 反代失败 ${targetPath}: ${error.message}`)
    socket.destroy()
  })
  socket.on('error', () => upstream.destroy())
  socket.on('close', () => upstream.destroy())
  upstream.on('close', () => socket.destroy())
})

server.listen(opts.port, opts.host, () => {
  log(`H5      : ${opts.h5Dir}`)
  log(`管理端  : ${opts.adminDir}${adminReady ? '' : '（未构建）'}`)
  log(`后端    : ${opts.backend}（/api 去前缀，/boot 保留）`)
  log(`本地入口: http://${opts.host}:${opts.port}/  |  http://${opts.host}:${opts.port}${ADMIN_BASE}/`)
  log('公网入口: 用内网穿透把该端口映射出去（见 dev-tools/cn-tunnel.sh）')
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    log(`收到 ${signal}，关闭网关`)
    server.close(() => process.exit(0))
    setTimeout(() => process.exit(0), 2000).unref()
  })
}
