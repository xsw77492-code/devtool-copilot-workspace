const http = require('http')
const fs = require('fs')
const path = require('path')
const { URL } = require('url')

function arg(name, def) {
  const idx = process.argv.indexOf(name)
  if (idx < 0) return def
  const v = process.argv[idx + 1]
  if (!v || v.startsWith('--')) return def
  return v
}

function hasFlag(name) {
  return process.argv.includes(name)
}

function mkdirp(p) {
  fs.mkdirSync(p, { recursive: true })
}

function nowIso() {
  return new Date().toISOString()
}

function writeEnv(outdir, sessionId, url) {
  const envPath = path.join(outdir, `${sessionId}.env`)
  const txt = `DEBUG_SERVER_URL=${url}\nDEBUG_SESSION_ID=${sessionId}\n`
  fs.writeFileSync(envPath, txt, 'utf8')
  return envPath
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let buf = ''
    req.on('data', (c) => {
      buf += c
      if (buf.length > 2 * 1024 * 1024) {
        reject(new Error('body too large'))
        try {
          req.destroy()
        } catch {}
      }
    })
    req.on('end', () => resolve(buf))
    req.on('error', reject)
  })
}

function tryListen(server, host, port) {
  return new Promise((resolve, reject) => {
    const onErr = (e) => {
      server.off('listening', onOk)
      reject(e)
    }
    const onOk = () => {
      server.off('error', onErr)
      resolve()
    }
    server.once('error', onErr)
    server.once('listening', onOk)
    server.listen(port, host)
  })
}

async function main() {
  const sessionId = arg('--session', '')
  if (!sessionId) {
    process.stderr.write('missing --session\n')
    process.exit(2)
  }
  const outdir = arg('--outdir', '.dbg')
  const startPort = Number(arg('--port', '7777'))
  const idle = Number(arg('--idle', '0'))
  const clean = hasFlag('--clean')

  const absOut = path.resolve(process.cwd(), outdir)
  mkdirp(absOut)
  const logPath = path.join(absOut, `trae-debug-log-${sessionId}.ndjson`)
  if (clean) {
    try {
      fs.unlinkSync(logPath)
    } catch {}
  }

  let lastAt = Date.now()
  let port = Number.isFinite(startPort) ? startPort : 7777

  const server = http.createServer(async (req, res) => {
    lastAt = Date.now()
    const u = new URL(req.url || '/', 'http://127.0.0.1')
    res.setHeader('Access-Control-Allow-Origin', '*')
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,DELETE,OPTIONS')
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type')
    if (req.method === 'OPTIONS') {
      res.writeHead(204)
      res.end()
      return
    }

    if (u.pathname === '/health' && req.method === 'GET') {
      let count = 0
      try {
        const txt = fs.readFileSync(logPath, 'utf8')
        count = txt ? txt.split('\n').filter(Boolean).length : 0
      } catch {}
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.writeHead(200)
      res.end(JSON.stringify({ ok: true, sessionId, count, time: nowIso() }))
      return
    }

    if (u.pathname === '/event' && req.method === 'POST') {
      try {
        const raw = await readBody(req)
        const obj = raw ? JSON.parse(raw) : {}
        const line = JSON.stringify({ ...obj, _ts: Date.now() })
        fs.appendFileSync(logPath, line + '\n', 'utf8')
        res.writeHead(200)
        res.end('ok')
      } catch (e) {
        res.writeHead(400)
        res.end(String(e && e.message ? e.message : 'bad request'))
      }
      return
    }

    if (u.pathname === '/logs' && req.method === 'DELETE') {
      try {
        fs.unlinkSync(logPath)
      } catch {}
      res.writeHead(200)
      res.end('ok')
      return
    }

    if (u.pathname === '/logs' && req.method === 'GET') {
      const last = Number(u.searchParams.get('last') || '0')
      const runId = u.searchParams.get('runId')
      const hypothesisId = u.searchParams.get('hypothesisId')
      let lines = []
      try {
        const txt = fs.readFileSync(logPath, 'utf8')
        lines = txt.split('\n').filter(Boolean)
      } catch {}
      if (Number.isFinite(last) && last > 0 && lines.length > last) {
        lines = lines.slice(lines.length - last)
      }
      let arr = []
      for (const ln of lines) {
        try {
          const o = JSON.parse(ln)
          arr.push(o)
        } catch {}
      }
      if (runId) arr = arr.filter((x) => String(x.runId || '') === String(runId))
      if (hypothesisId) arr = arr.filter((x) => String(x.hypothesisId || '') === String(hypothesisId))
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      res.writeHead(200)
      res.end(JSON.stringify(arr))
      return
    }

    res.writeHead(404)
    res.end('not found')
  })

  let ok = false
  for (let i = 0; i < 10; i++) {
    try {
      await tryListen(server, '127.0.0.1', port)
      ok = true
      break
    } catch {
      port += 1
    }
  }
  if (!ok) process.exit(3)

  const url = `http://127.0.0.1:${port}/event`
  const envPath = writeEnv(absOut, sessionId, url)
  process.stdout.write(`[debug-server] session=${sessionId}\n`)
  process.stdout.write(`[debug-server] url=${url}\n`)
  process.stdout.write(`[debug-server] env=${envPath}\n`)
  process.stdout.write(`[debug-server] log=${logPath}\n`)

  if (Number.isFinite(idle) && idle > 0) {
    setInterval(() => {
      const dt = Date.now() - lastAt
      if (dt > idle * 1000) {
        try {
          server.close(() => process.exit(0))
        } catch {
          process.exit(0)
        }
      }
    }, 1000)
  }
}

main().catch((e) => {
  process.stderr.write(String(e && e.stack ? e.stack : e) + '\n')
  process.exit(1)
})

