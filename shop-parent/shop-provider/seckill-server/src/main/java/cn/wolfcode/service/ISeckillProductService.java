package cn.wolfcode.service;

import cn.wolfcode.domain.SeckillProductVo;

import java.util.List;

/**
 * Created by lanxw
 */
public interface ISeckillProductService {
    // 查询秒杀列表的数据
    List<SeckillProductVo> queryByTime(Integer time);
// 根据秒杀场次和秒杀商品ID查询秒杀商品VO对象
    SeckillProductVo find(Integer time, Long seckillId);

    /**
     * 扣减库存
     * @param seckillId
     */
    int decrStockCount(Long seckillId);

    /**
     * 从缓存中获取数据
     * @param time
     * @return
     */
    List<SeckillProductVo> queryByTimeFromCache(Integer time);

    SeckillProductVo findFromCache(Integer time, Long seckillId);
}
