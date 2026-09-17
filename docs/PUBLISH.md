# 版本与发布规范

项目处于开发阶段，当前版本为 `0.1.0`。版本使用语义化格式 `0.次版本.修订号`，不在 Docker 标签中添加 `v` 前缀；GitHub Release 标签使用 `v0.1.0`。

## 何时递增

- `0.1.0 → 0.1.1`：缺陷修复、性能优化、文案或样式调整，不增加对外功能能力。
- `0.1.0 → 0.2.0`：完成新的用户可见模块或完整能力，例如市场、星图、邮件等。
- `1.0.0`：核心功能、数据迁移、权限与部署文档稳定，并完成可重复的发布验收后再使用。

开发阶段允许调整接口和数据结构，但每次发布都必须提供可回溯的 Release、镜像包和迁移说明。

## 单一版本基准

根目录 [VERSION](../VERSION) 是唯一发布版本来源。发版前必须同步修改以下三个文件为相同值：

```text
VERSION
backend/pom.xml 的 <revision>
frontend/package.json 的 version
```

本地打包脚本与 GitHub Actions 都会验证这三个值；不一致时直接失败，避免镜像、前端和后端版本混用。

## GitHub 发版

1. 在功能合入 `main` 后，将版本更新提交并推送。
2. 打开 Actions 的「Package Docker images」，从包含该版本的分支或提交手动运行，输入与 `VERSION` 一致的版本号。
3. 工作流在 GitHub Linux x64 Runner 构建 `linux/amd64` API/Web 镜像，生成 `tar.gz`、SHA-256 和构建元数据。
4. 工作流上传临时 Artifact；勾选发布时会创建 `v<version>` GitHub Release 并附加同一套文件。
5. 部署端下载 Release 附件，按 [Docker 部署指南](../deploy/production/README.md) 手动校验、导入与启动。部署仍需人工执行，工作流不保存服务器凭据，也不会自动连接服务器。
