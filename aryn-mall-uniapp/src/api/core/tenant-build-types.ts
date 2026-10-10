/**
 * 租户构建身份类型：默认模块（src/generated/tenant-build.ts 的 null 版本）与
 * 租户副本生成的真实身份模块共享此定义。消费者先判空再读字段；
 * 不允许用 any 绕过 —— 默认开发与租户副本分别走 type-check。
 */
export interface TenantBuildIdentity {
  schemaVersion: number
  key: string
  tenantId: string
  wxAppId: string
  name: string
  profile: 'production' | 'staging'
  deployment: 'boot' | 'cloud'
  openBoot: boolean
  apiBaseUrl: string
  buildId: string
}
