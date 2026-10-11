-- Smart Recruit 岗位操作记录：MySQL 8.0.13，InnoDB，job.id 为有符号 BIGINT。
-- 先确认数据库及父表结构。DDL 会隐式提交；在新代码启动之前创建该表。
-- 不重复执行、不覆盖既有表，不补造历史事件，也不修改原有岗位和投递数据。
SELECT DATABASE() AS current_schema, VERSION() AS mysql_version;
SHOW CREATE TABLE job;
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'job_activity';

CREATE TABLE job_activity (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作记录ID',
    job_id BIGINT NOT NULL COMMENT '所属岗位ID',
    operator_user_id BIGINT NOT NULL COMMENT '实际操作者ID',
    operator_name VARCHAR(100) NOT NULL COMMENT '操作者展示名称快照',
    operation VARCHAR(32) NOT NULL COMMENT 'CREATE_DRAFT/PUBLISH/UPDATE/PAUSE/RESUME/END',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '实际操作时间',
    PRIMARY KEY (id),
    KEY idx_job_activity_job_time (job_id, create_time, id),
    CONSTRAINT fk_job_activity_job FOREIGN KEY (job_id)
        REFERENCES job(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位操作时间线';

-- 验收结构。草稿删除级联清理展示记录，已发布岗位仍由 Service 禁止物理删除。
SHOW CREATE TABLE job_activity;
SELECT COUNT(*) AS activity_count FROM job_activity;
-- 旧岗位无记录时返回空列表；从新代码启用后积累真实业务记录。
