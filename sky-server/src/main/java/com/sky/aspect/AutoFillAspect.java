package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    /**
     * qie ru dian
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..))&& @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointCut(){};

    /**
     * 前置通知，在通知中进行公共字段的自动填充
     */
    @SneakyThrows
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) throws NoSuchMethodException {
        log.info("开始进行公共字段的自动填充");

        //总的来说，就是要在某个包的两个方法执行前，提前进行一些操作
        //所以找到切入点为两个包，即execution(* com.sky.mapper.*.*(..))，再找到方法，这里对方法进行了注解，即@annotation(com.sky.annotation.AutoFill)
        //而这个注解的定义在annotation包的autofill类里面，主要是对更新update和插入insert进行注解的
        //方法已经写明了，在之前就进行操作，所以是@Before("autoFillPointCut()")，而这边写好了执行方式，后面就对目标进行执行
        //目标就是mapper下面的有注解的方式


        //获得当前被拦截的方法上的数据库操作类型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        //获得被拦截方法的参数--实体对象
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        //准备赋值的数据
        OperationType operationType = autoFill.value();
        //根据不同的操作类型，对应赋值
        Object[] args = joinPoint.getArgs();//这里实体对象不为空，但是为了保证
        if(args==null||args.length==0){
            return;
        }
        Object entity = args[0];//这里不用employee，因为后面还可以有其他的自动填充的数据，比如order之类的
        LocalDateTime now=LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        /**
         * 作用：在运行时，动态找到 setCreateTime 这个 setter 方法
         *
         * 不是直接写 entity.setCreateTime(xxx)，而是通过代码 “找到这个方法”，后面一般搭配 .invoke() 去调用它。
         */

        /**
         * 如果事先知道 entity 是什么类，完全可以直接写 entity.setCreateTime()，简单又快。
         *
         * 但写反射这行代码的场景：写这段代码的时候，根本不知道 entity 到底是哪个实体类
         * if (entity instanceof User) {     ((User)entity).setCreateTime(LocalDateTime.now()); }
         * else if (entity instanceof Order) {     ((Order)entity).setCreateTime(LocalDateTime.now()); }
         * else if (entity instanceof Goods) {     ((Goods)entity).setCreateTime(LocalDateTime.now()); }
         * // 每新增一个实体，就要加一个if，实体几十个就爆炸了
         */
        if (operationType==OperationType.INSERT){
            try {  
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME,LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);


                setCreateTime.invoke(entity,now);
                setCreateUser.invoke(entity,currentId);
                setUpdateTime.invoke(entity,now);
                setUpdateUser.invoke(entity,currentId);

            } catch (Exception e) {
                log.error("公共字段自动填充失败(INSERT)", e);
            }

        }else if (operationType==OperationType.UPDATE){
            try {
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME,LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);
                setUpdateTime.invoke(entity,now);
                setUpdateUser.invoke(entity,currentId);
                log.info("填充success");
            } catch (Exception e) {
                log.error("自动填充字符失败2",e.getMessage());
            }
        }
        //反射多用于不知道这个方法存不存在，**程序**运行的时候**，动态去 “扒一个对象的信息”，找到它的类、方法、字段，然后调用方法 / 读写属性。**
    }
}
