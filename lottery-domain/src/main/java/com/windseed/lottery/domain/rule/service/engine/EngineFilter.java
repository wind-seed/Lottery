package com.windseed.lottery.domain.rule.service.engine;

import com.windseed.lottery.domain.rule.model.res.EngineResult;
import com.windseed.lottery.domain.rule.model.req.DecisionMatterReq;

/**
 * @description: 规则过滤器引擎
 */
public interface EngineFilter {

    /**
     * 规则过滤器接口
     *
     * @param matter      规则决策物料
     * @return            规则决策结果
     */
    EngineResult process(final DecisionMatterReq matter);

}
