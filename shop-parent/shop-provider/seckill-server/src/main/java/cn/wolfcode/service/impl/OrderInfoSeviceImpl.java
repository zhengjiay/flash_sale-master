package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.*;
import cn.wolfcode.lock.RedisDistributedLock;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.feign.IntegralFeignApi;
import cn.wolfcode.web.feign.PayFeignApi;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
/**
 * Created by wolfcode-lanxw
 */
@Service
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;
    @Autowired
    private PayFeignApi payFeignApi;
    @Value("${pay.returnUrl}")
    private String returnlUrl;
    @Value("${pay.notifyUrl}")
    private String notifyUrl;
    @Autowired
    private IntegralFeignApi integralFeignApi;
    @Autowired
    private RedisDistributedLock redisDistributedLock;

    @Override
    public OrderInfo findByPhoneAndSeckillId(String phone, Long seckillId) {
        return orderInfoMapper.findByPhoneAndSeckillId(phone,seckillId);
    }

    @Override
    @Transactional
    public OrderInfo doSeckill(String phone, SeckillProductVo seckillProductVo) {
        //减库存
        int effectCount = seckillProductService.decrStockCount(seckillProductVo.getId());
        if (effectCount == 0){
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        //-创建订单
        OrderInfo orderInfo = createOrderInfo(phone,seckillProductVo);
       //在redis设置set集合，存储抢购成功的用户的手机号码
        String orderKey = SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(String.valueOf(seckillProductVo.getId()));
        redisTemplate.opsForSet().add(orderKey, phone);
        return orderInfo;
    }

    //使用分布式锁扣减库存
//    @Override
//    public OrderInfo doSeckillByLock(String phone, SeckillProductVo seckillProductVo){
//        Long productVoId = seckillProductVo.getId();
//        if (redisDistributedLock.acquireLock(String.valueOf(productVoId))){
//            try {
//                //减库存
//                int effectCount = seckillProductService.decrStockCount(seckillProductVo.getId());
//                //-创建订单
//                OrderInfo orderInfo = createOrderInfo(phone,seckillProductVo);
//                //在redis设置set集合，存储抢购成功的用户的手机号码
//                String orderKey = SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(String.valueOf(seckillProductVo.getId()));
//                redisTemplate.opsForSet().add(orderKey, phone);
//                return orderInfo;
//            }finally {
//                redisDistributedLock.releaseLock(String.valueOf(productVoId));;
//            }
//
//        }else{
//            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
//        }
//    }

    @Override
    public OrderInfo findByOrderNo(String orderNo) {
        return orderInfoMapper.find(orderNo);
    }


    @Override
    public Result<String> payOnline(String orderNo) {
        //根据订单号查询订单对象
        OrderInfo orderInfo = this.findByOrderNo(orderNo);
        if (OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())){
            PayVo vo = new PayVo();
            vo.setOutTradeNo(orderNo);
            vo.setTotalAmount(String.valueOf(orderInfo.getSeckillPrice()));
            vo.setSubject(orderInfo.getProductName());
            vo.setBody(orderInfo.getProductName());
            vo.setNotifyUrl(notifyUrl);
            vo.setReturnUrl(returnlUrl);
            Result<String> result = payFeignApi.payOnline(vo);
            return result;
        }
        return Result.error(SeckillCodeMsg.PAY_STATUS_CHANGE);
    }

    @Override
    public int changePayStatus(String orderNo, Integer status, int payType) {
        return orderInfoMapper.changePayStatus(orderNo,status,payType);
    }

    @Override
    public void refundOnline(OrderInfo orderInfo) {
        RefundVo vo = new RefundVo();
        vo.setOutTradeNo(orderInfo.getOrderNo());
        vo.setRefundAmount(String.valueOf(orderInfo.getSeckillPrice()));
        vo.setRefundReason("不想要了");
        Result<Boolean> result = payFeignApi.refund(vo);
        if(result==null || result.hasError() || !result.getData()){
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }
        orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_REFUND);
    }

    @Override
    @Transactional
    public void payIntegral(String orderNo) {
        //根据订单号查询订单对象
        OrderInfo orderInfo = this.findByOrderNo(orderNo);
        if (OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())){
// 处于未支付状态
            PayLog log = new PayLog();
            log.setOrderNo(orderNo);
            log.setPayTime(new Date());
            log.setTotalAmount(String.valueOf(orderInfo.getIntergral()));
            log.setPayType(OrderInfo.PAYTYPE_INTERGRAL);
            payLogMapper.insert(log);//插入支付日志记录
            //远程调用积分支付，完成积分扣减
            OperateIntergralVo vo = new OperateIntergralVo();
            vo.setUserId(orderInfo.getUserId());
            vo.setValue(orderInfo.getIntergral());
            //调用积分服务
            Result result = integralFeignApi.decrIntegral(vo);

            if(result==null || result.hasError()){
                throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
            }

            //修改订单状态
            int effectCount = orderInfoMapper.changePayStatus(orderNo,OrderInfo.STATUS_ACCOUNT_PAID,OrderInfo.PAYTYPE_INTERGRAL);
            if (effectCount==0){
                throw new BusinessException(SeckillCodeMsg.PAY_ERROR);
            }
        }
    }

    @Override
    @Transactional
    public void refundIntegral(OrderInfo orderInfo) {

        if (OrderInfo.STATUS_ACCOUNT_PAID.equals(orderInfo.getStatus())){
            //添加退款记录
            RefundLog log = new RefundLog();
            log.setOrderNo(orderInfo.getOrderNo());
            log.setRefundAmount(orderInfo.getIntergral());
            log.setRefundReason("不要了");
            log.setRefundTime(new Date());
            log.setRefundType(OrderInfo.PAYTYPE_INTERGRAL);
            refundLogMapper.insert(log);
            //远程调用积分支付，完成积分增加
            OperateIntergralVo vo = new OperateIntergralVo();
            vo.setUserId(orderInfo.getUserId());
            vo.setValue(orderInfo.getIntergral());
            //调用积分服务
            Result result = integralFeignApi.incrIntegral(vo);
            if(result==null || result.hasError()){
                throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
            }
            //修改订单状态
            int effectCount = orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(),OrderInfo.STATUS_REFUND);
            if (effectCount==0){
                throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
            }
        }
    }

    private OrderInfo createOrderInfo(String phone, SeckillProductVo seckillProductVo) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(seckillProductVo,orderInfo);
        orderInfo.setUserId(Long.parseLong(phone));
        orderInfo.setCreateDate(new Date());
        orderInfo.setDeliveryAddrId(1L);
        orderInfo.setSeckillDate(seckillProductVo.getStartDate());
        orderInfo.setSeckillTime(seckillProductVo.getTime());
        orderInfo.setOrderNo(String.valueOf(IdGenerateUtil.get().nextId()));//订单编号
        orderInfo.setSeckillId(seckillProductVo.getId());
        orderInfoMapper.insert(orderInfo);
        return orderInfo;
    }
}
