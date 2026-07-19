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
import { defineConfig, loadEnv } from 'vite'
import UniModule from '@dcloudio/vite-plugin-uni'
import UniHelperManifest from '@uni-helper/vite-plugin-uni-manifest'
import UniHelperPages from '@uni-helper/vite-plugin-uni-pages'
import UniHelperLayouts from '@uni-helper/vite-plugin-uni-layouts'
import UniHelperComponents from '@uni-helper/vite-plugin-uni-components'
import AutoImport from 'unplugin-auto-import/vite'
import { WotResolver } from '@uni-helper/vite-plugin-uni-components/resolvers'
import UniKuRoot from '@uni-ku/root'
import Optimization from '@uni-ku/bundle-optimizer'

const Uni = ((UniModule as unknown as { default?: typeof UniModule }).default ?? UniModule)
// https://vitejs.dev/config/
export default async (mode: ConfigEnv) => {
  const UnoCSS = (await import('unocss/vite')).default
  const env = loadEnv(mode.mode, process.cwd())
  return defineConfig({
    server: {
      port: env.VITE_PORT as unknown as number,
      // 选项写法
      proxy: {
        '/api': {
          target: 'http://localhost:9999',
          changeOrigin: true,
          rewrite: path => path.replace(/^\/api/, ''),
        },
      },
    },
    optimizeDeps: {
      exclude: process.env.NODE_ENV === 'development' ? ['wot-design-uni'] : [],
    },
    plugins: [
      // https://github.com/uni-helper/vite-plugin-uni-manifest
      UniHelperManifest(),
      // https://github.com/uni-helper/vite-plugin-uni-pages
      UniHelperPages({
        dts: 'src/uni-pages.d.ts',
        subPackages: [
          'src/sub-pages',
        ],
        /**
         * 排除的页面，相对于 dir 和 subPackages
         * @default []
         */
        exclude: ['**/components/**/*.*'],
      }),
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
      // https://github.com/uni-ku/bundle-optimizer
      Optimization({
        logger: true,
      }),
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
      // https://github.com/antfu/unocss
      // see unocss.config.ts for config
      UnoCSS(),
    ],
  })
}
