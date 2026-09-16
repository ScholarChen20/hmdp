package com.hmdp.ai.service;

import com.hmdp.ai.config.RedisChatMemoryStore;
import com.hmdp.ai.dto.AiChatResponse;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiChatApplicationServiceTest {

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
        AiRequestContext.clear();
    }

    @Test
    void shouldRejectAnonymousUser() {
        AiChatApplicationService service = new AiChatApplicationService((memoryId, message) -> "ok", null);
        assertThrows(IllegalStateException.class, () -> service.chat(null, "??"));
    }

    @Test
    void shouldCreateConversationAndPassScopedMemoryId() {
        UserDTO user = new UserDTO();
        user.setId(8L);
        UserHolder.saveUser(user);
        String[] memoryId = new String[1];
        AiCustomerService customerService = (id, message) -> {
            memoryId[0] = id;
            return "??";
        };
        AiChatApplicationService service = new AiChatApplicationService(customerService, null);

        AiChatResponse response = service.chat(null, "??");

        assertEquals("??", response.getAnswer());
        assertEquals("8:" + response.getConversationId(), memoryId[0]);
    }

    @Test
    void shouldMarkExactConfirmationMessage() {
        UserDTO user = new UserDTO();
        user.setId(9L);
        UserHolder.saveUser(user);
        AiCustomerService customerService = (id, message) -> {
            assertEquals(true, AiRequestContext.isAppointmentConfirmed());
            return "?????";
        };
        AiChatApplicationService service = new AiChatApplicationService(customerService, null);

        service.chat("conversation-1", " ???? ");
    }
}
