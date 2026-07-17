USE aryn_promotion;

ALTER TABLE page_design
    ADD COLUMN draft_revision bigint NOT NULL DEFAULT 0 COMMENT '草稿修订号' AFTER page_content,
    ADD COLUMN schema_version int NOT NULL DEFAULT 1 COMMENT '装修协议版本' AFTER draft_revision,
    ADD COLUMN published_version_id varchar(32) NULL COMMENT '当前发布版本ID' AFTER schema_version,
    ADD COLUMN published_status char(2) NOT NULL DEFAULT '0' COMMENT '发布状态：0.未发布；1.已发布；' AFTER published_version_id,
    ADD COLUMN published_at datetime NULL COMMENT '发布时间' AFTER published_status,
    ADD COLUMN legacy_content_backup longtext NULL COMMENT '旧版装修内容备份' AFTER published_at;

CREATE TABLE page_design_version (
    id varchar(32) NOT NULL COMMENT '主键',
    page_design_id varchar(32) NOT NULL COMMENT '页面ID',
    version_no int NOT NULL COMMENT '版本号',
    schema_version int NOT NULL DEFAULT 1 COMMENT '装修协议版本',
    page_name varchar(50) NOT NULL COMMENT '发布时页面名称',
    page_type char(2) NOT NULL COMMENT '页面类型：0.微页面；1.首页；',
    page_content longtext NOT NULL COMMENT '发布页面内容',
    publish_remark varchar(255) NULL COMMENT '发布备注',
    publish_by varchar(60) NULL COMMENT '发布人',
    published_at datetime NOT NULL COMMENT '发布时间',
    create_by varchar(60) NULL COMMENT '创建人',
    create_time datetime NULL COMMENT '创建时间',
    del_flag char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    tenant_id varchar(32) NOT NULL COMMENT '租户ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_page_design_tenant_version (tenant_id, page_design_id, version_no),
    KEY idx_page_design_published_at (page_design_id, published_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '页面装修发布版本表';

CREATE TABLE page_design_template (
    id varchar(32) NOT NULL COMMENT '主键',
    template_name varchar(100) NOT NULL COMMENT '模板名称',
    template_type char(2) NOT NULL DEFAULT '0' COMMENT '模板类型：0.页面；1.组件组合；',
    page_type char(2) NOT NULL DEFAULT '2' COMMENT '页面类型：0.微页面；1.首页；2.通用；',
    template_content longtext NOT NULL COMMENT '模板内容',
    schema_version int NOT NULL DEFAULT 2 COMMENT '装修协议版本',
    system_flag char(2) NOT NULL DEFAULT '0' COMMENT '系统模板：0.否；1.是；',
    status char(2) NOT NULL DEFAULT '0' COMMENT '状态：0.正常；1.停用；',
    sort int NOT NULL DEFAULT 0 COMMENT '排序',
    create_by varchar(60) NULL COMMENT '创建人',
    update_by varchar(60) NULL COMMENT '修改人',
    create_time datetime NULL COMMENT '创建时间',
    update_time datetime NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag char(2) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0.显示；1.隐藏；',
    tenant_id varchar(32) NOT NULL COMMENT '租户ID',
    PRIMARY KEY (id),
    KEY idx_page_design_template_tenant (tenant_id, template_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '页面装修模板表';

UPDATE page_design
SET legacy_content_backup = page_content,
    published_status = CASE WHEN status = '0' THEN '1' ELSE '0' END,
    published_at = CASE WHEN status = '0' THEN COALESCE(update_time, create_time, NOW()) ELSE NULL END;

INSERT INTO page_design_version (
    id, page_design_id, version_no, schema_version, page_name, page_type, page_content,
    publish_remark, publish_by, published_at, create_by, create_time, del_flag, tenant_id
)
SELECT CONCAT('v1_', id), id, 1, 1, page_name, page_type, page_content,
       '历史页面初始化', COALESCE(update_by, create_by), COALESCE(update_time, create_time, NOW()),
       create_by, COALESCE(update_time, create_time, NOW()), '0', tenant_id
FROM page_design
WHERE status = '0' AND del_flag = '0';

UPDATE page_design
SET published_version_id = CONCAT('v1_', id)
WHERE published_status = '1' AND del_flag = '0';
