package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.DateUtil;
import cn.wolfcode.util.UserUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by lanxw
 */
@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;
    @RequestMapping("/doSeckill")
    @RequireLogin
    public Result<String> doSeckill (Integer time, Long seckillId, HttpServletRequest request){
// 判断是否处于抢购时间-一个用户只能抢一个商品-保证库存充足-创建秒杀订单 - 扣减库存
        SeckillProductVo seckillProductVo = seckillProductService.find(time, seckillId);
        boolean legalTime = DateUtil.isLegalTime(seckillProductVo.getStartDate(), seckillProductVo.getTime());
        if (!legalTime){
            return Result.error(CommonCodeMsg.ILLEGAL_OPERATION);
        }
//一个用户只能抢一个商品
        String token = request.getHeader(CommonConstants.TOKEN_NAME);
        String phone = UserUtil.getUserPhone(redisTemplate,token);
        OrderInfo orderInfo = orderInfoService.findByPhoneAndSeckillId(phone,seckillId);
        String orderSetKey = SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(String.valueOf(seckillId));

        if(redisTemplate.opsForSet().isMember(orderSetKey,phone)){
            //重复下单
            return Result.error(SeckillCodeMsg.REPEAT_SECKILL);
        }
//保证库存充足
        if(seckillProductVo.getStockCount()<=0){
            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);//提示库存不足
        }
//创建秒杀订单,扣减库存
        //使用redis控制秒杀请求人数
//        String seckillStockCountKey = SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(String.valueOf(time));
//        Long remianCount = redisTemplate.opsForHash().increment(seckillStockCountKey,String.valueOf(seckillId),-1);
//        if (remianCount<0){
//            //提示重复下单
//            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);
//        }
        orderInfo = orderInfoService.doSeckill(phone,seckillProductVo);

        return Result.success(orderInfo.getOrderNo());
    }
    @RequestMapping("/find")
    @RequireLogin
    public Result<OrderInfo> find(String orderNo,HttpServletRequest request){
        OrderInfo orderInfo = orderInfoService.findByOrderNo(orderNo);
        //只能看自己的订单
        String token = request.getHeader(CommonConstants.TOKEN_NAME);
        String phone = UserUtil.getUserPhone(redisTemplate,token);
        if(!phone.equals(String.valueOf(orderInfo.getUserId()))){
            return Result.error(CommonCodeMsg.ILLEGAL_OPERATION);

        }
        return Result.success(orderInfo);
    }




}
