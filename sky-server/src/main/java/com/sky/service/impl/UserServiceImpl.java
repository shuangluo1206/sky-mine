package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.Category;
import com.sky.entity.User;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类业务层
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    public static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatProperties weChatProperties;

    /**
     * 根据微信授权码实现微信登录     * @param userLoginDTO     * @return
     */
    public User wxLogin(UserLoginDTO userLoginDTO) {
       //取得授权码
        String code = userLoginDTO.getCode();
        String openid = getOpenid(code);
        if (openid==null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        //根据openid查询用户信息
   User user= userMapper.getByOpenid(openid);

        if (user==null){
            //当前是一个新用户，自动完成注册
            user = new User();
            user.setOpenid(openid);
            user.setCreateTime(LocalDateTime.now());
            userMapper.insert(user);

        }
        return user;
    }

    /*** 获取微信用户的openid * @param code * @return*/

    private String getOpenid(String code) {
        //请求参数封装
        Map map = new HashMap();
        map.put("appid",weChatProperties.getAppid());
        map.put("secret",weChatProperties.getSecret());
        map.put("js_code",code);
        map.put("grant_type","authorization_code");
        //调用工具类，向微信接口服务发送请求
        String json = HttpClientUtil.doGet(WX_LOGIN, map);
        log.info("微信登录返回结果：{}", json);

        //解析json字符串
        JSONObject jsonObject = JSON.parseObject(json);
        String openid = jsonObject.getString("openid");
        log.info("微信用户的openid为：{}", openid);

    return openid;
    }

}
