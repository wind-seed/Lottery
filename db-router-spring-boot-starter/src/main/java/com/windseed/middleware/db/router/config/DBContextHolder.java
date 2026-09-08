package com.windseed.middleware.db.router.config;

public final class DBContextHolder {

    private static final ThreadLocal<String> DB_KEY = new ThreadLocal<>();
    private static final ThreadLocal<String> TB_KEY = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SPLIT_TABLE = new ThreadLocal<>();

    private DBContextHolder() {
    }

    public static void setDBKey(String dbKey) {
        DB_KEY.set(dbKey);
    }

    public static String getDBKey() {
        return DB_KEY.get();
    }

    public static void setTBKey(String tbKey) {
        TB_KEY.set(tbKey);
    }

    public static String getTBKey() {
        return TB_KEY.get();
    }

    public static void setSplitTable(boolean splitTable) {
        SPLIT_TABLE.set(splitTable);
    }

    public static boolean isSplitTable() {
        return Boolean.TRUE.equals(SPLIT_TABLE.get());
    }

    public static void clear() {
        DB_KEY.remove();
        TB_KEY.remove();
        SPLIT_TABLE.remove();
    }

}
