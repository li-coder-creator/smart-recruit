# SmartRecruit API 接口测试用例文档

生成时间：2026-09-14  
依据来源：Postman 集合 `SmartRecruit API`、环境 `Smart Recruit Local`、当前后端代码。  
执行约束：未得到确认前，不执行删除、审核、状态变更、改密、投递、批量或会修改数据的请求。  
统一返回约定：业务成功通常为 `HTTP 200` 且 `body.code=200`；业务失败通常为 `HTTP 200` 且 `body.code=500`；未登录或 token 无效为 `HTTP 401`。当前后端只放行 `/user/login`、`/user/register`、`/company/register`，其余接口均需要 `Authorization: Bearer <token>`。

## 1. 测试数据约定

| 名称 | 说明 |
|---|---|
| 求职者账号 | 角色 `JOB_SEEKER`，用于简历、投递、查看自己的投递 |
| 企业账号 | 角色 `COMPANY`，企业状态覆盖 `PENDING`、`APPROVED`、`REJECTED` |
| 管理员账号 | 角色 `ADMIN`，用于企业审核、用户/企业列表 |
| 测试岗位 | 由已认证企业创建，状态覆盖 `ACTIVE=0`、`PAUSED=1` |
| 测试简历 | 由求职者创建，包含基础简历和教育经历 |
| 越权数据 | 另一个求职者/企业拥有的简历、岗位、投递记录 |

## 2. 用户与认证模块

| 用例编号 | 接口名称 | 请求方法与路径 | 测试场景 | 前置条件 | 请求参数 | 测试步骤 | 预期状态码 | 预期响应结果 | 优先级 | 测试类型 |
|---|---|---|---|---|---|---|---|---|---|---|
| AUTH-001 | 求职者注册 | POST `/user/register` | 合法用户名和密码注册 | 用户名不存在 | `username=abcde`, `password=abcde` | 发送注册请求 | 200 | `code=200,message=success` | P0 | 正常 |
| AUTH-002 | 求职者注册 | POST `/user/register` | 用户名最小长度边界 | 用户名不存在 | `username=abc12` 5 位 | 发送注册请求 | 200 | `code=200` | P1 | 边界 |
| AUTH-003 | 求职者注册 | POST `/user/register` | 用户名最大长度边界 | 用户名不存在 | 26 位字母数字下划线 | 发送注册请求 | 200 | `code=200` | P1 | 边界 |
| AUTH-004 | 求职者注册 | POST `/user/register` | 用户名 4 位 | 无 | `username=abcd` | 发送注册请求 | 200 | `code=500,message` 包含用户名规则 | P0 | 异常/边界 |
| AUTH-005 | 求职者注册 | POST `/user/register` | 用户名 27 位 | 无 | 27 位字符串 | 发送注册请求 | 200 | `code=500,message` 包含用户名规则 | P1 | 异常/边界 |
| AUTH-006 | 求职者注册 | POST `/user/register` | 用户名含非法字符 | 无 | `username=abc-01` | 发送注册请求 | 200 | `code=500,message` 包含用户名规则 | P1 | 异常 |
| AUTH-007 | 求职者注册 | POST `/user/register` | 缺少用户名 | 无 | 仅传 `password` | 发送注册请求 | 200 | `code=500,message=用户名不能为空` | P0 | 参数缺失 |
| AUTH-008 | 求职者注册 | POST `/user/register` | 密码最小/最大长度边界 | 用户名不存在 | 5 位、26 位合法密码 | 分别发送注册请求 | 200 | `code=200` | P1 | 边界 |
| AUTH-009 | 求职者注册 | POST `/user/register` | 密码过短或非法字符 | 无 | `password=1` 或 `abc-1` | 发送注册请求 | 200 | `code=500,message` 包含密码规则 | P0 | 异常/边界 |
| AUTH-010 | 求职者注册 | POST `/user/register` | 重复用户名 | 用户名已存在 | 同一用户名再次注册 | 发送注册请求 | 200 | `code=500,message=用户名已存在` | P0 | 业务规则 |
| AUTH-011 | 用户登录 | POST `/user/login` | 合法账号登录 | 已存在账号 | 正确用户名和密码 | 发送登录请求 | 200 | `code=200,data` 为 JWT 字符串；保存 token | P0 | 正常 |
| AUTH-012 | 用户登录 | POST `/user/login` | 用户不存在 | 无 | 不存在用户名 | 发送登录请求 | 200 | `code=500,message=用户不存在，请先注册` | P0 | 错误测试 |
| AUTH-013 | 用户登录 | POST `/user/login` | 密码错误 | 用户存在 | 错误密码 | 发送登录请求 | 200 | `code=500,message=用户名或密码错误` | P0 | 错误测试 |
| AUTH-014 | 用户登录 | POST `/user/login` | 缺少密码 | 无 | 仅传用户名 | 发送登录请求 | 200 | `code=500,message=密码不能为空` | P0 | 参数缺失 |
| AUTH-015 | 受保护接口 | GET `/user/info` | 未带 Authorization | 无 | 无 token | 发送请求 | 401 | 响应被拦截，无业务 body 要求 | P0 | 权限 |
| AUTH-016 | 受保护接口 | GET `/user/info` | Authorization 格式错误 | 无 | `Authorization: token xxx` | 发送请求 | 401 | 响应被拦截 | P0 | 权限 |
| AUTH-017 | 受保护接口 | GET `/user/info` | token 伪造/过期 | 无 | 无效 Bearer token | 发送请求 | 401 | 响应被拦截 | P0 | 安全 |
| USER-001 | 获取当前用户信息 | GET `/user/info` | 求职者查看自己信息 | 已登录求职者 | `Bearer applicantToken` | 发送请求 | 200 | `code=200,data.id/username/role` 存在，`password` 不应出现在响应 JSON | P0 | 正常/安全 |
| USER-002 | 获取当前用户信息 | GET `/user/info` | 企业查看自己信息 | 已登录企业 | `Bearer companyToken` | 发送请求 | 200 | `code=200,data.role=COMPANY` | P1 | 正常 |
| USER-003 | 修改用户资料 | PUT `/user/info` | 合法修改昵称、邮箱、手机号 | 已登录 | 合法 `username,nickname,email,phone` | 发送请求后 GET 校验 | 200 | `code=200`，再次查询字段已更新 | P0 | 正常 |
| USER-004 | 修改用户资料 | PUT `/user/info` | 邮箱格式错误 | 已登录 | `email=abc` | 发送请求 | 200 | `code=500,message=邮箱格式不正确` | P0 | 参数格式错误 |
| USER-005 | 修改用户资料 | PUT `/user/info` | 手机号边界与格式 | 已登录 | 合法 `1[3-9]` 开头 11 位、非法 10/12 位 | 分别发送请求 | 200 | 合法成功；非法 `code=500,message=手机号格式不正确` | P1 | 边界/格式 |
| USER-006 | 修改用户资料 | PUT `/user/info` | 缺少 username | 已登录 | 无 `username` | 发送请求 | 200 | `code=500,message=用户名不能为空` | P0 | 参数缺失 |
| USER-007 | 修改密码 | PATCH `/user/password` | 原密码正确且两次新密码一致 | 已登录测试账号 | `oldPassword,newPassword,rePassword` | 发送请求，再用新密码登录 | 200 | `code=200`，新密码可登录 | P0 | 正常 |
| USER-008 | 修改密码 | PATCH `/user/password` | 原密码错误 | 已登录 | 错误 `oldPassword` | 发送请求 | 200 | `code=500,message=原密码错误` | P0 | 错误测试 |
| USER-009 | 修改密码 | PATCH `/user/password` | 两次新密码不一致 | 已登录 | `newPassword != rePassword` | 发送请求 | 200 | `code=500,message=两次密码不一致` | P0 | 业务规则 |
| USER-010 | 修改密码 | PATCH `/user/password` | 新密码长度边界 | 已登录 | 4 位、5 位、26 位、27 位 | 分别发送请求 | 200 | 5/26 位成功；4/27 位失败 | P1 | 边界 |

