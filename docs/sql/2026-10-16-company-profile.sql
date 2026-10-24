-- 企业资料增量迁移，MySQL 8.0.13，执行前备份 company 结构和数据。
-- 开发库 smart_recruit 已于 2026-10-16 执行；已迁移环境不要重复执行。
-- 只适用于原 company 的 10 列结构、四张子表尚不存在的环境。
-- 不重复执行；DDL 隐式提交，失败后根据实际结构逐项核对，不自动删表删列。
-- 保留原企业准入 status、账号唯一索引和岗位外键；不补造工商及资质核验记录。
SELECT DATABASE() AS current_database, VERSION() AS mysql_version;
SHOW CREATE TABLE company;

ALTER TABLE company
 MODIFY COLUMN description TEXT NULL COMMENT '企业简介',
 ADD COLUMN full_name VARCHAR(200) NULL COMMENT '公司全称',
 ADD COLUMN industry VARCHAR(32) NULL COMMENT '行业代码',
 ADD COLUMN employee_size VARCHAR(32) NULL COMMENT '人员规模代码',
 ADD COLUMN financing_stage VARCHAR(32) NULL COMMENT '融资阶段代码',
 ADD COLUMN company_nature VARCHAR(32) NULL COMMENT '企业性质代码',
 ADD COLUMN website VARCHAR(255) NULL COMMENT '企业官网',
 ADD COLUMN commute_description VARCHAR(500) NULL COMMENT '交通与到访说明',
 ADD COLUMN founded_date DATE NULL COMMENT '成立日期',
 ADD COLUMN legal_representative VARCHAR(100) NULL COMMENT '法定代表人',
 ADD COLUMN registered_capital VARCHAR(100) NULL COMMENT '注册资本及币种展示文本',
 ADD COLUMN business_status VARCHAR(32) NULL COMMENT '经营状态代码',
 ADD COLUMN credit_code VARCHAR(18) NULL COMMENT '统一社会信用代码',
 ADD COLUMN registered_address VARCHAR(255) NULL COMMENT '注册地址',
 ADD COLUMN business_scope TEXT NULL COMMENT '经营范围',
 ADD COLUMN work_start_time TIME NULL COMMENT '上班时间',
 ADD COLUMN work_end_time TIME NULL COMMENT '下班时间',
 ADD COLUMN rest_policy VARCHAR(32) NULL COMMENT '休息制度代码',
 ADD COLUMN overtime_policy VARCHAR(32) NULL COMMENT '加班情况代码',
 ADD COLUMN work_notes TEXT NULL COMMENT '制度与福利补充说明',
 ADD COLUMN product_name VARCHAR(200) NULL COMMENT '产品或品牌名称',
 ADD COLUMN product_description TEXT NULL COMMENT '产品介绍',
 ADD COLUMN team_name VARCHAR(200) NULL COMMENT '团队或管理者名称',
 ADD COLUMN team_role VARCHAR(100) NULL COMMENT '介绍类型或管理者职务',
 ADD COLUMN team_bio TEXT NULL COMMENT '团队或管理者简介',
 ADD COLUMN profile_version BIGINT NOT NULL DEFAULT 0 COMMENT '完整资料版本',
 ADD COLUMN registration_version BIGINT NOT NULL DEFAULT 0 COMMENT '当前工商资料版本',
 ADD COLUMN registration_review_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED'
 COMMENT 'UNVERIFIED/PENDING/VERIFIED/REJECTED',
 ADD COLUMN verified_registration_version BIGINT NULL COMMENT '最近核验通过的工商版本';

CREATE TABLE company_benefit (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '福利记录ID',
 company_id BIGINT NOT NULL COMMENT '所属企业ID',
 benefit_code VARCHAR(32) NOT NULL COMMENT '福利代码',
 sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
 PRIMARY KEY (id),
 UNIQUE KEY uk_company_benefit (company_id, benefit_code),
 KEY idx_company_benefit_order (company_id, sort_order, id),
 CONSTRAINT fk_company_benefit_company FOREIGN KEY (company_id)
 REFERENCES company(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业福利';

CREATE TABLE company_photo (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '办公照片ID',
 company_id BIGINT NOT NULL COMMENT '所属企业ID',
 storage_key VARCHAR(512) NOT NULL COMMENT '内部存储引用',
 original_filename VARCHAR(255) NOT NULL COMMENT '原文件名',
 content_type VARCHAR(100) NOT NULL COMMENT '服务端确认的文件类型',
 file_size BIGINT NOT NULL COMMENT '文件大小，单位字节',
 sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_company_photo_storage (storage_key),
 KEY idx_company_photo_order (company_id, sort_order, id),
 CONSTRAINT fk_company_photo_company FOREIGN KEY (company_id)
 REFERENCES company(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业办公照片';

CREATE TABLE company_qualification (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '资质材料ID',
 company_id BIGINT NOT NULL COMMENT '所属企业ID',
 qualification_type VARCHAR(32) NOT NULL COMMENT 'BUSINESS_LICENSE/INDUSTRY_CERTIFICATE/OTHER',
 storage_key VARCHAR(512) NOT NULL COMMENT '私有文件存储引用',
 original_filename VARCHAR(255) NOT NULL COMMENT '原文件名',
 content_type VARCHAR(100) NOT NULL COMMENT '服务端确认的文件类型',
 file_size BIGINT NOT NULL COMMENT '文件大小，单位字节',
 review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
 deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已移除',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (id),
 UNIQUE KEY uk_company_qualification_storage (storage_key),
 UNIQUE KEY uk_qualification_company_id (company_id, id),
 KEY idx_company_qualification_list (company_id, deleted, create_time, id),
 CONSTRAINT fk_company_qualification_company FOREIGN KEY (company_id)
 REFERENCES company(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业私有资质材料';

CREATE TABLE company_review_record (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
 company_id BIGINT NOT NULL COMMENT '所属企业ID',
 review_type VARCHAR(32) NOT NULL COMMENT 'REGISTRATION/QUALIFICATION',
 qualification_id BIGINT NULL COMMENT '材料审核对应的材料ID',
 registration_version BIGINT NULL COMMENT '工商审核对应的资料版本',
 snapshot_json JSON NOT NULL COMMENT '提交审核时的资料快照',
 review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
 reviewer_user_id BIGINT NULL COMMENT '实际审核人ID',
 review_comment VARCHAR(500) NULL COMMENT '审核意见',
 submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_at DATETIME NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_company_registration_review (company_id, review_type, registration_version),
 UNIQUE KEY uk_company_qualification_review (qualification_id),
 KEY idx_company_review_queue (review_type, review_status, submitted_at, id),
 KEY idx_company_review_history (company_id, submitted_at, id),
 KEY idx_company_review_qualification (company_id, qualification_id),
 CONSTRAINT fk_company_review_company FOREIGN KEY (company_id)
 REFERENCES company(id) ON DELETE RESTRICT,
 CONSTRAINT fk_company_review_qualification FOREIGN KEY (company_id, qualification_id)
 REFERENCES company_qualification(company_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业工商与材料审核记录';

-- MySQL 8.0.13 不执行 CHECK，审核对象组合、大小上限、状态迁移均由后续 Service 校验。
SHOW CREATE TABLE company;
SHOW CREATE TABLE company_benefit;
SHOW CREATE TABLE company_photo;
SHOW CREATE TABLE company_qualification;
SHOW CREATE TABLE company_review_record;
SELECT status, COUNT(*) AS company_count FROM company GROUP BY status;
