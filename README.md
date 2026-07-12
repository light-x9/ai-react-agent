# LightManus - AI ReAct 智能体平台

> 基于 **ReAct（Reasoning + Acting）** 模式的自主规划 AI 智能体应用。
> 智能体可自主推理、调用多种工具完成复杂任务，支持流式响应与知识库检索。

---

## 截图

### 🏠 首页
智能体应用入口,展示对话交互界面与基础功能导航。
<img width="1496" height="1303" alt="屏幕截图 2026-07-04 154218" src="https://github.com/user-attachments/assets/f0fc0483-dc8c-42c9-8a05-88ac1ebdc3ef" />



### 💬 智能体对话界面
支持多轮连续对话,基于会话 ID 持久化上下文;调用外部工具（联网搜索、地图、PDF生成等10+工具）完成复杂任务,回复内容通过 SSE 流式推送。

<img width="1508" height="1218" alt="屏幕截图 2026-07-04 201446" src="https://github.com/user-attachments/assets/e27645bd-7019-4c65-abce-570146573c36" />


<img width="1524" height="1306" alt="屏幕截图 2026-07-04 160518" src="https://github.com/user-attachments/assets/87d1b9d6-f9ec-46bd-80b7-31cab52b7965" />




### 📚 知识库检索
支持上传文档构建个人知识库,基于 PGvector 向量数据库实现语义搜索,智能体可结合知识库内容回答问题,而非仅依赖通用知识。
<img width="497" height="321" alt="屏幕截图 2026-07-04 201639" src="https://github.com/user-attachments/assets/79aef1b8-23c6-476f-942f-8da268513244" />

<img width="1487" height="908" alt="屏幕截图 2026-07-04 201943" src="https://github.com/user-attachments/assets/cef2585c-2f92-4ee6-85a2-8f2652845af8" />

---

## 功能亮点

- **ReAct 推理循环** — Thought -> Action -> Observation 多步推理链路，工具调用与思考交替进行
- **多工具协同** — 8 个本地工具 + MCP 协议扩展（图片搜索、高德地图），支持能力开关按需装配
- **RAG 知识库** — 基于 PGvector 的向量检索，支持文档上传、预览、批量删除与语义搜索
- **对话附件分析** — 对话中随时上传 PDF / DOCX / XLSX 等文件，AI 直接按原文回答（一次性，非知识库）
- **流式响应** — 基于 SSE（Server-Sent Events）实时推送推理步骤，15s 心跳防超时
- **MCP 协议支持** — 通过 Model Context Protocol 接入外部工具，失败自动降级为纯本地工具
- **用户认证** — JWT + Spring Security 无状态认证，BCrypt 密码加密
- **多租户隔离** — 会话、知识库、文件均按 userId 隔离，防止跨用户数据泄漏
- **对话持久化** — 会话与消息存储到 PostgreSQL，支持跨设备同步
- **用量配额** — 每用户每日对话/搜索次数限制，原子更新防并发超用

---

## 系统架构

### 智能体层级

```
BaseAgent -> ReActAgent -> ToolCallAgent -> LightManus
```

- **BaseAgent**：状态机管理（IDLE → RUNNING → FINISHED/ERROR），SSE 流式推送，心跳保活，maxSteps=10 兜底
- **ReActAgent**：定义 think/act 循环抽象，结构化 JSON 输出（thought/action/observation/final）
- **ToolCallAgent**：执行 LLM 工具调用，解析工具参数并格式化结果
- **LightManus**：最终实例，按能力开关（纯对话/网页搜索/知识库/双开）选择 SystemPrompt，注入 chatId 做文件归属隔离

### 工具系统

#### 本地工具（8 个）

| 工具 | 功能 | 底层实现 |
|------|------|----------|
| webSearch | 联网搜索（含自动抓取摘要） | Serper API + Jsoup |
| knowledgeSearch | 知识库语义检索 | PGvector + DashScope Embedding |
| scrapeWebPage | 抓取网页全文内容 | Jsoup |
| FileOperationTool | 文件读写 + 生成 md/txt/json/csv | Java NIO，路径穿越防护 |
| downloadResource | 下载远程资源 | Hutool，SSRF 防护（禁私有 IP/localhost） |
| generatePDF | 生成 PDF 文件 | iText，文件名穿越防护 |
| executeTerminalCommand | 执行终端命令 | ProcessBuilder，黑名单 + 沙箱 + 超时 + 全局开关 |
| doTerminate | 终止任务并输出最终结果 | - |

#### MCP 协议工具（高德 + 图片搜索）

通过 `spring.ai.mcp.client.stdio` 自动加载：

| MCP Server | 能力 | 配置 |
|------|------|------|
| amap-maps-mcp-server | 天气查询、POI 搜索、路线规划、地理编码 | mcp-servers.json |
| image-search-mcp-server | 图片搜索（Pexels） | mcp-servers.json |

### 数据流

