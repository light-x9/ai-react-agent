#!/bin/bash
# ============================================
# 启动 ai-react-agent 后端
# ============================================
# 前提:
#   1. .env 文件已配置（cp .env.example .env && vim .env）
#   2. app.jar 在 /opt/ai-react-agent/
# ============================================

# ---------- 应用目录 ----------
APP_DIR="/opt/ai-react-agent"
JAR_FILE="$APP_DIR/app.jar"
LOG_FILE="$APP_DIR/app.log"
PID_FILE="$APP_DIR/app.pid"
ENV_FILE="$APP_DIR/.env"

# ---------- 加载环境变量 ----------
if [ ! -f "$ENV_FILE" ]; then
  echo "❌ 找不到 .env 文件！"
  echo "   请先: cd $APP_DIR && cp .env.example .env && vim .env"
  exit 1
fi
set -a
source $ENV_FILE
set +a

echo "=== 环境变量已加载 (Profile: $SPRING_PROFILES_ACTIVE) ==="

# ---------- 启动 ----------
cd $APP_DIR

# 如果已经启动就先停掉
if [ -f "$PID_FILE" ]; then
  OLD_PID=$(cat $PID_FILE)
  if kill -0 $OLD_PID 2>/dev/null; then
    echo "停止旧进程 (PID: $OLD_PID)"
    kill $OLD_PID
    sleep 3
  fi
  rm -f $PID_FILE
fi

# 后台启动
# -Xmx512m: 服务器内存 1.6GB，限制堆大小避免 OOM
# -Xms256m: 初始堆大小
nohup java -Xmx512m -Xms256m -jar $JAR_FILE > $LOG_FILE 2>&1 &
NEW_PID=$!
echo $NEW_PID > $PID_FILE

echo "=== 启动中，等待 Spring Boot 初始化（约 15 秒）... ==="
sleep 15

# 检查是否存活
if kill -0 $NEW_PID 2>/dev/null; then
  echo "✅ 启动成功 (PID: $NEW_PID)"
  echo "   日志: tail -f $LOG_FILE"
  echo "   端口: 8123"
else
  echo "❌ 启动失败，查看日志："
  tail -30 $LOG_FILE
fi
