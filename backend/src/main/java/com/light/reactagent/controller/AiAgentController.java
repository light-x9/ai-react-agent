package com.light.reactagent.controller;

import com.light.reactagent.agent.LightManus;
import com.light.reactagent.entity.PersonaProfile;
import com.light.reactagent.service.PersonaProfileService;
import com.light.reactagent.chatmemory.FileBasedChatMemory;
import com.light.reactagent.config.AgentRateLimiter;
import com.light.reactagent.service.KnowledgeRetrievalService;
import com.light.reactagent.service.UsageService;
import com.light.reactagent.tools.ToolRegistration;
import com.light.reactagent.util.ChatFileTextExtractor;
import com.light.reactagent.util.ChatFileTextExtractor.ExtractResult;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI Agent 流式接口
 * <p>
 * 按前端能力开关动态装配工具子集：纯对话/网页搜索/知识库/双开。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiAgentController {

    @Resource
    private ChatModel openaiChatModel;

    @Resource
    private AgentRateLimiter rateLimiter;

    @Resource
    private UsageService usageService;

    @Resource
    private ToolRegistration toolRegistration;

    @Resource
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Resource
    private FileBasedChatMemory chatMemory;

    @Resource
    private PersonaProfileService personaProfileService;

    /** 快速直答模式下的身份指令（SystemMessage，权重高于普通对话） */
    private static final String QUICK_REPLY_IDENTITY = """
            IMPORTANT — YOUR IDENTITY:
            Your name is Light (小光). You are NOT DeepSeek. You were NOT created by the DeepSeek company.
            You are a helpful AI assistant. Keep answers warm, concise, and in the same language as the user.
            When asked "what is your name" or "who are you" or "你叫什么" or "你是谁", you MUST answer:
            "我叫 Light，也可以叫我小光，是一个智能助手。有什么可以帮你的？"
            NEVER identify yourself as DeepSeek under any circumstances. NEVER mention DeepSeek.
            """;

    /**
     * 获取当前用户的画像（用于前端展示「继续上次」话题建议卡片）。
     * <p>
     * 返回结构：
     * <pre>
     * {
     *   "nickname": "xxx",
     *   "preferredLanguage": "zh",
     *   "techStack": "vue,spring boot",
     *   "interests": "AI,数据分析",
     *   "writingStyle": "concise",
     *   "recentTopics": ["话题1", "话题2"],
     *   "conversationCount": 42,
     *   "lastActiveAt": "2026-07-12T21:33:00",
     *   "suggestions": ["继续上次的 xxx", "你可能还想了解 yyy"]
     * }
     * </pre>
     */
    @GetMapping("/persona/me")
    public Map<String, Object> getMyPersona(HttpServletRequest httpRequest) {
        String nickname = resolveNickname(httpRequest);
        String clientKey = extractPrincipal(httpRequest);
        Long userId = clientKey.startsWith("user:") ? Long.valueOf(clientKey.substring(5)) : null;
        if (userId == null || nickname == null) {
            return Map.of("loggedIn", false);
        }
        PersonaProfile p = personaProfileService.getOrCreate(userId, nickname);
        List<String> recentTopics = parseTopicsSafe(p.getRecentTopics());

        // 基于最近话题生成 3 条「你可能想继续」的建议
        List<String> suggestions = buildSuggestions(recentTopics, p.getInterests());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("loggedIn", true);
        result.put("nickname", p.getNickname());
        result.put("preferredLanguage", p.getPreferredLanguage());
        result.put("techStack", p.getTechStack());
        result.put("interests", p.getInterests());
        result.put("writingStyle", p.getWritingStyle());
        result.put("recentTopics", recentTopics);
        result.put("conversationCount", p.getConversationCount());
        result.put("lastActiveAt", p.getLastActiveAt() != null ? p.getLastActiveAt().toString() : null);
        result.put("suggestions", suggestions);
        return result;
    }

    /**
     * 基于最近话题 + 兴趣生成 3 条对话建议。
     * 规则极简：最近话题倒序取 2 条 + 兴趣话题 1 条（如有）。
     */
    private List<String> buildSuggestions(List<String> recentTopics, String interests) {
        List<String> out = new ArrayList<>();
        // 最近话题倒序（最新在前）
        for (int i = recentTopics.size() - 1; i >= 0 && out.size() < 2; i--) {
            out.add("继续聊：" + recentTopics.get(i));
        }
        // 兴趣话题补充
        if (interests != null && !interests.isBlank() && out.size() < 3) {
            String[] parts = interests.split(",");
            for (String part : parts) {
                String t = part.strip();
                if (!t.isEmpty() && out.size() < 3) {
                    out.add("深入了解：" + t);
                }
            }
        }
        if (out.isEmpty()) {
            out.add("试试上传一份数据文件，让小光帮你分析");
            out.add("问一个你最近好奇的技术问题");
        }
        return out;
    }

    private List<String> parseTopicsSafe(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<ArrayList<String>>() {
                    });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 获取当前可用模型列表 + 路由健康状态。前端据此渲染「模型选择」下拉。
     */
    @GetMapping("/ai/models")
    public Map<String, Object> listModels() {
        // 如果路由器是 MultiModelRouter，暴露 route 状态
        if (openaiChatModel instanceof com.light.reactagent.router.MultiModelRouter router) {
            List<Map<String, Object>> models = new ArrayList<>();
            for (com.light.reactagent.router.RouteModel r : router.getRoutes()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", r.getName());
                m.put("primary", r.isPrimary());
                m.put("costWeight", r.getCostWeight());
                m.put("description", r.getDescription());
                m.put("circuitOpen", r.isCircuitOpen());
                m.put("consecutiveFailures", r.getConsecutiveFailures());
                models.add(m);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("models", models);
            result.put("totalCalls", router.getTotalCalls());
            result.put("fallbackCalls", router.getFallbackCalls());
            return result;
        }
        // 兜底：没有路由器时把当前模型包装成单条
        Map<String, Object> single = new LinkedHashMap<>();
        single.put("name", "default");
        single.put("primary", true);
        single.put("costWeight", 1.0);
        single.put("description", "默认模型");
        single.put("circuitOpen", false);
        single.put("consecutiveFailures", 0);
        return Map.of("models", List.of(single), "totalCalls", 0, "fallbackCalls", 0);
    }

    /**
     * 前端主动锁定模型（传 body: {"modelName": "ollama"}）。
     * 当前会话后续调用都走被锁定的模型，不再 failover。
     */
    @PostMapping("/ai/models/lock")
    public Map<String, Object> lockModel(@RequestBody Map<String, Object> body) {
        String modelName = (String) body.get("modelName");
        if (modelName == null || modelName.isBlank()) {
            com.light.reactagent.tools.file.FileContextHolder.lockModelName(null);
            return Map.of("locked", "");
        }
        com.light.reactagent.tools.file.FileContextHolder.lockModelName(modelName);
        return Map.of("locked", modelName);
    }

    /**
     * JSON 入口（向后兼容）：无附件时的常规对话。
     */
    @PostMapping(value = "/manus/chat",
            consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter doChatWithManusJson(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        return executeChat(request.message(), request.history(), request.webSearch(),
                request.knowledgeBase(), request.chatId(), null, httpRequest);
    }

    /**
     * Multipart 入口：前端传附件（PDF/DOCX/XLSX 等）+ 字段时使用。
     * <p>
     * 所有 Business 字段通过 form-data 字段传递，文件通过 file part 传递。
     */
    @PostMapping(value = "/manus/chat",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter doChatWithManusMultipart(HttpServletRequest httpRequest,
            @RequestParam(value = "message", required = false, defaultValue = "") String message,
            @RequestParam(value = "history", required = false) String history,
            @RequestParam(value = "webSearch", required = false, defaultValue = "true") boolean webSearch,
            @RequestParam(value = "knowledgeBase", required = false, defaultValue = "false") boolean knowledgeBase,
            @RequestParam(value = "chatId", required = false) String chatId,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return executeChat(message, history, webSearch, knowledgeBase, chatId, file, httpRequest);
    }

    /**
     * 核心对话逻辑（JSON 与 Multipart 共用）。
     * <p>
     * 与知识库模式的区别：这里的附件不做持久化/向量化/分块索引，仅作一次性原文读取后塞进 user message，
     * 这样 AI 看到的是一份完整原文的截断版，比 RAG 相似度检索更精准。
     *
     * @param message       用户消息
     * @param history       历史（保留字段，未使用）
     * @param webSearch     深度思考
     * @param knowledgeBase RAG 知识库
     * @param chatId        会话 id
     * @param file          可选附件（multipart 时有值，JSON 时为 null）
     * @param httpRequest   用于提取 userId
     */
    private SseEmitter executeChat(String message, String history,
                                   boolean webSearch, boolean knowledgeBase,
                                   String chatId, MultipartFile file,
                                   HttpServletRequest httpRequest) {
        // 1. 并发限流
        String clientKey = extractPrincipal(httpRequest);
        if (!rateLimiter.tryAcquire(clientKey)) {
            return rejectWith("当前对话并发数已达上限，请稍候再试。");
        }

        // 2. 用量额度（对话每日上限）
        String userId = clientKey.startsWith("user:") ? clientKey.substring(5) : null;
        if (userId != null && !usageService.checkAndIncrementChat(userId)) {
            rateLimiter.release(clientKey);
            return rejectWith("今日对话已达上限，请明日再试。");
        }

        AtomicBoolean released = new AtomicBoolean(false);
        Runnable releaseOnce = () -> {
            if (released.compareAndSet(false, true)) {
                rateLimiter.release(clientKey);
            }
        };

        try {
            // 3. chatId 缺失则自动生成（文件注册/下载追踪用）
            if (chatId == null || chatId.isBlank()) {
                chatId = java.util.UUID.randomUUID().toString();
            }
            if (message == null) {
                message = "";
            }

            // 4. 画像注入：在 user message 前追加「用户是谁」片段，让 LLM 每次对话前都记住用户
            String nickname = resolveNickname(httpRequest);
            Long userIdLong = (userId != null && !userId.isBlank()) ? Long.valueOf(userId) : null;
            if (userIdLong != null && nickname != null) {
                personaProfileService.touchConversation(userIdLong, nickname);
                String personaCtx = personaProfileService.buildSystemContext(userIdLong, nickname);
                if (personaCtx != null && !personaCtx.isBlank()) {
                    // 把画像拼到 user message 头部（权重仅次于 system prompt）
                    message = "<<<USER_PERSONA>>>\n" + personaCtx + "\n<<<END_USER_PERSONA>>>\n\n" + message;
                }
            }

            // 4. 附件文本提取（一次性，不入库不分块）：在用户消息之上叠加一道「附件原文」
            String savedAttachmentPath = null;
            if (file != null && !file.isEmpty()) {
                ExtractResult result = ChatFileTextExtractor.extract(file);
                String attachmentBlock = result.buildAttachmentBlock();
                // 附件内容放在用户问题之前，并引导 AI 紧扣附件作答
                message = attachmentBlock + "\n\n请紧扣以上附件内容回答用户问题。\n\n用户问题：" + message;

                // 附件为数据文件时：额外保存到 workspace，让 LLM 能调用 AnalyzeDataTool 生成图表
                String fn = file.getOriginalFilename() != null ? file.getOriginalFilename() : "data.csv";
                if (isDataFile(fn)) {
                    try {
                        savedAttachmentPath = saveAttachmentToWorkspace(file, fn);
                    } catch (Exception e) {
                        log.warn("附件保存到 workspace 失败（不影响对话链路）：{}", e.getMessage());
                    }
                }
            }

            // 5. 若识别到数据文件，追加提示让 LLM 主动调 AnalyzeDataTool 出图
            if (savedAttachmentPath != null) {
                message += "\n\n提示：该文件是一个数据文件，已保存到工作区 '" + savedAttachmentPath
                        + "'。你可以调用 analyze(filePath=\"" + savedAttachmentPath + "\")" +
                        " 让它生成一份交互式图表（JSON），图表配置会以文件形式返回给用户。";
            }

            // 5. 记忆主键：绑定用户（多租户安全）
            String memKey = (userId != null ? userId + ":" : "") + chatId;

            // 6. 知识库预检索式 RAG：在附件之上再叠加知识库检索结果（两者可叠加）
            if (knowledgeBase) {
                String kbContext = knowledgeRetrievalService.retrieve(message);
                if (kbContext != null) {
                    message = "请严格基于以下知识库内容回答用户问题，不要使用你自己的通用知识。\n\n"
                            + "=== 知识库内容 ===\n" + kbContext + "\n=== 结束 ===\n\n"
                            + "用户问题：" + message;
                }
            }

            // 「深度思考」关闭 → 快速直答：仍走后端 ChatMemory 取结构化历史
            // webSearch 字段语义现为「是否深度模式」，字段名保持不变以兼容前端协议
            if (!webSearch) {
                // 包装 onComplete：先异步更新画像，再释放并发许可
                final Long finalUserIdL = userIdLong;
                final String finalNicknameL = nickname;
                final String finalMessageL = message;
                Runnable releaseWithPersona = () -> {
                    try {
                        triggerPersonaUpdate(finalUserIdL, finalNicknameL, finalMessageL, null);
                    } finally {
                        releaseOnce.run();
                    }
                };
                return quickReply(chatMemory.get(memKey), message, memKey, releaseWithPersona);
            }

            // 「深度思考」开启 → Agent 模式：多步 ReAct 推理 + 工具调用
            // 1) 从 ChatMemory 拉取已窗口化 + 摘要的结构化历史（含之前各轮 user/assistant）
            List<Message> historyMsgs = chatMemory.get(memKey);
            // 2) 持久化当前用户消息（含可能的附件原文 + KB 上下文），供后续轮次检索
            chatMemory.add(memKey, List.of(new UserMessage(message)));

            ToolCallback[] tools = toolRegistration.buildToolSet(true, knowledgeBase);
            LightManus lightManus = new LightManus(tools, openaiChatModel,
                    true, knowledgeBase);
            // 注入 chatId 到 Agent，用于文件归属隔离（前端每次会话生成一个 UUID 作为 chatId）
            lightManus.setChatId(chatId);
            // 3) 用历史（含工具上下文的结构化消息）初始化 Agent 的 messageList
            lightManus.setMessageList(new ArrayList<>(historyMsgs));
            // 4) 完成后把最终答案写回 ChatMemory（仅 final answer，不含工具推理痕迹）
            // 5) 异步更新用户画像（从本轮对话摘要中抽取偏好/话题增量）
            final String finalUserMsg = message;
            final String finalNickname = nickname;
            final Long finalUserId = userIdLong;
            Runnable persistAndRelease = () -> {
                String ans = null;
                try {
                    ans = lightManus.getFinalAnswer();
                    if (ans != null && !ans.isBlank()) {
                        chatMemory.add(memKey, List.of(new AssistantMessage(ans)));
                    }
                } catch (Exception ignored) {
                    // 记忆写入失败不影响主流程
                } finally {
                    // 画像更新永不阻塞主流程，放在 finally 里确保总被触发
                    triggerPersonaUpdate(finalUserId, finalNickname, finalUserMsg, ans);
                    releaseOnce.run();
                }
            };
            lightManus.setOnAgentComplete(persistAndRelease);
            return lightManus.runStream(message);
        } catch (Exception e) {
            releaseOnce.run();
            throw e;
        }
    }

    /**
     * 快速直答模式：深度思考关闭时使用。
     * <p>
     * 不进入 Agent ReAct 循环，直接用 ChatModel 单轮调用，
     * 将回答通过 SSE final 事件返回（与 Agent 模式输出格式一致，前端无需区分）。
     * 历史由后端 ChatMemory 提供（结构化消息，而非前端拼接字符串）。
     *
     * @param history     已窗口化 + 摘要的结构化历史（来自 ChatMemory）
     * @param message     当前用户消息（可能已拼入知识库 RAG 结果）
     * @param memKey     记忆主键（userId:chatId）
     * @param onComplete  完成回调（释放并发许可等）
     */
    private SseEmitter quickReply(List<Message> history, String message, String memKey, Runnable onComplete) {
        SseEmitter emitter = new SseEmitter(120000L); // 2 分钟超时
        // 先持久化用户消息到记忆（与 Agent 模式保持一致）
        try {
            chatMemory.add(memKey, List.of(new UserMessage(message)));
        } catch (Exception ignored) {
        }
        CompletableFuture.runAsync(() -> {
            try {
                // 使用 SystemMessage 下发身份指令，比拼在 UserMessage 里权重更高
                SystemMessage systemMsg = new SystemMessage(QUICK_REPLY_IDENTITY);
                List<Message> promptMsgs = new ArrayList<>();
                promptMsgs.add(systemMsg);
                if (history != null) {
                    promptMsgs.addAll(history);
                }
                promptMsgs.add(new UserMessage(message));
                Prompt prompt = new Prompt(promptMsgs);
                ChatResponse response = openaiChatModel.call(prompt);
                String answer = response.getResult().getOutput().getText();
                if (answer == null) {
                    answer = "";
                }
                // 持久化最终回答到记忆（仅 final answer）
                try {
                    chatMemory.add(memKey, List.of(new AssistantMessage(answer)));
                } catch (Exception ignored) {
                }
                // 与 Agent 模式一致的事件格式：final 事件 + [DONE]
                emitter.send("{\"type\":\"final\",\"content\":\"" + escapeJson(answer) + "\"}");
                emitter.send("[DONE]");
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send("{\"type\":\"error\",\"content\":\"快速回答失败："
                            + escapeJson(String.valueOf(e.getMessage())) + "\"}");
                    emitter.send("[DONE]");
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                onComplete.run();
            }
        });
        return emitter;
    }

    /**
     * 简单 JSON 字符串转义，防止 SSE 解析中断
     */
    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** 判断文件名是否为可解析的数据文件 */
    private static boolean isDataFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase().trim();
        return lower.endsWith(".csv") || lower.endsWith(".tsv")
                || lower.endsWith(".xlsx") || lower.endsWith(".xls")
                || lower.endsWith(".json");
    }

    /**
     * 把附件字节保存到工作区 sandbox（固定到 file/ 子目录下），使 AnalyzeDataTool 能通过相对路径读取。
     * 文件名加 UUID 前缀避免并发/同名覆盖。
     *
     * @return 相对路径（file/xxx）供后续 analyze(filePath=...) 调用；失败返回 null
     */
    private String saveAttachmentToWorkspace(MultipartFile file, String originalName) {
        if (file == null || file.isEmpty()) return null;
        try {
            // 沙箱根：复用 FileToolSupport.resolveBaseDir("file")
            File baseDir = com.light.reactagent.tools.file.FileToolSupport.resolveBaseDir("file");
            baseDir.mkdirs();
            String safeName = originalName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
            String internalName = System.currentTimeMillis() + "_" + safeName;
            File target = new File(baseDir, internalName);
            Files.copy(file.getInputStream(), target.toPath());
            return "file/" + internalName;
        } catch (Exception e) {
            log.warn("saveAttachmentToWorkspace 失败：{}", e.getMessage(), e);
            return null;
        }
    }

    private SseEmitter rejectWith(String message) {
        SseEmitter rejected = new SseEmitter();
        try {
            rejected.send(message);
            rejected.complete();
        } catch (Exception e) {
            rejected.completeWithError(e);
        }
        return rejected;
    }

    /**
     * 触发异步画像更新（从本轮对话摘要中抽取偏好/话题增量）。
     * 任何参数为 null 时静默跳过，绝不抛异常。
     */
    private void triggerPersonaUpdate(Long userId, String nickname, String userMsg, String assistantMsg) {
        if (userId == null || nickname == null) return;
        try {
            String u = (userMsg == null) ? "" : userMsg;
            String a = (assistantMsg == null) ? "" : assistantMsg;
            // 剥离 <<<USER_PERSONA>>> 段，避免画像片段被重复抽取
            u = u.replaceAll("<<<USER_PERSONA>>>.*<<<END_USER_PERSONA>>>", "").strip();
            personaProfileService.afterConversationAsync(userId, nickname, u, a);
        } catch (Exception e) {
            log.warn("[Persona] triggerPersonaUpdate 失败（不影响主流程）：{}", e.getMessage());
        }
    }

    /** 从安全上下文取用户名（nickname 保底），用于画像注入 */
    private String resolveNickname(HttpServletRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    private String extractPrincipal(HttpServletRequest req) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return "user:" + auth.getName();
        }
        return "ip:" + extractClientIp(req);
    }

    private String extractClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip;
        }
        return req.getRemoteAddr();
    }

    /**
     * 对话请求体
     *
     * @param message       当前用户消息
     * @param history       历史上下文（可选）
     * @param webSearch     是否开启「深度思考」（Agent 模式）：true=多步推理+工具调用，false=快速直答
     * @param knowledgeBase 是否开启知识库（RAG 预检索，两种模式下都生效）
     * @param chatId        会话 ID（可选，前端生成的 UUID，用于文件归属隔离；为空时文件工具仍可生成文件但不做归属校验）
     */
    public record ChatRequest(String message, String history,
                              boolean webSearch, boolean knowledgeBase,
                              String chatId) {
    }
}
