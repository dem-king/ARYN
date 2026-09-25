import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      plugins: [],
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // mock代理目标地址
            target: 'http://localhost:9999',
            // 实时消息通道与接口共用 /api 前缀，必须开启 WebSocket 升级。
            ws: true,
          },
          '/message': {
            changeOrigin: true,
            target: 'http://localhost:9999',
            ws: true,
          },
        },
      },
    },
  };
});
