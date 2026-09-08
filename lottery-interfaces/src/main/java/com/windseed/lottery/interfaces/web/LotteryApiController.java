package com.windseed.lottery.interfaces.web;

import com.windseed.lottery.common.Constants;
import com.windseed.lottery.common.Result;
import com.windseed.lottery.domain.activity.model.aggregates.ActivityConfigRich;
import com.windseed.lottery.domain.activity.model.req.ActivityConfigReq;
import com.windseed.lottery.domain.activity.model.req.PartakeReq;
import com.windseed.lottery.domain.activity.model.res.PartakeResult;
import com.windseed.lottery.domain.activity.model.vo.ActivityVO;
import com.windseed.lottery.domain.activity.model.vo.AwardVO;
import com.windseed.lottery.domain.activity.model.vo.InvoiceVO;
import com.windseed.lottery.domain.activity.model.vo.StrategyDetailVO;
import com.windseed.lottery.domain.activity.model.vo.StrategyVO;
import com.windseed.lottery.domain.activity.service.deploy.IActivityDeploy;
import com.windseed.lottery.domain.activity.service.partake.IActivityPartake;
import com.windseed.lottery.domain.activity.service.stateflow.IStateHandler;
import com.windseed.lottery.domain.award.model.req.GoodsReq;
import com.windseed.lottery.domain.award.model.res.DistributionRes;
import com.windseed.lottery.domain.award.service.factory.DistributionGoodsFactory;
import com.windseed.lottery.domain.award.service.goods.IDistributionGoods;
import com.windseed.lottery.domain.rule.model.req.DecisionMatterReq;
import com.windseed.lottery.domain.rule.model.res.EngineResult;
import com.windseed.lottery.domain.rule.repository.IRuleRepository;
import com.windseed.lottery.domain.rule.service.engine.EngineFilter;
import com.windseed.lottery.domain.strategy.model.aggregates.StrategyRich;
import com.windseed.lottery.domain.strategy.model.vo.AwardBriefVO;
import com.windseed.lottery.domain.strategy.repository.IStrategyRepository;
import com.windseed.lottery.infrastructure.dao.IAwardDao;
import com.windseed.lottery.infrastructure.dao.IStrategyDao;
import com.windseed.lottery.infrastructure.dao.IUserStrategyExportDao;
import com.windseed.lottery.infrastructure.dao.IUserTakeActivityCountDao;
import com.windseed.lottery.infrastructure.dao.IUserTakeActivityDao;
import com.windseed.lottery.infrastructure.dao.RuleTreeDao;
import com.windseed.lottery.infrastructure.dao.RuleTreeNodeDao;
import com.windseed.lottery.infrastructure.dao.RuleTreeNodeLineDao;
import com.windseed.lottery.infrastructure.po.Award;
import com.windseed.lottery.infrastructure.po.RuleTree;
import com.windseed.lottery.infrastructure.po.RuleTreeNode;
import com.windseed.lottery.infrastructure.po.RuleTreeNodeLine;
import com.windseed.lottery.infrastructure.po.Strategy;
import com.windseed.lottery.infrastructure.po.UserStrategyExport;
import com.windseed.lottery.infrastructure.po.UserTakeActivity;
import com.windseed.lottery.infrastructure.po.UserTakeActivityCount;
import com.windseed.lottery.rpc.activity.booth.ILotteryActivityBooth;
import com.windseed.lottery.rpc.activity.booth.req.DrawReq;
import com.windseed.lottery.rpc.activity.booth.req.QuantificationDrawReq;
import com.windseed.lottery.rpc.activity.booth.res.DrawRes;
import com.windseed.lottery.rpc.activity.deploy.ILotteryActivityDeploy;
import com.windseed.lottery.rpc.activity.deploy.dto.ActivityDTO;
import com.windseed.lottery.rpc.activity.deploy.req.ActivityPageReq;
import com.windseed.lottery.rpc.activity.deploy.res.ActivityRes;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lottery")
public class LotteryApiController {

    @Resource
    private ILotteryActivityDeploy lotteryActivityDeploy;

    @Resource
    private ILotteryActivityBooth lotteryActivityBooth;

    @Resource
    private IActivityDeploy activityDeploy;

    @Resource
    private IActivityPartake activityPartake;

    @Resource
    private IStateHandler stateHandler;

    @Resource(name = "ruleEngineHandle")
    private EngineFilter engineFilter;

    @Resource
    private IRuleRepository ruleRepository;

