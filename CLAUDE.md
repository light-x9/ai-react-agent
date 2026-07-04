# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI ReAct Agent Platform (LightManus) — a full-stack AI agent application with a Spring Boot backend and Vue 3 frontend. The agent uses the ReAct (Reasoning and Acting) pattern to autonomously call tools, search the web, query a knowledge base, and more.

## Build & Run Commands

### Backend (Spring Boot + Maven)

```bash
# Run (from project root)
./start-backend.bat
# Or manually:
cd backend && ./mvnw spring-boot:run

# Test
cd backend && ./mvnw test

# Build JAR
cd backend && ./mvnw clean package
```

- Runs on port **8123**, context path `/api`
- Active profile: `local` (see `application-local.yml`)
- Requires: PostgreSQL running locally (database `react_agent`), `DASHSCOPE_API_KEY` env var

### Frontend (Vue 3 + Vite)

```bash
cd frontend
npm install
npm run dev      # dev server on port 3005
npm run build    # production build to dist/
npm run preview  # preview built output
```

### Search MCP Server (Spring Boot + Maven)

```bash
cd search-mcp-server
./mvnw spring-boot:run
```

- Runs on port **8127**, active profile `sse`
- Requires: `PEXELS_API_KEY` env var

## Architecture

### Agent Hierarchy (Backend)

```
BaseAgent (abstract)                    — state machine, step loop, SSE streaming
  └── ReActAgent (abstract)             — think() / act() pattern
        └── ToolCallAgent               — LLM tool calling via Spring AI ChatClient
              └── LightManus               — concrete super-agent with system prompts
```

- `BaseAgent.runStream()` executes the agent loop asynchronously, sending each step result to the frontend via SSE
- `ToolCallAgent.think()` calls the LLM with available tools; `ToolCallAgent.act()` executes any requested tool calls
- `LightManus` is instantiated per-request in `AiAgentController`, configured with all tools + MCP tools

### Tool System

Tools are registered centrally in `ToolRegistration.java`:
- **Local tools**: `WebSearchTool`, `WebScrapingTool`, `FileOperationTool`, `TerminalOperationTool`, `PDFGenerationTool`, `ResourceDownloadTool`, `TerminateTool`, `RagSearchTool`
- **MCP tools**: loaded from external MCP servers (e.g., `ImageSearchTool` from search-mcp-server), merged via `allToolsWithMcp` bean
- MCP tools degrade gracefully — if MCP server is unavailable, only local tools are used

### RAG / Knowledge Base

- `PgVectorVectorStoreConfig` creates a PgVector-backed vector store (HNSW index, 1536 dimensions, cosine distance)
- `KnowledgeBaseService` handles file upload (.txt/.md only): parse → split via `MyTokenTextSplitter` → embed → store
- `KnowledgeBaseController` exposes `/knowledge-base/upload`, `/knowledge-base/files`, `/knowledge-base/files/{sourceName}`

### Frontend Structure

- **Views**: `Home.vue` (landing), `SuperAgent.vue` (main chat interface)
- **Components**: `ChatRoom.vue` (message display + input), `ReActSteps.vue` (tool call visualization), `AppFooter.vue`, `AiAvatarFallback.vue`
- **API layer**: `src/api/index.js` — uses `EventSource` (SSE) for streaming chat, `axios` for knowledge base operations
- **Mock mode**: `SuperAgent.vue` has `USE_MOCK` flag for demo without backend

### Multi-turn Conversation Memory

- Frontend sends recent conversation history (last 6 messages) as `history` param with each request
- Backend prepends history as context to the current message before running the agent
- `chatId` is generated per page session (not currently used by backend for persistence)

### Key Configuration

| File | Purpose |
|------|---------|
| `backend/src/main/resources/application.yml` | Base config: port, context-path, Spring AI models, logging |
| `backend/src/main/resources/application-local.yml` | Local profile: PgVector DB connection, DashScope API key, MCP client, SearchAPI key |
| `search-mcp-server/src/main/resources/application.yml` | MCP server base config |
| `search-mcp-server/src/main/resources/application-sse.yml` | SSE transport mode for MCP |
| `frontend/vite.config.js` | Dev server port 3005, path alias `@` → `src/` |

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.4.4, Spring AI 1.0.0, Spring AI Alibaba (DashScope/Qwen), PgVector, MCP Client, Kryo, Jsoup, iText, Hutool, Knife4j (API docs)
- **Frontend**: Vue 3, Vue Router 4, Vite 4, Axios, `@vueuse/head`
- **MCP Server**: Spring Boot 3.4.5, Spring AI MCP Server WebMVC, Hutool

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/ai/manus/chat?message=...&history=...` | SSE streaming chat with SuperAgent |
| POST | `/api/knowledge-base/upload` | Upload .txt/.md file to knowledge base |
| GET | `/api/knowledge-base/files` | List uploaded files |
| DELETE | `/api/knowledge-base/files/{sourceName}` | Delete a file's chunks |

## Code Conventions

- Chinese comments are used throughout the codebase — preserve them as-is
- Lombok (`@Data`, `@Slf4j`, etc.) is used for boilerplate reduction
- Spring AI's `ChatClient` and `ToolCallingManager` are the primary LLM integration APIs
- Agent state machine: `IDLE → RUNNING → FINISHED|ERROR`
- Tool results are formatted for display in `ToolCallAgent.formatToolResultForDisplay()`
