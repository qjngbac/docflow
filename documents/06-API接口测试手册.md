# DocFlow Postman 接口测试手册

## 一、测试说明

### 1. 服务地址

- Java API：`http://localhost:8080/api/v1`
- 前端：`http://127.0.0.1:5173`
- CRDT WebSocket：`ws://127.0.0.1:1234`

本文所有 HTTP URL 都写成可直接替换 ID 的完整地址，例如：

```text
http://localhost:8080/api/v1/docs/1
http://localhost:8080/api/v1/docs/1/permissions/2
```

其中 `1` 是文档 ID，`2` 是协作者用户 ID。实际测试时使用前一个请求返回的 ID 替换，不要求使用 `{{baseUrl}}`。

### 2. 测试账号

建议准备：

| 账号 | 用户名 | 用途 |
| --- | --- | --- |
| A | `docflow_a` | 文档所有者 |
| B | `docflow_b` | 协作者 |
| C | `docflow_c` | 无权限用户 |
| Admin | `docflow_admin` | 系统管理员 |

管理员先正常注册，再执行：

```sql
UPDATE `user` SET `system_role`='ADMIN' WHERE `username`='docflow_admin';
```

### 3. Postman 环境变量

虽然 URL 使用完整地址，Token 和动态 ID 建议保存为环境变量：

```text
tokenA
refreshA
tokenB
tokenC
tokenAdmin
userIdA
userIdB
docId
folderId
versionNum
commentId
feedbackId
attachmentId
```

Bearer Token 输入框填 `{{tokenA}}`，不需要手工在每个请求复制长 Token。

### 4. 通用响应断言

Postman Tests：

```javascript
pm.test('HTTP 状态正常', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));
const body = pm.response.json();
pm.test('业务响应成功', () => pm.expect(body.code).to.eql(200));
```

错误请求按预期断言：

```javascript
const body = pm.response.json();
pm.test('请求被拒绝', () => pm.expect([400, 401, 403, 404, 409]).to.include(body.code));
pm.test('错误信息不为空', () => pm.expect(body.message).to.be.a('string').and.not.empty);
```

## 二、认证与会话

### 2.1 注册用户 A

```http
POST http://localhost:8080/api/v1/auth/register
Content-Type: application/json

{
  "username": "docflow_a",
  "password": "Test123456!",
  "email": "docflow_a@example.com"
}
```

用户 B、C 和 Admin 使用相同接口修改用户名和邮箱。重复注册应返回明确业务错误。

### 2.2 登录用户 A

```http
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "username": "docflow_a",
  "password": "Test123456!",
  "rememberMe": false
}
```

Tests：

```javascript
const body = pm.response.json();
pm.test('登录成功', () => pm.expect(body.code).to.eql(200));
pm.expect(body.data.accessToken).to.be.a('string');
pm.expect(body.data.refreshToken).to.be.a('string');
pm.environment.set('tokenA', body.data.accessToken);
pm.environment.set('refreshA', body.data.refreshToken);
if (body.data.user?.id) pm.environment.set('userIdA', body.data.user.id);
```

分别登录 B、C、Admin，保存 `tokenB/tokenC/tokenAdmin` 和用户 ID。

### 2.3 获取当前用户

```http
GET http://localhost:8080/api/v1/user/me
Authorization: Bearer {{tokenA}}
```

### 2.4 刷新 Token

```http
POST http://localhost:8080/api/v1/auth/refresh
Content-Type: application/json

{"refreshToken":"{{refreshA}}"}
```

保存新的 Access/Refresh Token，再使用旧 `refreshA` 重复请求，旧值必须失败，验证一次轮换。

### 2.5 设备管理

```text
GET    http://localhost:8080/api/v1/user/me/sessions
DELETE http://localhost:8080/api/v1/user/me/sessions/1
DELETE http://localhost:8080/api/v1/user/me/sessions/others
POST   http://localhost:8080/api/v1/auth/heartbeat
```

