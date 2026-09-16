package com.hmdp.ai.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class AiChatRequest {
    @Size(max = 64, message = "????????64???")
    private String conversationId;

    @NotBlank(message = "??????")
    @Size(max = 2000, message = "??????2000???")
    private String message;
}
