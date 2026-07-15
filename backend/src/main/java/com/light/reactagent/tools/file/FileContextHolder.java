package com.light.reactagent.tools.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件操作上下文持有者（ThreadLocal）
 * <p>
 * 用于在 Agent 异步执行线程中传递 chatId 等上下文信息到工具方法。
 * 工具方法通过 {@link #getChatId()} 获取当前会话 ID，用于文件归属隔离。
 * 工具方法通过 {@link #recordFileId(String)} 记录本次请求生成的文件 ID，
 * 供 Agent 执行完成后汇总到 final 事件中返回给前端。
 * <p>
 * 由 BaseAgent.runStream() 的异步线程设置，finally 中清理。
 */
public class FileContextHolder {

    private static final ThreadLocal<Map<String, Object>> CONTEXT = new ThreadLocal<>();

    /**
     * 本次请求生成的文件 ID 列表（按生成顺序）
     */
    private static final ThreadLocal<List<String>> GENERATED_FILE_IDS = new ThreadLocal<>();

    /**
     * 设置整个上下文 Map（由 BaseAgent 钩子调用）
     */
    public static void setAll(Map<String, Object> context) {
        CONTEXT.set(context);
    }

    /**
     * 获取当前线程的 chatId（工具方法调用）
     *
     * @return chatId，未设置时返回 null
     */
    public static String getChatId() {
        Map<String, Object> ctx = CONTEXT.get();
        if (ctx == null) {
            return null;
        }
        Object val = ctx.get("chatId");
        return val instanceof String s ? s : null;
    }

    /**
     * 获取当前线程的 userId（工具方法调用，由 BaseAgent 从 SecurityContext 注入）
     * <p>
     * 用于文件注册时记录归属用户，供下载接口做越权校验。
     *
     * @return userId，未设置时返回 null
     */
    public static String getUserId() {
        Map<String, Object> ctx = CONTEXT.get();
        if (ctx == null) {
            return null;
        }
        Object val = ctx.get("userId");
        return val instanceof String s ? s : null;
    }

    /**
     * 记录本次请求生成的一个文件 ID（由文件工具在注册元数据后调用）
     *
     * @param fileId 生成的文件 ID
     */
    public static void recordFileId(String fileId) {
        List<String> ids = GENERATED_FILE_IDS.get();
        if (ids == null) {
            ids = new ArrayList<>();
            GENERATED_FILE_IDS.set(ids);
        }
        ids.add(fileId);
    }

    /**
     * 获取本次请求生成的所有文件 ID（供 Agent 执行完成后汇总到 final 事件）
     *
     * @return 文件 ID 列表，无文件时返回空列表
     */
    public static List<String> getGeneratedFileIds() {
        List<String> ids = GENERATED_FILE_IDS.get();
        return ids != null ? new ArrayList<>(ids) : new ArrayList<>();
    }

    /**
     * 锁定当前会话使用的模型（用户在前端选择）
     */
    public static void lockModelName(String modelName) {
        Map<String, Object> ctx = CONTEXT.get();
        if (ctx != null) {
            ctx.put("lockedModelName", modelName);
        }
    }

    /**
     * 获取当前会话锁定的模型名（由路由器的 selectRoute() 读取）
     * 未锁定时返回 null。
     */
    public static String getLockedModelName() {
        Map<String, Object> ctx = CONTEXT.get();
        if (ctx == null) return null;
        Object val = ctx.get("lockedModelName");
        return val instanceof String s ? s : null;
    }

    /**
     * 清理当前线程的上下文（防止线程复用导致泄漏）
     */
    public static void clear() {
        CONTEXT.remove();
        GENERATED_FILE_IDS.remove();
    }
}
