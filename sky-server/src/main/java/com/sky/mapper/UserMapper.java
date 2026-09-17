package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.DishFlavor;
import com.sky.entity.User;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper {
    @AutoFill(OperationType.INSERT)
    public void insertBatch(List<DishFlavor> flavors) ;


    @Delete("delete from dish_flavor where dish_id = #{dishId}")
    void deleByDishrId(Long id);

    @Select("select * from dish_flavor where dish_id = #{dishId}")
    List<DishFlavor> getByDishId(Long id);

    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    void insert(User user);
}
