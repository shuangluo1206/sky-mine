package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
//        Dish dish = new Dish();
//        dish.setCategoryId(categoryId);
//        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品
//
//        List<DishVO> list = dishService.listWithFlavor(dish);
//
//        return Result.success(list);

        //现在修改逻辑，变为用redis进行缓存
        String key="dish_"+categoryId;
        //查看redis是否有缓存的数据
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        //若存在缓存数据，直接返回给前端
        if ((list!=null&& list.size()>0)){
            return Result.success(list);
        }
        //不存在那么创建该缓存数据
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品
        list=dishService.listWithFlavor(dish);
        redisTemplate.opsForValue().set(key,list);
        //将查询到的数据载入缓存
        return Result.success(list);
    }

}
