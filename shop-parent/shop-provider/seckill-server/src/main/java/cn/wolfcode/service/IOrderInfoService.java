package cn.wolfcode.service;


import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;

/**
 * Created by wolfcode-lanxw
 */
public interface IOrderInfoService {
    /**
     * 根据手机号和秒杀商品ID查询用户信息
     * @param phone
     * @param seckillId
     * @return
     */
    OrderInfo findByPhoneAndSeckillId(String phone, Long seckillId);

    /**
     * 创建秒杀订单 （扣减库存，创建秒杀订单
     * @param phone
     * @param seckillProductVo
     * @return
     */
    OrderInfo doSeckill(String phone, SeckillProductVo seckillProductVo);
//    OrderInfo doSeckillByLock(String phone, SeckillProductVo seckillProductVo);

    /**
     * 根据订单号查询订单对象
     * @param orderNo
     * @return
     */
    OrderInfo findByOrderNo(String orderNo);

    Result<String> payOnline(String orderNo);

    /**
     * 修改支付状态
     * @param orderNo
     * @param status
     * @param payType
     * @return
     */
    int changePayStatus(String orderNo, Integer status, int payType);

    /**
     * 在线退款
     * @param orderInfo
     */
    void refundOnline(OrderInfo orderInfo);

    /**
     * 积分支付
     * @param orderNo
     */
    void payIntegral(String orderNo);

    /**积分退款
     *
     * @param orderInfo
     */
    void refundIntegral(OrderInfo orderInfo);
}