全部携带 `Bearer {{tokenA}}`。删除当前设备后，当前 Token 的下一次受保护请求应失败。

### 2.6 资料、头像和密码

```text
PUT    http://localhost:8080/api/v1/user/me
POST   http://localhost:8080/api/v1/user/me/avatar        multipart file
DELETE http://localhost:8080/api/v1/user/me/avatar
PUT    http://localhost:8080/api/v1/user/me/password
```

修改资料示例：

```json
{"nickname":"用户A"}
```

修改密码后验证已有其他设备会话失效。

## 三、文件夹和文档

### 3.1 创建文件夹

```http
POST http://localhost:8080/api/v1/folders
Authorization: Bearer {{tokenA}}
Content-Type: application/json

{"name":"接口测试","parentId":0}
```

Tests 保存：

```javascript
const body = pm.response.json();
pm.environment.set('folderId', body.data.id);
```

### 3.2 文件夹列表、修改和删除

```text
GET    http://localhost:8080/api/v1/folders
PUT    http://localhost:8080/api/v1/folders/1
DELETE http://localhost:8080/api/v1/folders/1
```

PUT Body：

```json
{"name":"接口测试-已改名","parentId":0}
```

### 3.3 创建 CRDT 文档

```http
POST http://localhost:8080/api/v1/docs
Authorization: Bearer {{tokenA}}
Content-Type: application/json

{
  "title":"Postman CRDT 测试文档",
  "content":"<h1>初始内容</h1><p>由接口测试创建</p>",
  "contentFormat":"HTML",
  "folderId":0,
  "category":"测试"
}
```

Tests：

```javascript
const body = pm.response.json();
pm.test('新文档为 CRDT', () => pm.expect(body.data.collabMode).to.eql('CRDT'));
pm.environment.set('docId', body.data.id);
```

### 3.4 列表和详情

假设返回 docId 为 1：

```text
GET http://localhost:8080/api/v1/docs
GET http://localhost:8080/api/v1/docs/1
GET http://localhost:8080/api/v1/docs?scope=recent
GET http://localhost:8080/api/v1/docs?scope=favorites
GET http://localhost:8080/api/v1/docs?trash=true
```

将 URL 中的 `1` 替换为实际 `docId`。

### 3.5 更新元数据

```http
PUT http://localhost:8080/api/v1/docs/1
Authorization: Bearer {{tokenA}}
Content-Type: application/json

{
  "title":"Postman CRDT 测试文档-已更新",
  "category":"接口回归",
  "pinned":true,
  "favorite":true
}
```

CRDT 文档不要用此接口覆盖正文，正文通过 Yjs 协作服务更新。

### 3.6 搜索、复制和批量操作

```text
GET  http://localhost:8080/api/v1/docs/search?q=Postman
GET  http://localhost:8080/api/v1/search?q=Postman
POST http://localhost:8080/api/v1/docs/1/copy
POST http://localhost:8080/api/v1/docs/batch/move
POST http://localhost:8080/api/v1/docs/batch/trash
POST http://localhost:8080/api/v1/docs/batch/restore
POST http://localhost:8080/api/v1/docs/batch/permanent
```

批量 Body：

```json
{"ids":[1,2],"folderId":0}
```

### 3.7 回收站

```text
DELETE http://localhost:8080/api/v1/docs/1
POST   http://localhost:8080/api/v1/docs/1/restore
DELETE http://localhost:8080/api/v1/docs/1/permanent
GET    http://localhost:8080/api/v1/docs/trash/settings
```

彻底删除不可恢复，建议对复制出来的临时文档测试。

## 四、导入文档

Markdown/TXT 在当前前端中由浏览器读取、转换成 HTML 后调用 JSON 创建接口；下面的 multipart 接口只直接接收 DOC/DOCX。

