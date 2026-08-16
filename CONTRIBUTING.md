# 参与贡献

感谢关注云笺 DocFlow。提交代码前，请先阅读本说明和 [`documents/13-开发规范与版本发布流程.md`](documents/13-开发规范与版本发布流程.md)。

## 开发原则

- 保持 Java、CRDT 服务和前端之间的接口与权限语义一致。
- 新增数据库结构必须使用 Flyway 迁移，不修改已经发布的迁移文件。
- 不提交 `.env`、真实密码、密钥、邮箱授权码、证书、本地上传文件、日志或构建目录。
- 不用关闭鉴权、放宽所有用户权限或绕过 HTML 净化来解决功能问题。
- 影响 CRDT 的改动必须考虑重复、乱序、重连、服务重启和 checkpoint 恢复。

## 提交前检查

```powershell
cd docflow
mvn test

cd ..\docflow-collab
npm ci
npm test

cd ..\docflow-web
npm ci
npm test
npm run build
```

同时检查：

1. 新增或变更的接口已更新 API 文档和测试手册。
2. 数据库字段或索引变更已更新数据字典并提供迁移脚本。
3. 用户可见提示使用清楚、自然的中文。
4. 新配置已加入无敏感信息的示例文件和部署文档。
5. 没有把 `node_modules`、`target`、`dist`、缓存或运行日志加入提交。

## 提交信息

推荐使用简洁的 Conventional Commits 风格：

- `feat: 增加文档标签筛选`
- `fix: 修复协作者断线重连状态`
- `docs: 更新生产部署说明`
- `test: 补充 checkpoint 恢复测试`
- `chore: 更新构建配置`

## 问题报告

功能缺陷请提供复现步骤、预期结果、实际结果、浏览器/运行环境和必要的脱敏截图。安全漏洞请勿公开披露，改按 [SECURITY.md](SECURITY.md) 报告。
