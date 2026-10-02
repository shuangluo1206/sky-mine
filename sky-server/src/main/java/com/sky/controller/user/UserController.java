package com.sky.controller.user;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import io.jsonwebtoken.Jwt;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")//这里用户端的用户模块
@Tag(name = "C端-用户接口", description = "C端-用户接口")
@Slf4j
public class UserController {

    public static final String KEY = "SHOP_STATUS";

    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;

    @PostMapping("/login")
    @Operation(summary = "登录")
    public Result<UserLoginVO>login(@RequestBody UserLoginDTO userLoginDTO) throws Exception {
        log.info("微信用户登录，授权码为：{}",userLoginDTO.getCode());
        User user=userService.wxLogin(userLoginDTO);

        Map claims= new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID,user.getId());

       String token= JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(),claims);
        UserLoginVO userLoginVO=UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .token(token)
                .build();

        return Result.success(userLoginVO);
    }

}
