package com.hmdp.mapper;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    @Insert("INSERT IGNORE INTO tb_voucher_order (id, user_id, voucher_id) VALUES (#{id}, #{userId}, #{voucherId})")
    int insertIgnore(VoucherOrder voucherOrder);

}
