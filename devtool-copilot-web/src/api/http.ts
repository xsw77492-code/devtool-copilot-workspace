import axios from 'axios'

export interface R<T> {
  code: number
  message: string
  data: T
}

interface LoginResponse {
  accessToken: string
  refreshToken: string
  me: any
}

let refreshing: Promise<LoginResponse | null> | null = null

// #region debug-point A:init
const __DTC_DBG_SESSION = 'ui-slow-lag'
function __dtcDbgUrl() {
  const v = typeof localStorage === 'undefined' ? null : localStorage.getItem('dtc_debug_url')
  return v || 'http://127.0.0.1:7777/event'
}
function __dtcDbgRunId() {
  const v = typeof localStorage === 'undefined' ? null : localStorage.getItem('dtc_dbg_run')
  return v || 'pre'
}
function __dtcTraceId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}
function __dtcDbg(hypothesisId: string, location: string, msg: string, data?: any, traceId?: string) {
  const on = typeof localStorage === 'undefined' ? null : localStorage.getItem('dtc_debug_on')
  if (on !== '1') return
  if (typeof fetch === 'undefined') return
  fetch(__dtcDbgUrl(), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      sessionId: __DTC_DBG_SESSION,
      runId: __dtcDbgRunId(),
      hypothesisId,
      location,
      msg,
      data: data || {},
      traceId: traceId || null,
      ts: Date.now()
    })
  }).catch(() => {})
}
// #endregion

export async function apiGet<T>(path: string, query?: Record<string, string | number | boolean | undefined>) {
  const url = withQuery(path, query)
  return request<T>(url, { method: 'GET' })
}

export async function apiPost<T>(path: string, body?: unknown) {
  return request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })
}

export async function apiPut<T>(path: string, body?: unknown) {
  return request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) })
}

export async function apiDelete<T>(path: string) {
  return request<T>(path, { method: 'DELETE' })
}

function withQuery(path: string, query?: Record<string, string | number | boolean | undefined>) {
  if (!query) return path
  const usp = new URLSearchParams()
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null) continue
    usp.set(k, String(v))
  }
  const qs = usp.toString()
  return qs ? `${path}?${qs}` : path
}

async function request<T>(path: string, init: RequestInit, retried?: boolean): Promise<T> {
  const token = localStorage.getItem('dtc_token')
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> | undefined)
  }
  if (token) headers.Authorization = `Bearer ${token}`

  const method = (init.method || 'GET').toUpperCase()
  const rawBody = init.body
  let data: unknown = undefined
  if (rawBody !== undefined && rawBody !== null) {
    if (typeof rawBody === 'string') {
      data = rawBody.length ? JSON.parse(rawBody) : undefined
    } else {
      data = rawBody
    }
  }

  const traceId = __dtcTraceId()
  const t0 = typeof performance !== 'undefined' && performance.now ? performance.now() : Date.now()

  let resp: { status: number; data: unknown }
  try {
    resp = await axios.request({
      url: path,
      method: method as any,
      headers,
      data,
      validateStatus: () => true
    })
  } catch {
    // #region debug-point A:http-network-error
    __dtcDbg('A', 'api/http.ts:axios', '[DEBUG] http network error', { path, method }, traceId)
    // #endregion
    throw new Error('网络错误，请确认后端已启动')
  }

  const t1 = typeof performance !== 'undefined' && performance.now ? performance.now() : Date.now()
  const costMs = Math.round((t1 as any) - (t0 as any))

  const payload = resp.data as R<T> | undefined
  if (!payload || typeof payload.code !== 'number') {
    // #region debug-point A:http-bad-payload
    __dtcDbg(
      'A',
      'api/http.ts:payload',
      '[DEBUG] http invalid payload',
      { path, method, status: resp.status, costMs },
      traceId
    )
    // #endregion
    throw new Error(`请求失败(${resp.status})`)
  }

  if (costMs >= 250) {
    // #region debug-point A:http-latency
    __dtcDbg(
      'A',
      'api/http.ts:latency',
      '[DEBUG] http slow',
      { path, method, status: resp.status, code: payload.code, costMs },
      traceId
    )
    // #endregion
  }

  if (payload.code !== 0) {
    if (payload.code === 401) {
      // #region debug-point B:http-401
      __dtcDbg('B', 'api/http.ts:401', '[DEBUG] http 401', { path, method, costMs }, traceId)
      // #endregion
      const ok = await tryRefresh(path, retried)
      if (ok) {
        return request<T>(path, init, true)
      }
      localStorage.removeItem('dtc_token')
      localStorage.removeItem('dtc_refresh_token')
      localStorage.removeItem('dtc_me')
      if (typeof window !== 'undefined') {
        try {
          window.dispatchEvent(new CustomEvent('dtc:session-cleared'))
        } catch {
        }
      }
    }
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

async function tryRefresh(path: string, retried?: boolean): Promise<boolean> {
  if (retried) return false
  if (path.startsWith('/api/user/login')) return false
  if (path.startsWith('/api/user/register')) return false
  if (path.startsWith('/api/user/refresh')) return false
  if (path.startsWith('/api/user/password-reset')) return false

  const refreshToken = localStorage.getItem('dtc_refresh_token')
  if (!refreshToken) return false

  const res = await refreshOnce(refreshToken)
  if (!res) return false

  localStorage.setItem('dtc_token', res.accessToken)
  localStorage.setItem('dtc_refresh_token', res.refreshToken)
  localStorage.setItem('dtc_me', JSON.stringify(res.me))
  return true
}

async function refreshOnce(refreshToken: string): Promise<LoginResponse | null> {
  if (refreshing) return refreshing
  refreshing = (async () => {
    let resp: { status: number; data: unknown }
    try {
      resp = await axios.request({
        url: '/api/user/refresh',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        data: { refreshToken },
        validateStatus: () => true
      })
    } catch {
      return null
    }

    const payload = resp.data as R<LoginResponse> | undefined
    if (!payload || typeof payload.code !== 'number') return null
    if (payload.code !== 0) return null
    return payload.data
  })()

  try {
    return await refreshing
  } finally {
    refreshing = null
  }
}
