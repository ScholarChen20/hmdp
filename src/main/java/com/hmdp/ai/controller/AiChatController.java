package com.hmdp.ai.controller;

import com.hmdp.ai.dto.AiChatRequest;
import com.hmdp.ai.service.AiChatApplicationService;
import com.hmdp.dto.Result;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/ai/chat")
public class AiChatController {
    private final AiChatApplicationService aiChatApplicationService;

    public AiChatController(AiChatApplicationService aiChatApplicationService) {
        this.aiChatApplicationService = aiChatApplicationService;
    }

    @PostMapping
    public Result chat(@Valid @RequestBody AiChatRequest request, HttpServletRequest httpRequest) {
        return Result.ok(aiChatApplicationService.chat(request.getConversationId(), request.getMessage(), clientIp(httpRequest)));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Result clearConversation(@PathVariable String conversationId) {
        aiChatApplicationService.clearConversation(conversationId);
        return Result.ok();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
