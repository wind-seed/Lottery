package com.windseed.lottery.application.process.draw.impl;

import com.windseed.lottery.application.mq.producer.KafkaProducer;
import com.windseed.lottery.application.process.draw.IActivityDrawProcess;
import com.windseed.lottery.application.process.draw.req.DrawProcessReq;
import com.windseed.lottery.application.process.draw.res.DrawProcessResult;
import com.windseed.lottery.application.process.draw.res.RuleQuantificationCrowdResult;
import com.windseed.lottery.common.Constants;
import com.windseed.lottery.common.Result;
import com.windseed.lottery.domain.activity.model.req.PartakeReq;
import com.windseed.lottery.domain.activity.model.res.PartakeResult;
import com.windseed.lottery.domain.activity.model.vo.ActivityPartakeRecordVO;
import com.windseed.lottery.domain.activity.model.vo.DrawOrderVO;
import com.windseed.lottery.domain.activity.model.vo.InvoiceVO;
import com.windseed.lottery.domain.activity.service.partake.IActivityPartake;
import com.windseed.lottery.domain.award.model.req.GoodsReq;
import com.windseed.lottery.domain.award.model.res.DistributionRes;
import com.windseed.lottery.domain.award.service.factory.DistributionGoodsFactory;
import com.windseed.lottery.domain.award.service.goods.IDistributionGoods;
import com.windseed.lottery.domain.rule.model.req.DecisionMatterReq;
import com.windseed.lottery.domain.rule.model.res.EngineResult;
import com.windseed.lottery.domain.rule.service.engine.EngineFilter;
import com.windseed.lottery.domain.strategy.model.req.DrawReq;
import com.windseed.lottery.domain.strategy.model.res.DrawResult;
import com.windseed.lottery.domain.strategy.model.vo.DrawAwardVO;
import com.windseed.lottery.domain.strategy.service.draw.IDrawExec;
import com.windseed.lottery.domain.support.ids.IIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @description: 活动抽奖流程编排
 */
@Service
public class ActivityDrawProcessImpl implements IActivityDrawProcess {

    private Logger logger = LoggerFactory.getLogger(ActivityDrawProcessImpl.class);

    @Resource
    private IActivityPartake activityPartake;

    @Resource
    private IDrawExec drawExec;

    @Resource(name = "ruleEngineHandle")
    private EngineFilter engineFilter;

    @Resource
    private Map<Constants.Ids, IIdGenerator> idGeneratorMap;

    @Resource
    private KafkaProducer kafkaProducer;

    @Resource
    private DistributionGoodsFactory distributionGoodsFactory;

    @Value("${lottery.kafka.producer.enabled:true}")
    private boolean kafkaProducerEnabled;

