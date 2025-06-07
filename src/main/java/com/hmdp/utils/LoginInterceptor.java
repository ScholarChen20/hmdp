package com.hmdp.utils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 负责刷新token有效期拦截器
     * @param request
     * @param response
     * @param handler
     * @return
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        //1.判断是否登录
        if(UserHolder.getUser() == null){
            response.setStatus(401);
            // 拦截请求，返回401状态码
            return false;
        }
        // 有就放行
        return true;
    }

}
