package com.flightchess.net;

/**
 * 网络配置。
 *
 * 为简化部署，使用固定端口和简单字符串房间号。
 */
public final class NetConfig {

    private NetConfig() {
    }

    /** 默认监听端口。实际可在 UI 设置中修改。 */
    public static final int DEFAULT_PORT = 34567;

    /** 心跳间隔（毫秒）。 */
    public static final long HEARTBEAT_INTERVAL_MS = 3_000L;

    /** 判定掉线的超时时间（毫秒）。 */
    public static final long HEARTBEAT_TIMEOUT_MS = 10_000L;
}

