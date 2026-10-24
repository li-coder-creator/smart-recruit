# 企业照片与资质接口回归（2026-10-20）

## 结论

七个管理接口及配套图片读取接口均通过本轮回归。
主集合持久保留 154 条请求，实际执行 155 次 HTTP 请求、240 条断言，全部通过。
额外一次请求是并发后分组中的登录子请求；数量包含登录和身份核对，不代表功能数量或覆盖率。
所有 HTTP 请求通过 Postman MCP 的 runCollection 执行，没有使用鼠标操作或其他 HTTP 客户端。

## 环境与数据

- 集合：SmartRecruit API - Optimized Regression
  （56227183-00855bc5-e68c-4aab-bdc2-faffe0f70f82）。
- 文件夹：02 Company / 企业照片与资质回归 2026-10-20
  （c12cf91a-9530-9a87-0a90-9145c66bf847）。
- 环境：Smart Recruit Company Files Isolated Regression
  （56227183-c91f6f88-fbf5-40fb-b1cd-20e1631e06e5）。
- 地址：http://127.0.0.1:18082。
- 独立数据库：smart_recruit_profile_1792505311650。
- 独立目录：target/company-files-1792505311650。
- 账号 cp_fixture_1～7，ID 101～107；企业 201 APPROVED、202 PENDING、
  203 REJECTED、204 APPROVED、205 APPROVED；106 为求职者、107 为管理员。
- 密码复用现有 Postman 企业测试环境配置，只保存在专用环境 secret 中，不写入集合或文档。
- 源 smart_recruit 的核心表 SHA-256 数据指纹与测试前一致；本轮没有执行生产表迁移。
- 验收后已关闭本轮创建的隔离服务，保留数据库、文件及证据。

真实二进制夹具包括 JPG、PNG、单页 PDF、中文名称、空文件、损坏和伪装文件、
3MB/10MB 精确边界和超限文件、超像素图片。PDF 字节已固定以保证重复生成后 SHA-256 不变。
营业执照与“审核通过”均为隔离夹具，不代表真实认证或已完成管理端审核。

## 实际通过结果

| 阶段 | 内容 | 实际请求 | 通过断言 |
| --- | --- | ---: | ---: |
| A | 照片上传、列表、读取、删除、角色与归属 | 48 | 75 |
| B | 资质上传、原件下载、逻辑移除、私有权限 | 37 | 57 |
| C | 格式伪装、空文件、大小边界及超限 | 23 | 32 |
| D0 | 准备五张照片 | 6 | 9 |
| D1 | 第一个并发 multipart 上传 | 3 | 5 |
| D2 | 第二个并发 multipart 上传 | 3 | 5 |
| D | 并发后数量、拒绝第七张、删除释放名额 | 5 | 6 |
| E | 照片插入数据库失败 | 3 | 5 |
| F | 照片文件已暂存后数据库删除失败 | 5 | 8 |
| G | 资质审核快照插入失败 | 3 | 5 |
| H | 资质逻辑移除数据库失败 | 5 | 8 |
| J | 磁盘目录无法写入 | 3 | 5 |
| I | 已核验执照移除后核验标记失效 | 7 | 12 |
| R | 进程重启恢复照片及原件读取 | 4 | 8 |
| 合计 | | 155 | 240 |

## 关键请求与响应检查

| 请求 | 实际检查 |
| --- | --- |
| POST /company/photos，PNG 内容、伪造客户端 MIME | code=200，服务端 contentType=image/png，返回白名单字段 |
| GET /company/photos/{id}/file | HTTP200，PNG/JPG 实际类型，字节长度及 SHA-256 与上传文件一致 |
| POST /company/qualifications，BUSINESS_LICENSE + PDF | code=200，新材料 PENDING，创建对应审核快照 |
| GET /company/qualifications/{id}/file | attachment、no-store、nosniff，原件字节 SHA-256 一致 |
| 私有资质被求职者、管理员、其他企业访问 | code=500、data=null，不返回文件 |
| 照片被其他企业访问，或求职者访问未准入企业照片 | code=500、data=null |
| 所有未登录管理和读取入口 | HTTP401 |
| 3MB 照片、10MB 资质 | 精确边界成功；超一字节拒绝，不写入存储和数据库 |
| 文本伪装 PNG/PDF、SVG、扩展名不符、空文件、未知类型 | 拒绝；文件数量与数据库无部分新增 |
| 并发争取第六张 | 返回业务码恰好 [200,500]；失败原因是六张上限，最终六张 |
| DELETE 已审核营业执照 | code=200；列表与下载不再可见，businessLicenseVerified 由 true 变 false |
| 数据库或磁盘故障 | 业务失败；数据库记录、文件名、长度与内容摘要均和故障前基线一致 |