## 3. 企业模块

| 用例编号 | 接口名称 | 请求方法与路径 | 测试场景 | 前置条件 | 请求参数 | 测试步骤 | 预期状态码 | 预期响应结果 | 优先级 | 测试类型 |
|---|---|---|---|---|---|---|---|---|---|---|
| COMP-001 | 企业注册 | POST `/company/register` | 合法企业账号注册 | 用户名不存在 | 合法 `username,password,companyName` | 发送请求 | 200 | `code=200,data.companyId` 存在，`status=PENDING` | P0 | 正常 |
| COMP-002 | 企业注册 | POST `/company/register` | 企业名称缺失 | 无 | 无 `companyName` | 发送请求 | 200 | `code=500,message=企业名称不能为空` | P0 | 参数缺失 |
| COMP-003 | 企业注册 | POST `/company/register` | 用户名/密码边界 | 无 | 4/5/26/27 位 | 分别发送请求 | 200 | 边界内成功，边界外失败 | P1 | 边界 |
| COMP-004 | 企业注册 | POST `/company/register` | 重复用户名 | 用户名已存在 | 相同用户名 | 发送请求 | 200 | `code=500,message=用户名已存在` | P0 | 业务规则 |
| COMP-005 | 创建企业信息 | POST `/company` | 企业用户创建企业 | 已登录企业 | `name,description,logo,city,address` | 发送请求 | 200 | `code=200` | P1 | 正常 |
| COMP-006 | 创建企业信息 | POST `/company` | 求职者创建企业 | 已登录求职者 | 合法企业 body | 发送请求 | 200 | 当前代码未做角色限制，记录为潜在缺陷 | P0 | 权限 |
| COMP-007 | 创建企业信息 | POST `/company` | 缺少企业名称 | 已登录 | 无 `name` | 发送请求 | 200 | `code=500,message=企业名称不能为空` | P0 | 参数缺失 |
| COMP-008 | 查询当前企业 | GET `/company` | 企业用户查看自己的企业 | 已登录企业且已有企业 | 无 | 发送请求 | 200 | `code=200,data.userId` 对应当前用户 | P0 | 正常 |
| COMP-009 | 查询当前企业 | GET `/company` | 求职者查询企业 | 已登录求职者 | 无 | 发送请求 | 200 | 当前 controller 直接查询，可能返回 null；记录权限边界 | P1 | 权限 |
| COMP-010 | 修改企业信息 | PUT `/company` | 企业用户修改自己的企业 | 已登录企业且已有企业 | 合法 body | 发送请求后 GET 校验 | 200 | `code=200`，企业信息更新 | P0 | 正常 |
| COMP-011 | 修改企业信息 | PUT `/company` | 求职者修改企业 | 已登录求职者 | 合法 body | 发送请求 | 200 | `code=500,message=仅企业用户可访问` | P0 | 权限 |
| COMP-012 | 删除企业 | DELETE `/company` | 企业用户删除自己的企业 | 已登录企业且使用隔离测试数据 | 无 | 发送请求后 GET 校验 | 200 | `code=200`，后续查询为空或业务错误 | P1 | 正常/高风险 |
| COMP-013 | 删除企业 | DELETE `/company` | 求职者删除企业 | 已登录求职者 | 无 | 发送请求 | 200 | `code=500,message=仅企业用户可访问` | P0 | 权限/错误 |

