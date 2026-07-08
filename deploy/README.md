# 部署指南 - 阿里云 ECS

按端口分离方案：
- `http://IP/` → 旧 exam_system（不动）
- `http://IP:8081/` → 新项目 ai-react-agent

---

## 文件清单

| 文件 | 用途 |
|------|------|
| `nginx-ai-react-agent.conf` | Nginx 站点配置文件，监听 8081 |
| `start.sh` | 启动后端 JAR（带环境变量） |
| `stop.sh` | 停止后端 |
| `setup-pgvector.sh` | Docker 方式安装 PgVector 数据库 |
| `init-db.sql` | 创建数据库的 SQL |

---

## 快速部署步骤

### 1. 服务器准备

```bash
# 安装 JDK 21
sudo apt update
sudo apt install -y openjdk-21-jdk

# 安装 Nginx
sudo apt install -y nginx

# 安装 Docker（用 Docker 起 PgVector）
sudo apt install -y docker.io
sudo systemctl enable docker
sudo systemctl start docker

# 安装 Node.js（构建 MCP server 用，可选）
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
```

### 2. 数据库初始化

```bash
# 启动 PgVector 容器
bash setup-pgvector.sh

# 创建数据库
docker exec -i pgvector psql -U postgres -c "CREATE DATABASE react_agent;"
```

### 3. 上传文件并启动后端

```bash
# 在本地用 scp 上传 JAR
scp -i your-key.pem backend/target/react-agent-0.0.1-SNAPSHOT.jar root@IP:/opt/ai-react-agent/app.jar

# 在服务器上启动
cd /opt/ai-react-agent
vim start.sh  # 改环境变量
bash start.sh
```

### 4. 配置 Nginx 并启动前端

```bash
# 上传前端打包产物
scp -r frontend/dist/* root@IP:/var/www/ai-react-agent/

# 放置 nginx 配置
sudo cp nginx-ai-react-agent.conf /etc/nginx/sites-available/ai-react-agent
sudo ln -s /etc/nginx/sites-available/ai-react-agent /etc/nginx/sites-enabled/ai-react-agent

# 检查配置 + 重启
sudo nginx -t
sudo systemctl reload nginx
```

### 5. 验证

浏览器访问 `http://IP:8081/` 应该能看到前端页面，能注册登录即可。
