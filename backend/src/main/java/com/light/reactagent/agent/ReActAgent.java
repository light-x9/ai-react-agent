package com.light.reactagent.agent;

import com.light.reactagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考和行动
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                // AI 没有请求工具调用，说明本轮回答已完成
                setState(AgentState.FINISHED);
                // 获取 AI 的文字回复
                var messages = getMessageList();
                for (int i = messages.size() - 1; i >= 0; i--) {
                    var msg = messages.get(i);
                    if (msg instanceof org.springframework.ai.chat.messages.AssistantMessage am) {
                        String text = am.getText();
                        if (text != null && !text.isBlank()) {
                            return text;
                        }
                    }
                }
                return "对话结束";
            }
            // 再行动
            return act();
        } catch (Exception e) {
            // 记录异常日志
            e.printStackTrace();
            return "步骤执行失败：" + e.getMessage();
        }
    }

}