## 4. 岗位模块

| 用例编号 | 接口名称 | 请求方法与路径 | 测试场景 | 前置条件 | 请求参数 | 测试步骤 | 预期状态码 | 预期响应结果 | 优先级 | 测试类型 |
|---|---|---|---|---|---|---|---|---|---|---|
| JOB-001 | 发布岗位 | POST `/job` | 已认证企业发布岗位 | 企业状态 `APPROVED` | 合法 `title,salaryMin,salaryMax,status=0` | 发送请求 | 200 | `code=200` | P0 | 正常 |
| JOB-002 | 发布岗位 | POST `/job` | 未审核企业发布岗位 | 企业状态 `PENDING` | 合法 body | 发送请求 | 200 | `code=500,message=企业未通过验证` | P0 | 权限/业务规则 |
| JOB-003 | 发布岗位 | POST `/job` | 求职者发布岗位 | 已登录求职者 | 合法 body | 发送请求 | 200 | `code=500,message=仅企业用户可访问` | P0 | 权限 |
| JOB-004 | 发布岗位 | POST `/job` | 缺少岗位名称 | 已认证企业 | 无 `title` | 发送请求 | 200 | `code=500,message=岗位名称不能为空` | P0 | 参数缺失 |
| JOB-005 | 发布岗位 | POST `/job` | 薪资边界 | 已认证企业 | `salaryMin=0, salaryMax=0`、负数、超大值、`salaryMin>salaryMax` | 分别发送请求 | 200 | 当前代码未校验薪资，记录为潜在缺陷或需求缺口 | P1 | 边界/业务规则 |
| JOB-006 | 发布岗位 | POST `/job` | status 非法值 | 已认证企业 | `status=-1/2/字符串` | 发送请求 | 200 | 当前代码未显式校验，数字非法可能仍保存；字符串应失败 | P1 | 异常 |
| JOB-007 | 岗位列表 | GET `/job` | 查询全部岗位 | 已登录任意角色 | 无 | 发送请求 | 200 | `code=200,data` 为数组 | P0 | 正常 |
| JOB-008 | 岗位列表 | GET `/job` | 空数据 | 清空或隔离库无岗位 | 无 | 发送请求 | 200 | `code=200,data=[]` | P2 | 空数据 |
| JOB-009 | 企业岗位列表 | GET `/job/company` | 企业查看自己岗位 | 已登录企业 | 无 | 发送请求 | 200 | `code=200,data` 为数组，岗位属于当前企业 | P0 | 正常 |
| JOB-010 | 企业岗位列表 | GET `/job/company` | 求职者访问企业岗位列表 | 已登录求职者 | 无 | 发送请求 | 200 | `code=500,message=仅企业用户可访问` | P0 | 权限 |
| JOB-011 | 岗位详情 | GET `/job/{id}` | 查询存在岗位 | 岗位存在 | `id=jobId` | 发送请求 | 200 | `code=200,data.id=jobId` | P0 | 正常 |
| JOB-012 | 岗位详情 | GET `/job/{id}` | 岗位不存在 | 无 | `id=99999999` | 发送请求 | 200 | `code=500,message=岗位不存在` | P0 | 错误测试 |
| JOB-013 | 岗位详情 | GET `/job/{id}` | ID 边界 | 无 | `id=0,-1,Long最大值,abc` | 分别发送请求 | 200/500 | 数字不存在返回业务错误；非数字返回系统错误或参数转换错误 | P1 | 边界/类型错误 |
| JOB-014 | 修改岗位 | PUT `/job` | 企业修改自己的岗位 | 已认证企业且岗位属于自己 | body 含 `id,title` 等 | 发送请求后 GET 校验 | 200 | `code=200`，详情已更新 | P0 | 正常 |
| JOB-015 | 修改岗位 | PUT `/job` | 修改不存在岗位 | 已认证企业 | `id=99999999` | 发送请求 | 200 | `code=500,message=岗位不存在` | P0 | 错误测试 |
| JOB-016 | 修改岗位 | PUT `/job` | 修改其他企业岗位 | 已认证企业 A，岗位属于企业 B | 企业 A token + 企业 B 岗位 id | 发送请求 | 200 | `code=500,message=没有修改权限` | P0 | 越权 |
| JOB-017 | 删除岗位 | DELETE `/job/{id}` | 删除自己的测试岗位 | 已认证企业且隔离测试岗位存在 | `id=jobId` | 发送请求后再查详情 | 200 | 删除成功；再次查询岗位不存在 | P1 | 正常/高风险 |
| JOB-018 | 删除岗位 | DELETE `/job/{id}` | 删除其他企业岗位 | 已认证企业 A，岗位属于企业 B | `id=otherJobId` | 发送请求 | 200 | `code=500,message=没有删除权限` | P0 | 越权 |
| JOB-019 | 删除岗位 | DELETE `/job/{id}` | 重复删除 | 岗位第一次已删除 | 同一 `id` 再次 DELETE | 发送两次 | 200 | 第一次成功；第二次 `code=500,message=岗位不存在` | P1 | 幂等/重复 |
| JOB-020 | 岗位详情并发缓存 | GET `/job/{id}` | 多并发请求同一存在岗位 | Redis 可用，岗位存在 | `id=jobId` | 并发 50/100/200 次 | 200 | 均返回成功；服务无 `系统繁忙`；DB 查询次数合理 | P1 | 性能/并发 |
| JOB-021 | 岗位详情并发缓存 | GET `/job/{id}` | 多并发请求不存在岗位 | Redis 可用，岗位不存在 | `id=99999999` | 并发 50/100/200 次 | 200 | 均返回岗位不存在；缓存空值生效 | P1 | 性能/边界 |

