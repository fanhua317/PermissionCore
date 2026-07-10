import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:54321'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
      },
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
        },
        '/uploads': {
          target: apiProxyTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules/@element-plus/icons-vue')) return 'element-icons'
            if (id.includes('node_modules/element-plus')) return 'element-plus'
            if (id.includes('node_modules/vue') || id.includes('node_modules/pinia')) return 'vue-vendor'
            if (id.includes('node_modules/axios')) return 'http-vendor'
          },
        },
      },
    },
  }
})
