[OPEN] ai-apply-not-visible

## 症状
- AI 提示“已创建任务/可去查看”，但看板、项目任务页、任务详情页都看不到新任务
- 即使手动刷新也仍然看不到

## 预期
- AI apply 落地后：任务应真实落库，并在看板/项目页/任务详情页实时出现（至少刷新可见）

## 假设（待证伪）
1) apply 实际落地到的 projectId 与用户当前查看的 projectId 不一致（Context/路由/存储混用）
2) apply 接口未真正命中后端或失败回滚，但前端仍展示“已创建”（请求被代理/鉴权/异常被吞）
3) 任务已创建但被接口过滤掉（看板 list 参数/默认筛选/权限导致查询不到）
4) 任务创建成功但 createTask 返回的 taskId 与实际落库不一致，或后续写 checklist/deliverable 抛错导致事务回滚
5) 前端项目订阅与刷新链路未触发（WS 未连接/未 SUBSCRIBE 正确 projectId），导致“实时不更新”；但刷新仍不可见说明还有更早的链路问题

## 证据采集计划
- 后端对 apply 关键路径做最小插桩：记录 userId/projectId/plan 摘要、createTask 返回 taskIds、事务提交后按 projectId 查询的任务数量、以及 apply 响应
- 前端对 apply 调用与 board/项目页加载请求做最小插桩：记录请求 URL、projectId、响应 taskIds、以及当前页面选中的 projectId/筛选条件

## 复现步骤（待补充）
- 

