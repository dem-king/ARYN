/**
 * 产物契约插件（运行在租户副本的 Vite 内）。
 *
 * configResolved：固化本次生效的 VITE_* env 并与规范化身份逐字段比对，任何不一致直接失败。
 * generateBundle：从入口 chunk 沿 imports/dynamicImports 建立可达关系，确认身份模块
 *   （src/generated/tenant-build.ts）被入口可达的 chunk 携带，且身份字面量确实内联进 JS。
 * writeBundle：把证据写到 attempt 目录（绝不写进小程序输出目录，避免被上传）。
 *
 * 证据是结构化的（模块图可达性 + env 固化），不是"字符串命中即通过"；
 * 最终消费与放行由 build-tenant.mjs 的 verifier 结合证据文件复核。
 */

import fs from 'node:fs'
import path from 'node:path'

const IDENTITY_MODULE_SUFFIX = 'src/generated/tenant-build.ts'

export function createArtifactContractPlugin({ normalized, evidencePath }) {
  /** @type {Record<string, string>} */
  const effectiveEnv = {}
  /** @type {Array<string>} */
  const envMismatches = []
  const chunkEvidence = []
  const literalFindings = []
  let identityReachable = false
  let identityLocations = []

  function chunkGraph(bundle) {
    const byFacade = new Map()
    for (const fileName of Object.keys(bundle)) {
      const chunk = bundle[fileName]
      if (chunk.type === 'chunk') {
        byFacade.set(fileName, chunk)
      }
    }
    return byFacade
  }

  function collectReachable(bundle, byFacade, fromChunks) {
    const reachable = new Set()
    const queue = [...fromChunks]
    while (queue.length > 0) {
      const current = queue.pop()
      if (reachable.has(current)) {
        continue
      }
      reachable.add(current)
      const chunk = byFacade.get(current)
      if (!chunk) {
        continue
      }
      for (const next of [...chunk.imports ?? [], ...chunk.dynamicImports ?? []]) {
        if (byFacade.has(next) && !reachable.has(next)) {
          queue.push(next)
        }
      }
    }
    return reachable
  }

  return {
    name: 'aryn:artifact-contract',
    enforce: 'post',
    configResolved(config) {
      const env = config.env ?? {}
      const keys = ['VITE_TENANT_BUILD', 'VITE_TENANT_KEY', 'VITE_TENANT_ID', 'VITE_TENANT_WX_APPID', 'VITE_TENANT_NAME', 'VITE_OPEN_BOOT', 'VITE_API_BASE_URL', 'VITE_ENV_NAME', 'VITE_BUILD_ID']
      const expected = {
        VITE_TENANT_BUILD: 'true',
        VITE_TENANT_KEY: normalized.key,
        VITE_TENANT_ID: normalized.tenantId,
        VITE_TENANT_WX_APPID: normalized.wxAppId,
        VITE_TENANT_NAME: normalized.name,
        VITE_OPEN_BOOT: String(normalized.openBoot),
        VITE_API_BASE_URL: normalized.apiBaseUrl,
        VITE_ENV_NAME: normalized.profile,
        VITE_BUILD_ID: normalized.buildId,
      }
      for (const key of keys) {
        effectiveEnv[key] = String(env[key] ?? '')
        if (effectiveEnv[key] !== expected[key]) {
          envMismatches.push(`${key}: 期望 "${expected[key]}"，实际 "${effectiveEnv[key]}"`)
        }
      }
      if (envMismatches.length > 0) {
        throw new Error(`产物契约插件：生效 env 与规范化身份不一致\n${envMismatches.join('\n')}`)
      }
    },
    generateBundle(_options, bundle) {
      const byFacade = chunkGraph(bundle)
      const entryChunks = Object.keys(bundle).filter(name => bundle[name].type === 'chunk' && bundle[name].isEntry)
      if (entryChunks.length === 0) {
        throw new Error('产物契约插件：bundle 中没有入口 chunk')
      }
      const reachable = collectReachable(bundle, byFacade, entryChunks)

      const identityChunks = Object.keys(bundle).filter((name) => {
        const chunk = bundle[name]
        return chunk.type === 'chunk'
          && Object.keys(chunk.modules ?? {}).some(moduleId => moduleId.endsWith(IDENTITY_MODULE_SUFFIX))
      })
      identityReachable = identityChunks.some(name => reachable.has(name))
      identityLocations = identityChunks

      // 身份字面量必须真的内联进可达 JS（否则 identity 未被消费会被 tree-shaking 掉）
      const expectedLiterals = [normalized.tenantId, normalized.wxAppId, normalized.buildId, normalized.apiBaseUrl]
      for (const name of reachable) {
        const chunk = byFacade.get(name)
        const code = chunk.code ?? ''
        const hits = expectedLiterals.filter(literal => literal.length >= 8 && code.includes(literal))
        if (hits.length > 0) {
          literalFindings.push({ chunk: name, literals: hits })
        }
      }
      const missingLiterals = expectedLiterals.filter(literal =>
        !literalFindings.some(finding => finding.literals.includes(literal)))
      if (!identityReachable) {
        throw new Error(`产物契约插件：身份模块 ${IDENTITY_MODULE_SUFFIX} 未被入口 chunk 可达（可能未被 import 或被摇树），identityChunks=${JSON.stringify(identityChunks)}`)
      }
      if (missingLiterals.length > 0) {
        throw new Error(`产物契约插件：身份字面量未内联进可达 chunk: ${missingLiterals.join(', ')}`)
      }

      for (const name of Object.keys(byFacade)) {
        const chunk = byFacade.get(name)
        chunkEvidence.push({
          fileName: name,
          isEntry: Boolean(chunk.isEntry),
          isDynamicEntry: Boolean(chunk.isDynamicEntry),
          reachable: reachable.has(name),
        })
      }
    },
    writeBundle() {
      const evidence = {
        normalized,
        effectiveEnv,
        identityReachable,
        identityChunks: identityLocations,
        entryChunks: Object.keys(this.getBundleInfo?.() ?? {}),
        literalFindings,
        chunkEvidence,
        recordedAt: new Date().toISOString(),
      }
      fs.mkdirSync(path.dirname(evidencePath), { recursive: true })
      fs.writeFileSync(evidencePath, JSON.stringify(evidence, null, 2), 'utf8')
    },
  }
}
