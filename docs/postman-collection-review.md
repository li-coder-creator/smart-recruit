# SmartRecruit API Postman 集合审查与优化建议

审查时间：2026-09-14  
集合：`SmartRecruit API`  
工作区：`ha ha's Workspace`  
环境：`Smart Recruit Local`  
审查方式：通过 Postman MCP 检查集合、环境，并结合当前 Spring Boot 后端 Controller/DTO/Service 反向核对接口。后续已创建优化版集合并更新本地环境变量，原集合未覆盖或删除。

## 1. 集合现状

- Postman 集合已配置根级 Bearer Auth，token 使用 `{{token}}`。
- 工作区内存在环境 `Smart Recruit Local`，已补齐 `host=http://localhost:8081`、三类 token 变量和常用测试 ID 变量。
- 集合按 `Authentication`、`User`、`Resume`、`Company`、`Job`、`Application`、`Admin`、`Business Flow`、`Performance Test` 分组。
- MCP 搜索到 40 个请求项，包含多个业务流程副本和空请求。
- 后端当前实际接口约 37 个，核心模块包括用户、企业、岗位、简历、教育经历、投递、管理员审核。
- 已新增优化版集合 `SmartRecruit API - Optimized Regression`，UID `56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82`。

## 2. 主要问题清单

### 高严重程度

| 编号 | 问题 | 影响 | 建议 |
|---|---|---|---|
| H-01 | 环境变量 `host` 原为空，但多数请求使用 `{{host}}` | 选择环境后请求会直接失败或无法定位服务 | 已修复为 `http://localhost:8081`，后续按环境拆分 local/dev/test |
| H-02 | 集合中存在空请求：`Resume/New Request` 两个，以及 `Business Flow/企业招聘流程/Detail Copy` URL 为 `None` | Runner 执行时失败，影响报告通过率 | 删除空请求或补齐方法、URL、断言 |
| H-03 | 缺少后端实际接口 `PUT /user/info` | 用户资料修改接口无法被集合覆盖 | 新增“更新当前用户信息”请求和正常/异常断言 |
| H-04 | 部分测试脚本与后端返回不匹配，例如新增简历脚本读取 `res.data.id`，但后端 `POST /resume` 返回 `Result<Void>` | 正常接口也会被 Postman 判为失败 | 改为只断言 `code/message`，或后端改为返回新建 ID 后再提取变量 |
| H-05 | 多个请求样例使用无效密码 `1`，但后端密码规则为 5-26 位字母数字下划线 | 注册/改密请求预期与实际不一致 | 正常用例使用合法密码；异常用例单独验证短密码 |
| H-06 | 高风险写操作和删除操作未独立隔离测试数据 | 可能误删本地/测试库已有业务数据 | 对 `DELETE`、审核状态变更、改密、投递状态更新等请求使用专门测试数据，并默认跳过执行 |

### 中严重程度

| 编号 | 问题 | 影响 | 建议 |
|---|---|---|---|
| M-01 | URL 混用硬编码 `http://localhost:8081` 和 `{{host}}` | 环境切换困难 | 全部改为 `{{host}}/...` |
| M-02 | token 变量混用：根级 `{{token}}`、请求级 `{{json_web_token_0lpn}}`，部分手写 Authorization Header | 容易出现认证失败和角色混乱 | 统一根级 Bearer Auth；拆分 `applicantToken`、`companyToken`、`adminToken` |
| M-03 | 缺少多角色登录和变量提取流程 | 权限测试难以自动化 | 新增“登录求职者/企业/管理员”请求并分别保存 token |
| M-04 | 多数请求没有 description 和 saved example response | 集合作为接口文档的可读性不足 | 为每个请求补充用途、权限、参数、返回示例 |
| M-05 | 大量断言只检查 HTTP 200 和 `res.code`，缺少响应结构、字段类型、错误消息、敏感字段断言 | 缺陷发现能力弱 | 增加统一结构、字段类型、数组/分页结构、敏感字段不可见断言 |
| M-06 | GET 请求中存在无意义 raw body，例如 `CompanyApplicationList Copy` | Runner 表现不稳定，误导维护者 | 移除 GET body |
| M-07 | 业务流程副本与模块接口重复，名称带 `Copy` | 后续维护成本高 | 保留模块接口作为基线；流程接口放到 Flow 文件夹并以步骤编号命名 |
| M-08 | 管理员分页接口未覆盖 `companyStatus`、`userRole` 查询参数边界和非法枚举 | 条件查询风险未测 | 增加筛选、非法枚举、分页边界用例 |

### 低严重程度

| 编号 | 问题 | 影响 | 建议 |
|---|---|---|---|
| L-01 | 命名不统一：`Updata`、`Appllication`、`Adimin`、`Allcompany`、`Add` 等 | 可读性下降 | 统一命名为 `METHOD /path - 中文动作` |
| L-02 | 存在空 Header 行 | 集合显得凌乱 | 清理空 header |
| L-03 | 集合根级 prerequest/test 脚本为空 | 维护入口不清晰 | 添加公共断言函数或移除空脚本 |
| L-04 | 集合未 Git Connected | 集合变更难以随代码审查 | 建议导出集合到仓库或启用 Postman Git 同步 |

