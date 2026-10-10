import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'

/**
 * 租户配置解析、校验与规范化。
 *
 * 校验失败一律抛 TenantConfigError（message 面向构建者），由调用方转成非零退出；
 * 绝不回退到默认租户或示例域名。空 apiBaseUrl 是「未配置」，生产/预发构建必须失败。
 */

export const PROFILES = ['production', 'staging']
export const DEPLOYMENTS = ['boot', 'cloud']

export class TenantConfigError extends Error {
  constructor(message) {
    super(message)
    this.name = 'TenantConfigError'
  }
}

const KEY_RE = /^[a-z][a-z0-9-]{0,31}$/
const WX_APP_ID_RE = /^wx[0-9a-f]{16}$/
const FORBIDDEN_HOST_PATTERNS = [
  { re: /^localhost$/i, reason: 'localhost' },
  { re: /\.local$/i, reason: '本地 mDNS 域名' },
  { re: /\.invalid$/i, reason: '保留示例域名 .invalid' },
  { re: /\.test$/i, reason: '保留测试域名 .test' },
  { re: /(^|\.)example\.(com|org|net)$/i, reason: 'example 占位域名' },
  { re: /(^|\.)yourapp\.com$/i, reason: 'yourapp 占位域名' },
]
const FORBIDDEN_HOSTS = new Set(['127.0.0.1', '0.0.0.0', '::1', '::', '[::1]', '169.254.x.x'])
const ALLOWED_TOP_FIELDS = ['schemaVersion', 'key', 'name', 'tenantId', 'wxAppId', 'defaultProfile', 'defaultDeployment', 'profiles']

function fail(message) {
  throw new TenantConfigError(message)
}

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function readJsonFile(file) {
  let raw
  try {
    raw = fs.readFileSync(file, 'utf8')
  }
  catch (error) {
    fail(`无法读取租户配置 ${file}: ${error.message}`)
  }
  try {
    return JSON.parse(raw)
  }
  catch (error) {
    fail(`租户配置 ${file} 不是合法 JSON: ${error.message}`)
  }
}

export function listTenantKeys(tenantsDir) {
  let entries
  try {
    entries = fs.readdirSync(tenantsDir, { withFileTypes: true })
  }
  catch (error) {
    fail(`无法读取租户配置目录 ${tenantsDir}: ${error.message}`)
  }
  return entries
    .filter(entry => entry.isFile() && entry.name.endsWith('.json') && !entry.name.startsWith('_'))
    .map(entry => entry.name.slice(0, -'.json'.length))
    .sort()
}

/**
 * 校验单个租户配置文件并返回原始规范化结构（不含 profile/deployment 组合展开）。
 */
export function loadTenantConfig(tenantsDir, key) {
  if (typeof key !== 'string' || key.startsWith('_')) {
    fail(`非法租户 key: ${key}`)
  }
  if (!KEY_RE.test(key)) {
    fail(`租户 key "${key}" 不符合 ^[a-z][a-z0-9-]{0,31}$`)
  }
  const file = path.join(tenantsDir, `${key}.json`)
  if (!fs.existsSync(file)) {
    fail(`租户配置不存在: ${file}`)
  }
  const config = readJsonFile(file)
  if (!isPlainObject(config)) {
    fail(`租户配置 ${file} 顶层必须是 JSON 对象`)
  }
  const unknownFields = Object.keys(config).filter(field => !ALLOWED_TOP_FIELDS.includes(field))
  if (unknownFields.length > 0) {
    fail(`租户配置 ${file} 存在未知字段: ${unknownFields.join(', ')}`)
  }
  if (config.schemaVersion !== 1) {
    fail(`租户配置 ${file} 的 schemaVersion 必须是数字 1`)
  }
  if (typeof config.key !== 'string' || config.key !== key) {
    fail(`租户配置 ${file} 的 key 必须与文件名一致（"${key}"）`)
  }
  if (typeof config.name !== 'string' || !config.name.trim()) {
    fail(`租户配置 ${file} 的 name 不能为空`)
  }
  if (typeof config.tenantId !== 'string' || !/^[1-9][0-9]*$/.test(config.tenantId)) {
    fail(`租户配置 ${file} 的 tenantId 必须是十进制正整数字符串（不能是 JSON number，防止雪花 ID 精度丢失）`)
  }
  if (typeof config.wxAppId !== 'string' || !WX_APP_ID_RE.test(config.wxAppId)) {
    fail(`租户配置 ${file} 的 wxAppId 必须形如 wx + 16 位十六进制`)
  }
  if (config.wxAppId === 'wx0000000000000000') {
    fail(`租户配置 ${file} 使用了游客 AppID`)
  }
  if (!PROFILES.includes(config.defaultProfile)) {
    fail(`租户配置 ${file} 的 defaultProfile 必须是 ${PROFILES.join(' 或 ')}`)
  }
  if (!DEPLOYMENTS.includes(config.defaultDeployment)) {
    fail(`租户配置 ${file} 的 defaultDeployment 必须是 ${DEPLOYMENTS.join(' 或 ')}`)
  }
  if (!isPlainObject(config.profiles)) {
    fail(`租户配置 ${file} 缺少 profiles 对象`)
  }
  for (const profile of PROFILES) {
    if (!isPlainObject(config.profiles[profile])) {
      fail(`租户配置 ${file} 缺少 profiles.${profile}`)
    }
    for (const deployment of DEPLOYMENTS) {
      const combo = config.profiles[profile][deployment]
      if (!isPlainObject(combo)) {
        fail(`租户配置 ${file} 缺少 profiles.${profile}.${deployment}`)
      }
      const unknown = Object.keys(combo).filter(k => k !== 'apiBaseUrl')
      if (unknown.length > 0) {
        fail(`租户配置 ${file} profiles.${profile}.${deployment} 存在未知字段: ${unknown.join(', ')}`)
      }
      if (typeof combo.apiBaseUrl !== 'string') {
        fail(`租户配置 ${file} profiles.${profile}.${deployment}.apiBaseUrl 必须是字符串（空字符串表示未配置）`)
      }
    }
  }
  return config
}

