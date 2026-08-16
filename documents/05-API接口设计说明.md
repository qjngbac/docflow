# 云笺 DocFlow API 接口设计说明

## 1. 通用规范

- 开发地址：`http://localhost:8080/api/v1`。
- 认证头：`Authorization: Bearer <accessToken>`。
- JSON：`Content-Type: application/json`。
- 上传：`multipart/form-data`，不要手工固定 boundary。
- 时间：ISO-8601 字符串，服务端时区为 `Asia/Shanghai`。
- ID：后端为 `Long`，前端按普通数值/字符串路径参数使用。

统一响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

文件导出、下载和图片访问直接返回二进制内容。401 表示未认证或会话失效，403 表示已认证但无资源权限。

## 2. 认证与用户

| 方法 | 路径 | 说明 | 认证 |
| --- | --- | --- | --- |
| POST | `/auth/register` | 注册 | 否 |
| POST | `/auth/login` | 登录并创建设备会话 | 否 |
| POST | `/auth/refresh` | 轮换 Refresh Token | 否 |
| POST | `/auth/logout` | 注销会话 | 可选 |
| POST | `/auth/heartbeat` | 更新活跃状态 | 是 |
| POST | `/auth/password-reset/request` | 请求找回密码验证码 | 否 |
| POST | `/auth/password-reset/confirm` | 验证并重置密码 | 否 |
| GET | `/user/me` | 当前用户 | 是 |
| PUT | `/user/me` | 修改昵称等资料 | 是 |
| POST/DELETE | `/user/me/avatar` | 上传/删除头像 | 是 |
| PUT | `/user/me/welcome-dismissed` | 关闭欢迎文档 | 是 |
| POST/PUT | `/user/me/email/request`、`/user/me/email` | 请求并确认邮箱修改 | 是 |
| GET | `/user/me/sessions` | 查看登录设备 | 是 |
| DELETE | `/user/me/sessions/{sessionId}` | 退出指定设备 | 是 |
| DELETE | `/user/me/sessions/others` | 退出其他设备 | 是 |
| PUT | `/user/me/password` | 修改密码并撤销会话 | 是 |

登录示例：

```http
POST /api/v1/auth/login
Content-Type: application/json

{"username":"aprot","password":"your-password","rememberMe":false}
```

## 3. 文档与搜索

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/docs` | 文档列表，支持文件夹、回收站、最近和收藏条件 |
| GET | `/docs/search?q=` | 文档搜索 |
| GET | `/docs/trash/settings` | 回收站保留天数 |
| POST | `/docs` JSON | 创建空白/模板文档 |
| POST | `/docs` multipart | 后端解析 DOC/DOCX 创建文档；Markdown/TXT 由前端读取后走 JSON 创建 |
| GET | `/docs/{id}` | 文档详情和权限信息 |
| PUT | `/docs/{id}` | 更新元数据或 LEGACY 正文 |
| DELETE | `/docs/{id}` | 移入回收站 |
| POST | `/docs/{id}/restore` | 恢复 |
| DELETE | `/docs/{id}/permanent` | 彻底删除 |
| POST | `/docs/{id}/copy` | 复制 |
| POST | `/docs/batch/move` | 批量移动 |
| POST | `/docs/batch/trash` | 批量移入回收站 |
| POST | `/docs/batch/restore` | 批量恢复 |
| POST | `/docs/batch/permanent` | 批量彻底删除 |
| GET | `/search?q=` | 权限内搜索文档、文件夹和用户 |

创建示例：

```json
{
  "title": "项目计划",
  "folderId": 0,
  "category": "工作",
  "contentFormat": "HTML",
  "content": "<h1>项目计划</h1>",
  "coverImage": null
}
```

CRDT 文档普通更新接口不得用于覆盖正文，只修改标题、分类、文件夹、封面、页面设置、收藏和置顶等元数据。

## 4. 文件夹、标签和模板

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST | `/folders` | 文件夹树/创建文件夹 |
| PUT/DELETE | `/folders/{id}` | 重命名、移动或删除 |
| GET | `/tags` | 当前用户标签 |
| GET/PUT | `/docs/{docId}/tags` | 获取/设置文档标签 |
| GET/POST | `/templates` | 模板列表/创建个人模板 |
| DELETE | `/templates/{id}` | 删除自己的模板 |

## 5. 权限与分享

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/docs/{docId}/permissions` | 协作者列表 |
| POST | `/docs/{docId}/permissions` | 按用户名添加协作者 |
| PUT | `/docs/{docId}/permissions/{userId}` | 修改 READ/COMMENT/WRITE/ADMIN |
| DELETE | `/docs/{docId}/permissions/{userId}` | 移除协作者 |
| POST | `/docs/{docId}/share` | 创建分享链接 |
| GET | `/share/{token}?password=` | 访问分享文档 |

添加协作者：

```json
{"username":"collaborator","permission":"WRITE"}
```

## 6. CRDT 与 checkpoint

