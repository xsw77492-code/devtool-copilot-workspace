const http = require('http')
const fs = require('fs')
const path = require('path')
const os = require('os')

function arg(name, def) {
  const idx = process.argv.indexOf(`--${name}`)
  if (idx < 0) return def
  const v = process.argv[idx + 1]
  if (!v || v.startsWith('--')) return def
  return v
}

const sessionId = String(arg('session', ''))
if (!sessionId) {
  process.stderr.write('Missing --session\n')
  process.exit(2)
}

const outdir = String(arg('outdir', '.dbg'))
const clean = process.argv.includes('--clean')
const idle = Number(arg('idle', '0')) || 0
const remote = process.argv.includes('--remote')
const host = remote ? '0.0.0.0' : '127.0.0.1'
const startPort = Number(arg('port', '7777')) || 7777

const absOutdir = path.resolve(process.cwd(), outdir)
fs.mkdirSync(absOutdir, { recursive: true })

const logFile = path.join(absOutdir, `trae-debug-log-${sessionId}.ndjson`)
const envFile = path.join(absOutdir, `${sessionId}.env`)
if (clean) {
  try {
    fs.writeFileSync(logFile, '')
  } catch {
  }
}

let lastActive = Date.now()
let server = null
let actualPort = startPort

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type')
}

function nowMs() {
  return Date.now()
}

function listenWithProbe(p, triesLeft) {
  server = http.createServer((req, res) => {
    if (req.url !== '/event') {
      cors(res)
      res.statusCode = 404
      res.end('not found')
      return
    }
    if (req.method === 'OPTIONS') {
      cors(res)
      res.statusCode = 204
      res.end()
      return
    }
    if (req.method !== 'POST') {
      cors(res)
      res.statusCode = 405
      res.end('method not allowed')
      return
    }

    let raw = ''
    req.on('data', (c) => {
      raw += String(c || '')
    })
    req.on('end', () => {
      cors(res)
      try {
        const ev = raw ? JSON.parse(raw) : {}
        if (!ev.ts) ev.ts = nowMs()
        if (!ev.sessionId) ev.sessionId = sessionId
        fs.appendFileSync(logFile, `${JSON.stringify(ev)}\n`)
        lastActive = Date.now()
        res.statusCode = 200
        res.end('ok')
      } catch {
        res.statusCode = 400
        res.end('bad json')
      }
    })
  })

  server.on('error', (e) => {
    if (e && e.code === 'EADDRINUSE' && triesLeft > 0) {
      actualPort = p + 1
      listenWithProbe(actualPort, triesLeft - 1)
      return
    }
    process.stderr.write(String(e?.stack || e) + '\n')
    process.exit(1)
  })

  server.listen(p, host, () => {
    const apiUrl = `http://${remote ? pickIp() : '127.0.0.1'}:${p}/event`
    fs.writeFileSync(envFile, `DEBUG_SERVER_URL=${apiUrl}\nDEBUG_SESSION_ID=${sessionId}\n`)
    process.stdout.write('@@DEBUG_SERVER_INFO\n')
    process.stdout.write(
      JSON.stringify(
        {
          api_url: apiUrl,
          session_id: sessionId,
          log_dir: absOutdir,
          log_file: logFile,
          env_file: envFile
        },
        null,
        2
      ) + '\n'
    )
    process.stdout.write('@@END_DEBUG_SERVER_INFO\n')
  })
}

function pickIp() {
  const ifs = os.networkInterfaces()
  for (const k of Object.keys(ifs)) {
    const arr = ifs[k] || []
    for (const it of arr) {
      if (!it || it.family !== 'IPv4') continue
      if (it.internal) continue
      return it.address
    }
  }
  return '127.0.0.1'
}

if (idle > 0) {
  setInterval(() => {
    if (Date.now() - lastActive > idle * 1000) {
      process.exit(0)
    }
  }, 1000).unref()
}

listenWithProbe(actualPort, 10)

