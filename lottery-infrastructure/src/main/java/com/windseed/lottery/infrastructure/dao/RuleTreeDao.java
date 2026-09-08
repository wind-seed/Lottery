package com.windseed.lottery.infrastructure.dao;

import com.windseed.lottery.infrastructure.po.RuleTree;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @description: 规则树配置DAO
 */
@Mapper
public interface RuleTreeDao {

    /**
     * 规则树查询
     * @param id ID
     * @return   规则树
     */
    RuleTree queryRuleTreeByTreeId(Long id);

    List<RuleTree> queryRuleTreeList();

    /**
     * 规则树简要信息查询
     * @param treeId 规则树ID
     * @return       规则树
     */
    RuleTree queryTreeSummaryInfo(Long treeId);

}
