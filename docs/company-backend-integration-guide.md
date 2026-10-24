# 企业资料后端联调契约

更新：2026-10-21。用于企业资料、办公照片、资质材料、工商核验及材料审核的前端开发。
逐字段限制见 [字段表](company-profile-field-contract.md)，回归结果见 [收尾验收](company-close-regression-2026-10-21.md)。

## 一、接口与身份

除企业注册外均要求登录；公开主页中的“公开”指向求职者展示，未增加匿名白名单。
企业本人接口由登录账号确定企业归属，不允许传 companyId 切换归属。

| 业务 | 接口 | 身份 | 请求 / data |
| --- | --- | --- | --- |
| 企业注册 | POST /company/register | 匿名可用 | CompanyRegisterDTO / companyId、status=PENDING |
| 本人资料 | GET /company | 企业，准入不限 | 无 / CompanyProfileVO |
| 完整保存 | PUT /company | 企业，准入不限 | CompanyProfileUpdateDTO / null |
| 本人主页预览 | GET /company/preview | 企业，准入不限 | 无 / CompanyPublicProfileVO |
| 企业主页 | GET /company/{id}/profile | 求职者，目标企业已准入 | 无 / CompanyPublicProfileVO |
| 企业在招岗位 | GET /company/{id}/jobs | 求职者，目标企业已准入 | page、pageSize / PageResult<JobListVO> |
| 办公照片列表 | GET /company/photos | 企业本人 | 无 / CompanyPhotoVO[] |
| 上传办公照片 | POST /company/photos | 企业本人 | multipart：file / CompanyPhotoVO |
| 删除办公照片 | DELETE /company/photos/{photoId} | 企业本人 | 无 / null |
| 照片内容 | GET /company/photos/{photoId}/file | 本企业，或求职者且目标已准入 | 无 / 图片字节 |
| 资质列表 | GET /company/qualifications | 企业本人 | 无 / CompanyQualificationVO[] |
| 上传资质 | POST /company/qualifications | 企业本人 | multipart：file、qualificationType / CompanyQualificationVO |
| 企业资质原件 | GET /company/qualifications/{qualificationId}/file | 企业本人，材料未移除 | 无 / 附件字节 |
| 移除资质 | DELETE /company/qualifications/{qualificationId} | 企业本人 | 无 / null |
| 工商审核历史 | GET /company/registration-reviews | 企业本人 | reviewStatus、page、pageSize / 分页历史 |
| 材料审核历史 | GET /company/qualification-reviews | 企业本人 | reviewStatus、page、pageSize / 分页历史 |
| 工商审核队列 | GET /admin/company/registration-reviews | 管理员 | reviewStatus、page、pageSize / 分页摘要 |
| 工商审核详情 | GET /admin/company/registration-reviews/{reviewId} | 管理员 | 无 / 详情及八字段快照 |
| 工商审核决定 | PUT /admin/company/registration-reviews/{reviewId}/decision | 管理员 | decision、registrationVersion、reviewComment / null |
| 材料审核队列 | GET /admin/company/qualification-reviews | 管理员 | reviewStatus、qualificationType、page、pageSize / 分页摘要 |
| 材料审核详情 | GET /admin/company/qualification-reviews/{reviewId} | 管理员 | 无 / 详情及材料摘要快照 |
| 审核原件下载 | GET /admin/company/qualification-reviews/{reviewId}/file | 管理员 | 无 / 附件字节，支持追溯已移除材料 |
| 材料审核决定 | PUT /admin/company/qualification-reviews/{reviewId}/decision | 管理员 | decision、reviewComment / null |

管理员原有准入接口仍独立使用：GET /admin/company/pending、GET /admin/company、
GET /admin/company/{id}、PUT /admin/company/{id}/approve、PUT /admin/company/{id}/reject。
准入影响招聘及对求职者的展示；工商核验、材料审核不自动改变准入。

旧 POST /company、DELETE /company 已永久停用：登录后 HTTP 410，业务码 500，data=null。
企业账号与企业记录统一通过注册事务创建；当前不提供物理删除或企业注销替代入口。

