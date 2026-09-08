package com.windseed.middleware.db.router.strategy;

public interface IDBRouterStrategy {

    void doRouter(String dbKeyAttr);

    void setDBKey(int dbIdx);

    void setTBKey(int tbIdx);

    int dbCount();

    int tbCount();

    void clear();

}
