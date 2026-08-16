# DocFlow - 在线协作文档系统

## 项目结构

```
docflow/
├── pom.xml                    # 父工程 pom
├── docflow-common/            # 公共模块
│   └── pom.xml
├── docflow-server/            # 后端服务
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/docflow/
│       │   ├── DocflowApplication.java    # 启动类
│       │   ├── config/                    # 配置类
│       │   ├── controller/                # 控制器
│       │   ├── service/                   # 业务层
│       │   ├── entity/                    # 实体类
│       │   ├── mapper/                    # MyBatis Mapper
│       │   ├── security/                  # 安全相关（JWT）
│       │   └── common/                    # 公共类
│       └── resources/
│           ├── application.yml            # 配置文件
│           └── db/
│               └── schema.sql             # 建表 SQL
└── README.md
```

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

## 审阅与发布升级

已有数据库升级到本版本前，需要执行一次 `docflow-server/src/main/resources/db/upgrade_20260714_review_publishing.sql`。全新安装直接执行 `schema.sql` 即可。

本次升级增加文档封面和纸张设置、批注讨论线程与站内通知、仅批注权限、命名版本、版本对比、修订标记、段落修改者、显式分页符、服务端 PDF、DOCX 本地图片嵌入以及离线 HTML 导出。

### 分页范围

CRDT 编辑器保持单个连续的 ProseMirror 文档，以保证 Yjs 位置和跨用户选区稳定。编辑界面会显示 A4/Letter 纸张宽度、页边距和分页边界，并支持显式分页符；自动物理分页由打印预览、PDF 和 DOCX 导出完成。当前开源编辑面没有把实时可编辑正文拆成独立 DOM 页面，不能视为已经实现与 Word 完全一致的所见即所得自动分页。

富文本协作编辑器还支持受控字体、字号、颜色、对齐、行距与缩进，表格单元格合并、拆分、拖宽和按列排序，标题目录折叠、格式刷、特殊字符、脚注和文献引用。公式使用 KaTeX，支持常用符号面板、自动编号、交叉引用、矩阵、多行公式、mhchem 化学式以及常见 MathML 结构的导入和公式集合 MathML 导出。MathML 导入采用可预测的常用结构转换，不承诺任意第三方 MathML 扩展无损转换。

### 导出说明

- DOCX 会嵌入本项目 `/files/` 上传目录中的图片。后端不会主动抓取外部图片地址。
- 服务端 PDF 使用 OpenHTMLtoPDF。生产主机没有合适中文字体时，请通过 `EXPORT_FONT_PATH` 指定字体文件。
- 离线 HTML 会内联可访问的本地图片，并使用 MathML 保留公式；公式外观取决于浏览器的 MathML 支持。
- 复杂自定义 HTML、浮动布局、替换选区产生的修订和第三方 Markdown 扩展可能采用可预测的文本或布局降级。

## 快速开始

### 1. 创建数据库

在 MySQL 中执行 `docflow-server/src/main/resources/db/schema.sql`

### 2. 修改配置

编辑 `docflow-server/src/main/resources/application.yml`，修改数据库和 Redis 连接信息

### 3. 启动项目

```bash
# 方式一：IDEA 中运行 DocflowApplication.java

# 方式二：Maven 命令行
cd docflow-server
mvn spring-boot:run
```

## 登录会话、全局搜索与回收站

已有数据库升级时，先执行一次以下脚本；脚本可重复执行，已存在的字段和索引会自动跳过：

```text
docflow-server/src/main/resources/db/upgrade_20260717_auth_sessions.sql
```

- Access Token 默认 30 分钟过期，前端会使用一次一换的 Refresh Token 自动续期。Refresh Token 在数据库中只保存 SHA-256 摘要。
- 未选择“保持登录”时，会话保存在浏览器会话存储中，关闭浏览器即退出；选择后默认最多保持 30 天。
- 默认连续 30 分钟无操作会退出。个人中心的“登录设备”可以退出单个设备或其他全部设备。
- 左侧“全局搜索”或 `Ctrl + K` 可搜索当前用户有权访问的文档、自己的文件夹和可协作用户。
- 回收站默认保留 30 天，每天凌晨 2 点清理；页面会显示配置的保留期限和每篇文档的大致剩余时间。

相关环境变量见 `.env.example`：`JWT_SECRET`、`JWT_ACCESS_EXPIRATION_MS`、`AUTH_IDLE_TIMEOUT_MINUTES`、`AUTH_SESSION_DAYS`、`AUTH_REMEMBER_DAYS`、`TRASH_RETENTION_DAYS` 和 `TRASH_CLEANUP_CRON`。生产环境必须替换 `JWT_SECRET`。

### 4. 测试接口

```bash
# 注册
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456","email":"test@example.com"}'

# 登录
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'
```

## 技术栈

- Spring Boot 3.2.5
- MyBatis-Plus 3.5.6
- Spring Security + JWT
- MySQL 8.0
- Redis
- WebSocket（实时协作）
- Yjs + Tiptap Collaboration + Hocuspocus（CRDT 协作）
- Apache POI（DOCX 导入）
- Spring Boot Mail（SMTP 验证码）

## DOCX 导入

`POST /api/v1/docs` 同时支持 JSON 创建和 multipart DOCX 导入，不另设重复的文档资源接口。DOCX 会转换为经过安全净化的 HTML 富文本，支持标题、段落、粗体、斜体、下划线、删除线、常见列表、链接、引用/代码样式、表格和内嵌位图。内嵌图片保存在 `file.upload-dir` 下的 `docx-images` 目录，正文只保存可访问 URL，不保存 Base64。

限制：Word 自定义样式会降级为普通段落；复杂多级编号可能被扁平化；页眉页脚、脚注、批注、文本框、SmartArt、EMF/WMF 等矢量图不导入。文件上限为 10 MB，解压后上限为 50 MB。

## SMTP 验证码

生产环境至少设置以下环境变量：

```text
MAIL_ENABLED=true
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=example-user
MAIL_PASSWORD=your-secret
MAIL_FROM=no-reply@example.com
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLE=true
MAIL_SSL_ENABLE=false
APP_VERIFICATION_DEBUG_CODE=false
APP_ENV=production
```

完整无敏感信息示例见 `.env.example` 和 `docflow-server/src/main/resources/application-example.yml`。开发环境可以保持 `MAIL_ENABLED=false` 和 `APP_VERIFICATION_DEBUG_CODE=true`；真实邮件开启后，接口不会返回验证码。验证码一次有效，同邮箱和同 IP 默认 60 秒冷却，并有每日发送上限。

已有数据库还需执行一次：

```text
docflow-server/src/main/resources/db/upgrade_20260712_mail_rate_limit.sql
```

## CRDT 协作服务

项目最终选择 Yjs CRDT，不再计划或实现 OT。现有文档执行迁移后保留为 `LEGACY`，继续使用原修订号编辑流程；新文档默认 `CRDT`，正文更新只通过 Hocuspocus/Yjs 进行。

执行一次：

```text
docflow-server/src/main/resources/db/upgrade_20260712_crdt.sql
```

然后单独启动 `D:/Javaproject/docflow-collab`：

```bash
cd D:/Javaproject/docflow-collab
npm install
npm start
```

协作服务配置、MySQL/Redis 环境变量和故障行为见 `docflow-collab/README.md`。Java 后端仍负责 JWT 身份认证和文档权限；Hocuspocus 负责标准 Yjs 二进制同步；MySQL 保存增量/checkpoint，Redis 只负责多节点广播。
