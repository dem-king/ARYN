/*
 * @Author: weisheng
 * @Date: 2024-11-01 11:44:38
 * @LastEditTime: 2025-09-16 13:40:59
 * @LastEditors: weisheng
 * @Description:
 * @FilePath: /aryn-uniapp-pro/vite.config.ts
 * 记得注释
 */
import process from 'node:process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import UniModule from '@dcloudio/vite-plugin-uni'
import UniHelperLayouts from '@uni-helper/vite-plugin-uni-layouts'
import UniHelperComponents from '@uni-helper/vite-plugin-uni-components'
import AutoImport from 'unplugin-auto-import/vite'
import { WotResolver } from '@uni-helper/vite-plugin-uni-components/resolvers'
import UniKuRoot from '@uni-ku/root'
import { createPagesOptions } from './build/pages-options.mjs'

const Uni = ((UniModule as unknown as { default?: typeof UniModule }).default ?? UniModule)

const thisDir = path.dirname(fileURLToPath(import.meta.url))
const isTenantBuild = process.env.VITE_TENANT_BUILD === 'true'
const projectRoot = process.env.VITE_ROOT_DIR || process.cwd()

/**
 * 只处理本次 UNI_OUTPUT_DIR 的 project.config.json 补丁。
 * manifest 工厂已显式下发 miniprogramRoot: './'，这里只是兼容层：
 * 已有值且指向其它目录时抛错，不默默覆盖；解析失败在租户构建下必须抛错。
 */
function fixProjectConfig(tenantBuild: boolean) {
  const patch = () => {
    const outDir = process.env.UNI_OUTPUT_DIR
    if (!outDir) {
      return
    }
    const file = path.join(outDir, 'project.config.json')
    if (!fs.existsSync(file)) {
      return
    }
    let json: any
    try {
      json = JSON.parse(fs.readFileSync(file, 'utf8'))
    }
    catch (error) {
      if (tenantBuild) {
        throw new Error(`project.config.json 解析失败: ${(error as Error).message}`)
      }
      return
    }
    const existing = json.miniprogramRoot
    if (existing && existing !== './' && existing !== '') {
      if (tenantBuild) {
        throw new Error(`project.config.json miniprogramRoot 指向 "${existing}"，与期望 "./" 不一致，拒绝覆盖`)
      }
      return
    }
    if (!existing) {
      json.miniprogramRoot = './'
      fs.writeFileSync(file, JSON.stringify(json, null, 4), 'utf8')
    }
  }
  return {
    name: 'fix-project-config',
    writeBundle() { patch() },
    configureServer(server: any) {
      server.httpServer?.once?.('listening', () => setTimeout(patch, 1000))
    },
  }
}

// https://vitejs.dev/config/
export default async (mode: ConfigEnv) => {
  const UnoCSS = (await import('unocss/vite')).default
  const UniHelperPages = (await import('@uni-helper/vite-plugin-uni-pages')).default
  const { PageContext } = await import('@uni-helper/vite-plugin-uni-pages')
  const env = loadEnv(mode.mode, projectRoot)
  const pagesOptions = createPagesOptions(projectRoot)

  // pages 预生成屏障：在创建任何可能早读 pages 的插件（UniKuRoot/Uni）之前，
  // 先用与 UniHelperPages 完全相同的参数生成 src/pages.json。
  const pagesContext = new PageContext(pagesOptions, projectRoot)
  await pagesContext.updatePagesJSON()

  // manifest helper 只在默认（非租户）构建启用：租户构建的 manifest 已由脚本
  // 用同一工厂在 uni CLI 启动前写好，运行时 import helper 会晚于 CLI 早读并依赖
  // 未被 await 的内部 setup，这里彻底禁用。
  let manifestPlugin: any = null
  if (!isTenantBuild) {
    const UniHelperManifest = (await import('@uni-helper/vite-plugin-uni-manifest')).default
    manifestPlugin = UniHelperManifest()
  }

  const tenantContractPlugin = isTenantBuild
    ? (await import('./build/artifact-contract-plugin.mjs')).createArtifactContractPlugin({
        normalized: {
          schemaVersion: 1,
          key: process.env.VITE_TENANT_KEY!,
          tenantId: process.env.VITE_TENANT_ID!,
          wxAppId: process.env.VITE_TENANT_WX_APPID!,
          name: process.env.VITE_TENANT_NAME!,
          profile: process.env.VITE_ENV_NAME!,
          deployment: parseOpenBootToDeployment(process.env.VITE_OPEN_BOOT),
          openBoot: process.env.VITE_OPEN_BOOT === 'true',
          apiBaseUrl: process.env.VITE_API_BASE_URL!,
          buildId: process.env.VITE_BUILD_ID!,
        },
        evidencePath: path.join(path.dirname(process.env.UNI_OUTPUT_DIR!), 'artifact-evidence.json'),
      })
    : null

  return defineConfig({
    // 租户构建把 Vite 缓存隔离到本次 workRoot，避免 node_modules symlink 落到原根 .vite
    ...(isTenantBuild && process.env.TENANT_BUILD_CACHE_DIR
      ? { cacheDir: process.env.TENANT_BUILD_CACHE_DIR }
      : {}),
    server: {
      port: env.VITE_PORT as unknown as number,
      // 选项写法
      proxy: {
        '/api': {
          target: 'http://localhost:9999',
          changeOrigin: true,
          ws: true,
          rewrite: path => path.replace(/^\/api/, ''),
        },
      },
    },
    optimizeDeps: {
      exclude: process.env.NODE_ENV === 'development' ? ['wot-design-uni'] : [],
    },
    plugins: [
      // https://github.com/uni-helper/vite-plugin-uni-manifest
      ...(manifestPlugin ? [manifestPlugin] : []),
      // https://github.com/uni-helper/vite-plugin-uni-pages
      UniHelperPages(pagesOptions),
      // https://github.com/uni-helper/vite-plugin-uni-layouts
      UniHelperLayouts(),
      // https://github.com/uni-helper/vite-plugin-uni-components
      UniHelperComponents({
        resolvers: [WotResolver()],
        dts: 'src/components.d.ts',
        dirs: ['src/components', 'src/business'],
        directoryAsNamespace: true,
      }),
      // https://github.com/uni-ku/root
      UniKuRoot(),
      Uni(),
      UnoCSS({ mode: 'vue-scoped' }),
      // https://github.com/antfu/unplugin-auto-import
      AutoImport({
        imports: ['vue', '@vueuse/core', 'pinia', 'uni-app', {
          from: 'uni-mini-router',
          imports: ['createRouter', 'useRouter', 'useRoute'],
        }, {
          from: 'wot-design-uni',
          imports: ['useToast', 'useMessage', 'useNotify', 'CommonUtil'],
        }, {
          from: 'alova/client',
          imports: ['usePagination', 'useRequest'],
        }],
        dts: 'src/auto-imports.d.ts',
        dirs: ['src/composables', 'src/store', 'src/utils', 'src/api'],
        vueTemplate: true,
      }),
      ...(tenantContractPlugin ? [tenantContractPlugin] : []),
      fixProjectConfig(isTenantBuild),
    ],
  })
}

function parseOpenBootToDeployment(value: string | undefined) {
  return value === 'true' ? 'boot' : 'cloud'
}
