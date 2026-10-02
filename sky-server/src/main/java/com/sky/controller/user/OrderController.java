package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.exception.OrderBusinessException;
import com.sky.lock.SimpleRedisLock;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 订单
 */
@RestController("userOrderController")
@RequestMapping("/user/order")
@Slf4j
@Tag(name = "C端订单接口", description = "C端订单接口")
public class OrderController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private SimpleRedisLock simpleRedisLock;

    /**
     * 用户下单
     *
     * 防超卖③：分布式锁包住整个 submitOrder 事务（含提交）。
     * 不能把锁放进 Service：@Transactional 在 Service 方法返回后才提交，
     * 锁内释放会出现"锁已放、事务未提交"，别人读到旧库存，超卖复发。
     *
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    @Operation(summary = "用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) throws Exception {
        log.info("用户下单：{}", ordersSubmitDTO);
        //自旋排队：最多等 100 次 × 100ms = 10s，等不到才拒绝
        if (!simpleRedisLock.tryLock("order:submit", 100, 100)) {
            throw new OrderBusinessException("当前下单人数过多，请稍后重试");
        }
        try {
            OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);
            return Result.success(orderSubmitVO);
        } finally {
            simpleRedisLock.unlock("order:submit");
        }
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @Operation(summary = "订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    @GetMapping("/historyOrders")
    @Operation(summary = "历史订单查询")
    public Result<PageResult>page(int page, int pageSize,Integer status){
        PageResult pageResult=orderService.pageQuery4User(page,pageSize,status);
        return Result.success(pageResult);
    }


    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @Operation(summary = "查询订单详情")
    public Result<OrderVO> details(@PathVariable("id") Long id) {
        OrderVO orderVO = orderService.details(id);
        return Result.success(orderVO);
    }


    /**
     * 用户取消订单
     *
     * @return
     */
    @PutMapping("/cancel/{id}")
    @Operation(summary = "取消订单")
    public Result cancel(@PathVariable("id") Long id) throws Exception {
        orderService.userCancelById(id);
        return Result.success();
    }


    /**
     * 再来一单
     *
     * @param id
     * @return
     */
    @PostMapping("/repetition/{id}")
    @Operation(summary = "再来一单")
    public Result repetition(@PathVariable Long id) {
        orderService.repetition(id);
        return Result.success();
    }

    @GetMapping("/reminder/{id}")
    @Operation(summary = "用户催单")
    public  Result reminder(@PathVariable("id") Long id) throws Exception {
        orderService.reminder(id);
        return Result.success();
    }
}
