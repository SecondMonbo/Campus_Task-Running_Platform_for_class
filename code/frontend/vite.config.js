import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import { statefulDemoApi } from './vite.demo.js'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.API_PROXY_TARGET || 'http://localhost:8080'
  return {
    plugins: [vue(), ...(mode === 'demo' ? [statefulDemoApi()] : [])],
    server: {
      proxy: {
        '/api': {
          target,
          changeOrigin: true,
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
        '/actuator': { target, changeOrigin: true },
      },
    },
  }
})