    @Override
    public DrawProcessResult doDrawProcess(DrawProcessReq req) {
        // 1. 领取活动
        PartakeResult partakeResult = activityPartake.doPartake(new PartakeReq(req.getuId(), req.getActivityId()));
        if (!Constants.ResponseCode.SUCCESS.getCode().equals(partakeResult.getCode()) && !Constants.ResponseCode.NOT_CONSUMED_TAKE.getCode().equals(partakeResult.getCode())) {
            return new DrawProcessResult(partakeResult.getCode(), partakeResult.getInfo());
        }

        // 2. 首次成功领取活动，发送 MQ 消息
        if (Constants.ResponseCode.SUCCESS.getCode().equals(partakeResult.getCode())) {
            ActivityPartakeRecordVO activityPartakeRecord = new ActivityPartakeRecordVO();
            activityPartakeRecord.setuId(req.getuId());
            activityPartakeRecord.setActivityId(req.getActivityId());
            activityPartakeRecord.setStockCount(partakeResult.getStockCount());
            activityPartakeRecord.setStockSurplusCount(partakeResult.getStockSurplusCount());
            // 发送 MQ 消息
            sendLotteryActivityPartakeRecord(activityPartakeRecord);
        }

        Long strategyId = partakeResult.getStrategyId();
        Long takeId = partakeResult.getTakeId();

        // 3. 执行抽奖
        DrawResult drawResult = drawExec.doDrawExec(new DrawReq(req.getuId(), strategyId));
        if (Constants.DrawState.FAIL.getCode().equals(drawResult.getDrawState())) {
            // TODO 需要做个优化，如果用户是正常流程未中奖。那么应该扣掉当前抽奖单，修改状态为1 感谢星球伙伴 @忘谷茄茄 发现bug
            Result result = activityPartake.lockTackActivity(req.getuId(), req.getActivityId(), takeId);
            if (!Constants.ResponseCode.SUCCESS.getCode().equals(result.getCode())) {
                return new DrawProcessResult(result.getCode(), buildDrawFailureInfo("未中奖结果落库失败", req, takeId, result.getInfo()));
            }
            return new DrawProcessResult(Constants.ResponseCode.LOSING_DRAW.getCode(), Constants.ResponseCode.LOSING_DRAW.getInfo());
        }
        DrawAwardVO drawAwardVO = drawResult.getDrawAwardInfo();

        // 4. 结果落库
        DrawOrderVO drawOrderVO = buildDrawOrderVO(req, strategyId, takeId, drawAwardVO);
        Result recordResult = activityPartake.recordDrawOrder(drawOrderVO);
        if (!Constants.ResponseCode.SUCCESS.getCode().equals(recordResult.getCode())) {
            return new DrawProcessResult(recordResult.getCode(), buildDrawFailureInfo("中奖结果落库失败", req, takeId, recordResult.getInfo()));
        }

        // 5. 发送MQ，触发发奖流程
        InvoiceVO invoiceVO = buildInvoiceVO(drawOrderVO);
        Result invoiceResult = sendLotteryInvoice(invoiceVO);
        if (!Constants.ResponseCode.SUCCESS.getCode().equals(invoiceResult.getCode())) {
            return new DrawProcessResult(invoiceResult.getCode(), buildDrawFailureInfo("发奖流程触发失败", req, takeId, invoiceResult.getInfo()));
        }
        /*
        future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {

            @Override
            public void onSuccess(SendResult<String, Object> stringObjectSendResult) {
                // 4.1 MQ 消息发送完成，更新数据库表 user_strategy_export.mq_state = 1
                activityPartake.updateInvoiceMqState(invoiceVO.getuId(), invoiceVO.getOrderId(), Constants.MQState.COMPLETE.getCode());
            }

            @Override
            public void onFailure(Throwable throwable) {
                // 4.2 MQ 消息发送失败，更新数据库表 user_strategy_export.mq_state = 2 【等待定时任务扫码补偿MQ消息】
                activityPartake.updateInvoiceMqState(invoiceVO.getuId(), invoiceVO.getOrderId(), Constants.MQState.FAIL.getCode());
            }

        });
        */

        // 6. 返回结果
        return new DrawProcessResult(Constants.ResponseCode.SUCCESS.getCode(), Constants.ResponseCode.SUCCESS.getInfo(), drawAwardVO);
    }

    private void sendLotteryActivityPartakeRecord(ActivityPartakeRecordVO activityPartakeRecord) {
        if (kafkaProducerEnabled) {
            kafkaProducer.sendLotteryActivityPartakeRecord(activityPartakeRecord);
            return;
        }
        activityPartake.updateActivityStock(activityPartakeRecord);
    }

    private String buildDrawFailureInfo(String stage, DrawProcessReq req, Long takeId, String detail) {
        return String.format("%s：%s；用户ID=%s，活动ID=%s，领取ID=%s",
                stage,
                null == detail || detail.trim().isEmpty() ? "请查看服务日志定位具体原因" : detail,
                req.getuId(),
                req.getActivityId(),
                takeId);
    }