## 5. 简历与教育经历模块

| 用例编号 | 接口名称 | 请求方法与路径 | 测试场景 | 前置条件 | 请求参数 | 测试步骤 | 预期状态码 | 预期响应结果 | 优先级 | 测试类型 |
|---|---|---|---|---|---|---|---|---|---|---|
| RES-001 | 新增简历 | POST `/resume` | 求职者新增简历 | 已登录求职者 | `title,description,fileUrl` | 发送请求 | 200 | `code=200` | P0 | 正常 |
| RES-002 | 新增简历 | POST `/resume` | 企业新增简历 | 已登录企业 | 合法 body | 发送请求 | 200 | `code=500,message=仅求职者可访问` | P0 | 权限 |
| RES-003 | 新增简历 | POST `/resume` | 缺少标题 | 已登录求职者 | 无 `title` | 发送请求 | 200 | `code=500,message=简历名称不能为空` | P0 | 参数缺失 |
| RES-004 | 新增简历 | POST `/resume` | 标题边界和特殊字符 | 已登录求职者 | 空白、1 字、超长、中文、emoji、SQL/XSS 字符串 | 分别发送请求 | 200 | 空白失败；其余按需求判断，特殊字符不应导致系统异常 | P1 | 边界/安全 |
| RES-005 | 简历列表 | GET `/resume` | 查询我的简历 | 已登录求职者 | 无 | 发送请求 | 200 | `code=200,data` 为数组 | P0 | 正常 |
| RES-006 | 简历列表 | GET `/resume` | 企业查询简历列表 | 已登录企业 | 无 | 发送请求 | 200 | `code=500,message=仅求职者可访问` | P0 | 权限 |
| RES-007 | 简历详情 | GET `/resume/{id}` | 查询自己的简历 | 已登录求职者且简历属于自己 | `id=resumeId` | 发送请求 | 200 | `code=200,data.id=resumeId` | P0 | 正常 |
| RES-008 | 简历详情 | GET `/resume/{id}` | 查询不存在简历 | 已登录求职者 | `id=99999999` | 发送请求 | 200 | `code=500,message=简历不存在` | P0 | 错误测试 |
| RES-009 | 简历详情 | GET `/resume/{id}` | 查询他人简历 | 已登录求职者 A，简历属于 B | `id=otherResumeId` | 发送请求 | 200 | `code=500,message=无权限访问` | P0 | 越权 |
| RES-010 | 修改简历 | PUT `/resume` | 修改自己的简历 | 已登录求职者 | body 含 `id,title` | 发送请求后查询详情 | 200 | `code=200`，字段已更新 | P0 | 正常 |
| RES-011 | 修改简历 | PUT `/resume` | 修改不存在简历 | 已登录求职者 | `id=99999999` | 发送请求 | 200 | `code=500,message=简历不存在` | P0 | 错误测试 |
| RES-012 | 修改简历 | PUT `/resume` | 修改他人简历 | 已登录求职者 A，简历属于 B | `id=otherResumeId` | 发送请求 | 200 | `code=500,message=无权限访问` | P0 | 越权 |
| RES-013 | 删除简历 | DELETE `/resume/{id}` | 删除自己的测试简历 | 已登录求职者且隔离简历存在 | `id=resumeId` | 发送 DELETE 后查询 | 200 | 删除成功；再次查询 `简历不存在` | P1 | 正常/高风险 |
| RES-014 | 删除简历 | DELETE `/resume/{id}` | 删除他人简历 | 已登录求职者 A，简历属于 B | `id=otherResumeId` | 发送请求 | 200 | `code=500,message=无权限访问` | P0 | 越权 |
| EDU-001 | 新增教育经历 | POST `/resume/{resumeId}/education` | 给自己的简历新增教育经历 | 已登录求职者且简历存在 | `schoolName,degree,major,startDate,endDate` | 发送请求 | 200 | `code=200` | P0 | 正常 |
| EDU-002 | 新增教育经历 | POST `/resume/{resumeId}/education` | 缺少必填字段 | 已登录求职者 | 缺 `schoolName/degree/major` | 分别发送请求 | 200 | `code=500,message` 对应必填项 | P0 | 参数缺失 |
| EDU-003 | 新增教育经历 | POST `/resume/{resumeId}/education` | 日期格式错误 | 已登录求职者 | `startDate=2026/01/01` | 发送请求 | 200 | `code=500` 或系统错误信息，不应产生脏数据 | P1 | 参数格式错误 |
| EDU-004 | 新增教育经历 | POST `/resume/{resumeId}/education` | 结束日期早于开始日期 | 已登录求职者 | `startDate > endDate` | 发送请求 | 200 | 当前代码未校验，记录为业务规则缺口 | P1 | 业务规则 |
| EDU-005 | 新增教育经历 | POST `/resume/{resumeId}/education` | 给他人简历新增教育经历 | 已登录求职者 A，简历属于 B | `resumeId=otherResumeId` | 发送请求 | 200 | `code=500,message=无权限访问` | P0 | 越权 |
| EDU-006 | 教育经历列表 | GET `/resume/{resumeId}/education` | 查看自己的教育经历 | 已登录求职者 | `resumeId` | 发送请求 | 200 | `code=200,data` 为数组，按排序返回 | P0 | 正常 |
| EDU-007 | 教育经历列表 | GET `/resume/{resumeId}/education` | 查看不存在简历教育经历 | 已登录求职者 | `resumeId=99999999` | 发送请求 | 200 | `code=500,message=简历不存在` | P0 | 错误测试 |
| EDU-008 | 修改教育经历 | PUT `/resume/education/{educationId}` | 修改自己的教育经历 | 已登录求职者 | 合法 body | 发送请求后列表校验 | 200 | `code=200`，字段已更新 | P0 | 正常 |
| EDU-009 | 修改教育经历 | PUT `/resume/education/{educationId}` | 修改不存在教育经历 | 已登录求职者 | `educationId=99999999` | 发送请求 | 200 | `code=500,message=教育经历不存在` | P0 | 错误测试 |
| EDU-010 | 修改教育经历 | PUT `/resume/education/{educationId}` | 修改他人教育经历 | 已登录求职者 A，教育经历属于 B | `educationId=otherEducationId` | 发送请求 | 200 | `code=500,message=无权限访问` | P0 | 越权 |
| EDU-011 | 删除教育经历 | DELETE `/resume/education/{educationId}` | 删除自己的教育经历 | 已登录求职者且隔离数据存在 | `educationId` | 发送请求后列表校验 | 200 | `code=200`，列表不再包含该记录 | P1 | 正常/高风险 |
| EDU-012 | 删除教育经历 | DELETE `/resume/education/{educationId}` | 重复删除 | 第一次已删除 | 同一 `educationId` | 再次 DELETE | 200 | `code=500,message=教育经历不存在` | P1 | 幂等/重复 |

