-- 对话表扩展：新增 favorite / pin 字段（收藏与置顶功能）
-- 创建时间: 2026-07-16

ALTER TABLE conversations ADD COLUMN IF NOT EXISTS favorite BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT false;