```
用户 -> 前端 Vue 3 -> POST /api/ai/manus/chat (fetch+SSE)
                              |
                    AiAgentController
                    |-- JWT 鉴权
                    |-- 并发限流（全局 5 / 每用户 1）
                    |-- 用量额度检查
                    |-- 知识库预检索（可选）
                    |-- LightManus.run()
                              |
                    think/act 循环
                    1. 调用 LLM (DashScope Qwen)
                    2. 决定使用工具
                    3. 执行工具调用
                    4. 观察结果写入 SSE 流
                    5. 回到步骤 1（直到 doTerminate 或 maxSteps）
                              |
                    前端实时渲染推理步骤
```

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Java 21, Spring Boot 3.4.4, Spring AI 1.0.0 |
| LLM 集成 | DashScope (Qwen-turbo) |
| 数据库 | PostgreSQL 16 + PGvector（向量检索） |
| 认证 | Spring Security + JWT (HS256) + BCrypt |
| MCP | Spring AI MCP Client (StdioTransport) |
| 前端 | Vue 3, Vite 4, Vue Router 4, Pinia 3 |
| 状态持久化 | pinia-plugin-persistedstate (localStorage) |
| HTTP | Axios, fetch + ReadableStream (SSE) |
| 工具库 | Hutool, Jsoup, iText, Kryo, PDFBox (PDF), Apache POI (DOCX/XLSX/PPTX) |
| 文档 | Knife4j (Swagger UI，生产环境关闭) |

---

## 前端概览

### 页面

| 页面 | 路由 | 说明 |
|------|------|------|
| SuperAgent.vue | `/` | 核心 AI 对话页，需登录 |
| Login.vue | `/login` | 登录 / 注册页 |

### 核心组件

| 组件 | 功能 |
|------|------|
| ChatRoom.vue | 消息展示与输入，能力开关（网页搜索/知识库），配额提示，快捷提问 |
| ReActSteps.vue | ReAct 推理步骤可视化时间线（Thought → Action → Observation 循环） |
| SessionSidebar.vue | 会话列表、新建、删除、重命名 |
| AiAvatarFallback.vue | AI 头像占位组件 |
| AppFooter.vue | 页脚 |

### 状态管理

| Store | 持久化 Key | 说明 |
|------|------|------|
| userStore | `token` (localStorage) | JWT token + username，登出清除 |
| chatStore | `react-agent-chat` (localStorage) | 会话列表、消息、activeId、能力开关 |

- **路由守卫**：未登录自动跳 `/login`，已登录访问 `/login` 自动跳首页
- **SSE 实现**：基于 fetch + ReadableStream（非 EventSource），支持 POST body + JWT Authorization header
- **403 自动退出**：Token 过期或无效时自动跳登录页并清除缓存

---

## 快速开始

### 前置条件

- JDK 21
- Node.js 18+
- Maven（可使用内置 mvnw）
- PostgreSQL 16+（需启用 PGvector 扩展）

### 1. 克隆项目

```bash
git clone https://github.com/light-x9/ai-react-agent.git
cd ai-react-agent
```

### 2. 初始化数据库

```sql
CREATE DATABASE react_agent;
CREATE EXTENSION vector;  -- 启用 PGvector
```

### 3. 启动后端

```bash
cd backend

# 配置 application-local.yml 中的数据库连接（默认 localhost:5432/react_agent）

# 设置必需的环境变量
export DASHSCOPE_API_KEY=your-dashscope-api-key
export SERPER_API_KEY=your-serper-api-key    # 联网搜索
export JWT_SECRET=your-jwt-secret            # JWT 签名密钥（openssl rand -base64 48）

# 可选：MCP 工具（图片搜索 + 高德地图）
export PEXELS_API_KEY=your-pexels-api-key
export AMAP_MAPS_API_KEY=your-amap-api-key

# 启动
./mvnw spring-boot:run
```

后端运行在 http://localhost:8123/api

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 http://localhost:3005

---

## 项目结构

```
ai-react-agent/
├── backend/
│   └── src/main/java/com/light/reactagent/
│       ├── agent/            # 智能体核心（BaseAgent → ReActAgent → ToolCallAgent → LightManus）
│       │   └── model/        # AgentState 枚举
│       ├── advisor/          # Spring AI Advisor（日志、重读）
│       ├── chatmemory/       # 对话记忆（FileBasedChatMemory）
│       ├── config/           # 配置（并发限流、Spring 上下文工具）
│       ├── constant/         # 常量（文件路径等）
│       ├── controller/       # REST API（7 个控制器）
│       ├── entity/           # 数据实体（User, Conversation, Message, UsageRecord）
│       ├── exception/        # 全局异常处理器
│       ├── rag/              # RAG 知识库（分词、查询重写、PGVector 配置）
│       ├── repository/       # Spring Data JPA Repository
│       ├── security/         # Spring Security 配置、JWT、认证限流
│       ├── service/          # 业务服务（用量、知识库、检索）
│       └── tools/            # 工具实现 + 文件元数据管理
│           └── file/         # FileContextHolder, FileMetadata, FileMetadataManager
├── frontend/
│   └── src/
│       ├── api/              # API 层（axios 实例 + SSE 连接 + 18 个导出函数）
│       ├── components/       # 组件（ChatRoom, ReActSteps, SessionSidebar 等）
│       ├── router/           # 路由 + 导航守卫
│       ├── stores/           # Pinia 状态管理（userStore, chatStore）
│       └── views/            # 页面（SuperAgent, Login, Home）
└── search-mcp-server/        # 图片搜索 MCP Server（Spring Boot + Pexels API）
```

