package com.sky.controller.admin;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")
@Slf4j
@RequestMapping("/admin/shop")
@Api(tags = "店铺相关接口")
public class ShopController {
    public static final String KEY="SHOP_STATUS";

    @Autowired
    public RedisTemplate redisTemplate;

    /**
     * 设置营业状态
     * @param status
     * @return
     *
     */
    @PutMapping("/{status}")
    @ApiOperation("设置营业状态")
    public Result<String>setStatus(@PathVariable Integer status ){
        log.info("设置营业状态：{}",status==1 ?"营业中":"打样中");
        redisTemplate.opsForValue().set(KEY,status);
        return Result.success();
    }

    /**
     * 查询营业状态
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("查询营业状态")
    public  Result<Integer>getStatus(){
        Integer status=(Integer) redisTemplate.opsForValue().get(KEY);
        log.info("查询营业状态：{}",status==1?"营业中":"打样中");
        return Result.success(status);
    }




}