## 3. 与后端接口覆盖对比

| 模块 | 后端实际接口 | Postman 覆盖情况 | 备注 |
|---|---:|---|---|
| 用户 | 5 | 缺 1 个 | 缺 `PUT /user/info` |
| 企业 | 5 | 基本覆盖 | `POST/PUT/DELETE /company` 属写操作，需测试数据隔离 |
| 岗位 | 6 | 基本覆盖 | `GET /job/company` 放在 Company 文件夹，建议移到 Job 或保留交叉说明 |
| 简历 | 9 | 覆盖但含空请求 | 教育经历接口已覆盖，空请求需清理 |
| 投递 | 5 | 覆盖 | 需要补充重复投递、越权、状态边界 |
| 管理员 | 7 | 覆盖 | 需要补充分页、筛选、角色权限和审核状态边界 |
| 面试 | 0 | 未发现接口 | 当前代码无独立面试模块，投递状态 `INTERVIEW=2` 可作为面试阶段测试 |

## 4. 建议的 Postman 集合结构

```text
SmartRecruit API
├── 00 Auth
│   ├── POST /user/register - 注册求职者
│   ├── POST /company/register - 注册企业
│   ├── POST /user/login - 登录求职者
│   ├── POST /user/login - 登录企业
│   └── POST /user/login - 登录管理员
├── 01 User
│   ├── GET /user/info - 当前用户信息
│   ├── PUT /user/info - 修改用户资料
│   └── PATCH /user/password - 修改密码
├── 02 Company
├── 03 Job
├── 04 Resume
│   └── Education
├── 05 Application
├── 06 Admin
├── 90 Business Flow
│   ├── 企业认证流程
│   ├── 企业发布岗位流程
│   └── 求职者投递流程
└── 99 Performance
```

## 5. 建议环境变量

| 变量名 | 类型 | 示例值 | 用途 |
|---|---|---|---|
| `host` | default | `http://localhost:8081` | 当前服务地址 |
| `applicantUsername` | default | `test_applicant_{{$timestamp}}` | 求职者注册/登录 |
| `applicantPassword` | secret | `Test_12345` | 求职者密码 |
| `companyUsername` | default | `test_company_{{$timestamp}}` | 企业注册/登录 |
| `companyPassword` | secret | `Test_12345` | 企业密码 |
| `adminUsername` | default | 由数据库预置 | 管理员登录 |
| `adminPassword` | secret | 由数据库预置 | 管理员密码 |
| `applicantToken` | secret | 登录后写入 | 求职者 token |
| `companyToken` | secret | 登录后写入 | 企业 token |
| `adminToken` | secret | 登录后写入 | 管理员 token |
| `companyId` | default | 注册/查询后写入 | 企业审核 |
| `jobId` | default | 发布岗位后写入 | 岗位详情/投递 |
| `resumeId` | default | 新建简历后写入 | 简历详情/投递 |
| `educationId` | default | 新建教育经历后写入 | 教育经历更新/删除 |
| `applicationId` | default | 投递后通过列表提取 | 投递详情/状态更新 |

## 6. 建议公共断言

建议在集合级 test 脚本封装公共断言函数：

```javascript
pm.test("HTTP 状态码为 200", function () {
  pm.response.to.have.status(200);
});

const res = pm.response.json();

pm.test("统一响应结构正确", function () {
  pm.expect(res).to.have.property("code");
  pm.expect(res).to.have.property("message");
  pm.expect(res).to.have.property("data");
});
```

对未登录请求单独断言：

```javascript
pm.test("未认证返回 401", function () {
  pm.response.to.have.status(401);
});
```

## 7. Postman 云端优化结果与剩余建议

已完成：

1. 给 `Smart Recruit Local` 环境补齐 `host=http://localhost:8081`。
2. 新建优化版集合 `SmartRecruit API - Optimized Regression`。
3. 优化版集合统一使用 `{{host}}/...`。
4. 优化版集合新增缺失请求 `PUT {{host}}/user/info`。
5. 优化版集合按 `token/applicantToken/companyToken/adminToken` 设计认证变量。
6. 优化版集合加入集合级统一响应结构和敏感字段基础断言。
7. 高风险请求在优化版集合中标注 `SKIP_BY_DEFAULT`。

原集合仍建议后续处理：

1. 删除或补齐 3 个空/无效请求：两个 `New Request` 和 `Detail Copy`。
2. 修复原集合断言错误：新增简历、投递等 `Result<Void>` 接口不再读取 `res.data.id`。
3. 修复原集合无效正常样例：密码从 `1` 改为符合规则的 `Test_12345`。
4. 清理原集合空 Header、空集合脚本、拼写错误和 `Copy` 命名。

## 8. 本次未执行项

- 未执行 Postman Runner，因为当前 Postman MCP 未暴露可调用的 Runner 工具。
- 未执行删除类和改密类请求。
- 未覆盖原集合，原集合保留不动。
