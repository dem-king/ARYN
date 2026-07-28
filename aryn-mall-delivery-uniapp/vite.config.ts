import { fileURLToPath, URL } from 'node:url'
import UniModule from '@dcloudio/vite-plugin-uni'
import { defineConfig } from 'vite'

const Uni = ((UniModule as unknown as { default?: typeof UniModule }).default ?? UniModule)

export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  plugins: [Uni()],
})
