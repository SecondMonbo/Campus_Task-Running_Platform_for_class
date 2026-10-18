import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.API_PROXY_TARGET || 'http://localhost:8080'
  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api': {
          target,
          changeOrigin: true,
          // Cloud Mock needs no real local session or authorization credentials.
          configure(proxy) {
            if (mode !== 'apifox') return
            proxy.on('proxyReq', (request) => {
              request.removeHeader('cookie')
              request.removeHeader('authorization')
            })
            proxy.on('proxyRes', (response) => {
              delete response.headers['set-cookie']
            })
          },
        },
        '/actuator': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
