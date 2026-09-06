# 上游来源与本仓库修改

导入时间：2026-09-06。

| 本地目录 | 来源 | 固定标签及提交 | 源码许可证 |
|---|---|---|---|
| backend/ | https://github.com/continew-org/continew-admin | v4.1.0 / e0e3294b3d56c114e2afbc36aac38506fdcd0d0d | Apache-2.0，原文位于 backend/LICENSE |
| frontend/ | https://github.com/continew-org/continew-admin-ui | v4.1.0 / ecabeac07e5e0dec8d2f5aae52ad4273d830c2fb | Apache-2.0，原文位于 frontend/LICENSE |

直接纳入源码，不使用 git submodule。原有源码版权头保留。导入时省略 .git、.github、.image、.vscode 和上游 docker 目录；本项目使用独立构建流程和 Compose。上游 README 保留作历史说明，其中的演示地址、运行方法和图片引用不代表本项目配置，以根 README 与 docs/DEVELOPMENT.md 为准。

新增内容：continew-eve 模块、EVE Controller/前端 API/页面、菜单 SQL、本地配置及初始化/启动脚本、构建检查和项目文档。

修改的上游文件包括 backend/pom.xml、continew-server/pom.xml、application*.yml、db.changelog-master.yaml，以及 frontend/package.json、环境配置、index.html、vite.config.ts、encrypt.ts、env.d.ts 和 WebSocket 地址生成。移除 DemoEnvironmentJob 数据重置任务和网页统计脚本。更详细变更以本仓库 Git 提交为准。

ContiNew Starter 2.14.0 是 Maven 依赖，没有复制其源码；其发布 POM 标注 LGPL，详见 [上游 POM](https://repo.maven.apache.org/maven2/top/continew/starter/continew-starter/2.14.0/continew-starter-2.14.0.pom)。其他依赖与 EVE 资料各自保留原许可，不因本项目根许可证而被重新许可。

前端安装沿用上游锁文件，未重算依赖版本。新项目构建删除 index.html 中上游站点统计代码，避免向演示站统计服务发送访问信息。生产环境由维护者配置自己的域名、密钥、数据库和服务地址。

发布前清理：不导入独立 continew-extension 调度服务，保留核心调度插件但禁用；上游 seed 用户密码替换为不可登录标记，管理员仅在首次启动由运行环境随机密码初始化，其他演示账号禁用。移除前端预填演示密码、文档中的演示密码以及调度连接默认账号密码。原始源码中的私钥示例已替换为环境变量，不向仓库提交任何生成的私钥。

初始化安全性变更包含 LocalAdminBootstrap 及单元测试；Maven 默认启用单元测试，将上游 contextLoads 保留为 integration-tests profile 下的集成测试。初始用户 SQL 是本项目首次发布前的供应商补丁；后续升级不得修改已经应用的 changeset。

补充：上游未给 spring-boot-maven-plugin 固定版本，本项目显式设置为 ${spring-boot.version}（3.3.12），使打包器与框架版本一致。