| 方法 | 路径 | 调用方/说明 |
| --- | --- | --- |
| GET | `/docs/{docId}/crdt/access` | Hocuspocus/前端获取读写管理范围 |
| PUT | `/docs/{docId}/crdt/snapshot` | 协作服务回写净化 HTML 快照 |
| GET | `/docs/{docId}/crdt/checkpoint` | 文档管理员查看状态 |
| POST | `/docs/{docId}/crdt/checkpoint` | 强制 checkpoint |
| GET | `/docs/{docId}/crdt/checkpoint/history` | 历史恢复点 |
| POST | `/docs/{docId}/crdt/checkpoint/history/{id}/restore` | 恢复指定 checkpoint |

浏览器正文同步使用 `VITE_CRDT_URL` 指向 Hocuspocus WebSocket，不通过普通 JSON 接口发送 Yjs payload。Java 快照接口和 CRDT 内部管理接口使用共享管理密钥，仅限服务间调用。

## 7. 版本、批注、通知和文献

| 模块 | 方法与路径 |
| --- | --- |
| 版本 | `POST/GET/DELETE /docs/{docId}/versions` |
| 版本详情 | `GET /docs/{docId}/versions/{versionNum}` |
| 版本回滚 | `POST /docs/{docId}/versions/{versionNum}/rollback` |
| 批注 | `GET/POST /docs/{docId}/comments` |
| 批注状态 | `PUT /docs/{docId}/comments/{commentId}/status` |
| 批量状态 | `PUT /docs/{docId}/comments/batch/status` |
| 删除批注 | `DELETE /docs/{docId}/comments/{commentId}` |
| 通知 | `GET /notifications`、`GET /notifications/unread-count`；列表可传 `unreadOnly` 和可选 `type` |
| 已读 | `PUT /notifications/{id}/read`、`PUT /notifications/read-all` |
| 文献 | `GET/POST /docs/{docId}/references` |
| DOI 导入 | `POST /docs/{docId}/references/doi` |
| 删除文献 | `DELETE /docs/{docId}/references/{referenceId}` |

## 8. 文件、附件和导出

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/files/upload` | 通用图片/文件上传 |
| GET/POST | `/docs/{docId}/attachments` | 附件列表/小附件上传 |
| DELETE | `/docs/{docId}/attachments/{attachmentId}` | 删除附件 |
| POST | `/docs/{docId}/attachments/uploads` | 初始化分片上传 |
| PUT | `/docs/{docId}/attachments/uploads/{uploadId}/chunks/{index}` | 上传分片 |
| POST | `/docs/{docId}/attachments/uploads/{uploadId}/complete` | 合并校验 |
| POST | `/docs/{docId}/export/docx` | DOCX 导出 |
| POST | `/docs/{docId}/export/pdf` | PDF 导出 |

前端默认按 5 MB 分片、最多 3 个并发；服务端最终校验文件总大小和 SHA-256。

## 9. 反馈

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/feedback` | multipart 提交类型、描述、可选 docId 和最多 6 张图片 |
| GET | `/feedback/mine` | 当前用户反馈和管理员回复 |

## 10. 系统管理

以下接口要求 `user.system_role=ADMIN`，并由后端拦截器再次校验：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/overview` | 用户、文档、反馈、设备和附件概览 |
| GET | `/admin/users` | 按关键词、状态、角色筛选用户 |
| PUT | `/admin/users/{id}/status` | 封禁/解封，可传原因和到期时间 |
| PUT | `/admin/users/{id}/role` | USER/ADMIN 角色调整 |
| POST | `/admin/users/{id}/revoke-sessions` | 强制退出全部设备 |
| POST | `/admin/notifications` | 按全部、用户名或注册时间范围发送系统提示 |
| GET | `/admin/feedback` | 按状态和类型筛选反馈 |
| PUT | `/admin/feedback/{id}` | 更新反馈状态和回复 |
| GET | `/admin/logs` | 查看审计日志，可按 action 筛选 |
| GET/POST | `/admin/templates` | 系统模板列表/创建 |
| PUT/DELETE | `/admin/templates/{id}` | 修改/删除系统模板 |
| GET | `/admin/system-status` | 回收站、附件、上传和 CRDT 状态 |

发送提示请求示例：

```json
{
  "targetType": "REGISTERED_AT",
  "registeredFrom": "2026-08-01T00:00:00",
  "registeredTo": "2026-08-09T23:59:59",
  "content": "系统将在今晚 23:00 进行维护，请提前保存工作。"
}
```

`targetType=USER` 时填写 `username`；`ALL` 不需要目标字段。成功数据为 `{"sentCount":2}`。

## 11. 错误与重试

- 401：前端仅对非登录/刷新请求尝试一次 Token 刷新。
- 403：不自动重试，提示无权限。
- 409：提示内容已变化或恢复冲突，重新加载后处理。
- 413/文件业务错误：提示文件过大或不支持，不重试同一文件。
- 5xx/网络错误：可提示稍后重试；写操作不得盲目重复，CRDT 更新依靠幂等机制。

## 12. 维护说明

接口的参数约束最终以 Controller/DTO 校验为准。新增接口时必须同步更新本文件和 `DocFlow接口测试手册.md`；生产项目建议接入 springdoc-openapi，由代码生成 OpenAPI 规范并纳入 CI 差异检查。
