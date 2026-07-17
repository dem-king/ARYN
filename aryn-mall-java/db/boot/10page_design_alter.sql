USE aryn_boot;

ALTER TABLE page_design ADD COLUMN page_type char(2) NOT NULL DEFAULT '0' COMMENT '页面类型：0.微页面；1.首页；' AFTER page_content;

UPDATE page_design SET page_type = '1' WHERE home_status = '1';