```http
POST http://localhost:8080/api/v1/docs
Authorization: Bearer {{tokenA}}
Content-Type: multipart/form-data
```

Body form-data：

| Key | 类型 | 值 |
| --- | --- | --- |
| file | File | `.doc/.docx` 文件 |
| folderId | Text | `0` |
| category | Text | `导入测试` |

依次测试：正常 DOCX、空文档、损坏文件、超过限制的文件、伪装扩展名和含危险链接/HTML 的文档。错误响应不能包含 Java 堆栈。

## 五、协作者权限

### 5.1 添加用户 B

```http
POST http://localhost:8080/api/v1/docs/1/permissions
Authorization: Bearer {{tokenA}}
Content-Type: application/json

{"username":"docflow_b","permission":"WRITE"}
```

### 5.2 查看、修改和删除

假设用户 B 的 ID 为 2：

```text
GET    http://localhost:8080/api/v1/docs/1/permissions
PUT    http://localhost:8080/api/v1/docs/1/permissions/2
DELETE http://localhost:8080/api/v1/docs/1/permissions/2
```

PUT Body：

```json
{"permission":"COMMENT"}
```

### 5.3 权限矩阵

分别设置并使用 `tokenB` 验证：

| 权限 | 详情 | 创建批注 | 更新正文 | 管理权限 |
| --- | --- | --- | --- | --- |
| READ | 成功 | 403 | 403 | 403 |
| COMMENT | 成功 | 成功 | 403 | 403 |
| WRITE | 成功 | 成功 | 成功 | 403 |
| ADMIN | 成功 | 成功 | 成功 | 成功 |

使用 `tokenC` 访问详情和 CRDT access，必须返回 403。

## 六、CRDT 权限与恢复点

```text
GET  http://localhost:8080/api/v1/docs/1/crdt/access
GET  http://localhost:8080/api/v1/docs/1/crdt/checkpoint
POST http://localhost:8080/api/v1/docs/1/crdt/checkpoint
GET  http://localhost:8080/api/v1/docs/1/crdt/checkpoint/history
POST http://localhost:8080/api/v1/docs/1/crdt/checkpoint/history/1/restore
```

所有者和文档 ADMIN 可管理 checkpoint，WRITE 用户不可恢复。

Postman 不能完整验证 Hocuspocus/Yjs 二进制同步。真实协作必须使用两个浏览器或 `docflow-collab` 自动化测试，覆盖并发输入、重复/乱序、断线、重启、Redis 和双节点。

## 七、分享

```http
POST http://localhost:8080/api/v1/docs/1/share
Authorization: Bearer {{tokenA}}
Content-Type: application/json

{
  "permission":"READ",
  "password":"share123",
  "expiresAt":"2026-12-31T23:59:59"
}
```

返回 token 后测试：

```text
GET http://localhost:8080/api/v1/share/实际token?password=wrong
GET http://localhost:8080/api/v1/share/实际token?password=share123
```

## 八、批注、通知和文献

### 8.1 创建批注

```http
POST http://localhost:8080/api/v1/docs/1/comments
Authorization: Bearer {{tokenA}}
Content-Type: application/json

{
  "content":"请确认这一段 @docflow_b",
  "quotedText":"初始内容"
}
```

保存 `commentId`，测试：

```text
GET    http://localhost:8080/api/v1/docs/1/comments
PUT    http://localhost:8080/api/v1/docs/1/comments/1/status
PUT    http://localhost:8080/api/v1/docs/1/comments/batch/status
DELETE http://localhost:8080/api/v1/docs/1/comments/1
```

状态 Body：`{"resolved":true}`；批量 Body：`{"ids":[1,2],"resolved":true}`。

### 8.2 通知

```text
GET http://localhost:8080/api/v1/notifications
GET http://localhost:8080/api/v1/notifications?unreadOnly=true
GET http://localhost:8080/api/v1/notifications?unreadOnly=true&type=ADMIN_MESSAGE
GET http://localhost:8080/api/v1/notifications/unread-count
PUT http://localhost:8080/api/v1/notifications/1/read
PUT http://localhost:8080/api/v1/notifications/read-all
```

