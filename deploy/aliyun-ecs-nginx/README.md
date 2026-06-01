# 阿里云 ECS + Nginx 部署（RDS）

## 目标形态

- 前端：Nginx 静态资源 `/var/www/devtool-copilot`
- 后端：Spring Boot jar（本机 `127.0.0.1:8085`）
- 反代：Nginx
  - `/` → 前端 SPA
  - `/api/**` → 后端
  - `/api/notification/stream` → SSE（关闭缓冲）
  - `/ws/**` → WebSocket
- 数据库：阿里云 RDS MySQL
- 域名：已有域名，无证书（本方案包含证书落位与 Nginx HTTPS 模板）

## 1. 服务器目录约定

- `/opt/devtool-copilot/backend`：后端
  - `app.jar`
  - `devtool-copilot-backend.env`
- `/var/www/devtool-copilot`：前端 `dist/`
- `/etc/nginx/certs`：证书
  - `fullchain.pem`
  - `privkey.pem`

## 2. 前端发布

在本机仓库目录执行：

- `devtool-copilot-web` 目录构建：`npm run build`
- 将 `devtool-copilot-web/dist` 上传到服务器 `/var/www/devtool-copilot`

## 3. 后端发布

在本机仓库目录执行：

- 后端打包：`mvn -DskipTests package`
- 将 `target/*.jar` 上传到服务器 `/opt/devtool-copilot/backend/app.jar`

复制环境变量模板并按你的 RDS/SMTP 填好：

- `deploy/aliyun-ecs-nginx/env/devtool-copilot-backend.env.example`
- 服务器落位：`/opt/devtool-copilot/backend/devtool-copilot-backend.env`

## 4. systemd 守护进程

把服务文件上传到服务器：

- `deploy/aliyun-ecs-nginx/systemd/devtool-copilot-backend.service`
- 服务器落位：`/etc/systemd/system/devtool-copilot-backend.service`

启用并启动：

- `systemctl daemon-reload`
- `systemctl enable devtool-copilot-backend`
- `systemctl restart devtool-copilot-backend`
- `journalctl -u devtool-copilot-backend -f`

## 5. Nginx 配置（含 HTTPS / WS / SSE）

把 Nginx 配置上传到服务器：

- `deploy/aliyun-ecs-nginx/nginx/devtool-copilot.conf`
- 服务器落位：`/etc/nginx/conf.d/devtool-copilot.conf`

证书先放到：

- `/etc/nginx/certs/fullchain.pem`
- `/etc/nginx/certs/privkey.pem`

然后：

- `nginx -t`
- `systemctl reload nginx`

## 6. 证书获取（已有域名无证书）

两种常见方式任选其一：

- 阿里云证书服务签发后下载，把 `fullchain.pem/privkey.pem` 放到 `/etc/nginx/certs/`
- 使用 ACME/Certbot 签发，签发完成后把证书路径软链到 `/etc/nginx/certs/`

## 7. 健康检查

- 前端：打开域名首页能加载
- 后端：`/actuator/health` 返回 UP（如果你开启了 actuator）
- WebSocket：登录后实时协作能收到刷新
- SSE：通知中心能实时弹窗

## 8. 回滚策略（推荐）

- 前端：保留 `/var/www/devtool-copilot_prev`，一键切换目录并 reload nginx
- 后端：保留 `/opt/devtool-copilot/backend/app_prev.jar`，替换 app.jar 并重启 systemd

