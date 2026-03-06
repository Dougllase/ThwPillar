package com.newpillar.game.advancements;

import com.newpillar.game.enums.AchievementType;

import com.newpillar.NewPillar;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class AchievementSystem {
    private final NewPillar plugin;
    private final Map<UUID, Set<AchievementType>> playerAchievements = new HashMap<>();
    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private final Map<UUID, Integer> playerWins = new HashMap<>();
    private final Map<UUID, Integer> playerDeaths = new HashMap<>();
    private final Map<UUID, Integer> playerElbowKills = new HashMap<>();
    
    public AchievementSystem(NewPillar plugin) {
        this.plugin = plugin;
        loadData();
    }
    
    public void grantAchievement(Player player, AchievementType achievement) {
        UUID uuid = player.getUniqueId();
        Set<AchievementType> achievements = playerAchievements.computeIfAbsent(uuid, k -> new HashSet<>());
        
        plugin.getLogger().info("[调试] 尝试授予成就: " + achievement.getTitle() + " 给玩家: " + player.getName());
        plugin.getLogger().info("[调试] 玩家已有成就数: " + achievements.size());
        plugin.getLogger().info("[调试] 是否已包含该成就: " + achievements.contains(achievement));
        
        if (!achievements.contains(achievement)) {
            achievements.add(achievement);
            plugin.getLogger().info("[调试] 成就已添加到内存");
            
            // 注：音效由原版进度系统控制，这里不再播放
            
            // 发送成就消息
            String frameColor = switch (achievement.getFrame()) {
                case "challenge" -> "§6";
                case "goal" -> "§d";
                default -> "§a";
            };
            
            // 保存到数据库
            saveAchievementToDatabase(uuid, achievement);
            plugin.getLogger().info("[调试] 数据库保存完成");
            
            // 授予原生进度（会自动显示游戏内进度提示）
            grantAdvancement(player, achievement);
            plugin.getLogger().info("[调试] 原生进度授予完成");
            
            // 发送成就解锁消息给玩家
            player.sendMessage("§6§l[成就] §r" + frameColor + achievement.getTitle() + " §7- " + achievement.getDescription());
            plugin.getLogger().info("[调试] 成就授予完成: " + achievement.getTitle());
        } else {
            plugin.getLogger().info("[调试] 玩家已拥有该成就，跳过授予");
        }
    }
    
    /**
     * 授予玩家原生 Minecraft 进度
     */
    private void grantAdvancement(Player player, AchievementType achievement) {
        // 优先使用动态生成的进度
        AdvancementGenerator generator = plugin.getAdvancementGenerator();
        if (generator != null && generator.isGenerated()) {
            String key = generator.convertToKey(achievement);
            generator.grantAdvancement(player, key);
            return;
        }
        
        // 回退到旧的 AdvancementManager
        AdvancementManager advancementManager = plugin.getAdvancementManager();
        if (advancementManager != null && advancementManager.isInitialized()) {
            String key = advancementManager.convertToKey(achievement);
            advancementManager.grantAdvancement(player, key);
        }
    }
    
    /**
     * 保存成就到数据库
     */
    private void saveAchievementToDatabase(UUID uuid, AchievementType achievement) {
        if (plugin.getDatabaseManager() != null) {
            String key;
            if (plugin.getAdvancementGenerator() != null) {
                key = plugin.getAdvancementGenerator().convertToKey(achievement);
            } else {
                key = plugin.getAdvancementManager().convertToKey(achievement);
            }
            plugin.getDatabaseManager().saveAchievement(uuid, key);
        }
    }
    
    public void addKill(Player player) {
        UUID uuid = player.getUniqueId();
        int kills = playerKills.getOrDefault(uuid, 0) + 1;
        playerKills.put(uuid, kills);
        
        // 检查击杀成就
        if (kills >= 1) grantAchievement(player, AchievementType.KILLED_1);
        if (kills >= 20) grantAchievement(player, AchievementType.KILLED_20);
        if (kills >= 50) grantAchievement(player, AchievementType.KILLED_50);
        if (kills >= 100) grantAchievement(player, AchievementType.KILLED_100);
        if (kills >= 200) grantAchievement(player, AchievementType.KILLED_200);
        
        saveData();
    }
    
    public void addWin(Player player) {
        UUID uuid = player.getUniqueId();
        int wins = playerWins.getOrDefault(uuid, 0) + 1;
        playerWins.put(uuid, wins);
        
        // 检查胜利成就
        if (wins >= 1) grantAchievement(player, AchievementType.WIN_1);
        if (wins >= 20) grantAchievement(player, AchievementType.WIN_20);
        if (wins >= 50) grantAchievement(player, AchievementType.WIN_50);
        if (wins >= 100) grantAchievement(player, AchievementType.WIN_100);
        if (wins >= 200) grantAchievement(player, AchievementType.WIN_200);
        
        saveData();
    }
    
    public void grantItemAchievement(Player player, String itemId) {
        AchievementType achievement = switch (itemId.toLowerCase()) {
            case "bruce" -> AchievementType.BRUCE;
            case "blue_screen" -> AchievementType.BLUE_SCREEN;
            case "fly_mace" -> AchievementType.FLY_MACE;
            case "invisible_scarf" -> AchievementType.INVISIBLE_SCARF;
            case "big_flame_rod" -> AchievementType.BIG_FLAME_ROD;
            case "meow_axe" -> AchievementType.MEOW_AXE;
            case "pixie" -> AchievementType.PIXIE;
            case "rocket_boots" -> AchievementType.ROCKET_BOOTS;
            case "running_shoes" -> AchievementType.RUNNING_SHOES;
            case "witch_apple" -> AchievementType.WITCH_APPLE;
            case "yanpai" -> AchievementType.YANPAI;
            case "clock" -> AchievementType.CLOCK;
            case "hongbao" -> AchievementType.HONGBAO;
            case "hypnosis_app" -> AchievementType.HYPNOSIS_APP;
            case "bones_without_chicken_feet" -> AchievementType.BONES_WITHOUT_CHICKEN_FEET;
            case "knockback_stick" -> AchievementType.KNOCKBACK_STICK;
            case "godly_pickaxe" -> AchievementType.GODLY_PICKAXE;
            case "spawner" -> AchievementType.SPANWER;
            case "nether_star" -> AchievementType.NETHER_STAR_USE;
            case "dragon_breath" -> AchievementType.DRAGON_BREATH_USE;
            case "echo_shard" -> AchievementType.ECHO_SHARD_USE;
            case "fire_charge" -> AchievementType.FIRE_CHARGE_USE;
            case "tnt" -> AchievementType.TNT_USE;
            case "bow" -> AchievementType.BOW_USE;
            case "crossbow" -> AchievementType.CROSSBOW_USE;
            case "end_crystal" -> AchievementType.END_CRYSTAL_USE;
            case "feather" -> AchievementType.FEATHER_USE;
            case "enchanted_book" -> AchievementType.ENCHANTED_BOOK_USE;
            default -> null;
        };
        
        if (achievement != null) {
            grantAchievement(player, achievement);
        }
    }
    
    public void grantEventAchievement(Player player, String eventType) {
        AchievementType achievement = switch (eventType.toLowerCase()) {
            case "king_game" -> AchievementType.KING_GAME;
            case "nuclear" -> AchievementType.NUCLEAR;
            case "kingslayer" -> AchievementType.KINGSLAYER;
            default -> null;
        };

        if (achievement != null) {
            grantAchievement(player, achievement);
        }
    }

    /**
     * 处理玩家死亡成就
     */
    public void onPlayerDeath(Player player) {
        UUID uuid = player.getUniqueId();
        int deaths = playerDeaths.getOrDefault(uuid, 0) + 1;
        playerDeaths.put(uuid, deaths);

        // 检查死亡成就
        if (deaths >= 1) {
            grantAchievement(player, AchievementType.DEATH_1);
        }

        // 检查是否摔死
        if (player.getLastDamageCause() != null &&
            player.getLastDamageCause().getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
            grantAchievement(player, AchievementType.DEATH_FALL);
        }

        saveData();
    }

    /**
     * 添加肘击击杀（空手击杀）
     */
    public void addElbowKill(Player player) {
        UUID uuid = player.getUniqueId();
        int elbowKills = playerElbowKills.getOrDefault(uuid, 0) + 1;
        playerElbowKills.put(uuid, elbowKills);

        // 检查肘击王成就
        if (elbowKills >= 50) {
            grantAchievement(player, AchievementType.ELBOW_KING);
        }

        saveData();
    }

    public int getElbowKills(UUID uuid) {
        return playerElbowKills.getOrDefault(uuid, 0);
    }
    
    public boolean hasAchievement(UUID uuid, AchievementType achievement) {
        return playerAchievements.getOrDefault(uuid, new HashSet<>()).contains(achievement);
    }
    
    public int getKills(UUID uuid) {
        return playerKills.getOrDefault(uuid, 0);
    }
    
    public int getWins(UUID uuid) {
        return playerWins.getOrDefault(uuid, 0);
    }
    
    public Set<AchievementType> getPlayerAchievements(UUID uuid) {
        return playerAchievements.getOrDefault(uuid, new HashSet<>());
    }
    
    /**
     * 从数据库加载玩家成就数据
     * 在玩家加入时调用
     */
    public void loadPlayerData(UUID uuid) {
        if (plugin.getDatabaseManager() == null) return;
        
        // 加载成就
        Set<String> achievementKeys = plugin.getDatabaseManager().getPlayerAchievements(uuid);
        Set<AchievementType> achievements = new HashSet<>();
        
        for (String key : achievementKeys) {
            AchievementType type = convertKeyToType(key);
            if (type != null) {
                achievements.add(type);
            }
        }
        
        playerAchievements.put(uuid, achievements);
        
        // 从数据库加载击杀和胜利数
        var stats = plugin.getDatabaseManager().loadStatistics(uuid);
        // 这里我们假设统计数据中有击杀和胜利数，实际可能需要单独的表
        // 暂时使用内存中的数据
        
        plugin.getLogger().info("已从数据库加载玩家 " + uuid + " 的 " + achievements.size() + " 个成就");
    }
    
    /**
     * 将进度 key 转换为 AchievementType
     */
    private AchievementType convertKeyToType(String key) {
        return switch (key.toLowerCase()) {
            case "killed_1" -> AchievementType.KILLED_1;
            case "killed_20" -> AchievementType.KILLED_20;
            case "killed_50" -> AchievementType.KILLED_50;
            case "killed_100" -> AchievementType.KILLED_100;
            case "killed_200" -> AchievementType.KILLED_200;
            case "win_1" -> AchievementType.WIN_1;
            case "win_20" -> AchievementType.WIN_20;
            case "win_50" -> AchievementType.WIN_50;
            case "win_100" -> AchievementType.WIN_100;
            case "win_200" -> AchievementType.WIN_200;
            case "king_game" -> AchievementType.KING_GAME;
            case "nuclear" -> AchievementType.NUCLEAR;
            case "kingslayer" -> AchievementType.KINGSLAYER;
            case "bruce" -> AchievementType.BRUCE;
            case "blue_screen" -> AchievementType.BLUE_SCREEN;
            case "fly_mace" -> AchievementType.FLY_MACE;
            case "invisible_scarf" -> AchievementType.INVISIBLE_SCARF;
            case "big_flame_rod" -> AchievementType.BIG_FLAME_ROD;
            case "meow_axe" -> AchievementType.MEOW_AXE;
            case "pixie" -> AchievementType.PIXIE;
            case "rocket_boots" -> AchievementType.ROCKET_BOOTS;
            case "running_shoes" -> AchievementType.RUNNING_SHOES;
            case "witch_apple" -> AchievementType.WITCH_APPLE;
            case "yanpai" -> AchievementType.YANPAI;
            case "clock" -> AchievementType.CLOCK;
            case "hongbao" -> AchievementType.HONGBAO;
            case "hypnosis_app" -> AchievementType.HYPNOSIS_APP;
            case "bones_without_chicken_feet" -> AchievementType.BONES_WITHOUT_CHICKEN_FEET;
            case "knockback_stick" -> AchievementType.KNOCKBACK_STICK;
            case "godly_pickaxe" -> AchievementType.GODLY_PICKAXE;
            case "spawner" -> AchievementType.SPANWER;
            case "nether_star_use" -> AchievementType.NETHER_STAR_USE;
            case "dragon_breath_use" -> AchievementType.DRAGON_BREATH_USE;
            case "echo_shard_use" -> AchievementType.ECHO_SHARD_USE;
            case "fire_charge_use" -> AchievementType.FIRE_CHARGE_USE;
            case "tnt_use" -> AchievementType.TNT_USE;
            case "bow_use" -> AchievementType.BOW_USE;
            case "crossbow_use" -> AchievementType.CROSSBOW_USE;
            case "end_crystal_use" -> AchievementType.END_CRYSTAL_USE;
            case "feather_use" -> AchievementType.FEATHER_USE;
            case "enchanted_book_use" -> AchievementType.ENCHANTED_BOOK_USE;
            case "death_1" -> AchievementType.DEATH_1;
            case "death_fall" -> AchievementType.DEATH_FALL;
            case "elbow_king" -> AchievementType.ELBOW_KING;
            default -> null;
        };
    }
    
    private void loadData() {
        // 从配置文件加载数据（兼容旧版本）
        if (plugin.getConfig().contains("achievements")) {
            for (String uuidStr : plugin.getConfig().getConfigurationSection("achievements").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Set<AchievementType> achievements = new HashSet<>();
                
                List<String> achievementNames = plugin.getConfig().getStringList("achievements." + uuidStr + ".list");
                for (String name : achievementNames) {
                    try {
                        achievements.add(AchievementType.valueOf(name));
                    } catch (IllegalArgumentException ignored) {}
                }
                
                playerAchievements.put(uuid, achievements);
                playerKills.put(uuid, plugin.getConfig().getInt("achievements." + uuidStr + ".kills", 0));
                playerWins.put(uuid, plugin.getConfig().getInt("achievements." + uuidStr + ".wins", 0));
            }
        }
    }
    
    private void saveData() {
        // 保存到配置文件（作为备份）
        for (Map.Entry<UUID, Set<AchievementType>> entry : playerAchievements.entrySet()) {
            String uuidStr = entry.getKey().toString();
            List<String> achievementNames = new ArrayList<>();
            
            for (AchievementType achievement : entry.getValue()) {
                achievementNames.add(achievement.name());
            }
            
            plugin.getConfig().set("achievements." + uuidStr + ".list", achievementNames);
            plugin.getConfig().set("achievements." + uuidStr + ".kills", playerKills.getOrDefault(entry.getKey(), 0));
            plugin.getConfig().set("achievements." + uuidStr + ".wins", playerWins.getOrDefault(entry.getKey(), 0));
        }
        
        plugin.saveConfig();
    }
    
    public void showAchievementMenu(Player player) {
        UUID uuid = player.getUniqueId();
        Set<AchievementType> unlocked = getPlayerAchievements(uuid);
        
        player.sendMessage("§6§l========== 成就系统 ==========");
        player.sendMessage("§7击杀数: §f" + getKills(uuid));
        player.sendMessage("§7胜利数: §f" + getWins(uuid));
        player.sendMessage("§7已解锁成就: §f" + unlocked.size() + "/" + AchievementType.values().length);
        player.sendMessage("");
        
        // 显示已解锁成就
        if (!unlocked.isEmpty()) {
            player.sendMessage("§a§l已解锁成就:");
            for (AchievementType achievement : unlocked) {
                String color = switch (achievement.getFrame()) {
                    case "challenge" -> "§6";
                    case "goal" -> "§d";
                    default -> "§a";
                };
                player.sendMessage(color + "✦ " + achievement.getTitle() + " §7- " + achievement.getDescription());
            }
        }
        
        player.sendMessage("§6§l==============================");
    }
    
    /**
     * 打开原生进度界面
     */
    public void openAdvancementInterface(Player player) {
        AdvancementManager advancementManager = plugin.getAdvancementManager();
        if (advancementManager != null) {
            advancementManager.openAdvancementMenu(player);
        } else {
            player.sendMessage("§c成就系统尚未初始化！");
        }
    }
}
