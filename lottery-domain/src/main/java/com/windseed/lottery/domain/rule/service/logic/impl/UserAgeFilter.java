package com.windseed.lottery.domain.rule.service.logic.impl;

import com.windseed.lottery.domain.rule.model.req.DecisionMatterReq;
import com.windseed.lottery.domain.rule.service.logic.BaseLogic;
import org.springframework.stereotype.Component;

/**
 * @description: 年龄规则
 */
@Component
public class UserAgeFilter extends BaseLogic {

    @Override
    public String matterValue(DecisionMatterReq decisionMatter) {
        return decisionMatter.getValMap().get("age").toString();
    }

}
