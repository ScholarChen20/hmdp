-- Apply after cleaning duplicate rows in existing environments.
ALTER TABLE tb_follow
    ADD UNIQUE KEY uk_follow_user_target (user_id, follow_user_id);
