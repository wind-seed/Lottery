package com.windseed.lottery.interfaces.facade;

import com.windseed.lottery.application.process.draw.IActivityDrawProcess;
import com.windseed.lottery.application.process.draw.req.DrawProcessReq;
import com.windseed.lottery.application.process.draw.res.DrawProcessResult;
import com.windseed.lottery.application.process.draw.res.RuleQuantificationCrowdResult;
import com.windseed.lottery.common.Constants;
import com.windseed.lottery.domain.rule.model.req.DecisionMatterReq;
import com.windseed.lottery.domain.strategy.model.vo.DrawAwardVO;
import com.windseed.lottery.interfaces.assembler.IMapping;
import com.windseed.lottery.rpc.activity.booth.ILotteryActivityBooth;
import com.windseed.lottery.rpc.activity.booth.dto.AwardDTO;
import com.windseed.lottery.rpc.activity.booth.req.DrawReq;
import com.windseed.lottery.rpc.activity.booth.req.QuantificationDrawReq;
import com.windseed.lottery.rpc.activity.booth.res.DrawRes;
import com.alibaba.fastjson.JSON;
import org.apache.dubbo.config.annotation.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

/**
 * @description: 抽奖活动展台
 */
@Service
public class LotteryActivityBooth implements ILotteryActivityBooth {

    private Logger logger = LoggerFactory.getLogger(LotteryActivityBooth.class);

    @Resource
    private IActivityDrawProcess activityProcess;

    @Resource
    private IMapping<DrawAwardVO, AwardDTO> awardMapping;

    @Override
    public DrawRes doDraw(DrawReq drawReq) {
        try {
            logger.info("抽奖，开始 uId：{} activityId：{}", drawReq.getuId(), drawReq.getActivityId());

            // 1. 执行抽奖
            DrawProcessResult drawProcessResult = activityProcess.doDrawProcess(new DrawProcessReq(drawReq.getuId(), drawReq.getActivityId()));
            if (!Constants.ResponseCode.SUCCESS.getCode().equals(drawProcessResult.getCode())) {
                logger.warn("抽奖，失败 uId：{} activityId：{} code：{} info：{}", drawReq.getuId(), drawReq.getActivityId(), drawProcessResult.getCode(), drawProcessResult.getInfo());
                return new DrawRes(drawProcessResult.getCode(), drawProcessResult.getInfo());
            }

            // 2. 数据转换
            DrawAwardVO drawAwardVO = drawProcessResult.getDrawAwardVO();
            AwardDTO awardDTO = awardMapping.sourceToTarget(drawAwardVO);
            awardDTO.setActivityId(drawReq.getActivityId());

            // 3. 封装数据
            DrawRes drawRes = new DrawRes(Constants.ResponseCode.SUCCESS.getCode(), Constants.ResponseCode.SUCCESS.getInfo());
            drawRes.setAwardDTO(awardDTO);

            logger.info("抽奖，完成 uId：{} activityId：{} drawRes：{}", drawReq.getuId(), drawReq.getActivityId(), JSON.toJSONString(drawRes));

            return drawRes;
        } catch (Exception e) {
            logger.error("抽奖，失败 uId：{} activityId：{} reqJson：{}", drawReq.getuId(), drawReq.getActivityId(), JSON.toJSONString(drawReq), e);
            return new DrawRes(Constants.ResponseCode.UN_ERROR.getCode(), buildExceptionInfo("抽奖服务异常", e));
        }
    }

    @Override
    public DrawRes doQuantificationDraw(QuantificationDrawReq quantificationDrawReq) {
        try {
            logger.info("量化人群抽奖，开始 uId：{} treeId：{}", quantificationDrawReq.getuId(), quantificationDrawReq.getTreeId());

            // 1. 执行规则引擎，获取用户可以参与的活动号
            RuleQuantificationCrowdResult ruleQuantificationCrowdResult = activityProcess.doRuleQuantificationCrowd(new DecisionMatterReq(quantificationDrawReq.getuId(), quantificationDrawReq.getTreeId(), quantificationDrawReq.getValMap()));
            if (!Constants.ResponseCode.SUCCESS.getCode().equals(ruleQuantificationCrowdResult.getCode())) {
                logger.warn("量化人群抽奖，规则执行失败 uId：{} treeId：{} code：{} info：{}", quantificationDrawReq.getuId(), quantificationDrawReq.getTreeId(), ruleQuantificationCrowdResult.getCode(), ruleQuantificationCrowdResult.getInfo());
                return new DrawRes(ruleQuantificationCrowdResult.getCode(), ruleQuantificationCrowdResult.getInfo());
            }

            // 2. 执行抽奖
            Long activityId = ruleQuantificationCrowdResult.getActivityId();
            DrawProcessResult drawProcessResult = activityProcess.doDrawProcess(new DrawProcessReq(quantificationDrawReq.getuId(), activityId));
            if (!Constants.ResponseCode.SUCCESS.getCode().equals(drawProcessResult.getCode())) {
                logger.warn("量化人群抽奖，失败 uId：{} treeId：{} activityId：{} code：{} info：{}", quantificationDrawReq.getuId(), quantificationDrawReq.getTreeId(), activityId, drawProcessResult.getCode(), drawProcessResult.getInfo());
                return new DrawRes(drawProcessResult.getCode(), drawProcessResult.getInfo());
            }

            // 3. 数据转换
            DrawAwardVO drawAwardVO = drawProcessResult.getDrawAwardVO();
            AwardDTO awardDTO = awardMapping.sourceToTarget(drawAwardVO);
            awardDTO.setActivityId(activityId);

            // 4. 封装数据
            DrawRes drawRes = new DrawRes(Constants.ResponseCode.SUCCESS.getCode(), Constants.ResponseCode.SUCCESS.getInfo());
            drawRes.setAwardDTO(awardDTO);

            logger.info("量化人群抽奖，完成 uId：{} treeId：{} drawRes：{}", quantificationDrawReq.getuId(), quantificationDrawReq.getTreeId(), JSON.toJSONString(drawRes));

            return drawRes;
        } catch (Exception e) {
            logger.error("量化人群抽奖，失败 uId：{} treeId：{} reqJson：{}", quantificationDrawReq.getuId(), quantificationDrawReq.getTreeId(), JSON.toJSONString(quantificationDrawReq), e);
            return new DrawRes(Constants.ResponseCode.UN_ERROR.getCode(), buildExceptionInfo("量化人群抽奖服务异常", e));
        }
    }

    private String buildExceptionInfo(String stage, Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (null == message || message.trim().isEmpty()) {
            message = root.getClass().getSimpleName();
        }
        return stage + "：" + message;
    }

}
