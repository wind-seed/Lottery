const state = {
    meta: {},
    activities: [],
    strategies: [],
    awards: [],
    rules: [],
    userAwards: [],
    diagnostics: null,
    activeSection: "dashboard"
};

const titles = {
    dashboard: ["总览", "本地抽奖系统控制台"],
    database: ["数据库", "本地数据源、运行模式与健康检查"],
    activities: ["活动", "活动查询与状态流转"],
    draw: ["抽奖", "指定活动抽奖与规则人群抽奖"],
    deploy: ["创建", "活动、奖品、策略一次性配置"],
    catalog: ["策略奖品", "策略、概率、奖品基础数据"],
    rules: ["规则", "规则树查看与规则决策"],
    user: ["用户", "参与记录与中奖记录"],
    jobs: ["任务", "活动状态扫描与 MQ 补偿扫描"]
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => Array.from(document.querySelectorAll(selector));

function refreshIcons() {
    if (window.lucide) {
        window.lucide.createIcons();
    }
}

async function api(path, options = {}) {
    const init = Object.assign({headers: {}}, options);
    if (init.body && !init.headers["Content-Type"]) {
        init.headers["Content-Type"] = "application/json";
    }
    const res = await fetch(path, init);
    const payload = await res.json();
    if (!res.ok || payload.success === false) {
        throw new Error(payload.message || `请求失败：${res.status}`);
    }
    return payload.data;
}

function post(path, body) {
    return api(path, {method: "POST", body: JSON.stringify(body)});
}

function toast(message) {
    const el = $("#toast");
    el.textContent = message;
    el.classList.add("show");
    window.clearTimeout(toast.timer);
    toast.timer = window.setTimeout(() => el.classList.remove("show"), 2600);
}

function setStatus(ok, text) {
    const el = $("#serviceStatus");
    el.textContent = text;
    el.classList.toggle("ok", ok);
    el.classList.toggle("fail", !ok);
}

function formatJson(data) {
    return JSON.stringify(data || {}, null, 2);
}

function formatDate(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    const pad = (num) => String(num).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function escapeHtml(value) {
    return String(value == null ? "" : value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function optionLabel(name, value) {
    const list = state.meta[name] || [];
    const match = list.find((item) => String(item.value) === String(value));
    return match ? match.label : (value == null ? "" : value);
}

function tag(text, type = "") {
    return `<span class="tag ${type}">${escapeHtml(text)}</span>`;
}

function resultTag(result) {
    if (!result) return tag("无", "warn");
    return tag(`${result.code || ""} ${result.info || ""}`, result.code === "0000" ? "ok" : "danger");
}

function isBusinessSuccess(data) {
    return data && (data.code == null || String(data.code) === "0000" || Number(data.code) === 1);
}

function isLosingDraw(data) {
    return data && String(data.code) === "D001";
}

function showBusinessToast(data, successText, fallbackText) {
    const ok = isBusinessSuccess(data);
    toast(ok ? successText : (data && data.info ? data.info : fallbackText));
    return ok;
}

function drawToast(data, fallbackText) {
    const award = data && data.awardDTO;
    if (award) {
        toast(`恭喜中奖：${award.awardName || "奖品"}${award.awardContent ? `（${award.awardContent}）` : ""}`);
        return;
    }
    if (isLosingDraw(data)) {
        toast(data.info || "本次未中奖");
        return;
    }
    toast(data && data.info ? `抽奖失败：${data.info}` : fallbackText);
}

function renderDrawResult(data, sourceLabel) {
    const summary = $("#drawSummary");
    if (!summary) return;

    const award = data && data.awardDTO;
    const info = data && data.info ? data.info : "接口没有返回失败原因，请查看服务日志";
    if (award) {
        summary.className = "draw-summary success";
        summary.innerHTML = `
            <div class="draw-summary-title">中奖成功</div>
            <div class="prize-name">${escapeHtml(award.awardName || "未知奖品")}</div>
            <div class="prize-content">${escapeHtml(award.awardContent || "暂无奖品内容")}</div>
            <div class="draw-meta">
                <span>来源：${escapeHtml(sourceLabel)}</span>
                <span>活动ID：${escapeHtml(award.activityId)}</span>
                <span>奖品ID：${escapeHtml(award.awardId)}</span>
                <span>奖品类型：${escapeHtml(optionLabel("awardTypes", award.awardType) || award.awardType)}</span>
            </div>`;
        return;
    }

    if (isLosingDraw(data)) {
        summary.className = "draw-summary warn";
        summary.innerHTML = `
            <div class="draw-summary-title">本次未中奖</div>
            <div class="draw-summary-info">抽奖请求已正常完成，但策略结果没有命中奖品。</div>
            <div class="draw-meta">
                <span>来源：${escapeHtml(sourceLabel)}</span>
                <span>返回码：${escapeHtml(data.code)}</span>
                <span>结果：${escapeHtml(info)}</span>
            </div>`;
        return;
    }

    summary.className = "draw-summary danger";
    summary.innerHTML = `
        <div class="draw-summary-title">抽奖失败</div>
        <div class="draw-summary-info">${escapeHtml(info)}</div>
        <div class="draw-meta">
            <span>来源：${escapeHtml(sourceLabel)}</span>
            <span>返回码：${escapeHtml(data && data.code ? data.code : "无")}</span>
        </div>`;
}

function fillSelect(selector, options, selected) {
    const el = $(selector);
    el.innerHTML = (options || []).map((item) => {
        const checked = String(item.value) === String(selected) ? "selected" : "";
        return `<option value="${escapeHtml(item.value)}" ${checked}>${escapeHtml(item.label)}</option>`;
    }).join("");
}

function blankRow(colspan, text = "暂无数据") {
    return `<tr><td colspan="${colspan}" class="empty">${escapeHtml(text)}</td></tr>`;
}

function setSection(section) {
    state.activeSection = section;
    $$(".section").forEach((el) => el.classList.toggle("active", el.id === section));
    $$(".nav-item").forEach((el) => el.classList.toggle("active", el.dataset.section === section));
    $("#pageTitle").textContent = titles[section][0];
    $("#pageSubTitle").textContent = titles[section][1];
    refreshIcons();
}

async function loadMeta() {
    state.meta = await api("/api/lottery/metadata");
    fillSelect("#stateCurrent", state.meta.activityStates, 5);
    fillSelect("#stateAction", state.meta.stateActions, "arraignment");
    fillSelect("#newStrategyMode", state.meta.strategyModes, 2);
    fillSelect("#newGrantType", state.meta.grantTypes, 1);
}

async function loadHealth() {
    await api("/api/lottery/health");
    setStatus(true, "已连接");
}

function renderDiagnostics() {
    const data = state.diagnostics || {};
    const runtime = data.runtime || {};
    const database = data.database || {};
    const counts = data.counts || {};
    const warnings = data.warnings || [];

    $("#metricRunnableActivities").textContent = counts.runnableActivities || 0;
    $("#diagActivities").textContent = counts.activities || 0;
    $("#diagRunning").textContent = counts.runningActivities || 0;
    $("#diagRunnable").textContent = counts.runnableActivities || 0;
    $("#diagExpired").textContent = counts.expiredRunningActivities || 0;

    $("#runtimeInfo").innerHTML = [
        ["服务", data.databaseConnected ? "数据库已连接" : "数据库异常"],
        ["Profile", (runtime.profiles || []).join(", ") || "default"],
        ["端口", runtime.serverPort || "8081"],
        ["Java", runtime.javaVersion || ""],
        ["操作系统", runtime.osName || ""],
        ["分库/分表", `${database.dbCount || "-"} / ${database.tbCount || "-"}`],
        ["路由键", database.routerKey || ""],
        ["Kafka 生产者", data.kafkaProducerEnabled ? "启用" : "本地同步模式"],
        ["Kafka 消费者", data.kafkaListenerEnabled ? "启用" : "关闭"],
        ["XXL-Job", data.xxlJobEnabled ? "启用" : "关闭"]
    ].map(([key, value]) => `<div><span>${escapeHtml(key)}</span><strong>${escapeHtml(value)}</strong></div>`).join("");

    $("#dataSourceRows").innerHTML = (database.sources || []).map((item) => `<tr>
        <td>${escapeHtml(item.key)}</td>
        <td>${escapeHtml(item.database)}</td>
        <td>${escapeHtml(item.driver)}</td>
        <td>${escapeHtml(item.url)}</td>
        <td>${escapeHtml(item.username)}</td>
    </tr>`).join("") || blankRow(5);

    const warningHtml = warnings.length
        ? warnings.map((item) => `<div class="notice warn"><i data-lucide="triangle-alert"></i><span>${escapeHtml(item)}</span></div>`).join("")
        : `<div class="notice ok"><i data-lucide="circle-check"></i><span>未发现阻塞运行的问题。</span></div>`;
    $("#diagnosticWarnings").innerHTML = warningHtml;
    $("#dashboardWarnings").innerHTML = warningHtml;
    $("#diagnosticResult").textContent = formatJson(data);
    refreshIcons();
}

async function loadDiagnostics() {
    state.diagnostics = await api("/api/lottery/diagnostics");
    renderDiagnostics();
}

function renderActivities() {
    const rows = state.activities.map((item) => {
        const stock = `${item.stockSurplusCount == null ? "-" : item.stockSurplusCount}/${item.stockCount == null ? "-" : item.stockCount}`;
        const stateName = optionLabel("activityStates", item.state);
        const tagType = item.state === 5 ? "ok" : (item.state === 7 || item.state === 6 ? "danger" : "warn");
        return `<tr data-activity-id="${escapeHtml(item.activityId)}" data-state="${escapeHtml(item.state)}" data-strategy-id="${escapeHtml(item.strategyId)}">
            <td><button class="ghost-btn row-pick" data-pick-activity="${escapeHtml(item.activityId)}"><i data-lucide="mouse-pointer-click"></i><span>${escapeHtml(item.activityId)}</span></button></td>
            <td>${escapeHtml(item.activityName)}</td>
            <td>${escapeHtml(item.activityDesc)}</td>
            <td>${escapeHtml(item.stockCount)}</td>
            <td>${escapeHtml(item.stockSurplusCount)}</td>
            <td>${escapeHtml(item.takeCount)}</td>
            <td><button class="ghost-btn row-pick" data-pick-strategy="${escapeHtml(item.strategyId)}"><i data-lucide="search"></i><span>${escapeHtml(item.strategyId)}</span></button></td>
            <td>${tag(stateName, tagType)}</td>
            <td>${escapeHtml(item.creator)}</td>
        </tr>`;
    }).join("");

    $("#activityRows").innerHTML = rows || blankRow(9);
    $("#dashboardActivityRows").innerHTML = state.activities.slice(0, 8).map((item) => {
        const stock = `${item.stockSurplusCount == null ? "-" : item.stockSurplusCount}/${item.stockCount == null ? "-" : item.stockCount}`;
        return `<tr>
            <td>${escapeHtml(item.activityId)}</td>
            <td>${escapeHtml(item.activityName)}</td>
            <td>${escapeHtml(stock)}</td>
            <td>${escapeHtml(item.strategyId)}</td>
            <td>${tag(optionLabel("activityStates", item.state), item.state === 5 ? "ok" : "warn")}</td>
            <td>${escapeHtml(formatDate(item.beginDateTime))} - ${escapeHtml(formatDate(item.endDateTime))}</td>
        </tr>`;
    }).join("") || blankRow(6);

    $("#metricActivities").textContent = state.activities.length;
    const first = state.activities[0];
    if (first) {
        ["#drawActivityId", "#stateActivityId", "#userActivityId"].forEach((selector) => {
            if (!$(selector).value) $(selector).value = first.activityId;
        });
        $("#stateCurrent").value = first.state;
        if (!$("#strategyDetailId").value) $("#strategyDetailId").value = first.strategyId;
    }
    refreshIcons();
}

async function loadActivities() {
    const params = new URLSearchParams();
    params.set("page", "1");
    params.set("rows", "20");
    const id = $("#activityFilterId").value.trim();
    const name = $("#activityFilterName").value.trim();
    if (id) params.set("activityId", id);
    if (name) params.set("activityName", name);
    const data = await api(`/api/lottery/activities?${params.toString()}`);
    state.activities = data.activityDTOList || [];
    renderActivities();
}

function renderCatalog() {
    $("#metricStrategies").textContent = state.strategies.length;
    $("#metricAwards").textContent = state.awards.length;
    $("#metricRules").textContent = state.rules.length;

    $("#strategyRows").innerHTML = state.strategies.map((item) => `<tr>
        <td><button class="ghost-btn row-pick" data-pick-strategy="${escapeHtml(item.strategyId)}"><i data-lucide="search"></i><span>${escapeHtml(item.strategyId)}</span></button></td>
        <td>${escapeHtml(item.strategyDesc)}</td>
        <td>${tag(optionLabel("strategyModes", item.strategyMode))}</td>
        <td>${tag(optionLabel("grantTypes", item.grantType))}</td>
    </tr>`).join("") || blankRow(4);

    $("#awardRows").innerHTML = state.awards.map((item) => `<tr>
        <td>${escapeHtml(item.awardId)}</td>
        <td>${escapeHtml(item.awardName)}</td>
        <td>${tag(optionLabel("awardTypes", item.awardType))}</td>
        <td>${escapeHtml(item.awardContent)}</td>
    </tr>`).join("") || blankRow(4);

    $("#ruleRows").innerHTML = state.rules.map((item) => `<tr>
        <td><button class="ghost-btn row-pick" data-pick-rule="${escapeHtml(item.id)}"><i data-lucide="search"></i><span>${escapeHtml(item.id)}</span></button></td>
        <td>${escapeHtml(item.treeName)}</td>
        <td>${escapeHtml(item.treeRootNodeId)}</td>
    </tr>`).join("") || blankRow(3);
    refreshIcons();
}

async function loadCatalog() {
    const [strategies, awards, rules] = await Promise.all([
        api("/api/lottery/strategies"),
        api("/api/lottery/awards"),
        api("/api/lottery/rules")
    ]);
    state.strategies = strategies || [];
    state.awards = awards || [];
    state.rules = rules || [];
    renderCatalog();
    const firstRule = state.rules[0];
    if (firstRule) {
        ["#qDrawTreeId", "#ruleTreeId", "#ruleDetailId"].forEach((selector) => {
            if (!$(selector).value) $(selector).value = firstRule.id;
        });
    }
}

async function doDraw() {
    const data = await post("/api/lottery/draw", {
        uId: $("#drawUid").value.trim(),
        activityId: Number($("#drawActivityId").value)
    });
    renderDrawResult(data, "指定活动抽奖");
    $("#drawResult").textContent = formatJson(data);
    drawToast(data, "抽奖失败");
    await Promise.all([loadActivities(), loadUserData(false)]);
}

async function doQuantificationDraw() {
    const data = await post("/api/lottery/quantification-draw", {
        uId: $("#qDrawUid").value.trim(),
        treeId: Number($("#qDrawTreeId").value),
        valMap: {gender: $("#qDrawGender").value.trim(), age: $("#qDrawAge").value.trim()}
    });
    renderDrawResult(data, "量化人群抽奖");
    $("#drawResult").textContent = formatJson(data);
    drawToast(data, "规则抽奖失败");
    await Promise.all([loadActivities(), loadUserData(false)]);
}

async function changeState() {
    if (!window.confirm("确认执行活动状态流转？这个操作会修改数据库中的活动状态。")) {
        return;
    }
    const data = await post("/api/lottery/activity/state", {
        activityId: Number($("#stateActivityId").value),
        currentState: Number($("#stateCurrent").value),
        action: $("#stateAction").value
    });
    toast(data.info || "状态流转完成");
    await Promise.all([loadActivities(), loadDiagnostics()]);
}

async function loadStrategyDetail() {
    const id = $("#strategyDetailId").value.trim();
    const data = await api(`/api/lottery/strategies/${encodeURIComponent(id)}`);
    $("#strategyDetailResult").textContent = formatJson(data);
}

async function loadRuleDetail() {
    const id = $("#ruleDetailId").value.trim();
    const data = await api(`/api/lottery/rules/${encodeURIComponent(id)}`);
    $("#ruleResult").textContent = formatJson(data);
}

async function ruleDecision() {
    const data = await post("/api/lottery/rules/decision", {
        uId: $("#ruleUid").value.trim(),
        treeId: Number($("#ruleTreeId").value),
        valMap: {gender: $("#ruleGender").value.trim(), age: $("#ruleAge").value.trim()}
    });
    $("#ruleResult").textContent = formatJson(data);
    toast(data.success ? "规则执行完成" : "规则未命中");
}

async function partake() {
    const data = await post("/api/lottery/partake", {
        uId: $("#userUid").value.trim(),
        activityId: Number($("#userActivityId").value)
    });
    $("#userResult").textContent = formatJson(data);
    showBusinessToast(data, "参与完成", data.info || "参与失败");
    await Promise.all([loadActivities(), loadUserData(false)]);
}

function renderUserData(records, awards, count) {
    $("#takeRows").innerHTML = (records || []).map((item) => `<tr>
        <td>${escapeHtml(item.takeId)}</td>
        <td>${escapeHtml(item.activityId)}</td>
        <td>${escapeHtml(item.takeCount)}</td>
        <td>${escapeHtml(item.strategyId)}</td>
        <td>${tag(item.state === 0 ? "未使用" : "已使用", item.state === 0 ? "warn" : "ok")}</td>
        <td>${escapeHtml(formatDate(item.takeDate || item.createTime))}</td>
    </tr>`).join("") || blankRow(6);

    state.userAwards = awards || [];
    $("#userAwardRows").innerHTML = state.userAwards.map((item, index) => `<tr>
        <td>${escapeHtml(item.orderId)}</td>
        <td>${escapeHtml(item.awardName)} (${escapeHtml(item.awardId)})</td>
        <td>${tag(optionLabel("grantTypes", item.grantType) + " / " + grantStateName(item.grantState), item.grantState === 1 ? "ok" : "warn")}</td>
        <td>${tag(mqStateName(item.mqState), item.mqState === 1 ? "ok" : item.mqState === 2 ? "danger" : "warn")}</td>
        <td><button class="ghost-btn" data-distribute="${index}"><i data-lucide="truck"></i><span>发奖</span></button></td>
    </tr>`).join("") || blankRow(5);

    $("#userResult").textContent = formatJson({takeCount: count || null, takeRecords: records || [], awards: awards || []});
    refreshIcons();
}

function grantStateName(value) {
    if (value === 0) return "初始";
    if (value === 1) return "完成";
    if (value === 2) return "失败";
    return value == null ? "" : value;
}

function mqStateName(value) {
    if (value === 0) return "初始";
    if (value === 1) return "完成";
    if (value === 2) return "失败";
    return value == null ? "" : value;
}

async function loadUserData(showToast = true) {
    const uId = $("#userUid").value.trim();
    const activityId = $("#userActivityId").value.trim();
    if (!uId) return;
    const recordsUrl = activityId
        ? `/api/lottery/users/${encodeURIComponent(uId)}/take-records?activityId=${encodeURIComponent(activityId)}`
        : `/api/lottery/users/${encodeURIComponent(uId)}/take-records`;
    const countUrl = activityId
        ? `/api/lottery/users/${encodeURIComponent(uId)}/take-count?activityId=${encodeURIComponent(activityId)}`
        : null;
    const [records, awards, count] = await Promise.all([
        api(recordsUrl),
        api(`/api/lottery/users/${encodeURIComponent(uId)}/awards`),
        countUrl ? api(countUrl).catch(() => null) : Promise.resolve(null)
    ]);
    renderUserData(records, awards, count);
    if (showToast) toast("用户数据已刷新");
}

async function manualDistribution(index) {
    if (!window.confirm("确认手动发奖？这个操作会修改发奖状态。")) {
        return;
    }
    const item = state.userAwards[index];
    if (!item) return;
    const data = await post("/api/lottery/distribution", {
        uId: item.uId,
        orderId: item.orderId,
        awardId: item.awardId,
        awardType: item.awardType,
        awardName: item.awardName,
        awardContent: item.awardContent
    });
    $("#userResult").textContent = formatJson(data);
    toast(data.info || "发奖完成");
    await loadUserData(false);
}

function addAwardRow(row = {}) {
    const el = document.createElement("div");
    el.className = "editor-row award-row";
    el.innerHTML = `
        <label>奖品ID<input data-field="awardId" value="${escapeHtml(row.awardId || "")}"></label>
        <label>名称<input data-field="awardName" value="${escapeHtml(row.awardName || "")}"></label>
        <label>类型<select data-field="awardType">${(state.meta.awardTypes || []).map((item) => `<option value="${item.value}" ${String(row.awardType || 1) === String(item.value) ? "selected" : ""}>${escapeHtml(item.label)}</option>`).join("")}</select></label>
        <label>内容<input data-field="awardContent" value="${escapeHtml(row.awardContent || "")}"></label>
        <button class="row-btn" data-action="remove-row" title="删除">×</button>`;
    $("#awardEditor").appendChild(el);
}

function addDetailRow(row = {}) {
    const el = document.createElement("div");
    el.className = "editor-row detail detail-row";
    el.innerHTML = `
        <label>奖品ID<input data-field="awardId" value="${escapeHtml(row.awardId || "")}"></label>
        <label>名称<input data-field="awardName" value="${escapeHtml(row.awardName || "")}"></label>
        <label>库存<input data-field="awardCount" type="number" value="${escapeHtml(row.awardCount || 10)}"></label>
        <label>概率<input data-field="awardRate" value="${escapeHtml(row.awardRate || "0.20")}"></label>
        <button class="row-btn" data-action="remove-row" title="删除">×</button>`;
    $("#detailEditor").appendChild(el);
}

function fillCreateDefaults() {
    const now = new Date();
    const seed = String(now.getTime()).slice(-8);
    const activityId = Number(`12${seed}`);
    const strategyId = Number(`10${seed.slice(-5)}`);
    $("#newActivityId").value = activityId;
    $("#newActivityName").value = `抽奖活动-${seed}`;
    $("#newActivityDesc").value = "本地 UI 创建";
    $("#newStock").value = 100;
    $("#newTakeCount").value = 3;
    $("#newStrategyId").value = strategyId;
    $("#newStrategyMode").value = 2;
    $("#newGrantType").value = 1;
    $("#newCreator").value = "ui";
    $("#newBegin").value = toDateTimeLocal(now);
    const end = new Date(now.getTime() + 90 * 24 * 60 * 60 * 1000);
    $("#newEnd").value = toDateTimeLocal(end);
    $("#awardEditor").innerHTML = "";
    $("#detailEditor").innerHTML = "";
    const defaults = [
        {awardId: `${seed}01`, awardName: "一等奖", awardContent: "电脑", awardCount: 10, awardRate: "0.05"},
        {awardId: `${seed}02`, awardName: "二等奖", awardContent: "手机", awardCount: 20, awardRate: "0.15"},
        {awardId: `${seed}03`, awardName: "三等奖", awardContent: "耳机", awardCount: 70, awardRate: "0.30"}
    ];
    defaults.forEach((item) => {
        addAwardRow(Object.assign({awardType: 1}, item));
        addDetailRow(item);
    });
    refreshIcons();
}

function toDateTimeLocal(date) {
    const pad = (num) => String(num).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function collectRows(selector) {
    return $$(selector).map((row) => {
        const obj = {};
        row.querySelectorAll("[data-field]").forEach((input) => {
            obj[input.dataset.field] = input.type === "number" || input.tagName === "SELECT" ? Number(input.value) : input.value.trim();
        });
        return obj;
    });
}

async function createActivity() {
    const awards = collectRows(".award-row");
    const details = collectRows(".detail-row").map((item) => ({
        awardId: item.awardId,
        awardName: item.awardName,
        awardCount: Number(item.awardCount || 0),
        awardSurplusCount: Number(item.awardCount || 0),
        awardRate: String(item.awardRate || "0")
    }));
    const data = await post("/api/lottery/activities", {
        activityId: Number($("#newActivityId").value),
        activityName: $("#newActivityName").value.trim(),
        activityDesc: $("#newActivityDesc").value.trim(),
        beginDateTime: $("#newBegin").value,
        endDateTime: $("#newEnd").value,
        stockCount: Number($("#newStock").value),
        stockSurplusCount: Number($("#newStock").value),
        takeCount: Number($("#newTakeCount").value),
        strategyId: Number($("#newStrategyId").value),
        state: 1,
        creator: $("#newCreator").value.trim(),
        strategyDesc: `策略-${$("#newStrategyId").value}`,
        strategyMode: Number($("#newStrategyMode").value),
        grantType: Number($("#newGrantType").value),
        grantDate: $("#newBegin").value,
        extInfo: "",
        awards,
        strategyDetails: details
    });
    toast(data.info || "活动创建完成");
    await Promise.all([loadActivities(), loadCatalog(), loadDiagnostics()]);
}

async function loadTodoActivities() {
    const data = await api("/api/lottery/todo-activities");
    $("#todoRows").innerHTML = (data || []).map((item) => `<tr>
        <td>${escapeHtml(item.activityId)}</td>
        <td>${escapeHtml(item.activityName)}</td>
        <td>${tag(optionLabel("activityStates", item.state), item.state === 5 ? "ok" : "warn")}</td>
        <td>${escapeHtml(formatDate(item.endDateTime))}</td>
    </tr>`).join("") || blankRow(4);
}

async function activityStateScan() {
    const data = await post("/api/lottery/jobs/activity-state-scan", {});
    $("#jobResult").textContent = formatJson(data);
    toast("活动扫描完成");
    await Promise.all([loadActivities(), loadTodoActivities(), loadDiagnostics()]);
}

async function mqScan() {
    const data = await post("/api/lottery/mq/scan", {
        dbCount: Number($("#mqDb").value),
        tbCount: Number($("#mqTb").value)
    });
    $("#jobResult").textContent = formatJson(data);
    toast("MQ 扫描完成");
}

async function refreshAll() {
    try {
        await loadHealth();
        await Promise.all([loadActivities(), loadCatalog(), loadTodoActivities(), loadDiagnostics()]);
        toast("数据已刷新");
    } catch (error) {
        setStatus(false, "连接失败");
        toast(error.message);
    }
}

function bindEvents() {
    $$(".nav-item").forEach((button) => {
        button.addEventListener("click", () => setSection(button.dataset.section));
    });

    $("#refreshBtn").addEventListener("click", refreshAll);

    document.body.addEventListener("click", async (event) => {
        const button = event.target.closest("button");
        if (!button) return;
        const action = button.dataset.action;
        try {
            if (action === "load-activities") await loadActivities();
            if (action === "load-diagnostics") await loadDiagnostics();
            if (action === "change-state") await changeState();
            if (action === "draw") await doDraw();
            if (action === "qdraw") await doQuantificationDraw();
            if (action === "load-strategy-detail") await loadStrategyDetail();
            if (action === "load-rule-detail") await loadRuleDetail();
            if (action === "rule-decision") await ruleDecision();
            if (action === "partake") await partake();
            if (action === "load-user-data") await loadUserData(true);
            if (action === "fill-create-defaults") fillCreateDefaults();
            if (action === "add-award-row") addAwardRow();
            if (action === "add-detail-row") addDetailRow();
            if (action === "create-activity") await createActivity();
            if (action === "activity-state-scan") await activityStateScan();
            if (action === "mq-scan") await mqScan();
            if (action === "remove-row") button.closest(".editor-row").remove();
            if (button.dataset.pickActivity) pickActivity(button.dataset.pickActivity, button.closest("tr")?.dataset.state, button.closest("tr")?.dataset.strategyId);
            if (button.dataset.pickStrategy) pickStrategy(button.dataset.pickStrategy);
            if (button.dataset.pickRule) pickRule(button.dataset.pickRule);
            if (button.dataset.distribute) await manualDistribution(Number(button.dataset.distribute));
        } catch (error) {
            toast(error.message);
        }
    });
}

function pickActivity(activityId, activityState, strategyId) {
    ["#drawActivityId", "#stateActivityId", "#userActivityId"].forEach((selector) => $(selector).value = activityId);
    if (activityState) $("#stateCurrent").value = activityState;
    if (strategyId) pickStrategy(strategyId);
    toast(`已选择活动 ${activityId}`);
}

function pickStrategy(strategyId) {
    $("#strategyDetailId").value = strategyId;
}

function pickRule(ruleId) {
    ["#qDrawTreeId", "#ruleTreeId", "#ruleDetailId"].forEach((selector) => $(selector).value = ruleId);
}

async function boot() {
    bindEvents();
    try {
        await loadMeta();
        fillCreateDefaults();
        await refreshAll();
    } catch (error) {
        setStatus(false, "连接失败");
        toast(error.message);
    }
    refreshIcons();
}

document.addEventListener("DOMContentLoaded", boot);
