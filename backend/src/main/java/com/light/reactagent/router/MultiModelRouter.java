package com.light.reactagent.router;

import org.slf4j.Logger;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多模型路由器 —— 实现 Spring AI 的 ChatModel 接口，对外暴露为唯一的 ChatModel Bean。
 * <p>
 * 路由算法：
 * 1) 会话级锁定（SessionContextHolder）→ 走锁定的模型
 * 2) 未锁定 → 取 primary=true 的首选
 * 3) 首选熔断 → 按 costWeight 升序找下一个健康的
 * 4) 全部熔断 → 尝试 resetTimeout 已过期的 route 抢救；仍无则抛异常
 * <p>
 * 熔断策略：
 * - 连续失败 N 次（默认 3）→ 打开熔断
 * - 熔断后经过 resetTimeoutMs（默认 120s）→ 半开，允许一次试探调用
 * - 试探成功 → 闭合熔断；失败 → 重新计时
 */
public class MultiModelRouter implements ChatModel {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(MultiModelRouter.class);

    private final List<RouteModel> routes;
    private final int failureThreshold;
    private final long resetTimeoutMs;
    private final boolean healthEnabled;

    /** 路由统计（暂存，后续可接入 Metrics） */
    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong fallbackCalls = new AtomicLong();

    public MultiModelRouter(List<RouteModel> routes, int failureThreshold, long resetTimeoutMs, boolean healthEnabled) {
        // 首选模型排最前，其次按 costWeight 升序
        List<RouteModel> sorted = new CopyOnWriteArrayList<>(routes);
        sorted.sort(Comparator
                .comparing((RouteModel r) -> !r.isPrimary())
                .thenComparingDouble(RouteModel::getCostWeight));
        this.routes = sorted;
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
        this.healthEnabled = healthEnabled;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        RouteModel route = selectRoute(prompt);
        return doCall(route, prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        RouteModel route = selectRoute(prompt);
        return doStream(route, prompt);
    }

    /**
     * 选路核心逻辑。
     */
    private RouteModel selectRoute(Prompt prompt) {
        totalCalls.incrementAndGet();
        long now = System.currentTimeMillis();

        // 1) 查会话级锁定
        String locked = com.light.reactagent.tools.file.FileContextHolder.getLockedModelName();
        if (locked != null && !locked.isBlank()) {
            for (RouteModel r : routes) {
                if (r.getName().equalsIgnoreCase(locked) && isAvailable(r, now)) {
                    log.debug("[MultiModelRouter] 命中会话锁定 model={}", locked);
                    return r;
                }
            }
        }

        // 2) 找首选且健康的
        for (RouteModel r : routes) {
            if (r.isPrimary() && isAvailable(r, now)) {
                return r;
            }
        }

        // 3) 按 costWeight 找下一个健康的
        for (RouteModel r : routes) {
            if (isAvailable(r, now)) {
                if (r.isPrimary()) continue; // primary 不可用已确定
                log.info("[MultiModelRouter] 首选不可用，降级到 model={}", r.getName());
                fallbackCalls.incrementAndGet();
                return r;
            }
        }

        // 4) 全部熔断 → 尝试抢救已过 resetTimeout 的 route
        for (RouteModel r : routes) {
            if (r.isCircuitOpen() && (now - r.getLastFailureAt()) > resetTimeoutMs) {
                log.warn("[MultiModelRouter] 全熔断抢救 model={}", r.getName());
                r.setCircuitOpen(false);
                r.setConsecutiveFailures(0);
                fallbackCalls.incrementAndGet();
                return r;
            }
        }

        // 无可用路由
        throw new RuntimeException("所有模型路由均不可用：" + routes);
    }

    private boolean isAvailable(RouteModel route, long now) {
        if (!healthEnabled) return true;
        if (!route.isCircuitOpen()) return true;
        // 熔断中，但 resetTimeout 已过 → 半开允许试探
        return (now - route.getLastFailureAt()) > resetTimeoutMs;
    }

    private ChatResponse doCall(RouteModel route, Prompt prompt) {
        try {
            ChatResponse resp = route.getChatModel().call(prompt);
            onSuccess(route);
            return resp;
        } catch (Exception e) {
            onFailure(route, e);
            // 降级到下一个
            return fallback(prompt, route);
        }
    }

    private Flux<ChatResponse> doStream(RouteModel route, Prompt prompt) {
        // 流式调用降级：尝试主路由，失败时短路到 call 降级
        try {
            Flux<ChatResponse> flux = route.getChatModel().stream(prompt);
            return flux.doOnNext(r -> onSuccess(route))
                    .doOnError(e -> onFailure(route, e))
                    .onErrorResume(e -> {
                        // 降级为非流式的 call
                        ChatResponse resp = fallback(prompt, route);
                        return Flux.just(resp);
                    });
        } catch (Exception e) {
            onFailure(route, e);
            ChatResponse resp = fallback(prompt, route);
            return Flux.just(resp);
        }
    }

    private ChatResponse fallback(Prompt prompt, RouteModel failed) {
        long now = System.currentTimeMillis();
        for (RouteModel r : routes) {
            if (r.getName().equals(failed.getName())) continue;
            if (isAvailable(r, now)) {
                try {
                    log.info("[MultiModelRouter] fallback → model={}", r.getName());
                    fallbackCalls.incrementAndGet();
                    ChatResponse resp = r.getChatModel().call(prompt);
                    onSuccess(r);
                    return resp;
                } catch (Exception e) {
                    onFailure(r, e);
                }
            }
        }
        throw new RuntimeException("所有模型路由均调用失败");
    }

    private void onSuccess(RouteModel route) {
        if (!healthEnabled) return;
        route.setConsecutiveFailures(0);
        route.setCircuitOpen(false);
    }

    private void onFailure(RouteModel route, Throwable e) {
        if (!healthEnabled) return;
        route.setConsecutiveFailures(route.getConsecutiveFailures() + 1);
        route.setLastFailureAt(System.currentTimeMillis());
        if (route.getConsecutiveFailures() >= failureThreshold) {
            route.setCircuitOpen(true);
            log.warn("[MultiModelRouter] model={} 熔断打开（连续失败 {} 次）", route.getName(), route.getConsecutiveFailures());
        }
    }

    public List<RouteModel> getRoutes() {
        return Collections.unmodifiableList(routes);
    }

    public long getTotalCalls() {
        return totalCalls.get();
    }

    public long getFallbackCalls() {
        return fallbackCalls.get();
    }
}
