package cn.fhm.titles.econ;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Vault 经济软依赖（纯反射，不强制装 Vault 也能启动）。
 */
public final class VaultHook {

    private Object provider;
    private Method getBalance;
    private Method withdraw;
    private boolean available;

    public VaultHook() {
        resolve();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void resolve() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null) {
                return;
            }
            provider = registration.getClass().getMethod("getProvider").invoke(registration);
            getBalance = economyClass.getMethod("getBalance", String.class);
            withdraw = economyClass.getMethod("withdrawPlayer", String.class, double.class);
            available = provider != null;
        } catch (Throwable ignored) {
            available = false;
        }
    }

    public boolean available() {
        return available;
    }

    public double balance(Player player) {
        if (!available) {
            return 0;
        }
        try {
            Object value = getBalance.invoke(provider, player.getName());
            return value instanceof Number number ? number.doubleValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public boolean withdraw(Player player, double amount) {
        if (!available || amount <= 0) {
            return false;
        }
        try {
            Object response = withdraw.invoke(provider, player.getName(), amount);
            Object success = response.getClass().getField("transactionSuccess").get(response);
            return success instanceof Boolean flag && flag;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
