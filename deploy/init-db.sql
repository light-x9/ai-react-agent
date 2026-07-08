-- ============================================
-- ai-react-agent 数据库初始化
-- ============================================
-- 执行方法:
--   docker exec -i pgvector psql -U postgres react_agent < init-db.sql
--
-- 注意: pgvector/pgvector 镜像已自带 vector 扩展
--       建表由 Spring Boot 自动完成（开发环境 initialize-schema=true）
--       生产环境需手动管理（initialize-schema=false）
-- ============================================

-- 检查 vector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 验证
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
