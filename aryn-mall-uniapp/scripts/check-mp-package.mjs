#!/usr/bin/env node
/**
 * 微信小程序产物分包门禁：pnpm check:mp-package [产物目录]
 * 默认检查 dist/build/mp-weixin；dev 产物未压缩，体积不具参考性。
 */
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath, pathToFileURL } from 'node:url'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const { auditMpPackage, formatAuditReport, DEFAULT_MP_PACKAGE_POLICY } = await import(
  pathToFileURL(path.join(projectRoot, 'build', 'mp-package-audit.mjs')).href
)

const outputDir = path.resolve(projectRoot, process.argv[2] ?? 'dist/build/mp-weixin')
const result = auditMpPackage(outputDir, {
  ...DEFAULT_MP_PACKAGE_POLICY,
  requiredSubPackageRoots: ['pages/delivery'],
})
console.log(`产物目录: ${path.relative(projectRoot, outputDir)}`)
console.log(formatAuditReport(result))
process.exitCode = result.ok ? 0 : 1
