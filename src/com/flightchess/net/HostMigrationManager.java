package com.flightchess.net;

/**
 * 房主掉线后主机权威迁移的辅助类。
 *
 * 当前版本提供一个非常基础的策略：
 * - 由客户端在检测到与房主的连接中断时，基于 RoomInfo 中玩家顺序
 *   选择“颜色顺序上最靠前且仍在线的玩家”作为新的候选房主；
 * - 具体的 UI 交互（是否接受成为新房主、重新创建房间等）由上层控制器处理。
 */
public class HostMigrationManager {

    /**
     * 根据房间信息与原房主 ID，选择新的候选房主 ID。
     * 若找不到合适人选则返回 null。
     */
    public String chooseNewHost(RoomInfo roomInfo, String oldHostId) {
        if (roomInfo == null || roomInfo.getPlayers().isEmpty()) {
            return null;
        }
        // 简化策略：按玩家列表顺序找到第一个不是旧房主的玩家。
        for (PlayerInfo p : roomInfo.getPlayers()) {
            if (!p.getPlayerId().equals(oldHostId)) {
                return p.getPlayerId();
            }
        }
        return null;
    }
}