### 8.3 参考文献

```text
GET    http://localhost:8080/api/v1/docs/1/references
POST   http://localhost:8080/api/v1/docs/1/references
POST   http://localhost:8080/api/v1/docs/1/references/doi
DELETE http://localhost:8080/api/v1/docs/1/references/1
```

手工创建 Body 以 DTO 当前字段为准，至少包含 cite key 和 CSL JSON；DOI 导入测试网络失败时应返回可理解错误。

## 九、版本

### 9.1 保存版本

```http
POST http://localhost:8080/api/v1/docs/1/versions
Authorization: Bearer {{tokenA}}
Content-Type: application/json

{
  "name":"接口测试基线",
  "description":"Postman 保存",
  "content":"<h1>版本内容</h1>"
}
```

### 9.2 列表、详情、回滚和删除

```text
GET    http://localhost:8080/api/v1/docs/1/versions
GET    http://localhost:8080/api/v1/docs/1/versions/1
POST   http://localhost:8080/api/v1/docs/1/versions/1/rollback
DELETE http://localhost:8080/api/v1/docs/1/versions
```

## 十、文件、附件和导出

### 10.1 通用文件上传

```http
POST http://localhost:8080/api/v1/files/upload
Authorization: Bearer {{tokenA}}
Content-Type: multipart/form-data
```

form-data：`file=<选择文件>`。

### 10.2 文档附件

```text
GET    http://localhost:8080/api/v1/docs/1/attachments
POST   http://localhost:8080/api/v1/docs/1/attachments     multipart file
DELETE http://localhost:8080/api/v1/docs/1/attachments/1
```

### 10.3 分片上传

初始化：

```http
POST http://localhost:8080/api/v1/docs/1/attachments/uploads
Authorization: Bearer {{tokenA}}
Content-Type: application/json

{
  "fileName":"large.zip",
  "mimeType":"application/zip",
  "size":10485760,
  "totalChunks":2,
  "sha256":"完整文件SHA-256十六进制"
}
```

上传第 0/1 片：

```text
PUT http://localhost:8080/api/v1/docs/1/attachments/uploads/实际uploadId/chunks/0
PUT http://localhost:8080/api/v1/docs/1/attachments/uploads/实际uploadId/chunks/1
```

Body 为 form-data `file`，最后：

```text
POST http://localhost:8080/api/v1/docs/1/attachments/uploads/实际uploadId/complete
```

### 10.4 导出

```text
POST http://localhost:8080/api/v1/docs/1/export/docx
POST http://localhost:8080/api/v1/docs/1/export/pdf
```

Body：`{"content":"<h1>导出测试</h1>"}`。Postman 使用“Save Response to file”保存并打开核对。

## 十一、反馈

### 11.1 用户提交

```http
POST http://localhost:8080/api/v1/feedback
Authorization: Bearer {{tokenA}}
Content-Type: multipart/form-data
```

form-data：

| Key | 类型 | 值 |
| --- | --- | --- |
| type | Text | BUG |
| description | Text | 保存后更新时间没有变化 |
| docId | Text | 实际文档 ID |
| images | File | 截图，可重复 key，最多 6 张 |

### 11.2 查看自己的反馈

```text
GET http://localhost:8080/api/v1/feedback/mine
```

使用 `tokenB` 不能看到 A 的反馈。用无权 docId 提交应返回 403，第 7 张图片应失败。

## 十二、管理后台接口

全部使用 `Bearer {{tokenAdmin}}`。使用 `tokenA` 请求任一 `/admin` 接口必须 403。