返回字段白名单不包含 storageKey、内部磁盘路径、companyId、deleted 或审核人。
待审核和被拒绝企业可以管理自己的文件，但求职者不能访问其办公照片。

## 并发与恢复证据

两个完整集合以共同时间门限同时发送真实附件：

- 2026-10-20 22:46:55.024，两次 POST /company/photos 同时进入后端；
- 分别于 .066 和 .068 完成，业务结果 [200,500]；
- 没有突破六张上限；随后删除一张后恢复为五张。

四类数据库故障均使用只存在于隔离库的触发器，执行后立即移除。
每次比较照片、资质、审核记录及实际文件路径、字节数、SHA-256。
磁盘故障通过临时替换隔离目录模拟，验证后恢复原目录并对比基线。

重启前把仍有数据库记录的照片移入 .trash，并增加一个无引用 UUID 文件；
重启后照片恢复、孤儿文件被清理。逻辑删除资质原件仍保留。
最终审计：7 张照片、6 条资质（2 条已移除）、6 条资质审核记录、13 个受管理原件，
没有孤儿或回收目录残留；每条材料都有归属一致的审核记录。

## 工具问题及处理

首轮使用 MCP createCollection 创建嵌套请求时脚本未被保留，得到零断言；
该运行不计入通过结果。改为集合根层级请求后重新核对脚本，再复制到主集合。
本轮创建的缺少脚本的重复文件夹已备份并移除，没有删除其他原有目录。

pm.sendRequest 的附件子请求没有生成有效 multipart，两个请求被拒绝；
该失败运行不计入最终并发结论。已用 D1/D2 两个完整集合同时执行替代，并验证真实重叠。
云端集合拉取超时及测试夹具时间序列化问题均已处理后重跑。
最终从主集合读取完整模型，核对 154 条请求、测试脚本及所有 multipart src 路径。
复制产生的请求名称带 Copy 后缀是 Postman 自动命名，不影响用例内容。

## 重跑顺序与交付物

环境需要 runCompanyFilesRegression=true、filesIsolatedFixtureReady=true、
cfHost=http://127.0.0.1:18082；脚本跳过时不能算通过。

1. 使用 docs/testing 下两个夹具 Java 类在独立库构造初始数据及附件；密码通过进程环境提供。
2. 启动独立端口与独立文件目录，按 A → B → C → D0 执行。
3. 环境 cfRaceAt 设为未来约45秒的毫秒时间戳，同时执行 D1、D2，汇总结果后执行 D。
4. E/F/G/H 各执行前安装对应故障触发器，结束后 fault-off，verify-rollback 核对。
5. J 前 io-on，结束后 io-off；I 前 verified 构造审核通过的隔离材料。
6. 停止隔离服务，recovery-seed，再启动服务执行 R，最后 audit。

回归运行集合 ID 和本机运行状态保存在 target/files-state.json；
主集合是持久交付，target 内文件为本机证据，不提交。

- docs/postman/company-files-regression.postman_collection.json：无凭据用例导出。
- docs/testing/CompanyFilesFixture.java：文件、故障、恢复和物理文件审计夹具。
- docs/testing/CompanyProfileFixture.java：基础数据库夹具。
- target/files-result-*.json：最终各分组 Postman 输出。
- target/files-concurrency-evidence.json：并发业务结果及后端时间证据。
- target/files-final-evidence.json：最终数据库、物理原件与摘要核对。

这是功能与事务回归，不是负载测试；没有据此宣称吞吐量或多实例存储能力。
Java 编译通过，122 个源文件；git diff --check 通过。未提交或推送 Git。
