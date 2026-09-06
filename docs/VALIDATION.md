# 骨架验证记录

日期：2026-09-06。仅记录实际执行的结果，不将编译成功等同于运行联调成功。

| 检查 | 结果 |
|---|---|
| 上游版本 | 前后端均取 v4.1.0 标签，具体提交见 UPSTREAM.md |
| 后端构建 | Java 17.0.20 + Maven 3.9.11，10 个 reactor 模块成功，生成可执行 fat JAR |
| JAR 内容 | 确认包含 continew-eve、WorkspaceController、application-local.yml 和 EVE 菜单迁移 |
| 前端依赖 | pnpm 9.15.9，使用上游锁文件 frozen-lockfile 安装成功；未重算依赖图 |
| 前端类型检查 | Node.js 22.23.2，vue-tsc --noEmit 退出码 0 |
| 前端生产构建 | Node.js 22.23.2，Vite 5.2.11 构建成功、退出码 0，生成 dist |
| 管理员初始化单元测试 | 3 项通过：已有密码不覆盖、缺少初始密码拒绝、仅写入哈希且比较更新 |
| 配置静态检查 | Maven XML、Spring/Compose/CI YAML 和 Python 语法解析通过 |
| 本地初始化 | 实际生成新的环境文件、随机管理员初始密码及 RSA 密钥；Git 确认忽略 .env 和 frontend/.env.local |
| 提交内容检查 | 待提交文件未含本次生成的私钥、密码或密钥；未包含 node_modules、target、dist |

后端命令为 `mvn -B -ntp -Pfat_jar -pl continew-server -am -Dspotless.skip=true package`。本环境首次访问上游 Maven 镜像遇到 DNS 问题，随后使用临时 Maven settings 连接 Maven Central 完成构建，代理配置没有入库。

前端依赖安装在环境默认 Node 24 下完成，类型检查和打包切换到选定的 Node 22 执行。项目及 CI 要求 Node 22。上游完整功能包含 Office/PDF/图表等依赖，构建仍提示部分 chunk 大于 2 MB，以及 Vite CJS API 的弃用提示；本阶段未进行功能裁剪或包体优化。

## 尚未验证

- 当前执行环境没有 Docker、MySQL、Redis 可执行程序，未启动 Compose，未进行真实数据库迁移和完整 Spring 应用启动。
- 本站 admin 登录、验证码、EVE 菜单、普通用户权限拒绝等仍需按 DEVELOPMENT.md 在本地依赖齐全后验收。
- EVE SSO、回调网址导入、令牌刷新和军团接口均未实现/未联调。
- 上游默认跳过测试，本项目已改为运行单元测试；需要 MySQL/Redis 的 contextLoads 已改名为集成测试，通过 integration-tests profile 单独运行。首次尝试完整 contextLoads 因本环境数据库未启动失败，未将它报告为通过。
- Nginx 文件仅为同源代理示例，未实际部署；没有发布公网应用。

## 下一次运行验收

1. Compose 两个服务健康，后端首次 Liquibase 完成且再次启动不重复初始化。
2. 前端使用生成的 public key，与后端 private key 匹配，本站账号登录成功。
3. 超级管理员打开「EVE 军团 → 军团工作台」；普通用户无权限时看不到菜单，直接访问接口也被拒绝。
4. 未接入游戏时不出现模拟资产/人员数据；后端断开时页面显示错误和重试。

发布检查曾拒绝初版推送；清理后的版本移除了上游演示密码、调度默认密码，私钥仅接受环境变量。未公开的初始提交将合并替换，不将旧凭据示例保留在公开提交历史中。