## 6. 投递模块

| 用例编号 | 接口名称 | 请求方法与路径 | 测试场景 | 前置条件 | 请求参数 | 测试步骤 | 预期状态码 | 预期响应结果 | 优先级 | 测试类型 |
|---|---|---|---|---|---|---|---|---|---|---|
| APP-001 | 投递岗位 | POST `/application` | 求职者投递开放岗位 | 已登录求职者，岗位 `ACTIVE`，简历属于自己 | `jobId,resumeId` | 发送请求 | 200 | `code=200` | P0 | 正常 |
| APP-002 | 投递岗位 | POST `/application` | 企业用户投递岗位 | 已登录企业 | 合法 `jobId,resumeId` | 发送请求 | 200 | `code=500,message=仅求职者可访问` | P0 | 权限 |
| APP-003 | 投递岗位 | POST `/application` | 缺少 jobId | 已登录求职者 | 仅 `resumeId` | 发送请求 | 200 | `code=500,message=岗位ID不能为空` | P0 | 参数缺失 |
| APP-004 | 投递岗位 | POST `/application` | 缺少 resumeId | 已登录求职者 | 仅 `jobId` | 发送请求 | 200 | `code=500,message=简历ID不能为空` | P0 | 参数缺失 |
| APP-005 | 投递岗位 | POST `/application` | 岗位不存在 | 已登录求职者 | `jobId=99999999` | 发送请求 | 200 | `code=500,message=岗位不存在` | P0 | 错误测试 |
| APP-006 | 投递岗位 | POST `/application` | 岗位暂停招聘 | 岗位 `status=1` | `jobId=pausedJobId` | 发送请求 | 200 | `code=500,message=岗位已暂停招聘，暂不可投递` | P0 | 业务规则 |
| APP-007 | 投递岗位 | POST `/application` | 简历不存在 | 已登录求职者 | `resumeId=99999999` | 发送请求 | 200 | `code=500,message=简历不存在` | P0 | 错误测试 |
| APP-008 | 投递岗位 | POST `/application` | 使用他人简历投递 | 已登录求职者 A，简历属于 B | `resumeId=otherResumeId` | 发送请求 | 200 | `code=500,message=无权使用该简历` | P0 | 越权 |
| APP-009 | 投递岗位 | POST `/application` | 重复投递同一岗位 | 已完成一次投递 | 同一 `jobId` | 再次发送 | 200 | `code=500,message=请勿重复投递同一岗位` | P0 | 重复提交/幂等 |
| APP-010 | 我的投递 | GET `/application/mine` | 求职者查看自己的投递 | 已登录求职者 | 无 | 发送请求 | 200 | `code=200,data` 为数组，含 `statusText` | P0 | 正常 |
| APP-011 | 我的投递 | GET `/application/mine` | 企业访问我的投递 | 已登录企业 | 无 | 发送请求 | 200 | 当前代码未强制求职者角色，记录为权限边界缺口 | P1 | 权限 |
| APP-012 | 企业收到的投递 | GET `/application/my` | 已认证企业查看投递 | 已登录已认证企业 | 无 | 发送请求 | 200 | `code=200,data` 为数组，含岗位和简历标题 | P0 | 正常 |
| APP-013 | 企业收到的投递 | GET `/application/my` | 求职者访问企业投递列表 | 已登录求职者 | 无 | 发送请求 | 200 | `code=500,message=仅企业用户可访问` | P0 | 权限 |
| APP-014 | 投递详情 | GET `/application/{id}` | 求职者查看自己的投递详情 | 已登录求职者 | `id=applicationId` | 发送请求 | 200 | `code=200,data.id=applicationId,statusText` 存在 | P0 | 正常 |
| APP-015 | 投递详情 | GET `/application/{id}` | 企业查看自己岗位的投递详情 | 已认证企业 | `id=applicationId` | 发送请求 | 200 | `code=200,data` 存在 | P0 | 正常 |
| APP-016 | 投递详情 | GET `/application/{id}` | 查看不存在投递 | 已登录 | `id=99999999` | 发送请求 | 200 | `code=500,message=该投递不存在` | P0 | 错误测试 |
| APP-017 | 投递详情 | GET `/application/{id}` | 求职者查看他人投递 | 已登录求职者 A，投递属于 B | `id=otherApplicationId` | 发送请求 | 200 | `code=500,message=无权访问该投递` | P0 | 越权 |
| APP-018 | 投递详情 | GET `/application/{id}` | 企业查看其他企业岗位投递 | 企业 A，投递属于企业 B 岗位 | `id=otherApplicationId` | 发送请求 | 200 | `code=500,message=无权访问该投递` | P0 | 越权 |
| APP-019 | 修改投递状态 | PUT `/application/{id}/status` | 企业修改自己岗位投递状态 | 已认证企业 | `status=0/1/2/3/4` | 分别发送请求 | 200 | `code=200`，详情状态更新，`statusText` 正确 | P0 | 正常/边界 |
| APP-020 | 修改投递状态 | PUT `/application/{id}/status` | 状态边界非法 | 已认证企业 | `status=-1/5/null/字符串` | 分别发送请求 | 200 | `code=500,message` 为状态边界或类型错误 | P0 | 边界/类型错误 |
| APP-021 | 修改投递状态 | PUT `/application/{id}/status` | 求职者修改状态 | 已登录求职者 | `status=2` | 发送请求 | 200 | `code=500,message=仅企业用户可访问` | P0 | 权限 |
| APP-022 | 修改投递状态 | PUT `/application/{id}/status` | 企业修改其他企业投递 | 企业 A，投递属于企业 B | `id=otherApplicationId,status=2` | 发送请求 | 200 | `code=500,message=无权修改该投递` | P0 | 越权 |
| APP-023 | 修改投递状态 | PUT `/application/{id}/status` | 重复设置同一状态 | 已认证企业 | 连续两次 `status=2` | 发送两次 | 200 | 两次均成功或第二次无变化；数据不重复 | P1 | 幂等 |

