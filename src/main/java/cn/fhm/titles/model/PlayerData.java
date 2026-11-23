package cn.fhm.titles.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 单个玩家的称号数据。UUID 作为主键，所有字段可直接序列化为数据库字符串列。
 */
public final class PlayerData {

    public final UUID uuid;
    public String name = "";
    public String equipped = "";
    public final Map<String, Long> owned = new LinkedHashMap<>();
    public long coins = 0L;
    public boolean showChat = true;
    public boolean showTab = true;
    public boolean showHead = true;
    public final Set<String> purchased = new LinkedHashSet<>();
    public final Set<String> claimed = new LinkedHashSet<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * 深拷贝一份独立快照，交给数据库线程序列化。
     * 直接把活对象丢给异步线程会有并发问题：主线程 grant/revoke 时 DB 线程正在遍历 owned（LinkedHashMap），
     * 轻则 ConcurrentModificationException 让这次存档静默失败，重则写出半截数据。
     */
    public PlayerData copy() {
        PlayerData copy = new PlayerData(uuid);
        copy.name = name;
        copy.equipped = equipped;
        copy.coins = coins;
        copy.showChat = showChat;
        copy.showTab = showTab;
        copy.showHead = showHead;
        copy.owned.putAll(owned);
        copy.purchased.addAll(purchased);
        copy.claimed.addAll(claimed);
        return copy;
    }

    public boolean owns(String id) {
        return owned.containsKey(id);
    }

    public boolean ownedPermanent(String id) {
        Long expiry = owned.get(id);
        return expiry != null && expiry < 0;
    }

    public boolean ownedValid(String id) {
        Long expiry = owned.get(id);
        if (expiry == null) {
            return false;
        }
        return expiry < 0 || expiry > System.currentTimeMillis();
    }

    public long expiry(String id) {
        Long value = owned.get(id);
        return value == null ? Long.MIN_VALUE : value;
    }

    public void grant(String id, int days) {
        if (ownedPermanent(id)) {
            return;
        }
        if (days < 0) {
            owned.put(id, -1L);
            purchased.add(id);
            return;
        }
        long now = System.currentTimeMillis();
        long base = Math.max(now, expiry(id) == Long.MIN_VALUE ? now : Math.max(now, expiry(id)));
        owned.put(id, base + days * 86_400_000L);
        purchased.add(id);
    }

    public void revoke(String id) {
        owned.remove(id);
        if (equipped.equals(id)) {
            equipped = "";
        }
    }

    public long remainingMillis(String id) {
        Long expiry = owned.get(id);
        if (expiry == null) {
            return 0;
        }
        if (expiry < 0) {
            return -1;
        }
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    public String ownedSerialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> entry : owned.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    public void ownedDeserialize(String raw) {
        owned.clear();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (String part : raw.split(";")) {
            int idx = part.lastIndexOf('=');
            if (idx <= 0) {
                continue;
            }
            String id = part.substring(0, idx);
            try {
                owned.put(id, Long.parseLong(part.substring(idx + 1)));
            } catch (NumberFormatException ignored) {
                // 跳过损坏数据
            }
        }
    }

    public String flagsSerialize() {
        return (showChat ? "1" : "0") + "|" + (showTab ? "1" : "0") + "|" + (showHead ? "1" : "0");
    }

    public void flagsDeserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String[] parts = raw.split("\\|");
        if (parts.length > 0) {
            showChat = parts[0].equals("1");
        }
        if (parts.length > 1) {
            showTab = parts[1].equals("1");
        }
        if (parts.length > 2) {
            showHead = parts[2].equals("1");
        }
    }

    public static String setSerialize(Set<String> set) {
        return String.join(",", set);
    }

    public static List<String> setDeserialize(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }
}