---

## API 接口

> 所有接口基础路径为 `/api`（`server.servlet.context-path`），下表中路径均含此前缀。

### 认证（公开）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 用户注册 |
| POST | /api/auth/login | 用户登录，返回 JWT token |

### 智能体（需认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/ai/manus/chat | SSE 流式聊天（POST body 传 message + history） |

### 会话管理（需认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/conversation | 创建新会话 |
| GET | /api/conversation | 获取会话列表 |
| PUT | /api/conversation/{id} | 重命名会话 |
| DELETE | /api/conversation/{id} | 删除会话（含消息） |
| GET | /api/conversation/{id}/messages | 获取消息历史 |
| POST | /api/conversation/{id}/messages | 保存一条消息 |

### 知识库（需认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/knowledge-base/upload | 上传文件（multipart，max 10MB） |
| GET | /api/knowledge-base/files | 文件列表 |
| DELETE | /api/knowledge-base/files/{sourceName} | 删除单个文件 |
| POST | /api/knowledge-base/files/batch-delete | 批量删除文件 |
| GET | /api/knowledge-base/files/{sourceName}/preview | 预览文件内容 |
| POST | /api/knowledge-base/search-test | 知识库检索测试 |

### 文件下载（需认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/files/download | 下载 AI 生成文件（按 fileId + chatId 归属校验） |

### 用量（需认证）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/usage/today | 今日对话/搜索使用次数 |

### 系统

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/health | 健康检查（公开） |

---

## 配置说明

项目采用三层配置分离策略：

| 文件 | 用途 | 密钥来源 |
|------|------|------|
| `application.yml` | 公共配置（端口、JPA、日志、安全开关、默认 profile） | 部分使用 `${ENV_VAR:default}` |
| `application-local.yml` | 本地开发（完整数据库连接 + 密钥） | 可选硬编码（已 gitignore） |
| `application-prod.yml` | 生产环境（关闭 Swagger/自动建表，WARN 日志） | 全部通过环境变量注入 |

### 安全配置要点

- **终端工具**：`lightmanus.tool.terminal.enabled` 代码默认 `true`，但 `application-prod.yml` 已显式置为 `false`；仅受控内网可手动开启
- **CORS**：`cors.allowed-origins` 开发用 `*`，生产必须改为具体前端域名
- **JWT**：生产环境必须设置 `JWT_SECRET` 环境变量（≥32 字节随机字符串）
- **认证限流**：`/auth/login` 和 `/auth/register` 限制 5 次/秒/IP
- **会话隔离**：所有数据操作基于 SecurityContext 获取当前 userId

---

## 环境变量

| 变量 | 说明 | 必需 |
|------|------|------|
| `DASHSCOPE_API_KEY` | 通义千问 API Key（LLM + Embedding） | 是 |
| `JWT_SECRET` | JWT 签名密钥（≥32 字节随机字符串） | 生产必需 |
| `SERPER_API_KEY` | Serper 搜索 API Key（联网搜索） | 使用搜索时 |
| `DB_URL` | PostgreSQL 连接 URL | 否（默认 localhost:5432/react_agent） |
| `DB_USERNAME` | 数据库用户名 | 否（默认 postgres） |
| `PG_PASSWORD` | 数据库密码 | 否 |
| `PEXELS_API_KEY` | Pexels 图片搜索 Key | 使用图片搜索时 |
| `AMAP_MAPS_API_KEY` | 高德地图 API Key | 使用地图功能时 |
| `FILE_SAVE_DIR` | AI 生成文件存储目录 | 否（默认 ~/.react-agent/files） |
| `CORS_ORIGINS` | 允许的跨域域名（逗号分隔） | 否（开发默认 *） |

---

## 部署

### Docker Compose（推荐）

后端与前端分别构建镜像，通过 Nginx 反向代理统一入口：

```bash
# 构建
cd backend && ./mvnw clean package -DskipTests
cd ../frontend && npm run build

# 启动（需配置 docker-compose.yml）
docker compose up -d
```

### 手动部署

```bash
# 后端
cd backend && ./mvnw clean package -DskipTests
java -jar target/react-agent-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# 前端
cd frontend && npm run build
# 将 dist/ 部署到 Nginx 或 CDN
```

参考 `frontend/nginx.conf` 配置 Nginx 反向代理（含 SSE 支持 + 安全响应头）。

### 生产环境检查清单

- [ ] 所有密钥通过环境变量注入（不硬编码在任何文件中）
- [ ] `JWT_SECRET` 设为强随机密钥（≥32 字节）
- [ ] `cors.allowed-origins` 设为前端实际域名
- [ ] `lightmanus.tool.terminal.enabled` 设为 `false`
- [ ] 数据库使用独立实例并配置备份
- [ ] 前端部署启用 HTTPS
- [ ] 日志级别设为 INFO 或 WARN（生产 profile 已配）

---

## 许可证

MIT License
