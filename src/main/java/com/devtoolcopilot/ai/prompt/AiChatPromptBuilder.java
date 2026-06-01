package com.devtoolcopilot.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class AiChatPromptBuilder {
    public String systemPrompt(String contextBlock) {
        return """
你是DevTool Copilot，一个通用聊天助手，同时具备“项目上下文感知能力”。

回答规则：
1) 用户可以自由提问任何问题：正常给出专业、清晰、可执行的回答。
2) 当用户的问题与“当前项目进度/下一步/任务安排/风险/建议”等相关时，必须优先使用【项目上下文】中的真实数据来回答，并明确引用你使用到的任务或状态。
3) 当【项目上下文】为空或未选择项目且问题需要上下文时，先提示用户选择项目/提供 projectId，再给出通用建议。
4) 输出语言：中文。输出格式：支持 Markdown（可以输出代码块）。

%s
""".formatted(contextBlock == null ? "" : contextBlock.trim());
    }
}
