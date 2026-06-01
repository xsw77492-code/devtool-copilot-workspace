# Debug Session: ui-slow-lag
- **Status**: [OPEN]
- **Issue**: 页面交互/切换突然变慢、不丝滑（怀疑网络/后端/前端渲染/实时连接导致）
- **Debug Server**: http://127.0.0.1:7777/event
- **Log File**: .dbg/trae-debug-log-ui-slow-lag.ndjson

## Reproduction Steps
1. （待补充）打开某页面并进行某操作后感觉明显卡顿/延迟

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | 后端接口响应变慢/偶发超时，导致页面等待（Network/DB） | High | Low | Pending |
| B | 前端出现高频请求/重试（401 刷新 token、通知流、实时 WS 重连），导致主线程忙/网络拥堵 | Med | Low | Pending |
| C | 某页面渲染量激增（列表/图表/任务详情）触发主线程长任务（Long Task） | Med | Med | Pending |
| D | 本地环境问题（CPU/内存/磁盘占用高、浏览器扩展）导致卡顿 | Low | Low | Pending |

## Log Evidence
- A: 多个核心接口耗时稳定在 250~700ms（会直接体感为页面切换 1s+）
  - `/api/task/list?projectId=3` 出现 515ms、555ms、660ms、766ms
  - `/api/dashboard/overview...` 多次 330~685ms
  - `/api/task/kanban/list` 多次 315~350ms；拖拽 `POST /api/task/kanban/move` 多次 325~357ms
- B: 偶发 401（会触发 refresh 流程/清会话，可能引发额外等待与请求重试）
  - `GET /api/user/preferences` 出现 401，且本身耗时 269~470ms

（详见 .dbg/trae-debug-log-ui-slow-lag.ndjson）

## Verification Conclusion
[Pre-fix vs post-fix comparison]
