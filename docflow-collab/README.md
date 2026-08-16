# DocFlow CRDT 协作服务

该服务通过开源的 Hocuspocus 服务器运行成熟的 Yjs CRDT，不会自行实现一套自定义 CRDT 算法。

## 主要职责

- 调用 Java DocFlow API，对每个连接进行身份认证。
- 将 Java 后端中的文档读写权限应用到 Hocuspocus 连接。
- 强制限制可配置的二进制更新消息大小。
- 将去重后的 Yjs 增量更新持久化到 MySQL。
- 服务重启后，通过 checkpoint 和剩余增量更新恢复文档。
- 以事务方式创建带防抖机制的 checkpoint。
- 通过 Hocuspocus 官方 Redis 扩展，在多个节点之间广播文档更新和用户在线状态信息。
- checkpoint 成功后，将经过安全净化的 HTML 内容同步回 Java 后端。
- 在仅监听本机的管理端口上提供 checkpoint 状态、恢复验证和主动压缩；浏览器不能直接访问该端口。

二进制协议的映射关系记录在 `PROTOCOL.md` 中。

## 运行方式

1. 执行一次：

```text
docflow-server/src/main/resources/db/upgrade_20260712_crdt.sql
```

1. 本地开发时将 `.env.example` 复制为 `.env`，生产环境则配置同名外部环境变量。服务会自动读取 `.env`，外部环境变量优先；不要提交真实密码。
2. 启动 MySQL、Redis 和 Java 后端。
3. 安装并启动该服务：

```bash
npm install
npm start
```

服务会先验证 `crdt_checkpoint` 和 `crdt_update` 两张表可访问，再开放 WebSocket 端口。数据库密码错误或迁移未执行时会直接启动失败，不再伪装成客户端“无协作权限”。

1. 设置前端环境变量 `VITE_CRDT_URL`，例如：

```text
ws://127.0.0.1:1234
```

然后重新启动 Vite 前端开发服务器。

本地单节点开发时，可以通过以下配置关闭 Redis：

```text
CRDT_REDIS_ENABLED=false
```

生产环境的多节点部署必须启用 Redis，并为每个节点设置唯一的：

```text
CRDT_SERVER_NAME
```

同时需要监控 Redis 重连失败情况。

MySQL 仍然是主要的持久化存储，Redis 只负责多个节点之间的消息广播。

Java 和 CRDT 服务必须配置相同的 `CRDT_ADMIN_SECRET`。管理端口默认是 `127.0.0.1:1235`，不应通过公网或反向代理暴露。文档所有者或拥有 `ADMIN` 权限的协作者可以在编辑器中查看 checkpoint 状态并触发压缩。

## 故障处理行为

- 重复更新通过 `INSERT IGNORE` 处理，并使用唯一键 `(doc_id, update_id)` 防止重复写入。
- 由于 Yjs 更新具有交换性和幂等性，因此乱序更新和重复更新仍然是安全的。
- checkpoint 事务会先写入新的 checkpoint，再删除已被 checkpoint 覆盖的增量更新；如果事务回滚，原有增量更新会继续保留。
- 当文档仍处于加载状态时，Hocuspocus 会重试失败的防抖持久化操作；服务关闭时，也会刷新尚未保存的更新。
- 浏览器端还会通过 `y-indexeddb` 持久化状态，因此断线重连后只需要交换缺失的更新。

自动化测试使用相互独立的 `Y.Doc` 实例，覆盖并发和恢复场景。

不过，在正式部署到生产环境之前，仍然需要进行一次真实部署冒烟测试，测试环境应包含：

- 真实 MySQL
- 真实 Redis
- 两个协作服务节点
- 负载均衡器
