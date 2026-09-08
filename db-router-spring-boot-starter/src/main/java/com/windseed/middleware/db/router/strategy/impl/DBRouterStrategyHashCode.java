package com.windseed.middleware.db.router.strategy.impl;

import com.windseed.middleware.db.router.config.DBContextHolder;
import com.windseed.middleware.db.router.strategy.IDBRouterStrategy;

public class DBRouterStrategyHashCode implements IDBRouterStrategy {

    private final int dbCount;
    private final int tbCount;
    private final String defaultDbKey;

    public DBRouterStrategyHashCode(int dbCount, int tbCount, String defaultDbKey) {
        this.dbCount = dbCount;
        this.tbCount = tbCount;
        this.defaultDbKey = defaultDbKey;
    }

    @Override
    public void doRouter(String dbKeyAttr) {
        if (null == dbKeyAttr || dbKeyAttr.trim().isEmpty()) {
            DBContextHolder.setDBKey(defaultDbKey);
            return;
        }

        int size = dbCount * tbCount;
        int idx = (dbKeyAttr.hashCode() ^ (dbKeyAttr.hashCode() >>> 16)) & (size - 1);
        int dbIdx = idx / tbCount + 1;
        int tbIdx = idx - tbCount * (dbIdx - 1);
        setDBKey(dbIdx);
        setTBKey(tbIdx);
    }

    @Override
    public void setDBKey(int dbIdx) {
        DBContextHolder.setDBKey(String.format("db%02d", dbIdx));
    }

    @Override
    public void setTBKey(int tbIdx) {
        DBContextHolder.setTBKey(String.format("%03d", tbIdx));
    }

    @Override
    public int dbCount() {
        return dbCount;
    }

    @Override
    public int tbCount() {
        return tbCount;
    }

    @Override
    public void clear() {
        DBContextHolder.clear();
    }

}
