import path from 'node:path'

/**
 * uni-pages 预生成与 Vite 插件共享的唯一参数源。
 *
 * vite.config.ts 在创建 Uni()/UniKuRoot() 等插件前，用本参数先 new PageContext(...).updatePagesJSON()
 * 写出 src/pages.json；随后 UniHelperPages(同一参数) 在 configResolved 再做正式生成。
 * 两处参数必须同源，避免预生成与插件扫描范围漂移。
 */

export function createPagesOptions(root) {
  return {
    configSource: path.join(root, 'pages.config.ts'),
    dts: 'src/uni-pages.d.ts',
    subPackages: [
      'src/sub-pages',
    ],
    /**
     * 排除的页面，相对于 dir 和 subPackages
     * @default []
     */
    exclude: ['**/components/**/*.*'],
  }
}
