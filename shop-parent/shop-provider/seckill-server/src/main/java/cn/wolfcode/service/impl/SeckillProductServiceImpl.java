package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mapper.SeckillProductMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.web.feign.ProductFeignApi;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * Created by lanxw
 */
@Service
public class SeckillProductServiceImpl implements ISeckillProductService {
    @Autowired
    private SeckillProductMapper seckillProductMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private ProductFeignApi productFeignApi;

    @Override
    public List<SeckillProductVo> queryByTime(Integer time) {
        // 查询秒杀的商品-遍历获取商品ID－远程调用获取商品集合-封装VO并返回
        List<SeckillProduct> seckillProductList = seckillProductMapper.queryCurrentlySeckillProduct(time);
        if(seckillProductList.size()==0){
            return Collections.EMPTY_LIST;
        }
        List<Long> productIds = new ArrayList<>();
        for(SeckillProduct seckillProduct: seckillProductList){
            productIds.add(seckillProduct.getProductId());
        }
        Result<List<Product>> result = productFeignApi.queryByIds(productIds);
        if(result == null || result.hasError()){
            throw new BusinessException(SeckillCodeMsg.PRODUCT_SERVER_ERROR);
        }
        List<Product> productList = result.getData();
        Map<Long,Product> productMap = new HashMap<>();
        for(Product product:productList){
            productMap.put(product.getId(),product);
        }
        List<SeckillProductVo> seckillProductVoList = new ArrayList<>();
        for(SeckillProduct seckillProduct:seckillProductList){
            SeckillProductVo vo = new SeckillProductVo();
            Product product = productMap.get(seckillProduct.getProductId());
            BeanUtils.copyProperties(product,vo);
            BeanUtils.copyProperties(seckillProduct,vo);
            vo.setCurrentCount(seckillProduct.getStockCount());
            seckillProductVoList.add(vo);
        }
        return seckillProductVoList;
    }

    @Override
    public SeckillProductVo find(Integer time, Long seckillId) {
        SeckillProduct seckillProduct = seckillProductMapper.find(seckillId);
        List<Long> productIds = new ArrayList<>();
        productIds.add(seckillProduct.getProductId());
        Result<List<Product>> result = productFeignApi.queryByIds(productIds);
        if(result==null || result.hasError()){
            throw new BusinessException(SeckillCodeMsg.PRODUCT_SERVER_ERROR);
        }
        Product product = result.getData().get(0);
        SeckillProductVo vo = new SeckillProductVo();
        BeanUtils.copyProperties(product,vo);
        BeanUtils.copyProperties(seckillProduct,vo);
        vo.setCurrentCount(seckillProduct.getStockCount());
        return vo;
    }

    @Override
    public int decrStockCount(Long seckillId) {
        return seckillProductMapper.decrStock(seckillId);
    }

    @Override
    public List<SeckillProductVo> queryByTimeFromCache(Integer time) {
        String key = SeckillRedisKey.SECKILL_PRODUCT_HASH.getRealKey(String.valueOf(time));
        List<Object> objectList =redisTemplate.opsForHash().values(key);
        List<SeckillProductVo> seckillProductVoList = new ArrayList<>();
        for (Object objStr:objectList){
            seckillProductVoList.add(JSON.parseObject((String)objStr,SeckillProductVo.class));
        }
        return seckillProductVoList;
    }

    @Override
    public SeckillProductVo findFromCache(Integer time, Long seckillId) {
        String key = SeckillRedisKey.SECKILL_PRODUCT_HASH.getRealKey(String.valueOf(time));
        Object strObj = redisTemplate.opsForHash().get(key, String.valueOf(seckillId));
        SeckillProductVo vo = JSON.parseObject((String)strObj, SeckillProductVo.class);
        return vo;
    }
}
