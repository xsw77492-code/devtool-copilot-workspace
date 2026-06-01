import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_BACKEND_URL || 'http://127.0.0.1:8080'

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api': {
          target,
          changeOrigin: true
        },
        '/ws': {
          target,
          changeOrigin: true,
          ws: true
        },
        '/ai': {
          target,
          changeOrigin: true,
          bypass: (req) => {
            const accept = String(req.headers?.accept || '')
            if (req.method === 'GET' && accept.includes('text/html')) {
              return '/index.html'
            }
            return null
          }
        }
      }
    }
  }
})