## 7. 管理员模块

| 用例编号 | 接口名称 | 请求方法与路径 | 测试场景 | 前置条件 | 请求参数 | 测试步骤 | 预期状态码 | 预期响应结果 | 优先级 | 测试类型 |
|---|---|---|---|---|---|---|---|---|---|---|
| ADMIN-001 | 待审核企业列表 | GET `/admin/company/pending` | 管理员分页查询 | 已登录管理员 | `page=1,pageSize=10` | 发送请求 | 200 | `code=200,data.total/items` 存在 | P0 | 正常 |
| ADMIN-002 | 待审核企业列表 | GET `/admin/company/pending` | 分页边界 | 已登录管理员 | `page=1,pageSize=1/10/100` | 分别发送 | 200 | 返回数量不超过 pageSize | P1 | 边界 |
| ADMIN-003 | 待审核企业列表 | GET `/admin/company/pending` | 分页非法 | 已登录管理员 | `page=0,-1,abc,pageSize=0,-1,abc` | 分别发送 | 200/500 | 当前代码无显式校验，记录异常表现 | P1 | 参数错误 |
| ADMIN-004 | 待审核企业列表 | GET `/admin/company/pending` | 非管理员访问 | 求职者/企业 token | 无 | 发送请求 | 200 | `code=500,message=仅管理员可访问` | P0 | 权限 |
| ADMIN-005 | 公司列表 | GET `/admin/company` | 管理员查询全部公司 | 已登录管理员 | `page=1,pageSize=10` | 发送请求 | 200 | `code=200,data.total/items` 存在 | P0 | 正常 |
| ADMIN-006 | 公司列表 | GET `/admin/company` | 按状态筛选 | 已登录管理员 | `companyStatus=PENDING/APPROVED/REJECTED` | 分别发送 | 200 | 返回公司状态匹配筛选条件 | P0 | 筛选 |
| ADMIN-007 | 公司列表 | GET `/admin/company` | 非法状态筛选 | 已登录管理员 | `companyStatus=UNKNOWN/0/空格` | 分别发送 | 200/500 | 应返回参数绑定错误或业务错误，不应系统崩溃 | P1 | 参数格式错误 |
| ADMIN-008 | 公司详情 | GET `/admin/company/{id}` | 查询存在公司 | 已登录管理员 | `id=companyId` | 发送请求 | 200 | `code=200,data.id=companyId` | P0 | 正常 |
| ADMIN-009 | 公司详情 | GET `/admin/company/{id}` | 查询不存在公司 | 已登录管理员 | `id=99999999` | 发送请求 | 200 | `code=500,message=企业不存在` | P0 | 错误测试 |
| ADMIN-010 | 审核通过公司 | PUT `/admin/company/{id}/approve` | 管理员通过待审核企业 | 已登录管理员且测试公司 PENDING | `id=companyId` | 发送请求后查询详情 | 200 | `code=200,status=APPROVED` | P0 | 正常/高风险 |
| ADMIN-011 | 审核拒绝公司 | PUT `/admin/company/{id}/reject` | 管理员拒绝待审核企业 | 已登录管理员且测试公司 PENDING | `id=companyId` | 发送请求后查询详情 | 200 | `code=200,status=REJECTED` | P0 | 正常/高风险 |
| ADMIN-012 | 企业审核 | PUT `/admin/company/{id}/approve` | 非管理员审核 | 求职者/企业 token | `id=companyId` | 发送请求 | 200 | `code=500,message=仅管理员可访问` | P0 | 权限 |
| ADMIN-013 | 企业审核 | PUT `/admin/company/{id}/approve` | 审核不存在公司 | 已登录管理员 | `id=99999999` | 发送请求 | 200 | `code=500,message=企业不存在` | P0 | 错误测试 |
| ADMIN-014 | 企业审核 | PUT `/admin/company/{id}/approve` | 重复审核 | 公司已 APPROVED | 同一 `id` 再次 approve | 发送请求 | 200 | 当前代码会再次成功，状态保持 APPROVED；记录幂等行为 | P1 | 幂等 |
| ADMIN-015 | 用户列表 | GET `/admin/user` | 管理员查询全部用户 | 已登录管理员 | `page=1,pageSize=10` | 发送请求 | 200 | `code=200,data.total/items` 存在，响应不含密码 | P0 | 正常/安全 |
| ADMIN-016 | 用户列表 | GET `/admin/user` | 按角色筛选 | 已登录管理员 | `userRole=JOB_SEEKER/COMPANY/ADMIN` | 分别发送 | 200 | 返回用户角色匹配筛选条件 | P0 | 筛选 |
| ADMIN-017 | 用户列表 | GET `/admin/user` | 非法角色筛选 | 已登录管理员 | `userRole=USER/0/空格` | 发送请求 | 200/500 | 应返回参数绑定错误或系统错误，不应返回成功脏数据 | P1 | 参数格式错误 |
| ADMIN-018 | 用户详情 | GET `/admin/user/{id}` | 查询存在用户 | 已登录管理员 | `id=userId` | 发送请求 | 200 | `code=200,data.id=userId`，不含密码 | P0 | 正常/安全 |
| ADMIN-019 | 用户详情 | GET `/admin/user/{id}` | 查询不存在用户 | 已登录管理员 | `id=99999999` | 发送请求 | 200 | `code=500,message=用户不存在` | P0 | 错误测试 |
| ADMIN-020 | 用户详情 | GET `/admin/user/{id}` | 非管理员访问 | 求职者/企业 token | `id=userId` | 发送请求 | 200 | `code=500,message=仅管理员可访问` | P0 | 权限 |