    private Result sendLotteryInvoice(InvoiceVO invoiceVO) {
        if (!kafkaProducerEnabled) {
            try {
                IDistributionGoods distributionGoodsService = distributionGoodsFactory.getDistributionGoodsService(invoiceVO.getAwardType());
                if (null == distributionGoodsService) {
                    activityPartake.updateInvoiceMqState(invoiceVO.getuId(), invoiceVO.getOrderId(), Constants.MQState.FAIL.getCode());
                    return Result.buildErrorResult("未找到奖品发放服务");
                }

                DistributionRes distributionRes = distributionGoodsService.doDistribution(new GoodsReq(invoiceVO.getuId(), invoiceVO.getOrderId(), invoiceVO.getAwardId(), invoiceVO.getAwardName(), invoiceVO.getAwardContent()));
                if (!Constants.AwardState.SUCCESS.getCode().equals(distributionRes.getCode())) {
                    activityPartake.updateInvoiceMqState(invoiceVO.getuId(), invoiceVO.getOrderId(), Constants.MQState.FAIL.getCode());
                    return Result.buildErrorResult(distributionRes.getInfo());
                }

                activityPartake.updateInvoiceMqState(invoiceVO.getuId(), invoiceVO.getOrderId(), Constants.MQState.COMPLETE.getCode());
                return Result.buildSuccessResult();
            } catch (Exception e) {
                logger.error("本地同步发奖失败 uId：{} orderId：{}", invoiceVO.getuId(), invoiceVO.getOrderId(), e);
                activityPartake.updateInvoiceMqState(invoiceVO.getuId(), invoiceVO.getOrderId(), Constants.MQState.FAIL.getCode());
                return Result.buildErrorResult("本地同步发奖失败");
            }
        }

        ListenableFuture<SendResult<String, Object>> future = kafkaProducer.sendLotteryInvoice(invoiceVO);
        future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {

            @Override
            public void onSuccess(SendResult<String, Object> stringObjectSendResult) {
                activityPartake.updateInvoiceMqState(invoiceVO.getuId(), invoiceVO.getOrderId(), Constants.MQState.COMPLETE.getCode());
            }

            @Override
            public void onFailure(Throwable throwable) {
                activityPartake.updateInvoiceMqState(invoiceVO.getuId(), invoiceVO.getOrderId(), Constants.MQState.FAIL.getCode());
            }

        });
        return Result.buildSuccessResult();
    }

    @Override
    public RuleQuantificationCrowdResult doRuleQuantificationCrowd(DecisionMatterReq req) {
        // 1. 量化决策
        EngineResult engineResult = engineFilter.process(req);

        if (!engineResult.isSuccess()) {
            return new RuleQuantificationCrowdResult(Constants.ResponseCode.RULE_ERR.getCode(), Constants.ResponseCode.RULE_ERR.getInfo());
        }

        // 2. 封装结果
        RuleQuantificationCrowdResult ruleQuantificationCrowdResult = new RuleQuantificationCrowdResult(Constants.ResponseCode.SUCCESS.getCode(), Constants.ResponseCode.SUCCESS.getInfo());
        ruleQuantificationCrowdResult.setActivityId(Long.valueOf(engineResult.getNodeValue()));

        return ruleQuantificationCrowdResult;
    }

    private DrawOrderVO buildDrawOrderVO(DrawProcessReq req, Long strategyId, Long takeId, DrawAwardVO drawAwardVO) {
        long orderId = idGeneratorMap.get(Constants.Ids.SnowFlake).nextId();
        DrawOrderVO drawOrderVO = new DrawOrderVO();
        drawOrderVO.setuId(req.getuId());
        drawOrderVO.setTakeId(takeId);
        drawOrderVO.setActivityId(req.getActivityId());
        drawOrderVO.setOrderId(orderId);
        drawOrderVO.setStrategyId(strategyId);
        drawOrderVO.setStrategyMode(drawAwardVO.getStrategyMode());
        drawOrderVO.setGrantType(drawAwardVO.getGrantType());
        drawOrderVO.setGrantDate(drawAwardVO.getGrantDate());
        drawOrderVO.setGrantState(Constants.GrantState.INIT.getCode());
        drawOrderVO.setAwardId(drawAwardVO.getAwardId());
        drawOrderVO.setAwardType(drawAwardVO.getAwardType());
        drawOrderVO.setAwardName(drawAwardVO.getAwardName());
        drawOrderVO.setAwardContent(drawAwardVO.getAwardContent());
        return drawOrderVO;
    }

    private InvoiceVO buildInvoiceVO(DrawOrderVO drawOrderVO) {
        InvoiceVO invoiceVO = new InvoiceVO();
        invoiceVO.setuId(drawOrderVO.getuId());
        invoiceVO.setOrderId(drawOrderVO.getOrderId());
        invoiceVO.setAwardId(drawOrderVO.getAwardId());
        invoiceVO.setAwardType(drawOrderVO.getAwardType());
        invoiceVO.setAwardName(drawOrderVO.getAwardName());
        invoiceVO.setAwardContent(drawOrderVO.getAwardContent());
        invoiceVO.setShippingAddress(null);
        invoiceVO.setExtInfo(null);
        return invoiceVO;
    }

}
