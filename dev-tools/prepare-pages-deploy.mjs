#!/usr/bin/env node
/**
 * 生成可直接上传 Cloudflare Pages 的两个静态站：H5（C 端）与管理端。
 *
 * 做法：
 *   - 复制构建产物到 outputs/pages/{h5,admin}；
 *   - 在每个站点根目录写 _worker.js（Pages Functions 高级模式）：
 *       /api/* 反向代理到本机后端隧道（去掉 /api 前缀，等价 Vite dev proxy），
 *       其余请求交给静态资源。这样前端构建里 API 基址保持同源 /api，
 *       后端隧道域名变化时不用重新构建前端，只改 worker 的 BACKEND_URL 即可；
 *   - 写 _redirects（SPA history 回退，H5 需要；管理端 hash 路由无副作用）；
 *   - 打包 h5.zip / admin.zip，方便 Cloudflare Pages 控制台拖拽上传。
 *
 * 用法（仓库根目录）：
 *   node dev-tools/prepare-pages-deploy.mjs --backend https://xxx.trycloudflare.com
 *   可选：--h5 <dir> --admin <dir> --out <dir> --skip-admin --skip-h5
 *
 * 部署后：在 Cloudflare Pages 项目的 Settings → Environment variables 增加
 *   BACKEND_URL = https://<当前后端隧道域名>
 * 隧道域名变了只改这个变量并重新部署（Retry deployment）即可。
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function argsParse(argv) {
  const opts = {
    backend: '',
    h5: path.join(ROOT, 'aryn-mall-uniapp', 'dist', 'build', 'h5'),
    admin: path.join(ROOT, 'aryn-mall-ui', 'apps', 'web-ele', 'dist'),
    out: path.join(ROOT, 'outputs', 'pages'),
    skipH5: false,
    skipAdmin: false,
  }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--backend') opts.backend = argv[++i]
    else if (a === '--h5') opts.h5 = path.resolve(argv[++i])
    else if (a === '--admin') opts.admin = path.resolve(argv[++i])
    else if (a === '--out') opts.out = path.resolve(argv[++i])
    else if (a === '--skip-h5') opts.skipH5 = true
    else if (a === '--skip-admin') opts.skipAdmin = true
    else if (a === '-h' || a === '--help') {
      console.log('用法: node dev-tools/prepare-pages-deploy.mjs --backend https://<后端隧道域名> [--h5 dir] [--admin dir] [--out dir] [--skip-admin] [--skip-h5]')
      process.exit(0)
    }
    else { console.error('未知参数:', a); process.exit(1) }
  }
  return opts
}

const opts = argsParse(process.argv.slice(2))
if (!opts.backend) {
  console.error('缺少 --backend，例如：--backend https://apart-temple-citizen-cap.trycloudflare.com')
  process.exit(1)
}
if (!/^https:\/\//.test(opts.backend)) {
  console.error('--backend 必须是 https:// 开头的公网地址（小程序/浏览器/Pages 都要求受信任 https）')
  process.exit(1)
}
const BACKEND = opts.backend.replace(/\/+$/, '')

function workerSource(fallback) {
  return `// Cloudflare Pages Functions（高级模式）：/api/* -> 本机后端隧道，其余走静态资源。
// 后端地址优先读环境变量 BACKEND_URL，未配置时用下面的 fallback。
// 注意：Pages Functions 免费额度约 10 万请求/天，且不支持 WebSocket（在线客服聊天会不可用）。
const FALLBACK_BACKEND = '${fallback}'

export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    const backend = String((env && env.BACKEND_URL) || FALLBACK_BACKEND).replace(/\\/+$/, '')

    if (url.pathname === '/api' || url.pathname.startsWith('/api/')) {
      const suffix = url.pathname.slice(4) // 去掉 /api
      const target = backend + (suffix || '/') + url.search
      const headers = new Headers(request.headers)
      headers.delete('host')
      // 不转发 accept-encoding，避免上游压缩后 Workers 自动解压导致的编码头不一致
      headers.delete('accept-encoding')
      const init = { method: request.method, headers, redirect: 'manual' }
      if (request.method !== 'GET' && request.method !== 'HEAD') {
        // 直接透传 request.body 在 Cloudflare Workers 可用，但为了本地 Node 也能复现/测试，
        // 这里统一读成 ArrayBuffer（普通 JSON 请求体很小，开销可忽略）
        init.body = await request.arrayBuffer()
      }
      return fetch(target, init)
    }

    // 页面请求（history 路由、刷新深链）直接回 index.html：
    // 避免先走 ASSETS 时 Cloudflare 把 /xxx/index 308 成 /xxx/ 导致前端路由匹配不上。
    const isPageRequest = (request.method === 'GET' || request.method === 'HEAD')
      && String(request.headers.get('accept') || '').includes('text/html')
    const lastSegment = url.pathname.split('/').filter(Boolean).pop() || ''
    const looksLikeAsset = lastSegment.includes('.')
    if (isPageRequest && !looksLikeAsset) {
      return env.ASSETS.fetch(new Request(new URL('/', url), request))
    }

    const assetRes = await env.ASSETS.fetch(request)
    return assetRes
  },
}
`
}

function copyDir(src, dest) {
  if (!fs.existsSync(src)) {
    console.error(`找不到构建产物: ${src}`)
    return false
  }
  fs.rmSync(dest, { recursive: true, force: true })
  fs.mkdirSync(dest, { recursive: true })
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const from = path.join(src, entry.name)
    const to = path.join(dest, entry.name)
    if (entry.isDirectory()) copyDir(from, to)
    else fs.copyFileSync(from, to)
  }
  return true
}

function zip(dir, zipPath) {
  try {
    fs.rmSync(zipPath, { force: true })
    execFileSync('zip', ['-qr', zipPath, '.'], { cwd: dir })
    return true
  }
  catch (e) {
    console.warn(`[pages] 打包失败（可手动压缩目录）: ${e.message.split('\n')[0]}`)
    return false
  }
}

function deployProject(name, dir, zipPath, extraNotes) {
  fs.writeFileSync(path.join(dir, '_worker.js'), workerSource(BACKEND))
  fs.writeFileSync(path.join(dir, '_redirects'), '/* /index.html 200\n')
  const readme = `# ${name} → Cloudflare Pages 部署说明

生成时间：${new Date().toLocaleString('zh-CN')}
后端地址（fallback）：${BACKEND}

## 方式 A：控制台拖拽上传（最简单）
1. Cloudflare 控制台 → Workers & Pages → Create → Pages → Upload assets；
2. 项目名即域名：https://<项目名>.pages.dev；
3. 把 **本目录里的全部文件**（含 _worker.js、_redirects）一起上传/或上传 ${path.basename(zipPath)}；
4. 部署完成后，在项目 Settings → Environment variables 增加：
   BACKEND_URL = ${BACKEND}
5. 打开 https://<项目名>.pages.dev 验证。

## 方式 B：命令行（支持 Functions 更稳）
npx wrangler pages deploy "${dir}" --project-name ${name}
（首次会打开浏览器登录 Cloudflare，免费账号即可）

## 后端隧道地址变了怎么办
1. 在 Pages 项目 Settings → Environment variables 修改 BACKEND_URL；
2. 重新部署（Deployments → Retry deployment），前端**不需要重新构建**。

${extraNotes || ''}
`
  fs.writeFileSync(path.join(dir, 'DEPLOY.md'), readme)
  const ok = zip(dir, zipPath)
  return ok
}

fs.mkdirSync(opts.out, { recursive: true })
const results = []

if (!opts.skipH5) {
  const dir = path.join(opts.out, 'h5')
  if (copyDir(opts.h5, dir)) {
    deployProject('aryn-h5', dir, path.join(opts.out, 'h5.zip'), `说明：
- 这是 C 端 uni-app 的 H5 包，接口基址为同源 /api（由 _worker.js 代理到后端）。
- history 路由已用 _redirects 做 SPA 回退。
- 图片由数据库里的图片隧道 https 地址直接加载，不经过 Pages。`)
    results.push(`H5      -> ${dir}  (${path.join(opts.out, 'h5.zip')})`)
  }
}
else {
  results.push('H5      已跳过（--skip-h5）')
}

if (!opts.skipAdmin) {
  const dir = path.join(opts.out, 'admin')
  if (copyDir(opts.admin, dir)) {
    deployProject('aryn-admin', dir, path.join(opts.out, 'admin.zip'), `说明：
- 这是管理端（vben）静态包，接口基址为同源 /api（由 _worker.js 代理到后端）。
- 管理端为 hash 路由，_redirects 只是兜底。
- 登录账号使用后端 sys_user 的账号；演示前建议确认可登录。`)
    results.push(`管理端  -> ${dir}  (${path.join(opts.out, 'admin.zip')})`)
  }
}
else {
  results.push('管理端  已跳过（--skip-admin）')
}

console.log('[pages] 生成完成：')
for (const r of results) console.log('  ' + r)
console.log(`[pages] 后端 fallback：${BACKEND}`)
console.log('[pages] 上传后记得在 Pages 项目里设置环境变量 BACKEND_URL，并查看各目录下的 DEPLOY.md')