## 8. 集合级测试与安全检查

| 用例编号 | 接口名称 | 请求方法与路径 | 测试场景 | 前置条件 | 请求参数 | 测试步骤 | 预期状态码 | 预期响应结果 | 优先级 | 测试类型 |
|---|---|---|---|---|---|---|---|---|---|---|
| COLL-001 | 全集合 | 全部请求 | 变量完整性 | 已选择 `Smart Recruit Local` | `host,token,*Id` | Runner 前检查变量 | 不适用 | 缺失变量应阻塞执行并列入报告 | P0 | 配置 |
| COLL-002 | 全集合 | 全部请求 | 统一响应结构 | 需返回 JSON 的接口 | 无 | 对成功/业务失败响应断言 | 200 | 含 `code,message,data` | P0 | 结构 |
| COLL-003 | 全集合 | 全部请求 | 响应时间基线 | 本地服务启动 | 无 | 执行非写接口 | 200 | 普通接口响应 `<1000ms`，缓存详情 `<500ms` 作为参考 | P2 | 性能 |
| COLL-004 | 全集合 | 全部请求 | Content-Type | JSON body 接口 | `Content-Type: application/json` | 发送请求 | 200 | 后端正确解析 JSON | P1 | Header |
| COLL-005 | 全集合 | 全部请求 | 敏感字段检查 | 用户/管理员查询接口 | 无 | 检查响应 JSON | 200 | 不返回 `password`、明文 token、数据库连接信息 | P0 | 安全 |
| COLL-006 | 全集合 | 全部写接口 | 重复提交 | 隔离测试数据 | 相同 body 连续提交 | 连续发送两次 | 200 | 注册/投递应阻止重复；修改类应保持一致；删除类第二次应业务失败 | P1 | 幂等/重复 |
| COLL-007 | 全集合 | 全部 ID 路径接口 | ID 类型错误 | 无 | `id=abc/null/0/-1/99999999` | 分别发送 | 200/500 | 不应返回成功脏数据，不应暴露异常栈 | P1 | 边界/错误 |
| COLL-008 | 全集合 | 字符串字段 | 特殊字符与超长输入 | 无 | 中文、空格、SQL 注入片段、XSS 片段、1000 字符 | 分别发送 | 200 | 不应出现系统异常或脚本执行，按业务规则成功/失败 | P1 | 安全/边界 |

