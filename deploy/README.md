# DocFlow 生产运维文件

本目录提供生产部署示例。复制到服务器后，必须先替换所有 `docflow.example.com`、安装路径、告警接收地址和占位密钥，再启用对应配置。

## 目录说明

- `nginx/`：HTTP 引导、HTTPS 反向代理、IP 拒绝列表和可选 ModSecurity 规则。
- `monitoring/`：Prometheus 抓取、黑盒探测、告警规则和 Alertmanager 示例。
- `scripts/`：备份、恢复、备份校验、证书申请、健康检查、发布和应用回滚脚本。
- `systemd/`：Java 服务、CRDT 协作服务和每日备份任务示例。
- `backup.env.example`：不含敏感信息的备份参数模板。
- `mysql-backup.cnf.example`：复制到服务器私有目录并设置权限为 `0600`，禁止提交真实数据库密码。

完整启用步骤、备份验证和回滚流程见 [`../documents/18-生产部署备份监控与回滚指南.md`](../documents/18-生产部署备份监控与回滚指南.md)。