    @Resource
    private IStrategyRepository strategyRepository;

    @Resource
    private IStrategyDao strategyDao;

    @Resource
    private IAwardDao awardDao;

    @Resource
    private RuleTreeDao ruleTreeDao;

    @Resource
    private RuleTreeNodeDao ruleTreeNodeDao;

    @Resource
    private RuleTreeNodeLineDao ruleTreeNodeLineDao;

    @Resource
    private IUserTakeActivityDao userTakeActivityDao;

    @Resource
    private IUserTakeActivityCountDao userTakeActivityCountDao;

    @Resource
    private IUserStrategyExportDao userStrategyExportDao;

    @Resource
    private DistributionGoodsFactory distributionGoodsFactory;

    @Resource
    private Environment environment;

    @ExceptionHandler(Exception.class)
    public ApiResponse<String> handleException(Exception e) {
        return ApiResponse.fail(e.getMessage() == null ? "系统异常" : e.getMessage());
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("time", new Date());
        return ApiResponse.ok(data);
    }

    @GetMapping("/metadata")
    public ApiResponse<Map<String, Object>> metadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activityStates", options(new Object[][]{
                {1, "编辑"}, {2, "提审"}, {3, "撤审"}, {4, "通过"},
                {5, "运行"}, {6, "拒绝"}, {7, "关闭"}, {8, "开启"}
        }));
        data.put("stateActions", options(new Object[][]{
                {"arraignment", "提审"}, {"checkPass", "审核通过"}, {"checkRefuse", "审核拒绝"},
                {"checkRevoke", "撤销审核"}, {"doing", "运行活动"}, {"close", "关闭活动"}, {"open", "开启活动"}
        }));
        data.put("strategyModes", options(new Object[][]{{1, "单项概率"}, {2, "总体概率"}}));
        data.put("awardTypes", options(new Object[][]{{1, "文字描述"}, {2, "兑换码"}, {3, "优惠券"}, {4, "实物奖品"}}));
        data.put("grantTypes", options(new Object[][]{{1, "即时"}, {2, "定时"}, {3, "人工"}}));
        data.put("ruleLimitTypes", options(new Object[][]{{1, "="}, {2, ">"}, {3, "<"}, {4, ">="}, {5, "<="}, {6, "枚举"}}));
        return ApiResponse.ok(data);
    }

    @GetMapping("/diagnostics")
    public ApiResponse<Map<String, Object>> diagnostics() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("time", new Date());
        data.put("runtime", runtimeInfo());
        data.put("database", databaseInfo());

        Map<String, Object> counts = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        boolean databaseConnected = true;
        try {
            ActivityPageReq req = new ActivityPageReq(1, 100);
            req.setErpId("diagnostics");
            ActivityRes activityRes = lotteryActivityDeploy.queryActivityListByPageForErp(req);
            List<ActivityDTO> activities = activityRes.getActivityDTOList() == null ? new ArrayList<ActivityDTO>() : activityRes.getActivityDTOList();
            Date now = new Date();
            int running = 0;
            int expiredRunning = 0;
            int runnable = 0;
            for (ActivityDTO activity : activities) {
                if (Constants.ActivityState.DOING.getCode().equals(activity.getState())) {
                    running++;
                    if (activity.getEndDateTime() != null && activity.getEndDateTime().before(now)) {
                        expiredRunning++;
                    }
                    if ((activity.getBeginDateTime() == null || !activity.getBeginDateTime().after(now))
                            && (activity.getEndDateTime() == null || !activity.getEndDateTime().before(now))
                            && activity.getStockSurplusCount() != null && activity.getStockSurplusCount() > 0) {
                        runnable++;
                    }
                }
            }
            counts.put("activities", activityRes.getCount() == null ? activities.size() : activityRes.getCount());
            counts.put("runningActivities", running);
            counts.put("runnableActivities", runnable);
            counts.put("expiredRunningActivities", expiredRunning);
            counts.put("strategies", strategyDao.queryStrategyList().size());
            counts.put("awards", awardDao.queryAwardList().size());
            counts.put("ruleTrees", ruleTreeDao.queryRuleTreeList().size());

            if (expiredRunning > 0) {
                warnings.add("存在已过期但仍为运行状态的活动，建议执行任务页的活动扫描。");
            }
            if (runnable == 0) {
                warnings.add("当前没有可直接抽奖的有效活动，建议先在创建页生成一个未来活动并流转到运行状态。");
            }
        } catch (Exception e) {
            databaseConnected = false;
            warnings.add("数据库检查失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }

        data.put("databaseConnected", databaseConnected);
        data.put("counts", counts);
        data.put("warnings", warnings);
        data.put("localMode", Boolean.TRUE);
        data.put("kafkaProducerEnabled", environment.getProperty("lottery.kafka.producer.enabled", Boolean.class, Boolean.TRUE));
        data.put("kafkaListenerEnabled", environment.getProperty("lottery.kafka.listener.enabled", Boolean.class, Boolean.TRUE));
        data.put("xxlJobEnabled", environment.getProperty("lottery.xxl-job.enabled", Boolean.class, Boolean.TRUE));
        return ApiResponse.ok(data);
    }

    @GetMapping("/activities")
    public ApiResponse<ActivityRes> activities(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int rows,
                                                @RequestParam(required = false) Long activityId,
                                                @RequestParam(required = false) String activityName) {
        ActivityPageReq req = new ActivityPageReq(page, rows);
        req.setErpId("ui");
        req.setActivityId(activityId);
        if (hasText(activityName)) {
            req.setActivityName(activityName.trim());
        }
        return ApiResponse.ok(lotteryActivityDeploy.queryActivityListByPageForErp(req));
    }

    @PostMapping("/activities")
    public ApiResponse<Result> createActivity(@RequestBody CreateActivityRequest req) {
        if (req == null || req.activityId == null || req.strategyId == null) {
            return ApiResponse.fail("活动ID和策略ID不能为空");
        }
        if (req.awards == null || req.awards.isEmpty()) {
            return ApiResponse.fail("奖品配置不能为空");
        }
        if (req.strategyDetails == null || req.strategyDetails.isEmpty()) {
            return ApiResponse.fail("策略明细不能为空");
        }

        ActivityVO activity = new ActivityVO();
        activity.setActivityId(req.activityId);
        activity.setActivityName(defaultText(req.activityName, "抽奖活动-" + req.activityId));
        activity.setActivityDesc(defaultText(req.activityDesc, ""));
        activity.setBeginDateTime(defaultDate(req.beginDateTime, new Date()));
        activity.setEndDateTime(defaultDate(req.endDateTime, nextYear()));
        activity.setStockCount(defaultInt(req.stockCount, 100));
        activity.setStockSurplusCount(defaultInt(req.stockSurplusCount, defaultInt(req.stockCount, 100)));
        activity.setTakeCount(defaultInt(req.takeCount, 1));
        activity.setStrategyId(req.strategyId);
        activity.setState(defaultInt(req.state, Constants.ActivityState.EDIT.getCode()));
        activity.setCreator(defaultText(req.creator, "ui"));

        StrategyVO strategy = new StrategyVO();
        strategy.setStrategyId(req.strategyId);
        strategy.setStrategyDesc(defaultText(req.strategyDesc, "抽奖策略-" + req.strategyId));
        strategy.setStrategyMode(defaultInt(req.strategyMode, Constants.StrategyMode.ENTIRETY.getCode()));
        strategy.setGrantType(defaultInt(req.grantType, 1));
        strategy.setGrantDate(defaultDate(req.grantDate, new Date()));
        strategy.setExtInfo(defaultText(req.extInfo, ""));

        List<StrategyDetailVO> detailList = new ArrayList<>();
        for (StrategyDetailRequest item : req.strategyDetails) {
            StrategyDetailVO detail = new StrategyDetailVO();
            detail.setStrategyId(req.strategyId);
            detail.setAwardId(item.awardId);
            detail.setAwardName(defaultText(item.awardName, item.awardId));
            detail.setAwardCount(defaultInt(item.awardCount, 1));
            detail.setAwardSurplusCount(defaultInt(item.awardSurplusCount, defaultInt(item.awardCount, 1)));
            detail.setAwardRate(item.awardRate == null ? BigDecimal.ZERO : item.awardRate);
            detailList.add(detail);
        }
        strategy.setStrategyDetailList(detailList);

        List<AwardVO> awardList = new ArrayList<>();
        for (AwardRequest item : req.awards) {
            AwardVO award = new AwardVO();
            award.setAwardId(item.awardId);
            award.setAwardType(defaultInt(item.awardType, Constants.AwardType.DESC.getCode()));
            award.setAwardName(defaultText(item.awardName, item.awardId));
            award.setAwardContent(defaultText(item.awardContent, ""));
            awardList.add(award);
        }

        activityDeploy.createActivity(new ActivityConfigReq(req.activityId, new ActivityConfigRich(activity, strategy, awardList)));
        return ApiResponse.ok(Result.buildSuccessResult());
    }

    @PostMapping("/activity/state")
    public ApiResponse<Result> changeState(@RequestBody StateChangeRequest req) {
        if (req == null || req.activityId == null || req.currentState == null || !hasText(req.action)) {
            return ApiResponse.fail("活动ID、当前状态和动作不能为空");
        }
        Constants.ActivityState currentState = activityStateOf(req.currentState);
        if (currentState == null) {
            return ApiResponse.fail("未知活动状态：" + req.currentState);
        }

        Result result;
        if ("arraignment".equals(req.action)) {
            result = stateHandler.arraignment(req.activityId, currentState);
        } else if ("checkPass".equals(req.action)) {
            result = stateHandler.checkPass(req.activityId, currentState);
        } else if ("checkRefuse".equals(req.action)) {
            result = stateHandler.checkRefuse(req.activityId, currentState);
        } else if ("checkRevoke".equals(req.action)) {
            result = stateHandler.checkRevoke(req.activityId, currentState);
        } else if ("doing".equals(req.action)) {
            result = stateHandler.doing(req.activityId, currentState);
        } else if ("close".equals(req.action)) {
            result = stateHandler.close(req.activityId, currentState);
        } else if ("open".equals(req.action)) {
            result = stateHandler.open(req.activityId, currentState);
        } else {
            return ApiResponse.fail("未知动作：" + req.action);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/partake")
    public ApiResponse<PartakeResult> partake(@RequestBody UserActivityRequest req) {
        if (req == null || !hasText(req.uId) || req.activityId == null) {
            return ApiResponse.fail("用户ID和活动ID不能为空");
        }
        return ApiResponse.ok(activityPartake.doPartake(new PartakeReq(req.uId.trim(), req.activityId)));
    }

    @PostMapping("/draw")
    public ApiResponse<DrawRes> draw(@RequestBody UserActivityRequest req) {
        if (req == null || !hasText(req.uId) || req.activityId == null) {
            return ApiResponse.fail("用户ID和活动ID不能为空");
        }
        return ApiResponse.ok(lotteryActivityBooth.doDraw(new DrawReq(req.uId.trim(), req.activityId)));
    }

    @PostMapping("/quantification-draw")
    public ApiResponse<DrawRes> quantificationDraw(@RequestBody QuantificationRequest req) {
        if (req == null || !hasText(req.uId) || req.treeId == null) {
            return ApiResponse.fail("用户ID和规则树ID不能为空");
        }
        QuantificationDrawReq drawReq = new QuantificationDrawReq();
        drawReq.setuId(req.uId.trim());
        drawReq.setTreeId(req.treeId);
        drawReq.setValMap(req.valMap == null ? new HashMap<String, Object>() : req.valMap);
        return ApiResponse.ok(lotteryActivityBooth.doQuantificationDraw(drawReq));
    }

    @PostMapping("/rules/decision")
    public ApiResponse<EngineResult> ruleDecision(@RequestBody QuantificationRequest req) {
        if (req == null || !hasText(req.uId) || req.treeId == null) {
            return ApiResponse.fail("用户ID和规则树ID不能为空");
        }
        DecisionMatterReq matterReq = new DecisionMatterReq(req.uId.trim(), req.treeId, req.valMap == null ? new HashMap<String, Object>() : req.valMap);
        return ApiResponse.ok(engineFilter.process(matterReq));
    }

    @GetMapping("/strategies")
    public ApiResponse<List<Strategy>> strategies() {
        return ApiResponse.ok(strategyDao.queryStrategyList());
    }

    @GetMapping("/strategies/{strategyId}")
    public ApiResponse<StrategyRich> strategy(@PathVariable Long strategyId) {
        return ApiResponse.ok(strategyRepository.queryStrategyRich(strategyId));
    }

    @GetMapping("/awards")
    public ApiResponse<List<Award>> awards() {
        return ApiResponse.ok(awardDao.queryAwardList());
    }

    @GetMapping("/awards/{awardId}")
    public ApiResponse<AwardBriefVO> award(@PathVariable String awardId) {
        return ApiResponse.ok(strategyRepository.queryAwardInfo(awardId));
    }

    @GetMapping("/rules")
    public ApiResponse<List<RuleTree>> rules() {
        return ApiResponse.ok(ruleTreeDao.queryRuleTreeList());
    }

    @GetMapping("/rules/{treeId}")
    public ApiResponse<Map<String, Object>> ruleDetail(@PathVariable Long treeId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tree", ruleTreeDao.queryRuleTreeByTreeId(treeId));
        data.put("rich", ruleRepository.queryTreeRuleRich(treeId));
        data.put("nodes", ruleTreeNodeDao.queryRuleTreeNodeList(treeId));
        data.put("lines", ruleTreeNodeLineDao.queryRuleTreeNodeLineListByTreeId(treeId));
        return ApiResponse.ok(data);
    }

    @GetMapping("/users/{uId}/take-count")
    public ApiResponse<UserTakeActivityCount> userTakeCount(@PathVariable String uId, @RequestParam Long activityId) {
        UserTakeActivityCount req = new UserTakeActivityCount();
        req.setuId(uId);
        req.setActivityId(activityId);
        return ApiResponse.ok(userTakeActivityCountDao.queryUserTakeActivityCount(req));
    }

    @GetMapping("/users/{uId}/take-records")
    public ApiResponse<List<UserTakeActivity>> userTakeRecords(@PathVariable String uId,
                                                               @RequestParam(required = false) Long activityId) {
        UserTakeActivity req = new UserTakeActivity();
        req.setuId(uId);
        req.setActivityId(activityId);
        return ApiResponse.ok(userTakeActivityDao.queryTakeActivityList(req));
    }

    @GetMapping("/users/{uId}/awards")
    public ApiResponse<List<UserStrategyExport>> userAwards(@PathVariable String uId) {
        return ApiResponse.ok(userStrategyExportDao.queryUserStrategyExportListByUId(uId));
    }

    @GetMapping("/todo-activities")
    public ApiResponse<List<ActivityVO>> todoActivities(@RequestParam(defaultValue = "0") Long id) {
        return ApiResponse.ok(activityDeploy.scanToDoActivityList(id));
    }

    @PostMapping("/jobs/activity-state-scan")
    public ApiResponse<List<Result>> activityStateScan() {
        List<ActivityVO> activityList = activityDeploy.scanToDoActivityList(0L);
        List<Result> results = new ArrayList<>();
        for (ActivityVO activity : activityList) {
            if (Constants.ActivityState.PASS.getCode().equals(activity.getState())) {
                results.add(stateHandler.doing(activity.getActivityId(), Constants.ActivityState.PASS));
            } else if (Constants.ActivityState.DOING.getCode().equals(activity.getState()) && activity.getEndDateTime() != null && activity.getEndDateTime().before(new Date())) {
                results.add(stateHandler.close(activity.getActivityId(), Constants.ActivityState.DOING));
            }
        }
        return ApiResponse.ok(results);
    }

    @PostMapping("/mq/scan")
    public ApiResponse<List<InvoiceVO>> scanInvoice(@RequestBody MqScanRequest req) {
        int db = req == null || req.dbCount == null ? 1 : req.dbCount;
        int tb = req == null || req.tbCount == null ? 0 : req.tbCount;
        return ApiResponse.ok(activityPartake.scanInvoiceMqState(db, tb));
    }

    @PostMapping("/distribution")
    public ApiResponse<DistributionRes> distribution(@RequestBody DistributionRequest req) {
        if (req == null || !hasText(req.uId) || req.orderId == null || req.awardType == null || !hasText(req.awardId)) {
            return ApiResponse.fail("用户ID、订单ID、奖品类型和奖品ID不能为空");
        }
        IDistributionGoods distributionGoods = distributionGoodsFactory.getDistributionGoodsService(req.awardType);
        if (distributionGoods == null) {
            return ApiResponse.fail("未找到奖品发放服务");
        }
        DistributionRes res = distributionGoods.doDistribution(new GoodsReq(req.uId, req.orderId, req.awardId, req.awardName, req.awardContent));
        if (Constants.AwardState.SUCCESS.getCode().equals(res.getCode())) {
            activityPartake.updateInvoiceMqState(req.uId, req.orderId, Constants.MQState.COMPLETE.getCode());
        }
        return ApiResponse.ok(res);
    }

    private Constants.ActivityState activityStateOf(Integer code) {
        for (Constants.ActivityState item : Constants.ActivityState.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return null;
    }

    private List<Map<String, Object>> options(Object[][] items) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] item : items) {
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("value", item[0]);
            option.put("label", item[1]);
            list.add(option);
        }
        return list;
    }

    private boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private String defaultText(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }

    private Integer defaultInt(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Date defaultDate(String value, Date defaultValue) {
        Date parsed = parseDate(value);
        return parsed == null ? defaultValue : parsed;
    }

    private Date parseDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        String[] patterns = new String[]{"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern);
                format.setLenient(false);
                return format.parse(value.trim());
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    private Date nextYear() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 1);
        return calendar.getTime();
    }

    private Map<String, Object> runtimeInfo() {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("profiles", environment.getActiveProfiles());
        runtime.put("serverPort", environment.getProperty("server.port", "8081"));
        runtime.put("javaVersion", System.getProperty("java.version"));
        runtime.put("osName", System.getProperty("os.name"));
        runtime.put("userTimezone", System.getProperty("user.timezone"));
        return runtime;
    }

    private Map<String, Object> databaseInfo() {
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("dbCount", environment.getProperty("mini-db-router.jdbc.datasource.dbCount", "2"));
        database.put("tbCount", environment.getProperty("mini-db-router.jdbc.datasource.tbCount", "4"));
        database.put("default", environment.getProperty("mini-db-router.jdbc.datasource.default", "db00"));
        database.put("routerKey", environment.getProperty("mini-db-router.jdbc.datasource.routerKey", "uId"));

        List<Map<String, Object>> sources = new ArrayList<>();
        addDataSourceInfo(sources, environment.getProperty("mini-db-router.jdbc.datasource.default", "db00"));
        String list = environment.getProperty("mini-db-router.jdbc.datasource.list", "");
        for (String item : list.split(",")) {
            if (hasText(item)) {
                addDataSourceInfo(sources, item.trim());
            }
        }
        database.put("sources", sources);
        return database;
    }

    private void addDataSourceInfo(List<Map<String, Object>> sources, String key) {
        Map<String, Object> source = new LinkedHashMap<>();
        String base = "mini-db-router.jdbc.datasource." + key + ".";
        String url = environment.getProperty(base + "url", "");
        source.put("key", key);
        source.put("driver", environment.getProperty(base + "driver-class-name", ""));
        source.put("database", extractDatabaseName(url));
        source.put("url", sanitizeJdbcUrl(url));
        source.put("username", environment.getProperty(base + "username", ""));
        sources.add(source);
    }

    private String extractDatabaseName(String url) {
        if (!hasText(url)) {
            return "";
        }
        int start = url.indexOf("/", "jdbc:mysql://".length());
        if (start < 0 || start + 1 >= url.length()) {
            return "";
        }
        int end = url.indexOf("?", start);
        return end < 0 ? url.substring(start + 1) : url.substring(start + 1, end);
    }

    private String sanitizeJdbcUrl(String url) {
        if (!hasText(url)) {
            return "";
        }
        int query = url.indexOf("?");
        return query < 0 ? url : url.substring(0, query);
    }

    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> ok(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.success = true;
            response.message = "success";
            response.data = data;
            return response;
        }

        public static <T> ApiResponse<T> fail(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.success = false;
            response.message = message;
            return response;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }

    public static class UserActivityRequest {
        public String uId;
        public Long activityId;
    }

    public static class QuantificationRequest {
        public String uId;
        public Long treeId;
        public Map<String, Object> valMap;
    }

    public static class StateChangeRequest {
        public Long activityId;
        public Integer currentState;
        public String action;
    }

    public static class MqScanRequest {
        public Integer dbCount;
        public Integer tbCount;
    }

    public static class DistributionRequest {
        public String uId;
        public Long orderId;
        public String awardId;
        public Integer awardType;
        public String awardName;
        public String awardContent;
    }

    public static class CreateActivityRequest {
        public Long activityId;
        public String activityName;
        public String activityDesc;
        public String beginDateTime;
        public String endDateTime;
        public Integer stockCount;
        public Integer stockSurplusCount;
        public Integer takeCount;
        public Long strategyId;
        public Integer state;
        public String creator;
        public String strategyDesc;
        public Integer strategyMode;
        public Integer grantType;
        public String grantDate;
        public String extInfo;
        public List<AwardRequest> awards;
        public List<StrategyDetailRequest> strategyDetails;
    }

    public static class AwardRequest {
        public String awardId;
        public Integer awardType;
        public String awardName;
        public String awardContent;
    }

    public static class StrategyDetailRequest {
        public String awardId;
        public String awardName;
        public Integer awardCount;
        public Integer awardSurplusCount;
        public BigDecimal awardRate;
    }
}
