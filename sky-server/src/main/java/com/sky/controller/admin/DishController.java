package com.sky.controller.admin;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/admin/dish")
@Slf4j
@Api(tags = "菜品相关接口")
public class DishController {
    @Autowired
    private DishService dishService;
    /**
     * 新增菜品
     *
     */
    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping
    @ApiOperation("新增菜品")
    public Result<String> save(@RequestBody DishDTO dishDTO){
        log.info("新增菜品：{}",dishDTO);
         dishService.saveWithFlavor(dishDTO);

         //清理缓存数据
        Long categoryId = dishDTO.getCategoryId();
        String key="dish_"+categoryId;
        cleanCahe(key);
        return Result.success();
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult>page(DishPageQueryDTO dishPageQueryDTO){
        log.info("菜品分页查询: {}",dishPageQueryDTO);
        PageResult pageResult=dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     *批量菜品删除
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("批量删除菜品")
    public  Result delete(@RequestParam List<Long> ids){
        log.info("批量删除菜品：{}",ids);
        dishService.deleteBatch(ids);

        //将所有菜品批量数据清理掉，所有以dish_开头的key
        cleanCahe("dish_*");
        return Result.success();
    }

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询菜品和关联的口味数据")
    public Result<DishVO> getById(@PathVariable Long id){
        log.info("根据id查询菜品和关联的口味数据");
        return Result.success(dishService.getByIdWithFlavor(id));
    }

    @PutMapping
    @ApiOperation("更新菜品")
    public Result update(@RequestBody DishDTO dishDTO){
        log.info("更新菜品:{}",dishDTO);
        dishService.updateWithFlavor(dishDTO);

        //删除所有菜品的缓存数据
        //将所有菜品批量数据清理掉，所有以dish_开头的key
        cleanCahe("dish_*");
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("菜品起售停售")
    public Result<String> startOrStop(@PathVariable Integer status, Long id) {
        dishService.startOrStop(status, id);

        //将所有菜品批量数据清理掉，所有以dish_开头的key
        cleanCahe("dish_*");


        return Result.success();
    }


    private void cleanCahe(String pattern){
        //将所有菜品批量数据清理掉，所有以dish_开头的key
        Set keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }
}
