import { createLogger, defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// 静默 vite 在 Spring Boot devtools 热重启窗口期里大量抛出的
// "ws proxy error: write ECONNABORTED/ECONNRESET" 噪音。
// 这些错误的根因是后端在重启瞬间强制关闭了 ws，前端 reconnect 1.2s 后
// 又撞上后端尚未就绪的握手窗口，导致浏览器 socket 提前 abort。
// 终端刷屏不影响功能，连接在前端 scheduleReconnect 下会自动恢复。
const viteLogger = createLogger()
const origLoggerError = viteLogger.error.bind(viteLogger)
viteLogger.error = (msg: string, options?: any) => {
  if (typeof msg === 'string' && /(ws proxy error|EcoNNABORTED|ECONNRESET)/i.test(msg)) {
    return
  }
  origLoggerError(msg, options)
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // Keep the browser-to-backend path consistent across environments. The
  // backend used by this workspace listens on 8085; .env files can still
  // override it when a different deployment is required.
  const target = env.VITE_BACKEND_URL || 'http://127.0.0.1:8085'

  const proxy = {
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

  return {
    plugins: [vue()],
    customLogger: viteLogger,
    server: {
      host: '127.0.0.1',
      port: 5174,
      strictPort: true,
      proxy
    },
    preview: {
      host: '127.0.0.1',
      port: 4173,
      strictPort: true,
      proxy
    }
  }
})
