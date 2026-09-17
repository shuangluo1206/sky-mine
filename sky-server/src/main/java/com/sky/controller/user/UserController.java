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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")//这里用户端的用户模块
@Api(tags = "C端-用户接口")
@Slf4j
public class UserController {

    public static final String KEY = "SHOP_STATUS";

    @Autowired
    private UserService userService;
    @Autowired
    private JwtProperties jwtProperties;

    @PostMapping("/login")
    @ApiOperation("登录")
    public Result<UserLoginVO>login(@RequestBody UserLoginDTO userLoginDTO){
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
