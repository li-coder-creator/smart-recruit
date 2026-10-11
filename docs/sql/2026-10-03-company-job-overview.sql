-- 企业招聘总览依赖的投递面试历史标记。
-- 开发库 smart_recruit 已于 2026-10-03 执行；此脚本供其他未迁移环境使用。
-- 不要在已存在该列的数据库重复执行。
-- 执行前确认目标库并备份 job_application；MySQL DDL 会隐式提交。
-- true=曾进入面试，false=确认未进入面试，NULL=旧记录缺少历史证据。
-- 不推断录用或拒绝记录经历过面试；允许跳过面试直接录用。

SELECT DATABASE() AS current_schema;
SHOW COLUMNS FROM job_application LIKE 'has_interviewed';
SELECT status, COUNT(*) AS application_count
FROM job_application
GROUP BY status;

-- 在暂停投递新增/状态修改写入的窗口中，依次执行以下三步。
-- 先保留旧记录为未知，当前处于面试的记录有明确的历史证据。
ALTER TABLE job_application
    ADD COLUMN has_interviewed TINYINT(1) NULL DEFAULT NULL
    COMMENT '是否曾进入面试：1是，0否，NULL旧记录未知';

UPDATE job_application
SET has_interviewed = TRUE, update_time = update_time
WHERE status = 2; -- ApplicationStatus.INTERVIEW，数据库迁移中的既定状态码。

-- 旧记录的 NULL 保留；之后新增投递默认未进入面试，Mapper INSERT 也显式写 FALSE。
ALTER TABLE job_application
    ALTER COLUMN has_interviewed SET DEFAULT FALSE;

-- 其他旧记录只有具备可核实的历史证据时，才按投递 ID 更新为 TRUE/FALSE。
-- 只要统计范围内还有未知记录，总览累计面试数与转化率返回 NULL。
SELECT status, has_interviewed, COUNT(*) AS application_count
FROM job_application
GROUP BY status, has_interviewed;
