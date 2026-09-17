package com.sky.service;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.Category;
import com.sky.entity.User;
import com.sky.result.PageResult;
import java.util.List;
public interface UserService {


    User wxLogin(UserLoginDTO userLoginDTO);
}
