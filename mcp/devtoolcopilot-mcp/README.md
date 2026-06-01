# DevTool Copilot MCP（简易版）

这是一个最小可用的 MCP stdio server，用来让 AI 通过“工具调用”去操作 DevTool Copilot（创建任务/更新状态/写清单与交付物/一键 apply plan）。

## 设计目标

- 只提供“新增/推进”类能力，避免破坏性操作
- 不做复杂权限体系：复用现有后端 JWT
- 与站内 AI Chat 的“自动化落地”方向一致（工具定义可复用）

## 环境变量

- `DTC_BASE_URL`：后端地址，默认 `http://127.0.0.1:8080`
- `DTC_TOKEN`：登录后的 JWT（必须）

## 启动方式

使用 Node.js 直接运行：

```bash
node mcp/devtoolcopilot-mcp/server.mjs
```

## Tools（摘要）

- `dtc.task.create`
- `dtc.task.get`
- `dtc.task.list`
- `dtc.task.kanban`
- `dtc.task.updateStatus`
- `dtc.task.checklist.add`
- `dtc.task.deliverable.add`
- `dtc.agent.applyPlan`

## 安全边界（简化但够用）

- 鉴权完全依赖 `DTC_TOKEN` 对应的用户权限
- MCP 层不额外引入“管理员/超级权限”

