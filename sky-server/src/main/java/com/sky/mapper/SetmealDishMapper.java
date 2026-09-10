package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
@Mapper
public interface SetmealDishMapper {
    /*
    删除菜品
     */
    List<Long> getSetmealIdsByDishIds(List<Long> ids);
}
