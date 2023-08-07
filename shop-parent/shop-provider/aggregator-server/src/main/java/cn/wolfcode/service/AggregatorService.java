package cn.wolfcode.service;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.domain.Product;
import cn.wolfcode.web.feign.PayFeignApi;
import cn.wolfcode.web.feign.ProductFeignApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Future;

@Service
public class AggregatorService {
    @Autowired
    private PayFeignApi payFeignApi;
    @Autowired
    private ProductFeignApi productFeignApi;

    @Async("asyncExecutor") // 使用自定义的线程池进行异步调用
    public Future<String> processProduct(List<Long> productIds) {
    // 调用订单服务
        Result<List<Product>> proIds = productFeignApi.queryByIds(productIds);
        System.out.println("商品ID：" + productIds);
        return new AsyncResult(productIds);
}

    @Async("asyncExecutor") // 使用自定义的线程池进行异步调用
    public Future<String> processPayment(PayVo vo) {
        // 调用支付服务
        Result<String> paymentId = payFeignApi.payOnline(vo);
        System.out.println("支付成功，支付ID：" + paymentId);
        return new AsyncResult(paymentId);
    }


}