## 9. 默认跳过的高风险请求

在确认测试数据隔离前，以下请求建议 Postman Runner 默认跳过或只在专用测试库执行：

| 请求 | 原因 |
|---|---|
| DELETE `/company` | 删除企业数据 |
| DELETE `/job/{id}` | 删除岗位数据，并影响投递关联 |
| DELETE `/resume/{id}` | 删除简历数据，并影响投递关联 |
| DELETE `/resume/education/{educationId}` | 删除教育经历 |
| PUT `/admin/company/{id}/approve` | 改变企业审核状态 |
| PUT `/admin/company/{id}/reject` | 改变企业审核状态 |
| PATCH `/user/password` | 改变登录凭据 |
| POST `/application` | 新增投递记录，重复投递影响后续测试 |
| PUT `/application/{id}/status` | 改变招聘流程状态 |

## 10. 执行顺序建议

1. 环境变量检查：`host`、三类账号、三类 token。
2. Auth 正常用例：注册/登录并提取 token。
3. 只读接口冒烟：用户信息、岗位列表/详情、简历列表、投递列表、管理员列表。
4. 参数校验与权限错误测试。
5. 写接口正常流程，只使用隔离测试数据。
6. 高风险删除、审核、改密、状态流转测试。
7. 重复提交、幂等、并发缓存测试。
8. 汇总报告并标记失败、跳过、阻塞原因。
