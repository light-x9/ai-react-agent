#!/bin/bash
# ============================================
# 停止 ai-react-agent 后端
# ============================================

PID_FILE="/opt/ai-react-agent/app.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "⚠️  PID 文件不存在，尝试通过端口查找..."
  PID=$(lsof -ti:8123 2>/dev/null || ss -tlnp | grep 8123 | grep -oP 'pid=\K[0-9]+')
  if [ -z "$PID" ]; then
    echo "❌ 未找到运行中的进程"
    exit 1
  fi
else
  PID=$(cat $PID_FILE)
fi

if kill -0 $PID 2>/dev/null; then
  echo "停止进程 (PID: $PID)..."
  kill $PID
  sleep 3
  # 十秒后强制杀
  if kill -0 $PID 2>/dev/null; then
    echo "进程未响应，强制停止"
    kill -9 $PID
  fi
  rm -f $PID_FILE
  echo "✅ 已停止"
else
  echo "⚠️  进程 $PID 已不存在"
  rm -f $PID_FILE
fi
