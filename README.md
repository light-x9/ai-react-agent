# LightManus - AI ReAct 智能体平台

> 基于 **ReAct（Reasoning + Acting）** 模式的自主规划 AI 智能体应用。
> 智能体可自主推理、调用多种工具完成复杂任务，支持流式响应与知识库检索。

---

## 功能亮点

- **ReAct 推理循环** — Thought → Action → Observation 多步推理链路，工具调用与思考交替进行
- **多工具协同** — 联网搜索、图片搜索、高德地图、PDF 生成、文件操作等 10+ 工具
- **RAG 知识库** — 基于 PGvector 的向量检索，支持文档上传与语义搜索
- **流式响应** — 基于 SSE（Server-Sent Events）实时推送推理步骤
- **MCP 协议支持** — 通过 Model Context Protocol 接入外部工具
- **对话记忆** — 基于会话 ID 的上下文持久化，支持多轮连续对话

---

## 系统架构

### 智能体层级


`
BaseAgent -> ReActAgent -> ToolCallAgent -> LightManus
`
- BaseAgent：状态机管理（IDLE - RUNNING - FINISHED/ERROR），SSE 流式推送
- ReActAgent：定义 think/act 循环抽象
- ToolCallAgent：执行 LLM 工具调用，处理工具结果
- LightManus：最终实例，注入所有工具

### 工具系统

当前已内置以下本地工具：

| 工具 | 功能 | API 来源 |
|------|------|----------|
| searchWeb | 联网搜索信息 | SearchAPI |
| searchImage | 搜索图片 | Pexels |
| queryWeather | 查询城市实时天气 | 高德地图 |
| searchPoi | 搜索地点（景点、餐厅等）| 高德地图 |
| scrapeWebPage | 抓取网页内容 | Jsoup |
| readFile/writeFile | 文件读写 | - |
| downloadResource | 下载远程资源 | Hutool |
| generatePDF | 生成 PDF 文件 | iText |
| executeTerminalCommand | 执行终端命令 | - |
| searchKnowledgeBase | 知识库检索 | PGvector |
| doTerminate | 终止任务 | - |

### 数据流

`
用户 -> 前端 Vue 3 -> SSE -> AiAgentController -> LightManus
                                                     |
                                           think() 循环
                                           1. 调用 LLM 思考
                                           2. 决定使用工具
                                           3. 执行工具调用
                                           4. 观察结果
                                           5. 回到步骤 1
                                                     |
                                               流式推送 SSE
                                                     |
                                              前端实时展示推理步骤
`

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21, Spring Boot 3.4.4, Spring AI 1.0.0 |
| LLM 集成 | DashScope (Qwen), Ollama |
| 数据库 | PostgreSQL 16 + PGvector |
| MCP | Spring AI MCP Client / Server |
| 前端 | Vue 3, Vue Router 4, Vite 4 |
| HTTP | Axios, SSE (EventSource) |
| 工具库 | Hutool, Jsoup, iText, Kryo |
| 文档 | Knife4j (Swagger UI) |

---

## 前端概览

前端基于 Vue 3 + Vite 构建，包含两个主要页面：

- **首页（Home.vue）** -- 项目介绍、功能展示卡片、快速入口
- **智能体页面（SuperAgent.vue）** -- 核心聊天界面

### 核心组件

| 组件 | 功能 |
|------|------|
| ChatRoom.vue | 消息展示与输入，支持快捷提问、文件上传 |
| ReActSteps.vue | AI 推理步骤可视化时间线 |
| AiAvatarFallback.vue | 头像占位组件 |
| AppFooter.vue | 页脚 |

### 前端特性

- SSE 流式响应：基于 EventSource 实现，逐字展示 AI 回复
- 推理步骤可视化：每个工具调用作为独立步骤实时展示
- Mock 模式：支持无后端调试
- 知识库管理：文件上传、列表查看、删除
- 生产部署：通过 Nginx 反代前后端

---

## 快速开始

### 前置条件

- JDK 21
- Node.js 18+
- Maven（可使用内置 mvnw）
- PostgreSQL 16+（启用 PGvector 扩展）

### 1. 克隆项目

`ash
git clone https://github.com/light-x9/ai-react-agent.git
cd ai-react-agent
`

### 2. 启动后端

`ash
cd backend
# 配置 application-local.yml 中的数据库连接
# 设置环境变量
set DASHSCOPE_API_KEY=your-dashscope-api-key
set PEXELS_API_KEY=your-pexels-api-key
# 启动
./mvnw spring-boot:run
`

后端运行在 http://localhost:8123/api

### 3. 启动前端

`ash
cd frontend
npm install
npm run dev
`

前端运行在 http://localhost:3005

---

## 项目结构

`
ai-react-agent/
├── backend/           # Spring Boot 后端
│   ├── agent/         # 智能体核心（分层架构）
│   ├── advisor/       # Spring AI Advisor
│   ├── chatmemory/    # 对话记忆
│   ├── config/        # 跨域等配置
│   ├── controller/    # REST API
│   ├── rag/           # RAG 知识库
│   ├── service/       # 业务服务
│   └── tools/         # 工具实现
├── frontend/          # Vue 3 前端
│   └── src/
│       ├── api/       # API 层
│       ├── components/# 组件
│       ├── views/     # 页面
│       └── router/    # 路由
└── search-mcp-server/ # 图片搜索 MCP Server
`

---

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/ai/manus/chat | SSE 流式聊天 |
| GET | /api/health | 健康检查 |
| POST | /api/knowledge-base/upload | 上传知识库文件 |
| GET | /api/knowledge-base/files | 获取文件列表 |
| DELETE | /api/knowledge-base/files/{sourceName} | 删除文件 |

---

## 环境变量

| 变量 | 说明 | 必需 |
|------|------|------|
| DASHSCOPE_API_KEY | 通义千问 API Key | 是 |
| PEXELS_API_KEY | Pexels 图片搜索 Key | 使用图片搜索时 |
| AMAP_MAPS_API_KEY | 高德地图 API Key | 使用地图功能时 |
| PG_PASSWORD | PostgreSQL 密码 | 否（默认 postgres） |
| JAVA_HOME | JDK 路径 | 是 |

---

## 部署

`ash
# 后端
cd backend && ./mvnw clean package -DskipTests
java -jar target/react-agent-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# 前端
cd frontend && npm run build
`

参考 frontend/nginx.conf 配置 Nginx 反向代理。

---

## 截图

> 待添加项目截图（首页、智能体对话界面、工具调用推理步骤可视化等）

---

## 许可证

MIT License
