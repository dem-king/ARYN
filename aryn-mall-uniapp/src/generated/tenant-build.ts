/**
 * 默认（开发）构建的身份模块：恒为 null。
 *
 * 租户构建由 scripts/build-tenant.mjs 在副本中重新生成本文件，写入真实冻结身份；
 * 快照白名单排除了本文件，默认版本永远不会进入租户副本。
 * 运行时守卫（src/api/core/tenant-identity.ts）以 null 与否判定租户模式。
 */
import type { TenantBuildIdentity } from '../api/core/tenant-build-types'

export const tenantBuildIdentity: Readonly<TenantBuildIdentity> | null = null
