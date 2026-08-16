# DocFlow 前端

在线协作文档系统前端，基于 Vue3 + Vite 构建。

## 技术栈

- Vue 3.4
- Vue Router 4
- Pinia（状态管理）
- Axios（HTTP 请求）
- Lucide Vue Next（图标库）
- Md-Editor-V3（Markdown 编辑器）
- Day.js（时间处理）

## 项目结构

```
docflow-web/
├── index.html
├── package.json
├── vite.config.js
├── public/
│   └── favicon.svg
├── src/
│   ├── main.js              # 入口文件
│   ├── App.vue               # 根组件
│   ├── api/
│   │   └── index.js          # API 请求封装
│   ├── assets/
│   │   └── main.css          # 全局样式
│   ├── components/
│   │   └── FolderTreeNode.vue # 文件夹树组件
│   ├── router/
│   │   └── index.js          # 路由配置
│   ├── store/
│   │   └── index.js          # 状态管理
│   ├── utils/
│   │   ├── websocket.js      # WebSocket 协作客户端
│   │   └── toast.js          # Toast 提示
│   └── views/
│       ├── Login.vue          # 登录/注册页
│       ├── Layout.vue         # 主布局（侧边栏）
│       ├── DocList.vue        # 文档列表页
│       ├── DocEdit.vue        # 文档编辑页
│       └── ShareView.vue      # 分享访问页
└── IMAGES.md                  # 图片资源文档
```

## 功能特性

- ✅ 用户注册/登录（JWT 认证）
- ✅ 文档列表、创建、编辑、删除
- ✅ 文件夹管理（树形结构）
- ✅ Markdown 编辑器（实时预览）
- ✅ WebSocket 实时协作
- ✅ Yjs CRDT 富文本协作与离线恢复
- ✅ 在线用户显示
- ✅ 版本历史管理
- ✅ 文档分享（链接 + 密码）
- ✅ 修订号冲突提示
- ✅ 自动保存
- ✅ DOCX 富文本导入（含常见内嵌图片）
- ✅ Markdown / 富文本双向转换
- ✅ 响应式设计

## Markdown 与富文本转换范围

当前转换支持 1-6 级标题、粗体、斜体、删除线、下划线 HTML、有序/无序及嵌套列表、引用、行内代码、代码块、链接、图片、表格、分割线、任务列表、换行和空段落。转换前后的 HTML 都经过 DOMPurify 净化，后端保存富文本时还会再次使用 Jsoup 净化。

已知限制：Markdown 没有标准下划线语法，因此使用安全的 `<u>` 标签保留；复杂自定义 HTML、第三方 Markdown 扩展、单元格合并和自定义 CSS 不保证无损，无法表达的结构会降级为普通文本、段落或标准表格。格式切换前仍建议保留版本记录。

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

开发服务器：`http://localhost:5173`
后端接口：`http://localhost:8080`（通过代理转发）

CRDT 协作服务默认地址：`ws://localhost:1234`。生产和自定义端口通过外部环境变量 `VITE_CRDT_URL` 配置。`LEGACY` 文档继续使用原 `/ws/doc/{id}` 通道；新建 `CRDT` 文档使用 Hocuspocus/Yjs，二者不会混用。

## 后端接口

| 模块 | 接口数 | 说明 |
|------|--------|------|
| 用户 | 2 | 注册、登录 |
| 文档 | 8 | CRUD + 搜索 + 置顶 + 移动 + 复制 |
| 文件夹 | 6 | 创建 + 列表 + 树形 + 详情 + 重命名 + 删除 |
| 权限 | 4 | 添加 + 列表 + 修改 + 删除协作者 |
| 分享 | 4 | 生成 + 列表 + 删除链接 + 访问分享 |
| 版本 | 5 | 保存 + 列表 + 详情 + 回滚 + 删除 |
| 文件 | 3 | 上传文件 + 上传图片 + 删除文件 |
| WebSocket | 1 | 实时协作连接 |

共计 33 个接口。
