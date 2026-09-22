-- Apply after cleaning duplicate rows in existing environments.
ALTER TABLE tb_voucher_order
    ADD UNIQUE KEY uk_user_voucher (user_id, voucher_id);
