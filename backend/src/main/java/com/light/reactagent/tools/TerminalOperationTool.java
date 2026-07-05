package com.light.reactagent.tools;

import cn.hutool.core.util.StrUtil;
import com.light.reactagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 终端操作工具（安全加固版）
 * <p>
 * 安全策略：
 * 1. 危险命令拦截：通过 token 黑名单挡住 del/format/shutdown/reg 等破坏性或提权命令
 * 2. 工作目录隔离：固定在沙箱目录内执行，避免误伤工程文件
 * 3. 执行超时：防止恶意命令 hang 死进程
 * 4. 输出截断：防止超长输出撑爆 LLM 上下文
 * 5. 可通过配置 lightmanus.tool.terminal.enabled 完全禁用该工具
 */
@Slf4j
public class TerminalOperationTool {

    /**
     * 是否启用终端工具（生产环境建议关闭，或仅在内网受控场景开启）
     */
    private final boolean enabled;

    /**
     * 沙箱工作目录，所有命令都在此目录下执行
     */
    private final File sandboxDir = new File(FileConstant.FILE_SAVE_DIR + "/sandbox");

    /**
     * 危险命令 token 黑名单（已小写）。
     * 涵盖：文件删除、磁盘格式化、关机重启、注册表/服务/用户管理、脚本宿主、外联下载执行、权限篡改等。
     * 按 [\s&|;,]+ 分割后逐 token 匹配，可抓到 "echo hi & del x" 这类管道/连接符后的危险命令。
     */
    private static final Set<String> DANGEROUS_COMMANDS = Set.of(
            "del", "erase", "rd", "rmdir", "format", "shutdown", "restart",
            "reg", "regedit", "sc", "net", "net1",
            "powershell", "pwsh", "wscript", "cscript", "mshta",
            "certutil", "bitsadmin", "taskkill", "diskpart", "cipher",
            "takeown", "icacls", "cacls", "attrib", "fsutil",
            "mklink", "mountvol", "bcdedit"
    );

    /**
     * 危险子串（用于抓重定向到系统目录、调用系统工具等组合场景）
     */
    private static final Set<String> DANGEROUS_SUBSTRINGS = Set.of(
            "\\windows\\system32", "/windows/system32",
            "\\windows\\system", "\\bootmgr",
            ":\\$recycle.bin", ":\\perflogs"
    );

    /**
     * 命令最大执行时长（秒）
     */
    private static final long TIMEOUT_SECONDS = 15;

    /**
     * 输出最大字符数，超出截断
     */
    private static final int MAX_OUTPUT_LENGTH = 4000;

    public TerminalOperationTool(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 默认构造器：启用终端工具（保持向后兼容，仅在未显式配置时使用）
     */
    public TerminalOperationTool() {
        this(true);
    }

    @Tool(description = "Execute a safe command in the terminal. " +
            "Destructive commands (delete, format, shutdown, registry/service/user management, " +
            "external download/exec, permission change) are blocked. " +
            "Execution runs in a sandbox directory with a 15s timeout and 4000-char output cap.")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        if (!enabled) {
            return "终端工具已被管理员禁用。";
        }
        if (StrUtil.isBlank(command)) {
            return "命令不能为空。";
        }

        // 1. 危险命令检测
        String blockReason = checkDangerous(command);
        if (blockReason != null) {
            log.warn("拦截危险终端命令 | 原因：{} | 原始命令：{}", blockReason, command);
            return "已拦截危险命令：" + blockReason + "。破坏性、提权或外联操作被禁止。";
        }

        // 2. 确保沙箱目录存在
        if (!sandboxDir.exists() && !sandboxDir.mkdirs()) {
            return "无法创建沙箱执行目录：" + sandboxDir.getAbsolutePath();
        }

        StringBuilder output = new StringBuilder();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            builder.directory(sandboxDir);
            // 合并 stderr 到 stdout，避免读 stderr 阻塞
            builder.redirectErrorStream(true);
            process = builder.start();

            // 3. 读取输出（边读边判断长度上限）
            final Process p = process;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() > MAX_OUTPUT_LENGTH) {
                        output.append("\n...（输出已截断，超过 ").append(MAX_OUTPUT_LENGTH).append(" 字符）");
                        break;
                    }
                }
            }

            // 4. 等待进程结束（带超时）
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                // 超时强制终止整棵进程树（cmd 启动的子进程也要杀掉）
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                output.append("\n命令执行超时（超过 ").append(TIMEOUT_SECONDS).append(" 秒），已强制终止。");
                return output.toString();
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output.append("命令执行失败，退出码：").append(exitCode);
            }
        } catch (IOException e) {
            log.error("执行终端命令 IO 异常：{}", e.getMessage());
            output.append("执行命令出错：").append(e.getMessage());
        } catch (InterruptedException e) {
            log.error("终端命令执行被中断");
            Thread.currentThread().interrupt();
            output.append("命令执行被中断。");
        } finally {
            // 兜底：确保进程被回收
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
        return output.toString();
    }

    /**
     * 危险命令检测
     *
     * @param command 待检测的原始命令
     * @return 命中时返回拦截原因，未命中返回 null
     */
    private String checkDangerous(String command) {
        String lower = command.toLowerCase();
        // 1. token 级匹配：按空白/连接符分割，逐 token 比对黑名单
        String[] tokens = lower.split("[\\s&|;,]+");
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (DANGEROUS_COMMANDS.contains(token)) {
                return "包含危险命令 [" + token + "]";
            }
        }
        // 2. 子串级匹配：系统目录、引导区等
        for (String substring : DANGEROUS_SUBSTRINGS) {
            if (lower.contains(substring)) {
                return "涉及系统敏感路径 [" + substring + "]";
            }
        }
        return null;
    }
}
