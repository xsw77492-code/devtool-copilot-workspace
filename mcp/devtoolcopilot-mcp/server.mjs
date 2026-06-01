const BASE_URL = (process.env.DTC_BASE_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '')
const TOKEN = process.env.DTC_TOKEN || ''

function jsonRpcError(id, code, message, data) {
  return {
    jsonrpc: '2.0',
    id: id ?? null,
    error: {
      code,
      message,
      ...(typeof data === 'undefined' ? {} : { data })
    }
  }
}

function jsonRpcResult(id, result) {
  return { jsonrpc: '2.0', id: id ?? null, result }
}

function send(msg) {
  const body = Buffer.from(JSON.stringify(msg), 'utf8')
  process.stdout.write(`Content-Length: ${body.length}\r\n\r\n`)
  process.stdout.write(body)
}

async function apiCall(method, path, body) {
  if (!TOKEN) throw new Error('DTC_TOKEN 未设置')
  const resp = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${TOKEN}`
    },
    body: body ? JSON.stringify(body) : undefined
  })
  const text = await resp.text()
  let obj = null
  try {
    obj = text ? JSON.parse(text) : null
  } catch {
    obj = null
  }
  if (!resp.ok) {
    const msg = obj && typeof obj.message === 'string' ? obj.message : `HTTP ${resp.status}`
    const err = new Error(msg)
    err.status = resp.status
    err.raw = text
    throw err
  }
  if (!obj || typeof obj.code !== 'number') return obj
  if (obj.code !== 0) throw new Error(obj.message || '请求失败')
  return obj.data
}

const tools = [
  {
    name: 'dtc.task.create',
    description: '创建任务（默认分配给自己，source=MCP）',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['projectId', 'title'],
      properties: {
        projectId: { type: 'number' },
        title: { type: 'string' },
        description: { type: 'string' },
        priority: { type: 'string', enum: ['HIGH', 'MEDIUM', 'LOW'] }
      }
    }
  },
  {
    name: 'dtc.task.get',
    description: '获取任务详情',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['taskId'],
      properties: { taskId: { type: 'number' } }
    }
  },
  {
    name: 'dtc.task.list',
    description: '列出项目任务（按后端默认排序）',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['projectId'],
      properties: { projectId: { type: 'number' } }
    }
  },
  {
    name: 'dtc.task.kanban',
    description: '获取项目看板任务列表',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['projectId'],
      properties: { projectId: { type: 'number' } }
    }
  },
  {
    name: 'dtc.task.updateStatus',
    description: '更新任务状态（TODO/DOING/DONE）',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['taskId', 'status'],
      properties: {
        taskId: { type: 'number' },
        status: { type: 'string', enum: ['TODO', 'DOING', 'DONE'] },
        baseUpdatedAt: { type: 'string' },
        forceDone: { type: 'boolean' }
      }
    }
  },
  {
    name: 'dtc.task.checklist.add',
    description: '新增任务验收清单项',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['taskId', 'content'],
      properties: { taskId: { type: 'number' }, content: { type: 'string' } }
    }
  },
  {
    name: 'dtc.task.deliverable.add',
    description: '新增任务交付物（LINK/DOC/PR）',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['taskId', 'type', 'title'],
      properties: {
        taskId: { type: 'number' },
        type: { type: 'string', enum: ['LINK', 'DOC', 'PR'] },
        title: { type: 'string' },
        url: { type: 'string' },
        content: { type: 'string' }
      }
    }
  },
  {
    name: 'dtc.agent.applyPlan',
    description: '将 Agent Plan 一键落地到项目（批量创建任务/清单/交付物）',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      required: ['projectId', 'plan'],
      properties: {
        projectId: { type: 'number' },
        plan: { type: 'object' }
      }
    }
  }
]

async function callTool(name, args) {
  if (name === 'dtc.task.create') {
    const data = await apiCall('POST', '/api/task/create', {
      projectId: args.projectId,
      title: args.title,
      description: args.description ?? null,
      priority: args.priority ?? null,
      assigneeId: null,
      source: 'MCP'
    })
    return { taskId: data }
  }
  if (name === 'dtc.task.get') {
    const data = await apiCall('GET', `/api/task/${encodeURIComponent(args.taskId)}`, null)
    return data
  }
  if (name === 'dtc.task.list') {
    const url = new URL(`${BASE_URL}/api/task/list`)
    url.searchParams.set('projectId', String(args.projectId))
    const data = await apiCall('GET', url.pathname + url.search, null)
    return data
  }
  if (name === 'dtc.task.kanban') {
    const url = new URL(`${BASE_URL}/api/task/kanban/list`)
    url.searchParams.set('projectId', String(args.projectId))
    const data = await apiCall('GET', url.pathname + url.search, null)
    return data
  }
  if (name === 'dtc.task.updateStatus') {
    await apiCall('PUT', `/api/task/${encodeURIComponent(args.taskId)}/status`, {
      status: args.status,
      baseUpdatedAt: args.baseUpdatedAt ?? null,
      forceDone: typeof args.forceDone === 'boolean' ? args.forceDone : null
    })
    return { ok: true }
  }
  if (name === 'dtc.task.checklist.add') {
    const data = await apiCall('POST', `/api/task/${encodeURIComponent(args.taskId)}/checklist`, {
      content: args.content
    })
    return { checklistId: data }
  }
  if (name === 'dtc.task.deliverable.add') {
    const data = await apiCall('POST', `/api/task/${encodeURIComponent(args.taskId)}/deliverable`, {
      type: args.type,
      title: args.title,
      url: args.url ?? null,
      content: args.content ?? null
    })
    return { deliverableId: data }
  }
  if (name === 'dtc.agent.applyPlan') {
    const data = await apiCall('POST', '/ai/agent/apply', {
      projectId: args.projectId,
      plan: args.plan
    })
    return data
  }
  throw new Error(`未知工具: ${name}`)
}

let buf = Buffer.alloc(0)
process.stdin.on('data', async (chunk) => {
  buf = Buffer.concat([buf, chunk])
  while (true) {
    const headerEnd = buf.indexOf('\r\n\r\n')
    if (headerEnd < 0) return
    const header = buf.slice(0, headerEnd).toString('utf8')
    const m = header.match(/Content-Length:\s*(\d+)/i)
    if (!m) {
      buf = buf.slice(headerEnd + 4)
      continue
    }
    const len = Number(m[1])
    const total = headerEnd + 4 + len
    if (buf.length < total) return
    const body = buf.slice(headerEnd + 4, total).toString('utf8')
    buf = buf.slice(total)
    let msg = null
    try {
      msg = JSON.parse(body)
    } catch {
      send(jsonRpcError(null, -32700, 'Parse error'))
      continue
    }
    handleMessage(msg).catch((e) => {
      const id = msg && typeof msg.id !== 'undefined' ? msg.id : null
      send(jsonRpcError(id, -32000, String(e?.message || '内部错误')))
    })
  }
})

async function handleMessage(msg) {
  const id = typeof msg.id === 'undefined' ? null : msg.id
  const method = String(msg.method || '')
  const params = msg.params || {}

  if (method === 'initialize') {
    send(
      jsonRpcResult(id, {
        protocolVersion: '2024-11-05',
        serverInfo: { name: 'devtoolcopilot-mcp', version: '0.1.0' },
        capabilities: { tools: {} }
      })
    )
    return
  }

  if (method === 'tools/list') {
    send(jsonRpcResult(id, { tools }))
    return
  }

  if (method === 'tools/call') {
    const name = String(params.name || '')
    const args = params.arguments || {}
    const out = await callTool(name, args)
    send(
      jsonRpcResult(id, {
        content: [{ type: 'text', text: JSON.stringify(out) }]
      })
    )
    return
  }

  if (method === 'ping') {
    send(jsonRpcResult(id, {}))
    return
  }

  send(jsonRpcError(id, -32601, `Method not found: ${method}`))
}

process.stdin.resume()
