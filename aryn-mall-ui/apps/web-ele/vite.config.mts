import { fileURLToPath } from 'node:url';

import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      resolve: {
        alias: {
          // 将 `#` 子路径映射到本应用 src 目录，使应用内及工作区依赖包
          // （如 @vben/utils）中的 `#/api/request` 等导入能正确解析到本应用已配置的请求客户端。
          '#': fileURLToPath(new URL('./src', import.meta.url)),
        },
      },
      plugins: [],
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // mock代理目标地址
            target: 'http://localhost:9999',
            ws: false,
          },
        },
      },
    },
  };
});
