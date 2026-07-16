-- 用户表扩展：新增 avatar / bio 字段（个人中心功能）
-- 创建时间: 2026-07-16

ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio VARCHAR(300);
