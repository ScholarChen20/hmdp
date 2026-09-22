package com.hmdp.ai.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiAudit {
    String operation() default "chat";

    int conversationIdIndex() default 0;

    int messageIndex() default 1;
}
