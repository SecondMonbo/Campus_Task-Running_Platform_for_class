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
        '/api': { target, changeOrigin: true },
        '/actuator': { target, changeOrigin: true },
      },
    },
  }
})
