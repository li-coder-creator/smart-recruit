# 企业办公照片与资质材料

## 接口契约

普通 JSON 响应沿用 Result：成功业务码 200，失败业务码 500；未登录 HTTP 401。
所有管理接口使用当前登录企业，不接收客户端 companyId 或审核结果。
待审核、被拒绝的企业也可完善自己的文件资料。

| 业务 | 方法与路径 | 请求 | 返回 data |
| --- | --- | --- | --- |
| 自己的照片列表 | GET /company/photos | 无 | List<CompanyPhotoVO> |
| 上传照片 | POST /company/photos | multipart，file | CompanyPhotoVO |
| 删除照片 | DELETE /company/photos/{photoId} | 正整数 ID | null |
| 自己的资质列表 | GET /company/qualifications | 无 | List<CompanyQualificationVO> |
| 上传资质 | POST /company/qualifications | multipart，file、qualificationType | CompanyQualificationVO |
| 下载资质原件 | GET /company/qualifications/{qualificationId}/file | 正整数 ID | 文件字节，不包装 Result |
| 移除资质 | DELETE /company/qualifications/{qualificationId} | 正整数 ID | null |

照片另有必要的配套读取路由 GET /company/photos/{photoId}/file：
本企业可预览；求职者只可读取准入 APPROVED 企业的照片；
其他企业、管理员不能通过这个入口访问。没有新增匿名白名单。

资质类型为 BUSINESS_LICENSE、INDUSTRY_CERTIFICATE、OTHER。
新资质由服务端设置 PENDING，并在同一事务保存 QUALIFICATION 审核快照。
请求即使携带 reviewStatus、deleted、companyId，也不能控制系统字段。
管理员材料审核和历史原件读取已接入，见 company-qualification-review-api-guide.md。

## 响应字段

CompanyPhotoVO：

- id、originalFilename、contentType、fileSize（字节）、sortOrder、createTime；
- previewUrl：相对 API 路径，不是磁盘路径或无需身份校验的公开链接。

CompanyQualificationVO：

- id、qualificationType、originalFilename、contentType、fileSize；
- reviewStatus、createTime、updateTime、downloadUrl。

不返回 storageKey、内部绝对路径、账号归属或私有文件内容。
照片按 sortOrder、id 升序；材料按 createTime、id 降序；无记录返回 []。
企业下载只允许读取自己的未移除材料；管理员使用独立审核入口读取原件。

## 文件校验

- 照片最多 6 张，JPG/PNG，单张最多 3×1024×1024 字节。
- 资质支持 PDF/JPG/PNG，单个最多 10×1024×1024 字节。
- 容器 multipart 文件上限 10MB，请求上限 11MB；业务层仍分别执行照片和资质上限。
- 忽略客户端声明的 MIME，ImageIO 检查实际格式并完成图片解码；
  扩展名必须与实际格式匹配，jpeg/jpg 均接受。
- 图片像素总数上限为 2500 万，作为解压内存保护；不会静默压缩或改写原图。
- PDF 使用 Apache PDFBox 3.0.8 解析页面结构，拒绝伪造文件头、损坏、加密或无页面文件。
  这只检查可解析性，不证明材料真实或已经通过业务审核。
- 原始名称只保留 basename，拒绝控制字符、空名称及过长名称；
  磁盘名独立生成 UUID，不使用客户端名称拼接路径。

PDFBox 版本及 API 参考：
[Apache 官方下载](https://pdfbox.apache.org/download.cgi)、
[PDFBox 3.0 入门](https://pdfbox.apache.org/3.0/getting-started.html)。
依赖排除 commons-logging，使用 Spring 已提供的 spring-jcl。

## 本地存储和访问

配置 smart-recruit.company-files.root；
默认 ./storage/company，可通过 SMART_RECRUIT_COMPANY_FILE_ROOT 覆盖。
storage 已加入 Git 忽略；目录不作为静态资源映射，资质原件不提供静态地址。
对象路径为 photos/UUID.png、photos/UUID.jpg 或 qualifications/UUID.扩展名。

存储根目录用 .database 绑定数据库名称，拒绝切换数据库却复用同一个文件目录，
防止恢复清理时误删另一个数据库的原件。隔离回归使用独立数据库、目录和 18082 端口。
备份与恢复应同时处理数据库和对应文件目录。

读取接口设置正确的实际 Content-Type、Content-Length、
Content-Disposition、Cache-Control:no-store、X-Content-Type-Options:nosniff。
照片 inline，资质 attachment，文件名采用 UTF-8 Content-Disposition 编码。
下载在权限校验后有界读取到 byte[]，单次最多 10MB；共享行锁持有到读取完成，
避免删除操作在读取期间移动或移除目标。

前端携带 Bearer 令牌获取图片 Blob，再用 URL.createObjectURL 显示并及时 revokeObjectURL；
不能直接依赖不携带令牌的普通 img src。
现有 JSON 请求拦截器会检查业务码，不能把 Blob 直接按 Result 处理；
前端应使用专用二进制请求，或仅对 JSON 响应执行业务码检查。
文件错误响应仍可能是 HTTP200 的 Result JSON，Blob 请求需检查 Content-Type 并解析错误。
GET /company/preview 和 GET /company/{id}/profile 已在 CompanyPublicProfileVO 中返回 photos，
与管理列表共用 CompanyPhotoConverter 白名单和预览路径规则。
照片按 sortOrder、id 排序，无记录为 []；主页 photoCount 与列表长度一致。
本人预览允许任意准入状态；求职者主页及图片读取要求目标企业准入 APPROVED。

## 并发和事务

CompanyFileController 负责 multipart 参数接收和响应构造。
CompanyFileServiceImpl 负责企业身份、归属、数量、审核状态、事务和 VO 白名单。
CompanyFileMapper 负责 SQL；CompanyFileStorage 负责内容识别、私有路径、字节读写和补偿。

上传照片先验证文件，再 SELECT company ... FOR UPDATE 锁定该企业记录。
随后对照片使用锁定读获取当前记录，检查数量并插入。
不能使用等待企业锁之前建立的 RR 快照统计，否则两个请求可能都看到旧的 5 张。
同一企业的文件写入使用同一父记录锁；删除也按父记录、子记录的顺序锁定。

资质插入与审核记录插入处于一个事务，快照包含材料 ID、类型、名称、实际 MIME、
字节数和 SHA-256，不包含磁盘路径。
审核结果由后续管理员业务改变，新上传不能自行通过审核。

## 数据库之外的文件补偿

数据库事务不会自动回滚磁盘操作，本实现额外处理：

1. 上传：生成 UUID 文件；事务回滚时删除新文件，提交后保留。
2. 删除照片：先移入 .trash；数据库回滚恢复，提交后清理回收文件。
3. 移除资质：只更新 deleted，保留原件和审核记录，用于历史追溯。
4. 启动恢复：按数据库引用恢复回收目录照片，清理无引用的 UUID 文件；
   已逻辑删除的资质仍有引用，不作为孤儿清理。
5. 本进程正在处理的对象由 inFlight 集合保护；清理失败记录错误并在下次启动重试。

这是单机、单应用进程的本地存储方案，不是数据库与文件系统的分布式原子事务。
不能让多个应用进程共享同一目录；多实例部署需要迁移对象存储并设计持久清理任务。
进程崩溃或磁盘不可用后的最终恢复依赖目录可用和数据库、文件备份的一致性。
管理员可经审核记录读取已移除原件用于追溯，读取时校验摘要；企业端旧下载入口仍排除已移除材料。
原件保留期限仍需后续明确。