function validateApiBaseUrl(baseUrl, label) {
  if (!baseUrl.trim()) {
    fail(`${label} 的 apiBaseUrl 为空：该组合尚未配置部署地址，不能构建（请由部署负责人填写真实 HTTPS origin）`)
  }
  let url
  try {
    url = new URL(baseUrl)
  }
  catch {
    fail(`${label} 的 apiBaseUrl "${baseUrl}" 不是合法 URL`)
  }
  if (url.protocol !== 'https:') {
    fail(`${label} 的 apiBaseUrl 必须是 HTTPS，收到 "${url.protocol}"`)
  }
  if (url.username || url.password) {
    fail(`${label} 的 apiBaseUrl 不允许携带账号密码`)
  }
  if (url.search || url.hash) {
    fail(`${label} 的 apiBaseUrl 不允许携带 query 或 fragment`)
  }
  if (url.pathname && url.pathname !== '/') {
    fail(`${label} 的 apiBaseUrl 统一为 origin，不允许携带路径 "${url.pathname}"`)
  }
  const hostname = url.hostname.toLowerCase()
  if (FORBIDDEN_HOSTS.has(hostname) || hostname.startsWith('169.254.') || hostname.startsWith('fe80:')) {
    fail(`${label} 的 apiBaseUrl 指向本机/链路本地地址 "${hostname}"，不能用于发布包`)
  }
  for (const { re, reason } of FORBIDDEN_HOST_PATTERNS) {
    if (re.test(hostname)) {
      fail(`${label} 的 apiBaseUrl 使用了${reason} "${hostname}"`)
    }
  }
  return `${url.protocol}//${url.host}`
}

/**
 * 全量预检：所有配置文件各自合法，且不同 key 不共享 tenantId / wxAppId。
 * `all` 构建在编译任何包之前调用；单租户构建也调用（错误提前失败）。
 */
export function precheckAll(tenantsDir) {
  const keys = listTenantKeys(tenantsDir)
  if (keys.length === 0) {
    fail(`租户配置目录 ${tenantsDir} 没有任何可用配置（下划线开头的模板文件不参与构建）`)
  }
  const byTenantId = new Map()
  const byAppId = new Map()
  const configs = keys.map((key) => {
    const config = loadTenantConfig(tenantsDir, key)
    if (byTenantId.has(config.tenantId)) {
      fail(`租户 ${key} 与 ${byTenantId.get(config.tenantId)} 重复使用 tenantId ${config.tenantId}`)
    }
    byTenantId.set(config.tenantId, key)
    if (byAppId.has(config.wxAppId)) {
      fail(`租户 ${key} 与 ${byAppId.get(config.wxAppId)} 重复使用微信 AppID ${config.wxAppId}`)
    }
    byAppId.set(config.wxAppId, key)
    return config
  })
  return configs
}

/**
 * 解析命令行 profile/deployment：未显式传参时回退租户 default，且该组合的 apiBaseUrl 必须已配置。
 */
export function resolveTarget(config, { profile, deployment }) {
  const resolvedProfile = profile ?? config.defaultProfile
  const resolvedDeployment = deployment ?? config.defaultDeployment
  if (!PROFILES.includes(resolvedProfile)) {
    fail(`profile 必须是 ${PROFILES.join(' 或 ')}，收到 "${resolvedProfile}"`)
  }
  if (!DEPLOYMENTS.includes(resolvedDeployment)) {
    fail(`deployment 必须是 ${DEPLOYMENTS.join(' 或 ')}，收到 "${resolvedDeployment}"`)
  }
  const combo = config.profiles[resolvedProfile]?.[resolvedDeployment]
  if (!isPlainObject(combo)) {
    fail(`租户 ${config.key} 缺少 profiles.${resolvedProfile}.${resolvedDeployment}`)
  }
  const apiBaseUrl = validateApiBaseUrl(combo.apiBaseUrl, `租户 ${config.key} profiles.${resolvedProfile}.${resolvedDeployment}`)
  return {
    profile: resolvedProfile,
    deployment: resolvedDeployment,
    openBoot: resolvedDeployment === 'boot',
    apiBaseUrl,
  }
}

/**
 * 规范化成构建全程使用的唯一身份对象（buildId 由调用方生成传入）。
 */
export function normalize(config, target, buildId) {
  return Object.freeze({
    schemaVersion: 1,
    key: config.key,
    name: config.name.trim(),
    tenantId: config.tenantId,
    wxAppId: config.wxAppId,
    profile: target.profile,
    deployment: target.deployment,
    openBoot: target.openBoot,
    apiBaseUrl: target.apiBaseUrl,
    buildId,
  })
}

export function newBuildId() {
  return crypto.randomUUID()
}

/** 供 --dry-run / 日志使用：剥掉任何敏感字段后的展示形态。 */
export function toDisplay(normalized) {
  return {
    key: normalized.key,
    name: normalized.name,
    tenantId: normalized.tenantId,
    wxAppId: normalized.wxAppId,
    profile: normalized.profile,
    deployment: normalized.deployment,
    openBoot: normalized.openBoot,
    apiBaseUrl: normalized.apiBaseUrl,
    buildId: normalized.buildId,
  }
}
