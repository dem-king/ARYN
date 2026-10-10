/*
 * manifest 源配置 → 薄包装。
 *
 * 完整静态对象已迁移到 build/manifest-factory.mjs（唯一工厂）；本文件只负责
 * 区分默认开发与租户构建两种身份来源：
 * - 默认开发：固定默认名称与微信 AppID，忽略残留的租户环境变量；
 * - 租户构建（VITE_TENANT_BUILD=true）：由 build-tenant.mjs 注入名称与 AppID，缺任一立即失败。
 * src/manifest.json 是生成物；租户构建不会触碰原目录的该文件。
 */
import { defineManifestConfig } from '@uni-helper/vite-plugin-uni-manifest'
import { createManifest, DEFAULT_APP_NAME, DEFAULT_WX_APP_ID } from './build/manifest-factory.mjs'

const tenantBuild = process.env.VITE_TENANT_BUILD === 'true'
if (tenantBuild && (!process.env.VITE_TENANT_NAME || !process.env.VITE_TENANT_WX_APPID)) {
  throw new Error('租户构建缺少名称或微信 AppID')
}

export default defineManifestConfig(createManifest({
  name: tenantBuild ? process.env.VITE_TENANT_NAME as string : DEFAULT_APP_NAME,
  wxAppId: tenantBuild ? process.env.VITE_TENANT_WX_APPID as string : DEFAULT_WX_APP_ID,
}))