## 二、JSON、分页及失败处理

- JSON 成功：HTTP 200，`{code:200,message:...,data:...}`；普通业务拒绝：HTTP 200、code=500、data=null。
- 未登录 / 令牌无效：HTTP 401；停用入口：HTTP 410。客户端同时处理 HTTP 状态和业务码。
- 分页读取 `data.items`、`data.total`；默认 page=1、pageSize=10，最大 pageSize=100。
- 图片与附件成功返回二进制，不能统一按 Result.code 解析。
- Blob 请求失败可能返回 JSON Blob；结合 HTTP 状态和 Content-Type 解析错误，不直接保存为图片或 PDF。
- 图片 URL 仍需 Bearer 令牌；用带认证的请求获取 Blob 并生成对象 URL，释放不用的对象 URL。
- multipart 让浏览器设置带 boundary 的 Content-Type；只传 file 和约定的业务参数。

## 三、资料保存规则

PUT 是完整表单替换。七个基础必填项为 name、fullName、industry、employeeSize、city、address、description；
另需 benefits（可为 []）及 profileVersion。旧前端只提交 name、description、logo、city、address 已不能完成保存。

```json
{
  "name": "示例品牌",
  "fullName": "示例有限公司",
  "industry": "SOFTWARE",
  "employeeSize": "FROM_20_TO_99",
  "city": "上海",
  "address": "实际办公地址",
  "description": "企业介绍",
  "benefits": [],
  "profileVersion": 0
}
```

示例版本仅用于新注册企业，编辑已有企业须提交 GET 返回的实际版本。
可选字段 null / 遗漏会清空，不是局部 PATCH。字符串去首尾空白，空白归一为 null。
保存成功重新 GET；版本冲突提示用户刷新或重新整理修改，不自动用新版本覆盖重试。
不可编辑 userId、status、审核状态、审核版本、logo 等系统字段。
日期为 yyyy-MM-dd，时间字段为 LocalTime 格式；工作开始和结束时间同时填写或同时清空。
枚举使用字段表所列英文代码，不能传中文说明或数字序号。

## 四、审核与展示

- 工商八字段真实变化才产生新版本和审核快照，普通资料变更不重置工商审核。
- 工商 decision=APPROVE / REJECT，须携带详情返回的 registrationVersion；REJECT 必须有意见，最多 500 字。
- 材料决定同样使用 APPROVE / REJECT、驳回必填意见；不传工商版本。
- 两类审核只允许处理当前可审核的待审记录；详情的 reviewable 用于界面按钮，服务端仍重新检查。
- 旧工商通过版本不能冒充当前版本通过；企业修改工商资料后 registrationVerified=false。
- businessLicenseVerified 由“未移除且已通过的营业执照是否存在”计算。
- 企业移除资质后本人不能下载，管理员仍可查看原审核证据；移除不是重新驳回。
- 公开响应 photos 按 sortOrder、id 排序，暂无数据为 []，photoCount 与列表长度一致。
- 管理 VO 有版本、待完善字段及准入状态；公开 VO 只含约定展示字段，不能用实体或管理 VO 替代。

## 五、文件限制与本期边界

办公照片：JPG / PNG，每张不超过 3 MiB，最多 6 张。
资质：JPG / PNG / PDF，每份不超过 10 MiB。服务端检查真实文件内容，不只看扩展名。
文件位于私有存储，响应不暴露磁盘路径；下载含 no-store、nosniff 和附件头。

本期采用单实例本地存储；企业注销、对象存储 / 多实例共享存储、证据保留期限、Logo 专用上传不在本轮范围。
本轮后端收尾不代表前端完成：下一阶段先开发企业资料完整表单、版本冲突提示、照片与资质管理，
再开发审核反馈和求职者企业主页，并执行浏览器联调。

详细实现：[资料](company-profile-api-guide.md)、[文件](company-files-api-guide.md)、
[工商审核](company-registration-review-api-guide.md)、[材料审核](company-qualification-review-api-guide.md)。
