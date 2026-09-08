package com.windseed.lottery.infrastructure.repository;

import com.windseed.lottery.domain.strategy.model.aggregates.StrategyRich;
import com.windseed.lottery.domain.strategy.model.vo.AwardBriefVO;
import com.windseed.lottery.domain.strategy.model.vo.StrategyBriefVO;
import com.windseed.lottery.domain.strategy.model.vo.StrategyDetailBriefVO;
import com.windseed.lottery.domain.strategy.repository.IStrategyRepository;
import com.windseed.lottery.infrastructure.dao.IAwardDao;
import com.windseed.lottery.infrastructure.dao.IStrategyDao;
import com.windseed.lottery.infrastructure.dao.IStrategyDetailDao;
import com.windseed.lottery.infrastructure.po.Award;
import com.windseed.lottery.infrastructure.po.Strategy;
import com.windseed.lottery.infrastructure.po.StrategyDetail;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @description: 策略表仓储服务
 */
@Repository
public class StrategyRepository implements IStrategyRepository {

    @Resource
    private IStrategyDao strategyDao;

    @Resource
    private IStrategyDetailDao strategyDetailDao;

    @Resource
    private IAwardDao awardDao;

    @Override
    public StrategyRich queryStrategyRich(Long strategyId) {
        Strategy strategy = strategyDao.queryStrategy(strategyId);
        List<StrategyDetail> strategyDetailList = strategyDetailDao.queryStrategyDetailList(strategyId);

        StrategyBriefVO strategyBriefVO = new StrategyBriefVO();
        strategyBriefVO.setStrategyId(strategy.getStrategyId());
        strategyBriefVO.setStrategyDesc(strategy.getStrategyDesc());
        strategyBriefVO.setStrategyMode(strategy.getStrategyMode());
        strategyBriefVO.setGrantType(strategy.getGrantType());
        strategyBriefVO.setGrantDate(strategy.getGrantDate());
        strategyBriefVO.setExtInfo(strategy.getExtInfo());

        List<StrategyDetailBriefVO> strategyDetailBriefVOList = new ArrayList<>();
        for (StrategyDetail strategyDetail : strategyDetailList) {
            StrategyDetailBriefVO  strategyDetailBriefVO = new StrategyDetailBriefVO();
            strategyDetailBriefVO.setStrategyId(strategyDetail.getStrategyId());
            strategyDetailBriefVO.setAwardId(strategyDetail.getAwardId());
            strategyDetailBriefVO.setAwardName(strategyDetail.getAwardName());
            strategyDetailBriefVO.setAwardCount(strategyDetail.getAwardCount());
            strategyDetailBriefVO.setAwardSurplusCount(strategyDetail.getAwardSurplusCount());
            strategyDetailBriefVO.setAwardRate(strategyDetail.getAwardRate());
            strategyDetailBriefVOList.add(strategyDetailBriefVO);
        }

        return new StrategyRich(strategyId, strategyBriefVO, strategyDetailBriefVOList);
    }

    @Override
    public AwardBriefVO queryAwardInfo(String awardId) {

        Award award = awardDao.queryAwardInfo(awardId);

        // 可以使用 BeanUtils.copyProperties(award, awardBriefVO)、或者基于ASM实现的Bean-Mapping，但在效率上最好的依旧是硬编码
        // 以使用自研 vo2dto 插件，帮助生成 x.set(y.get) 插件安装包：vo2dto tool

        AwardBriefVO awardBriefVO = new AwardBriefVO();
        awardBriefVO.setAwardId(award.getAwardId());
        awardBriefVO.setAwardType(award.getAwardType());
        awardBriefVO.setAwardName(award.getAwardName());
        awardBriefVO.setAwardContent(award.getAwardContent());

        return awardBriefVO;
    }

    @Override
    public List<String> queryNoStockStrategyAwardList(Long strategyId) {
        return strategyDetailDao.queryNoStockStrategyAwardList(strategyId);
    }

    @Override
    public boolean deductStock(Long strategyId, String awardId) {
        StrategyDetail req = new StrategyDetail();
        req.setStrategyId(strategyId);
        req.setAwardId(awardId);
        int count = strategyDetailDao.deductStock(req);
        return count == 1;
    }

}
