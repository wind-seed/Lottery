package com.windseed.lottery.application.process.deploy.impl;

import com.windseed.lottery.application.process.deploy.IActivityDeployProcess;
import com.windseed.lottery.domain.activity.model.aggregates.ActivityInfoLimitPageRich;
import com.windseed.lottery.domain.activity.model.req.ActivityInfoLimitPageReq;
import com.windseed.lottery.domain.activity.service.deploy.IActivityDeploy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @description: 活动部署实现
 */
@Service
public class ActivityDeployProcessImpl implements IActivityDeployProcess {

    @Resource
    private IActivityDeploy activityDeploy;

    @Override
    public ActivityInfoLimitPageRich queryActivityInfoLimitPage(ActivityInfoLimitPageReq req) {
        return activityDeploy.queryActivityInfoLimitPage(req);
    }
}
