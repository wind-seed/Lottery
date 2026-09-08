package com.windseed.lottery.infrastructure.dao;

import com.windseed.lottery.infrastructure.po.Strategy;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @description: 策略表DAO
 */
@Mapper
public interface IStrategyDao {

    /**
     * 查询策略配置
     *
     * @param strategyId 策略ID
     * @return           策略配置信息
     */
    Strategy queryStrategy(Long strategyId);

    List<Strategy> queryStrategyList();

    /**
     * 插入策略配置
     *
     * @param req 策略配置
     */
    void insert(Strategy req);

}
