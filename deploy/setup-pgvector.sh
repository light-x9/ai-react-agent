#!/bin/bash
# ============================================
# 启动 PgVector Docker 容器
# ============================================
# 端口: 5433（避开本地可能存在的 PostgreSQL 5432）
# 数据库: react_agent
# ============================================

CONTAINER_NAME="ai-pgvector"
PG_PASSWORD="your_db_password_here"   # ← 改成你的密码（和 start.sh .env 保持一致）
PG_PORT="5433"

# 如果容器已存在就先删掉
docker stop $CONTAINER_NAME 2>/dev/null
docker rm $CONTAINER_NAME 2>/dev/null

docker run -d \
  --name $CONTAINER_NAME \
  -e POSTGRES_PASSWORD=$PG_PASSWORD \
  -e POSTGRES_DB=react_agent \
  -p $PG_PORT:5432 \
  --restart unless-stopped \
  pgvector/pgvector:pg16

echo "=== PgVector 启动中 ==="
sleep 3

if docker ps | grep -q $CONTAINER_NAME; then
  echo "✅ PgVector 启动成功"
  echo "   容器名: $CONTAINER_NAME"
  echo "   端口: $PG_PORT"
  echo "   数据库: react_agent"
  echo "   账号: postgres"
  echo "   密码: $PG_PASSWORD"
else
  echo "❌ 启动失败，查看日志："
  docker logs $CONTAINER_NAME
fi