```text
GET  http://localhost:8080/api/v1/admin/overview
GET  http://localhost:8080/api/v1/admin/users?q=docflow&status=1&role=USER
PUT  http://localhost:8080/api/v1/admin/users/2/status
PUT  http://localhost:8080/api/v1/admin/users/2/role
POST http://localhost:8080/api/v1/admin/users/2/revoke-sessions
POST http://localhost:8080/api/v1/admin/notifications
GET  http://localhost:8080/api/v1/admin/feedback?status=OPEN&type=BUG
PUT  http://localhost:8080/api/v1/admin/feedback/1
GET  http://localhost:8080/api/v1/admin/logs?action=ADMIN_
GET  http://localhost:8080/api/v1/admin/templates
POST http://localhost:8080/api/v1/admin/templates
PUT  http://localhost:8080/api/v1/admin/templates/1
DELETE http://localhost:8080/api/v1/admin/templates/1
GET  http://localhost:8080/api/v1/admin/system-status
```

封禁 Body：

```json
{
  "enabled": false,
  "reason": "接口测试临时封禁",
  "expiresAt": "2026-12-31T10:00:00"
}
```

角色 Body：`{"role":"ADMIN"}`。

处理反馈 Body：

```json
{"status":"RESOLVED","adminReply":"问题已处理，请刷新后重试。"}
```

系统提示分别测试以下 Body：

```json
{"targetType":"ALL","content":"系统将在今晚 23:00 维护，请提前保存工作。"}
```

```json
{"targetType":"USER","username":"docflow_b","content":"请检查您的账号资料。"}
```

```json
{
  "targetType":"REGISTERED_AT",
  "registeredFrom":"2026-08-01T00:00:00",
  "registeredTo":"2026-08-09T23:59:59",
  "content":"欢迎新用户使用云笺。"
}
```

断言 `data.sentCount` 与接收人数一致。使用接收账号查询 `type=ADMIN_MESSAGE` 应看到提示；调用单条已读后再次查询不应返回该条。普通用户发送预期 403，反向时间、空内容、超过 500 字和不存在用户名应返回明确错误。

验证反馈用户收到通知。不要尝试封禁当前管理员或取消自己的 ADMIN，预期应被拒绝。

## 十三、异常与安全测试

1. 不带 Token 访问 `/docs`：401。
2. 用户 C 获取 A 文档：403。
3. READ 用户更新正文/元数据：403。
4. COMMENT 用户改正文：403；创建批注成功。
5. 普通用户访问 `/admin/overview`：403。
6. 已撤销 Access Token：401。
7. 旧 Refresh Token 重放：401。
8. 非法权限 `OWNER`：400。
9. 超大附件、伪 MIME、路径型文件名：拒绝或安全改名。
10. 正文包含 `<script>alert(1)</script>`：保存/展示后脚本被净化。
11. 损坏 DOCX 和 ZIP 炸弹：明确业务错误，无堆栈。
12. 已删除文档继续连接 CRDT：拒绝。

## 十四、推荐执行顺序

1. 注册并登录 A、B、C、Admin。
2. 验证 Refresh Token 和设备。
3. A 创建文件夹与 CRDT 文档。
4. 验证列表、搜索、元数据、复制和回收站。
5. A 给 B 依次设置四级权限并验证边界。
6. 两浏览器执行 CRDT 实时、离线和重连测试。
7. 测试分享、批注、通知、版本和 checkpoint。
8. 测试导入、附件、分片和导出。
9. A 提交反馈，Admin 处理并回复。
10. Admin 分别按全部、用户名和注册时间发送系统提示，接收者验证顶部显示与关闭。
11. Admin 封禁测试用户，验证设备失效后再解封。
11. 执行异常、安全和数据一致性检查。

## 十五、测试结束清理

- 删除临时分享、附件、反馈图片和测试文档。
- 解封测试用户，撤销多余管理员角色。
- 清理测试数据库前先确认不是开发重要数据。
- 保存 Postman 测试结果，但不要导出真实 Token、密码和生产地址。
