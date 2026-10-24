# 企业主页办公照片列表验收（2026-10-21）

## 结论与范围

GET /company/preview 和 GET /company/{id}/profile 已返回 photos。
本轮通过 Postman MCP 执行 76 次 HTTP 请求、146 条断言，全部通过。
数量包含登录、身份核对和权限拒绝场景，不代表业务功能数量或覆盖率。
没有通过鼠标、curl 或其他 HTTP 客户端测试接口。

复用现有 company_photo、排序 SQL 和图片读取入口，没有数据库迁移或新增路由。
新增 CompanyPhotoConverter，管理列表、上传响应和主页共用照片字段白名单及预览地址规则。
保留原有只读 REPEATABLE_READ 事务，使 photoCount 与 photos 来自同一快照。

## 持久用例和隔离环境

- 主集合：SmartRecruit API - Optimized Regression，56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82。
- 文件夹：02 Company / 企业主页照片回归 2026-10-21，0103a7d0-68c9-1d06-59d6-b1f21ac12383。
- 环境：Smart Recruit Company Gallery Isolated Regression，56227183-069527fb-5eef-40b7-b4f3-f69c3809a4a6。
- 服务地址：http://127.0.0.1:18083。
- 独立数据库：smart_recruit_profile_1792570780422。
- 独立文件目录：target/company-gallery-1792570780422。
- 账号：cp_fixture_1～7，ID 101～107；1～5 为企业，6 为求职者，7 为管理员。
- 企业：201/204/205 为 APPROVED，202 为 PENDING，203 为 REJECTED；C 阶段将隔离企业 204 改为 REJECTED。
- 测试前所有企业均无照片；使用真实 PNG/JPG 小文件经上传接口生成记录和磁盘文件。
- 密码从已有 Postman 测试环境复用，仅保存在环境 secret 和夹具进程环境，不写入集合导出或本文。
- 验收后已关闭本轮启动的隔离服务；数据库、文件和本机证据保留。

请求预执行保护要求 runCompanyGalleryRegression=true、galleryFixtureReady=true，
cgHost=http://127.0.0.1:18083。跳过请求不能计作通过。

## 结果

| 分组 | 内容 | HTTP 请求 | 通过断言 |
| --- | --- | ---: | ---: |
| A | 登录身份、空数组、上传后主页同步、角色与企业隔离、图片原件 | 51 | 94 |
| B | 非插入顺序和重复排序值、删除同步、清空、图片失效 | 17 | 38 |
| C | 企业准入撤销后重新校验主页和已取得的图片 URL | 8 | 14 |
| 合计 | | 76 | 146 |

每组通过主集合用例的同内容分组集合执行，执行前已核对主集合的 76 条请求、脚本及 multipart 附件路径。
分组集合：

- A：56227183-4ac02b99-5855-4ea7-871b-b9ac284038f4。
- B：56227183-a4d5adda-5ac6-4f66-9829-a651c753eb56。
- C：56227183-2cebde9d-1035-4770-b02e-24168570bea5。

## 请求、响应及边界

| 请求 / 场景 | 实际响应与检查 |
| --- | --- |
| 无照片时查询本人预览或主页 | code=200，photos=[]，photoCount=0 |
| 上传 a.png、b.jpg、c.png 后查询 | code=200，两个入口返回相同照片元数据；管理列表使用相同字段 |
| 排序夹具将三张照片 sortOrder 调整为 4、0、0 | 返回 b.jpg、c.png、a.png；相同 sortOrder 时按 id 升序 |
| 切换到其他企业 | 只返回目标企业自己的照片，不混入原企业 |
| PENDING / REJECTED 企业本人预览和读取照片 | 成功；求职者访问这些企业主页或照片时 code=500、data=null |
| 非企业访问本人预览；企业或管理员访问求职者主页 | code=500、data=null |
| 未登录 / 无效令牌 | HTTP401 |
| 主页响应字段 | 精确校验公开白名单；照片没有 storageKey、companyId、磁盘路径、文件字节或资质信息 |
| 求职者读取已准入企业图片 | HTTP200，实际图片 Content-Type、inline、no-store；SHA-256 与上传文件一致 |
| 删除照片后再次查询 | photos 和 photoCount 同步减少；企业本人和求职者都不能再读取原 URL |
| 删除全部照片 | 本人预览和主页 photos=[]、photoCount=0；管理列表为空，其他企业照片保留 |
| 企业 204 准入撤销后使用原 URL | 求职者主页和图片读取均拒绝；该企业本人预览、图片读取仍成功 |

最终保留 3 张其他企业的照片，数据库引用与磁盘文件一一对应，无本轮删除产生的孤儿或回收目录残留。
源 smart_recruit 核心表数据指纹与测试前一致。

## 旧契约用例同步

旧资料回归中 12 条严格检查公开响应字段的用例已加入 photos，
管理视图与公开视图比较时单独排除新增的照片字段，并追加照片数组、计数和字段白名单检查。
已同步主集合、原 D/E/M 分组集合和 company-profile-regression.postman_collection.json；
主集合原请求参数、地址和认证配置未变，更新后的脚本已读回核对。
本轮没有重跑旧资料整套 144 条请求，146 条通过断言仅来自上述新增分组。

## 重跑与交付

1. 使用 docs/testing/CompanyProfileFixture.java 和 CompanyGalleryFixture.java 编译夹具。
2. 通过 PROFILE_FIXTURE_PASSWORD 提供测试密码，对全新、符合校验的独立数据库及 target/company-gallery-* 目录执行 setup。
3. 将 target/company-gallery-fixtures/office.png 复制为 a.png、c.png，将 office.jpg 复制为 b.jpg。
4. 启动隔离服务，指定独立数据库 URL、18083 端口及独立存储目录，确认启动完成。
5. Postman MCP 执行 A；夹具 sort 后执行 B；夹具 revoke 后执行 C；最后夹具 audit 核对源数据及物理文件。
6. 关闭本轮隔离服务。重复测试须使用新的隔离数据库，不能在已完成删除的状态上直接重跑 A。

交付文件：

- docs/postman/company-gallery-regression.postman_collection.json：不含凭据的 76 条用例导出。
- docs/testing/CompanyGalleryFixture.java：隔离建库、真实图片、排序和准入变更夹具、文件与源数据审计。
- target/gallery-state.json：本机分组和环境 ID。
- target/gallery-result-A/B/C.json：已脱敏的 Postman 执行结果。
- target/gallery-final-evidence.json：最终数据库记录与文件引用审计。
- target/gallery-before/：修改前的相关文件副本，用于确认保留原改动和学习注释。

Java 编译成功，123 个业务源文件；git diff --check 通过。
本次为后端接口验收，前端图片 Blob 获取与页面渲染仍需联调。
未提交或推送 Git。
