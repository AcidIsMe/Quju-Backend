-- 为已有数据库添加评分字段（如 reviews 表已存在）
ALTER TABLE reviews ADD COLUMN rating TINYINT UNSIGNED DEFAULT NULL COMMENT '评分 1-5' AFTER content;
