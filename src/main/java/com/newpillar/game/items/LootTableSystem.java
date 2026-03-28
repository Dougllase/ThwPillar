package com.newpillar.game.items;

import com.google.gson.*;
import com.newpillar.NewPillar;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class LootTableSystem {
    private final NewPillar plugin;
    private final SpecialItemManager specialItemManager;
    private final Map<String, LootTable> lootTables = new HashMap<>();
    private final Map<String, List<String>> itemTags = new HashMap<>();
    private final Random random = new Random();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // 特殊物品ID映射
    private final Map<String, SpecialItemCreator> specialItems = new HashMap<>();
    
    // 管理员物品列表（不允许玩家获取）
    private static final Set<Material> ADMIN_ITEMS = EnumSet.of(
        Material.COMMAND_BLOCK,
        Material.CHAIN_COMMAND_BLOCK,
        Material.REPEATING_COMMAND_BLOCK,
        Material.STRUCTURE_BLOCK,
        Material.JIGSAW,
        Material.TEST_BLOCK,
        Material.TEST_INSTANCE_BLOCK,
        Material.STRUCTURE_VOID,
        Material.BARRIER,  // 屏障也包含在内，虽然之前允许
        Material.LIGHT,
        Material.COMMAND_BLOCK_MINECART,
        Material.DEBUG_STICK,
        Material.KNOWLEDGE_BOOK,
        Material.BUNDLE  // 可能包含任意物品
    );
    
    // 最大重抽次数，防止无限循环
    private static final int MAX_REROLL_ATTEMPTS = 10;
    
    public LootTableSystem(NewPillar plugin) {
        this.plugin = plugin;
        this.specialItemManager = new SpecialItemManager(plugin);
        initSpecialItems();
        loadItemTags();
        loadLootTables();
    }
    
    /**
     * 加载物品标签配置
     */
    private void loadItemTags() {
        File tagsDir = new File(plugin.getDataFolder(), "tags/item");
        if (!tagsDir.exists()) {
            tagsDir.mkdirs();
        }
        
        // 每次都重新复制默认标签文件（确保更新）
        createDefaultItemTags(tagsDir);
        
        loadItemTagsFromDirectory(tagsDir, "newpillar");
    }
    
    /**
     * 从目录加载物品标签
     */
    private void loadItemTagsFromDirectory(File dir, String namespace) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                loadItemTagsFromDirectory(file, namespace);
            } else if (file.getName().endsWith(".json")) {
                String name = namespace + ":" + file.getName().replace(".json", "");
                try {
                    List<String> items = loadItemTag(file);
                    itemTags.put(name, items);
                    plugin.getLogger().info("加载物品标签: " + name + " (" + items.size() + " 个物品)");
                } catch (Exception e) {
                    plugin.getLogger().warning("加载物品标签失败: " + name + " - " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 加载单个物品标签文件
     */
    private List<String> loadItemTag(File file) throws IOException {
        List<String> items = new ArrayList<>();
        
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            
            if (json.has("values")) {
                JsonArray values = json.getAsJsonArray("values");
                for (JsonElement element : values) {
                    items.add(element.getAsString());
                }
            }
        }
        
        return items;
    }
    
    /**
     * 创建默认物品标签文件
     */
    private void createDefaultItemTags(File tagsDir) {
        // 从插件资源中复制默认标签文件
        try {
            // 复制 item.json
            InputStream itemTagStream = plugin.getResource("tags/item/item.json");
            if (itemTagStream != null) {
                File itemTagFile = new File(tagsDir, "item.json");
                copyResourceToFile(itemTagStream, itemTagFile);
                itemTagStream.close();
                plugin.getLogger().info("已复制默认物品标签文件: item.json");
            } else {
                plugin.getLogger().warning("无法从资源加载 tags/item/item.json");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("创建默认物品标签失败: " + e.getMessage());
        }
    }
    
    /**
     * 复制资源文件到目标位置
     */
    private void copyResourceToFile(InputStream source, File target) throws IOException {
        target.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = source.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }
    
    private void initSpecialItems() {
        // 注册特殊物品创建器（与战利品表 special/all.json 中的物品对应）
        specialItems.put("knockback_stick", this::createKnockbackStick);
        specialItems.put("witch_apple", this::createWitchApple);
        specialItems.put("bruce", this::createBruce);
        specialItems.put("godly_pickaxe", this::createGodlyPickaxe);
        specialItems.put("spear", this::createSpear);
        specialItems.put("clock", this::createClock);
        specialItems.put("meow_axe", this::createMeowAxe);
        specialItems.put("big_flame_rod", this::createBigFlameRod);
        specialItems.put("yanpai", this::createYanpai);
        specialItems.put("blue_screen", this::createBlueScreen);
        specialItems.put("gravity_boots", this::createGravityBoots);
        specialItems.put("hongbao", this::createHongbao);
        specialItems.put("hypnosis_app", this::createHypnosisApp);
        specialItems.put("invisible_sand", this::createInvisibleSand);
        specialItems.put("feather", this::createFeather);
        specialItems.put("rocket_boots", this::createRocketBoots);
        specialItems.put("running_shoes", this::createRunningShoes);
        specialItems.put("fly_mace", this::createFlyMace);
        specialItems.put("bones_without_chicken_feet", this::createBonesWithoutChickenFeet);
        specialItems.put("iron_sword", this::createSpecialIronSword);
        specialItems.put("pixie", this::createPixie);
        specialItems.put("spawner", this::createSpawner);
        // 新增的特殊物品
        specialItems.put("special_bow", this::createSpecialBow);
        specialItems.put("special_crossbow", this::createSpecialCrossbow);
        specialItems.put("life_steal_sword", this::createLifeStealSword);
        specialItems.put("poison_dagger", this::createPoisonDagger);
        specialItems.put("shield_generator", this::createShieldGenerator);
        specialItems.put("ex_curry_stick", this::createExCurryStick);
        specialItems.put("the_world", this::createTheWorld);
        specialItems.put("russian_roulette", this::createRussianRoulette);
        // 其他系统中使用的特殊物品
        specialItems.put("lucky_block", this::createLuckyBlock);
    }
    
    private void loadLootTables() {
        File lootTableDir = new File(plugin.getDataFolder(), "loot_tables");
        if (!lootTableDir.exists()) {
            lootTableDir.mkdirs();
        }
        
        // 每次都检查并创建默认战利品表（确保更新）
        createDefaultLootTables(lootTableDir);
        
        loadLootTableFromDirectory(lootTableDir, "");
    }
    
    private void loadLootTableFromDirectory(File dir, String prefix) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                loadLootTableFromDirectory(file, prefix + file.getName() + "/");
            } else if (file.getName().endsWith(".json")) {
                String name = prefix + file.getName().replace(".json", "");
                try {
                    LootTable table = loadLootTable(file);
                    lootTables.put(name, table);
                    plugin.getLogger().info("加载战利品表: " + name);
                } catch (Exception e) {
                    plugin.getLogger().warning("加载战利品表失败: " + name + " - " + e.getMessage());
                }
            }
        }
    }
    
    private LootTable loadLootTable(File file) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            return parseLootTable(json);
        }
    }
    
    private LootTable parseLootTable(JsonObject json) {
        LootTable table = new LootTable();
        
        if (json.has("pools")) {
            JsonArray pools = json.getAsJsonArray("pools");
            for (JsonElement poolElement : pools) {
                JsonObject poolObj = poolElement.getAsJsonObject();
                LootPool pool = new LootPool();
                
                int rolls = poolObj.has("rolls") ? poolObj.get("rolls").getAsInt() : 1;
                pool.setRolls(rolls);
                
                if (poolObj.has("entries")) {
                    JsonArray entries = poolObj.getAsJsonArray("entries");
                    for (JsonElement entryElement : entries) {
                        JsonObject entryObj = entryElement.getAsJsonObject();
                        LootEntry entry = parseLootEntry(entryObj);
                        if (entry != null) {
                            pool.addEntry(entry);
                        }
                    }
                }
                
                table.addPool(pool);
            }
        }
        
        return table;
    }
    
    private LootEntry parseLootEntry(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "item";
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        
        LootEntry entry = new LootEntry(type, weight);
        
        switch (type) {
            case "item" -> {
                if (json.has("name")) {
                    entry.setItemName(json.get("name").getAsString());
                }
            }
            case "loot_table" -> {
                if (json.has("value")) {
                    entry.setLootTableName(json.get("value").getAsString());
                }
            }
            case "special", "special_item" -> {
                if (json.has("name")) {
                    entry.setSpecialItemName(json.get("name").getAsString());
                }
            }
            case "potion" -> {
                // 旧格式兼容
                entry.setPotionType(true);
                if (json.has("effect")) {
                    entry.setPotionEffect(json.get("effect").getAsString());
                }
                if (json.has("duration")) {
                    entry.setPotionDuration(json.get("duration").getAsInt());
                }
                if (json.has("amplifier")) {
                    entry.setPotionAmplifier(json.get("amplifier").getAsInt());
                }
            }
            case "enchanted_book" -> {
                entry.setEnchantedBook(true);
            }
            case "tag" -> {
                // 支持 tag 类型（数据包格式）
                if (json.has("name")) {
                    entry.setTagName(json.get("name").getAsString());
                }
                entry.setExpand(json.has("expand") && json.get("expand").getAsBoolean());
            }
            case "group" -> {
                // 支持 group 类型
                entry.setGroup(true);
                if (json.has("children") || json.has("entries")) {
                    JsonArray children = json.has("children") ? 
                        json.getAsJsonArray("children") : json.getAsJsonArray("entries");
                    List<LootEntry> childEntries = new ArrayList<>();
                    for (JsonElement childElement : children) {
                        LootEntry childEntry = parseLootEntry(childElement.getAsJsonObject());
                        if (childEntry != null) {
                            childEntries.add(childEntry);
                        }
                    }
                    entry.setChildEntries(childEntries);
                }
            }
        }
        
        // 解析functions（新格式支持）
        if (json.has("functions")) {
            JsonArray functions = json.getAsJsonArray("functions");
            List<LootFunction> functionList = new ArrayList<>();
            for (JsonElement funcElement : functions) {
                JsonObject funcObj = funcElement.getAsJsonObject();
                String function = funcObj.get("function").getAsString();
                
                switch (function) {
                    case "set_count" -> {
                        if (funcObj.has("count")) {
                            JsonObject countObj = funcObj.getAsJsonObject("count");
                            int min = countObj.has("min") ? countObj.get("min").getAsInt() : 1;
                            int max = countObj.has("max") ? countObj.get("max").getAsInt() : 1;
                            entry.setCountRange(min, max);
                        }
                    }
                    case "set_custom_data" -> {
                        if (funcObj.has("tag")) {
                            entry.setCustomData(funcObj.getAsJsonObject("tag"));
                        }
                    }
                    case "set_lore" -> {
                        if (funcObj.has("lore")) {
                            List<String> lore = new ArrayList<>();
                            JsonArray loreArray = funcObj.getAsJsonArray("lore");
                            for (JsonElement loreElement : loreArray) {
                                lore.add(loreElement.getAsString());
                            }
                            entry.setLore(lore);
                        }
                    }
                    case "set_potion" -> {
                        // 新格式：item + set_potion function
                        if (funcObj.has("id")) {
                            entry.setPotionType(true);
                            entry.setPotionEffect(funcObj.get("id").getAsString());
                        }
                        // 支持自定义时长（秒）
                        if (funcObj.has("duration")) {
                            entry.setPotionDuration(funcObj.get("duration").getAsInt());
                        }
                        // 支持自定义等级（0=I级, 1=II级）
                        if (funcObj.has("amplifier")) {
                            entry.setPotionAmplifier(funcObj.get("amplifier").getAsInt());
                        }
                    }
                    case "enchant_randomly" -> {
                        entry.setEnchantRandomly(true);
                    }
                    case "set_components" -> {
                        if (funcObj.has("components")) {
                            entry.setComponents(funcObj.getAsJsonObject("components"));
                        }
                    }
                }
            }
            entry.setFunctions(functionList);
        }
        
        return entry;
    }
    
    public ItemStack getRandomLoot(String tableName) {
        LootTable table = lootTables.get(tableName);
        if (table == null) {
            plugin.getLogger().warning("战利品表不存在: " + tableName);
            return null;
        }
        
        List<ItemStack> results = new ArrayList<>();
        
        for (LootPool pool : table.getPools()) {
            for (int i = 0; i < pool.getRolls(); i++) {
                LootEntry entry = pool.getRandomEntry(random);
                if (entry != null) {
                    ItemStack item = createItemFromEntryWithSafetyCheck(entry, tableName);
                    if (item != null) {
                        results.add(item);
                    }
                }
            }
        }
        
        // 返回第一个物品（简化处理）
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * 安全地创建物品，检测并替换管理员物品
     * 如果抽到管理员物品，会自动重抽，营造没有OP物品的假象
     */
    private ItemStack createItemFromEntryWithSafetyCheck(LootEntry entry, String tableName) {
        int attempts = 0;
        ItemStack item = null;
        
        while (attempts < MAX_REROLL_ATTEMPTS) {
            item = createItemFromEntry(entry);
            
            // 检查是否是管理员物品
            if (item != null && isAdminItem(item)) {
                String itemName = item.getType().name();
                plugin.getLogger().info("[安全检测] 抽到了管理员物品 " + itemName + "，正在重新抽取... (尝试 " + (attempts + 1) + "/" + MAX_REROLL_ATTEMPTS + ")");
                attempts++;
                // 继续循环，重新抽取
                continue;
            }
            
            // 不是管理员物品，直接返回
            return item;
        }
        
        // 如果重抽次数用尽，返回null（不给予任何物品）
        plugin.getLogger().warning("[安全检测] 重抽次数用尽，无法获取安全物品，返回空");
        return null;
    }
    
    /**
     * 检查物品是否是管理员物品
     */
    private boolean isAdminItem(ItemStack item) {
        if (item == null) return false;
        return ADMIN_ITEMS.contains(item.getType());
    }
    
    /**
     * 生成战利品（使用配置的 main_pool）
     * 供调试指令使用
     */
    public ItemStack generateLoot() {
        String mainPool = plugin.getConfig().getString("loot_pools.main_pool", "main");
        return getRandomLoot(mainPool);
    }
    
    /**
     * 获取所有已加载的战利品表名称
     * @return 战利品表名称列表
     */
    public List<String> getAllLootTableNames() {
        return new ArrayList<>(lootTables.keySet());
    }
    
    /**
     * 测试指定战利品表
     * @param tableName 战利品表名称
     * @param times 测试次数
     * @return 测试结果统计 Map<物品名称, 出现次数>
     */
    public java.util.Map<String, Integer> testLootTable(String tableName, int times) {
        java.util.Map<String, Integer> results = new java.util.HashMap<>();
        
        for (int i = 0; i < times; i++) {
            ItemStack item = getRandomLoot(tableName);
            if (item != null) {
                String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() 
                    ? item.getItemMeta().getDisplayName() 
                    : item.getType().name();
                results.merge(itemName, 1, Integer::sum);
            } else {
                results.merge("(空)", 1, Integer::sum);
            }
        }
        
        return results;
    }
    
    private ItemStack createItemFromEntry(LootEntry entry) {
        String type = entry.getType();
        return switch (type) {
            case "item" -> createItemWithFunctions(entry);
            case "loot_table" -> getRandomLoot(stripNamespace(entry.getLootTableName()));
            case "special", "special_item" -> createSpecialItem(entry.getSpecialItemName());
            case "potion" -> createPotion(entry);
            case "enchanted_book" -> createEnchantedBook();
            case "tag" -> createTagItem(entry);
            case "group" -> createGroupItem(entry);
            default -> null;
        };
    }
    
    /**
     * 去除战利品表名称中的命名空间前缀
     * 例如 "newpillar:main" -> "main"
     * "newpillar:fishing/fish" -> "fishing/fish"
     */
    private String stripNamespace(String name) {
        if (name == null) return null;
        int colonIndex = name.indexOf(':');
        if (colonIndex >= 0) {
            return name.substring(colonIndex + 1);
        }
        return name;
    }
    
    private ItemStack createItemWithFunctions(LootEntry entry) {
        Material material = Material.matchMaterial(entry.getItemName());
        if (material == null) {
            plugin.getLogger().warning("未知物品: " + entry.getItemName());
            return null;
        }
        
        // 处理药水物品
        if (entry.isPotionType()) {
            return createPotionFromEntry(entry);
        }
        
        int amount = entry.getRandomCount(random);
        ItemStack item = new ItemStack(material, amount);
        
        // 应用functions
        if (entry.isEnchantRandomly()) {
            item = applyEnchantRandomly(item);
        }
        
        // 应用components
        if (entry.getComponents() != null) {
            item = applyComponents(item, entry.getComponents());
        }
        
        return item;
    }
    
    private ItemStack createPotionFromEntry(LootEntry entry) {
        // 判断是喷溅药水还是普通药水
        Material potionMaterial = Material.POTION;
        String itemName = entry.getItemName();
        if (itemName != null && itemName.equalsIgnoreCase("splash_potion")) {
            potionMaterial = Material.SPLASH_POTION;
        }
        
        ItemStack potion = new ItemStack(potionMaterial);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        
        if (meta != null && entry.getPotionEffect() != null) {
            // 检查是否有自定义时长（非默认值600秒）
            boolean hasCustomDuration = entry.getPotionDuration() != 600;
            
            if (hasCustomDuration) {
                // 使用自定义时长和等级
                PotionEffectType effectType = getPotionEffectType(entry.getPotionEffect());
                if (effectType != null) {
                    meta.addCustomEffect(new PotionEffect(effectType, 
                        entry.getPotionDuration() * 20, 
                        entry.getPotionAmplifier()), true);
                }
            } else {
                // 使用原版药水类型（默认时长）
                try {
                    org.bukkit.potion.PotionType potionType = org.bukkit.potion.PotionType.valueOf(
                        entry.getPotionEffect().toUpperCase());
                    meta.setBasePotionType(potionType);
                } catch (IllegalArgumentException e) {
                    // 如果无法识别，使用自定义效果
                    PotionEffectType effectType = PotionEffectType.getByName(entry.getPotionEffect());
                    if (effectType != null) {
                        meta.addCustomEffect(new PotionEffect(effectType, 
                            entry.getPotionDuration() * 20, 
                            entry.getPotionAmplifier()), true);
                    }
                }
            }
            potion.setItemMeta(meta);
        }
        
        return potion;
    }
    
    /**
     * 将药水ID转换为PotionEffectType
     */
    private PotionEffectType getPotionEffectType(String potionId) {
        // 原版药水类型到效果的映射
        return switch (potionId.toLowerCase()) {
            case "swiftness", "speed" -> PotionEffectType.SPEED;
            case "slowness" -> PotionEffectType.SLOWNESS;
            case "haste" -> PotionEffectType.HASTE;
            case "mining_fatigue" -> PotionEffectType.MINING_FATIGUE;
            case "strength" -> PotionEffectType.STRENGTH;
            case "instant_health", "healing" -> PotionEffectType.INSTANT_HEALTH;
            case "instant_damage", "harming" -> PotionEffectType.INSTANT_DAMAGE;
            case "jump_boost", "leaping" -> PotionEffectType.JUMP_BOOST;
            case "nausea" -> PotionEffectType.NAUSEA;
            case "regeneration" -> PotionEffectType.REGENERATION;
            case "resistance" -> PotionEffectType.RESISTANCE;
            case "fire_resistance" -> PotionEffectType.FIRE_RESISTANCE;
            case "water_breathing" -> PotionEffectType.WATER_BREATHING;
            case "invisibility" -> PotionEffectType.INVISIBILITY;
            case "blindness" -> PotionEffectType.BLINDNESS;
            case "night_vision" -> PotionEffectType.NIGHT_VISION;
            case "hunger" -> PotionEffectType.HUNGER;
            case "weakness" -> PotionEffectType.WEAKNESS;
            case "poison" -> PotionEffectType.POISON;
            case "wither" -> PotionEffectType.WITHER;
            case "health_boost" -> PotionEffectType.HEALTH_BOOST;
            case "absorption" -> PotionEffectType.ABSORPTION;
            case "saturation" -> PotionEffectType.SATURATION;
            case "glowing" -> PotionEffectType.GLOWING;
            case "levitation" -> PotionEffectType.LEVITATION;
            case "luck" -> PotionEffectType.LUCK;
            case "unluck" -> PotionEffectType.UNLUCK;
            case "slow_falling" -> PotionEffectType.SLOW_FALLING;
            case "conduit_power" -> PotionEffectType.CONDUIT_POWER;
            case "dolphins_grace" -> PotionEffectType.DOLPHINS_GRACE;
            case "bad_omen" -> PotionEffectType.BAD_OMEN;
            case "hero_of_the_village" -> PotionEffectType.HERO_OF_THE_VILLAGE;
            case "darkness" -> PotionEffectType.DARKNESS;
            case "trial_omen" -> PotionEffectType.TRIAL_OMEN;
            case "raid_omen" -> PotionEffectType.RAID_OMEN;
            case "wind_charged" -> PotionEffectType.WIND_CHARGED;
            case "weaving" -> PotionEffectType.WEAVING;
            case "oozing" -> PotionEffectType.OOZING;
            case "infested" -> PotionEffectType.INFESTED;
            default -> PotionEffectType.getByName(potionId);
        };
    }
    
    private ItemStack createTagItem(LootEntry entry) {
        String tagName = entry.getTagName();
        
        // 从缓存中获取标签物品列表
        List<String> items = itemTags.get(tagName);
        
        if (items == null || items.isEmpty()) {
            plugin.getLogger().warning("物品标签不存在或为空: " + tagName);
            // 回退：返回随机原版物品
            return createRandomVanillaItem();
        }
        
        // 随机选择一个物品
        String itemName = items.get(random.nextInt(items.size()));
        
        // 解析物品名称（支持命名空间）
        String materialName = itemName;
        if (itemName.contains(":")) {
            materialName = itemName.substring(itemName.indexOf(":") + 1);
        }
        
        // 转换为 Material
        Material material = Material.matchMaterial(materialName);
        
        if (material == null) {
            plugin.getLogger().warning("无法识别物品: " + itemName + " (来自标签: " + tagName + ")");
            // 尝试再次随机选择
            return createRandomVanillaItem();
        }
        
        if (!material.isItem()) {
            plugin.getLogger().warning("不是有效物品: " + itemName);
            return createRandomVanillaItem();
        }
        
        return new ItemStack(material);
    }
    
    /**
     * 创建随机原版物品（作为回退）
     */
    private ItemStack createRandomVanillaItem() {
        Material[] materials = Material.values();
        Material randomMaterial = materials[random.nextInt(materials.length)];
        while (!randomMaterial.isItem()) {
            randomMaterial = materials[random.nextInt(materials.length)];
        }
        return new ItemStack(randomMaterial);
    }
    
    private ItemStack createGroupItem(LootEntry entry) {
        // group 类型 - 从子条目中选择
        List<LootEntry> children = entry.getChildEntries();
        if (children.isEmpty()) {
            return null;
        }
        
        // 根据权重选择子条目
        int totalWeight = children.stream().mapToInt(LootEntry::getWeight).sum();
        int roll = random.nextInt(totalWeight);
        int currentWeight = 0;
        
        for (LootEntry child : children) {
            currentWeight += child.getWeight();
            if (roll < currentWeight) {
                return createItemFromEntry(child);
            }
        }
        
        return createItemFromEntry(children.get(0));
    }
    
    private ItemStack applyEnchantRandomly(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        // 随机添加1-3个附魔
        int enchantCount = 1 + random.nextInt(3);
        Enchantment[] enchantments = Enchantment.values();
        
        for (int i = 0; i < enchantCount; i++) {
            Enchantment enchantment = enchantments[random.nextInt(enchantments.length)];
            if (enchantment.canEnchantItem(item)) {
                int level = 1 + random.nextInt(enchantment.getMaxLevel());
                meta.addEnchant(enchantment, level, true);
            }
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack applyComponents(ItemStack item, JsonObject components) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        // 应用 custom_name
        if (components.has("custom_name")) {
            JsonObject nameObj = components.getAsJsonObject("custom_name");
            String name = nameObj.has("text") ? nameObj.get("text").getAsString() : "";
            meta.setDisplayName("§6§l" + name);
        }
        
        // 应用 lore
        if (components.has("lore")) {
            List<String> lore = new ArrayList<>();
            JsonArray loreArray = components.getAsJsonArray("lore");
            for (JsonElement loreElement : loreArray) {
                if (loreElement.isJsonObject()) {
                    JsonObject loreObj = loreElement.getAsJsonObject();
                    String text = loreObj.has("text") ? loreObj.get("text").getAsString() : "";
                    String color = loreObj.has("color") ? loreObj.get("color").getAsString() : "gray";
                    lore.add("§" + getColorCode(color) + text);
                } else if (loreElement.isJsonArray()) {
                    // 处理复杂 lore 数组
                    JsonArray loreParts = loreElement.getAsJsonArray();
                    StringBuilder loreLine = new StringBuilder();
                    for (JsonElement part : loreParts) {
                        if (part.isJsonObject()) {
                            JsonObject partObj = part.getAsJsonObject();
                            String text = partObj.has("text") ? partObj.get("text").getAsString() : "";
                            String color = partObj.has("color") ? partObj.get("color").getAsString() : "gray";
                            loreLine.append("§").append(getColorCode(color)).append(text);
                        }
                    }
                    lore.add(loreLine.toString());
                }
            }
            meta.setLore(lore);
        }
        
        // 应用 enchantment_glint_override
        if (components.has("enchantment_glint_override")) {
            meta.setEnchantmentGlintOverride(components.get("enchantment_glint_override").getAsBoolean());
        }
        
        // 应用 custom_data
        if (components.has("custom_data")) {
            JsonObject customData = components.getAsJsonObject("custom_data");
            if (customData.has("item")) {
                setCustomItemId(meta, customData.get("item").getAsString());
            }
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private String getColorCode(String colorName) {
        return switch (colorName.toLowerCase()) {
            case "black" -> "0";
            case "dark_blue" -> "1";
            case "dark_green" -> "2";
            case "dark_aqua" -> "3";
            case "dark_red" -> "4";
            case "dark_purple" -> "5";
            case "gold" -> "6";
            case "gray" -> "7";
            case "dark_gray" -> "8";
            case "blue" -> "9";
            case "green" -> "a";
            case "aqua" -> "b";
            case "red" -> "c";
            case "light_purple" -> "d";
            case "yellow" -> "e";
            case "white" -> "f";
            default -> "7";
        };
    }
    
    private ItemStack createItem(LootEntry entry) {
        Material material = Material.matchMaterial(entry.getItemName());
        if (material == null) {
            plugin.getLogger().warning("未知物品: " + entry.getItemName());
            return null;
        }
        
        int amount = entry.getRandomCount(random);
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // 设置lore
            if (!entry.getLore().isEmpty()) {
                meta.setLore(entry.getLore());
            }
            
            // 设置自定义数据
            if (entry.getCustomData() != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                NamespacedKey key = new NamespacedKey(plugin, "custom_data");
                container.set(key, PersistentDataType.STRING, entry.getCustomData().toString());
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private ItemStack createPotion(LootEntry entry) {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        
        if (meta != null && entry.getPotionEffect() != null) {
            PotionEffectType effectType = PotionEffectType.getByName(entry.getPotionEffect());
            if (effectType != null) {
                meta.addCustomEffect(new PotionEffect(effectType, 
                    entry.getPotionDuration() * 20, 
                    entry.getPotionAmplifier()), true);
                potion.setItemMeta(meta);
            }
        }
        
        return potion;
    }
    
    private ItemStack createEnchantedBook() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        
        if (meta != null) {
            // 随机添加1-3个附魔
            int enchantCount = 1 + random.nextInt(3);
            Enchantment[] enchantments = Enchantment.values();
            
            for (int i = 0; i < enchantCount; i++) {
                Enchantment enchantment = enchantments[random.nextInt(enchantments.length)];
                int level = 1 + random.nextInt(enchantment.getMaxLevel());
                meta.addStoredEnchant(enchantment, level, true);
            }
            
            // 添加说明lore
            meta.setLore(Arrays.asList(
                "§7将附魔书放在§6主手§7，需要附魔的物品放在§6副手",
                "§6右键§7进行附魔"
            ));
            
            book.setItemMeta(meta);
        }
        
        return book;
    }
    
    private ItemStack createSpecialItem(String name) {
        // 首先尝试从 SpecialItemManager 创建（包含正确的附魔和属性）
        SpecialItemManager.SpecialItemType type = specialItemManager.getSpecialItemTypeById(name);
        if (type != null) {
            return specialItemManager.createSpecialItem(type);
        }
        
        // 回退到旧的创建器（用于兼容性）
        SpecialItemCreator creator = specialItems.get(name.toLowerCase());
        if (creator != null) {
            return creator.create();
        }
        return null;
    }
    
    /**
     * 通过ID创建特殊物品（公共方法，供外部调用）
     * @param id 特殊物品ID（如 "knockback_stick"）
     * @return 特殊物品ItemStack，如果ID无效则返回null
     */
    public ItemStack createSpecialItemById(String id) {
        if (id == null) return null;
        
        // 首先尝试从 SpecialItemManager 创建
        SpecialItemManager.SpecialItemType type = specialItemManager.getSpecialItemTypeById(id);
        if (type != null) {
            return specialItemManager.createSpecialItem(type);
        }
        
        // 回退到旧的创建器
        SpecialItemCreator creator = specialItems.get(id.toLowerCase());
        if (creator != null) {
            return creator.create();
        }
        return null;
    }
    
    // 特殊物品创建方法
    private ItemStack createBruce() {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lBruce!");
            meta.setLore(Arrays.asList("§7布鲁斯之力！", "§e右键使用"));
            setCustomItemId(meta, "bruce");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createBlueScreen() {
        ItemStack item = new ItemStack(Material.BLUE_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§9§l蓝屏");
            meta.setLore(Arrays.asList("§7蓝屏警告！", "§e右键使用"));
            setCustomItemId(meta, "blue_screen");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createFlyMace() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l流星锤");
            meta.setLore(Arrays.asList("§7可以投掷的重锤！", "§e右键使用"));
            setCustomItemId(meta, "fly_mace");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createInvisibleScarf() {
        ItemStack item = new ItemStack(Material.WHITE_WOOL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f§l隐形围巾");
            meta.setLore(Arrays.asList("§7让你隐形！", "§e右键使用"));
            setCustomItemId(meta, "invisible_scarf");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createMeowAxe() {
        ItemStack item = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§l喵喵斧");
            meta.setLore(Arrays.asList("§7喵喵喵！", "§e右键使用"));
            setCustomItemId(meta, "meow_axe");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createPixie() {
        ItemStack item = new ItemStack(Material.FEATHER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§l小精灵");
            meta.setLore(Arrays.asList("§7带你飞行！", "§e右键使用"));
            setCustomItemId(meta, "pixie");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createRocketBoots() {
        // 使用 SpecialItemManager 创建以保持一致性
        return specialItemManager.createSpecialItem(SpecialItemManager.SpecialItemType.ROCKET_BOOTS);
    }

    private ItemStack createRunningShoes() {
        // 使用 SpecialItemManager 创建以保持一致性
        return specialItemManager.createSpecialItem(SpecialItemManager.SpecialItemType.RUNNING_SHOES);
    }
    
    private ItemStack createWitchApple() {
        ItemStack item = new ItemStack(Material.APPLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5§l女巫苹果");
            meta.setLore(Arrays.asList("§7女巫的特制苹果！", "§e右键食用"));
            setCustomItemId(meta, "witch_apple");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createYanpai() {
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l验牌");
            meta.setLore(Arrays.asList("§7火焰之力！", "§e右键使用"));
            setCustomItemId(meta, "yanpai");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createClock() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l时钟");
            meta.setLore(Arrays.asList("§7控制时间！", "§e右键使用"));
            setCustomItemId(meta, "clock");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createHongbao() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l红包");
            meta.setLore(Arrays.asList("§7恭喜发财！", "§e右键打开"));
            setCustomItemId(meta, "hongbao");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createHypnosisApp() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§l催眠APP");
            meta.setLore(Arrays.asList("§7催眠其他玩家！", "§e右键使用"));
            setCustomItemId(meta, "hypnosis_app");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBonesWithoutChickenFeet() {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f§l无鸡脚");
            meta.setLore(Arrays.asList("§7没有鸡脚的骨头！", "§e右键使用"));
            setCustomItemId(meta, "bones_without_chicken_feet");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createKnockbackStick() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§l击退棒");
            meta.setLore(Arrays.asList("§7强力的击退！", "§e用于攻击"));
            meta.addEnchant(Enchantment.KNOCKBACK, 5, true);
            setCustomItemId(meta, "knockback_stick");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createGodlyPickaxe() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§l神镐");
            meta.setLore(Arrays.asList("§7神级镐子！", "§e用于挖掘"));
            meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.addEnchant(Enchantment.FORTUNE, 5, true);
            setCustomItemId(meta, "godly_pickaxe");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createSpawner() {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l刷怪笼");
            meta.setLore(Arrays.asList("§7可以生成怪物！", "§e右键放置"));
            setCustomItemId(meta, "spawner");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createLuckyBlock() {
        return plugin.getLuckyBlockSystem().createLuckyBlock();
    }

    // 新增的特殊物品创建方法
    private ItemStack createSpecialBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l神弓");
            meta.setLore(Arrays.asList("§7力量5、耐久3、冲击2、破甲5", "§e右键使用"));
            setCustomItemId(meta, "special_bow");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSpecialCrossbow() {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l神弩");
            meta.setLore(Arrays.asList("§7快速装填3、耐久3、多重射击3、破甲5", "§e右键使用"));
            setCustomItemId(meta, "special_crossbow");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createLifeStealSword() {
        ItemStack item = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l生命偷取剑");
            meta.setLore(Arrays.asList("§7大血条转移术", "§7造成伤害的50%转化为生命值", "§e右键使用"));
            setCustomItemId(meta, "life_steal_sword");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPoisonDagger() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§l剧毒匕首");
            meta.setLore(Arrays.asList("§7是的，这剑有毒", "§7攻击附加3秒中毒效果", "§e右键使用"));
            setCustomItemId(meta, "poison_dagger");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShieldGenerator() {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§l护盾发生器");
            meta.setLore(Arrays.asList("§7安如磐石", "§7冷却时间：1分钟", "§7右键生成5秒可吸收8点生命值的护盾", "§e右键使用"));
            setCustomItemId(meta, "shield_generator");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createExCurryStick() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l『EX咖喱棒』");
            meta.setLore(Arrays.asList("§7右键召唤大光柱", "§7光柱倒下造成伤害并击退", "§7冷却时间：90s", "§7伤害：6-13", "§e右键使用"));
            setCustomItemId(meta, "ex_curry_stick");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTheWorld() {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l砸瓦鲁多");
            meta.setLore(Arrays.asList("§7The World!!!", "§7冷却时间：1分30秒", "§7右键冻结周围玩家9秒", "§e右键使用"));
            setCustomItemId(meta, "the_world");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createRussianRoulette() {
        ItemStack item = new ItemStack(Material.IRON_HORSE_ARMOR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§4§l俄罗斯轮盘枪");
            meta.setLore(Arrays.asList("§7试试你的运气", "§7右键打开选择界面", "§7选择子弹数量后开枪", "§71-6颗子弹，奖励递增", "§76颗子弹可获得特殊成就", "§e右键使用"));
            setCustomItemId(meta, "russian_roulette");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSpear() {
        ItemStack item = new ItemStack(Material.GOLDEN_SPEAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l长♂矛");
            meta.setLore(Arrays.asList("§7一把锋利的长矛", "§e右键使用"));
            setCustomItemId(meta, "spear");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBigFlameRod() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l大火杆");
            meta.setLore(Arrays.asList("§7燃烧吧！", "§e右键使用"));
            setCustomItemId(meta, "big_flame_rod");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGravityBoots() {
        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5§l重力靴");
            meta.setLore(Arrays.asList("§7控制重力！", "§e右键使用"));
            setCustomItemId(meta, "gravity_boots");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInvisibleSand() {
        // 使用 SpecialItemManager 创建以保持一致性
        return specialItemManager.createSpecialItem(SpecialItemManager.SpecialItemType.INVISIBLE_SAND);
    }

    private ItemStack createFeather() {
        // 使用 SpecialItemManager 创建以保持一致性
        return specialItemManager.createSpecialItem(SpecialItemManager.SpecialItemType.FEATHER);
    }

    private ItemStack createSpecialIronSword() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7§l铁剑");
            meta.setLore(Arrays.asList("§7特殊的铁剑", "§e右键使用"));
            setCustomItemId(meta, "iron_sword");
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setCustomItemId(ItemMeta meta, String id) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "item_id");
        container.set(key, PersistentDataType.STRING, id);
    }
    
    private void createDefaultLootTables(File dir) {
        // 从插件资源复制战利品表文件
        copyLootTableFromResource(dir, "main.json");
        copyLootTableFromResource(dir, "all_item.json");
        copyLootTableFromResource(dir, "potion.json");
        copyLootTableFromResource(dir, "sea.json");
        copyLootTableFromResource(dir, "common.json");
        copyLootTableFromResource(dir, "rare.json");
        copyLootTableFromResource(dir, "epic.json");
        copyLootTableFromResource(dir, "legendary.json");
        copyLootTableFromResource(dir, "enchanted_book.json");
        copyLootTableFromResource(dir, "moon.json");
        
        // 复制子目录
        File specialDir = new File(dir, "special");
        specialDir.mkdirs();
        copyLootTableFromResource(specialDir, "special/all.json");
        copyLootTableFromResource(specialDir, "special/item.json");
        
        File fishingDir = new File(dir, "fishing");
        fishingDir.mkdirs();
        copyLootTableFromResource(fishingDir, "fishing/fish.json");
        copyLootTableFromResource(fishingDir, "fishing/junk.json");
        copyLootTableFromResource(fishingDir, "fishing/treasure.json");
        
        File luckyBlockDir = new File(dir, "lucky_block");
        luckyBlockDir.mkdirs();
        copyLootTableFromResource(luckyBlockDir, "lucky_block/main.json");
    }
    
    private void copyLootTableFromResource(File dir, String resourcePath) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        File targetFile = new File(dir, fileName);
        
        // 如果文件已存在，不覆盖（保留用户自定义）
        if (targetFile.exists()) {
            return;
        }
        
        try (InputStream is = plugin.getResource("loot_tables/" + resourcePath)) {
            if (is != null) {
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                }
                plugin.getLogger().info("已复制战利品表: " + resourcePath);
            } else {
                plugin.getLogger().warning("无法从资源加载战利品表: " + resourcePath);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("复制战利品表失败: " + resourcePath + " - " + e.getMessage());
        }
    }
    
    private void createMainLootTable(File dir) {
        JsonObject mainTable = new JsonObject();
        JsonArray pools = new JsonArray();
        
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        
        JsonArray entries = new JsonArray();
        
        // 普通物品 85%
        JsonObject allItemEntry = new JsonObject();
        allItemEntry.addProperty("type", "loot_table");
        allItemEntry.addProperty("value", "all_item");
        allItemEntry.addProperty("weight", 85);
        entries.add(allItemEntry);
        
        // 药水 5%
        JsonObject potionEntry = new JsonObject();
        potionEntry.addProperty("type", "loot_table");
        potionEntry.addProperty("value", "potion");
        potionEntry.addProperty("weight", 5);
        entries.add(potionEntry);
        
        // 特殊物品 7%
        JsonObject specialEntry = new JsonObject();
        specialEntry.addProperty("type", "loot_table");
        specialEntry.addProperty("value", "special/all");
        specialEntry.addProperty("weight", 7);
        entries.add(specialEntry);
        
        // 附魔书 3%
        JsonObject bookEntry = new JsonObject();
        bookEntry.addProperty("type", "enchanted_book");
        bookEntry.addProperty("weight", 3);
        entries.add(bookEntry);
        
        pool.add("entries", entries);
        pools.add(pool);
        mainTable.add("pools", pools);
        
        saveLootTable(dir, "main", mainTable);
    }
    
    private void createAllItemsLootTable(File dir) {
        JsonObject table = new JsonObject();
        JsonArray pools = new JsonArray();
        
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        
        JsonArray entries = new JsonArray();
        
        // 添加各种物品
        addItemEntry(entries, "diamond", 5);
        addItemEntry(entries, "iron_ingot", 15);
        addItemEntry(entries, "gold_ingot", 12);
        addItemEntry(entries, "emerald", 8);
        addItemEntry(entries, "golden_apple", 5);
        addItemEntry(entries, "ender_pearl", 8);
        addItemEntry(entries, "arrow", 20);
        addItemEntry(entries, "string", 18);
        addItemEntry(entries, "gunpowder", 12);
        
        pool.add("entries", entries);
        pools.add(pool);
        table.add("pools", pools);
        
        saveLootTable(dir, "all_item", table);
    }
    
    private void createPotionLootTable(File dir) {
        JsonObject table = new JsonObject();
        JsonArray pools = new JsonArray();
        
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        
        JsonArray entries = new JsonArray();
        
        // 各种药水效果
        addPotionEntry(entries, "SPEED", 600, 1, 10);
        addPotionEntry(entries, "STRENGTH", 600, 1, 8);
        addPotionEntry(entries, "REGENERATION", 200, 1, 5);
        addPotionEntry(entries, "FIRE_RESISTANCE", 1200, 0, 8);
        addPotionEntry(entries, "NIGHT_VISION", 1200, 0, 10);
        addPotionEntry(entries, "INVISIBILITY", 600, 0, 5);
        addPotionEntry(entries, "JUMP_BOOST", 600, 2, 8);
        addPotionEntry(entries, "WATER_BREATHING", 1200, 0, 8);
        
        pool.add("entries", entries);
        pools.add(pool);
        table.add("pools", pools);
        
        // 保存到主目录，不是special子目录
        saveLootTable(dir, "potion", table);
    }
    
    private void createSpecialLootTable(File dir) {
        JsonObject table = new JsonObject();
        JsonArray pools = new JsonArray();
        
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        
        JsonArray entries = new JsonArray();
        
        // 特殊物品
        addSpecialEntry(entries, "bruce", 5);
        addSpecialEntry(entries, "blue_screen", 5);
        addSpecialEntry(entries, "fly_mace", 3);
        addSpecialEntry(entries, "invisible_scarf", 5);
        addSpecialEntry(entries, "meow_axe", 5);
        addSpecialEntry(entries, "pixie", 3);
        addSpecialEntry(entries, "rocket_boots", 5);
        addSpecialEntry(entries, "running_shoes", 5);
        addSpecialEntry(entries, "witch_apple", 5);
        addSpecialEntry(entries, "yanpai", 5);
        addSpecialEntry(entries, "clock", 5);
        addSpecialEntry(entries, "hongbao", 8);
        addSpecialEntry(entries, "hypnosis_app", 3);
        addSpecialEntry(entries, "bones_without_chicken_feet", 5);
        addSpecialEntry(entries, "knockback_stick", 5);
        addSpecialEntry(entries, "godly_pickaxe", 2);
        addSpecialEntry(entries, "spawner", 1);
        addSpecialEntry(entries, "lucky_block", 10);
        
        pool.add("entries", entries);
        pools.add(pool);
        table.add("pools", pools);
        
        File specialDir = new File(dir, "special");
        specialDir.mkdirs();
        saveLootTable(specialDir, "all", table);
    }

    private void createSeaLootTable(File dir) {
        JsonObject table = new JsonObject();
        JsonArray pools = new JsonArray();

        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);

        JsonArray entries = new JsonArray();

        // 80% 几率从主战利品表获取
        JsonObject mainEntry = new JsonObject();
        mainEntry.addProperty("type", "loot_table");
        mainEntry.addProperty("value", "newpillar:main");
        mainEntry.addProperty("weight", 80);
        entries.add(mainEntry);

        // 20% 几率从钓鱼专属表获取
        JsonObject fishingGroup = new JsonObject();
        fishingGroup.addProperty("type", "group");
        fishingGroup.addProperty("weight", 20);

        JsonArray fishingEntries = new JsonArray();

        // 鱼类 8
        JsonObject fishEntry = new JsonObject();
        fishEntry.addProperty("type", "loot_table");
        fishEntry.addProperty("value", "newpillar:fishing/fish");
        fishEntry.addProperty("weight", 8);
        fishingEntries.add(fishEntry);

        // 垃圾 12
        JsonObject junkEntry = new JsonObject();
        junkEntry.addProperty("type", "loot_table");
        junkEntry.addProperty("value", "newpillar:fishing/junk");
        junkEntry.addProperty("weight", 12);
        fishingEntries.add(junkEntry);

        // 宝藏 5
        JsonObject treasureEntry = new JsonObject();
        treasureEntry.addProperty("type", "loot_table");
        treasureEntry.addProperty("value", "newpillar:fishing/treasure");
        treasureEntry.addProperty("weight", 5);
        fishingEntries.add(treasureEntry);

        fishingGroup.add("entries", fishingEntries);
        entries.add(fishingGroup);

        pool.add("entries", entries);
        pools.add(pool);
        table.add("pools", pools);

        saveLootTable(dir, "sea", table);

        // 创建钓鱼子表
        createFishingSubTables(dir);
    }

    private void createFishingSubTables(File dir) {
        // 创建钓鱼目录
        File fishingDir = new File(dir, "fishing");
        fishingDir.mkdirs();

        // 鱼类表 - 使用完整的 minecraft: 命名空间
        JsonObject fishTable = new JsonObject();
        JsonArray fishPools = new JsonArray();
        JsonObject fishPool = new JsonObject();
        fishPool.addProperty("rolls", 1);
        JsonArray fishEntries = new JsonArray();
        addItemEntry(fishEntries, "minecraft:cod", 60);
        addItemEntry(fishEntries, "minecraft:salmon", 25);
        addItemEntry(fishEntries, "minecraft:tropical_fish", 10);
        addItemEntry(fishEntries, "minecraft:pufferfish", 5);
        fishPool.add("entries", fishEntries);
        fishPools.add(fishPool);
        fishTable.add("pools", fishPools);
        saveLootTable(fishingDir, "fish", fishTable);

        // 垃圾表 - 使用完整的 minecraft: 命名空间
        JsonObject junkTable = new JsonObject();
        JsonArray junkPools = new JsonArray();
        JsonObject junkPool = new JsonObject();
        junkPool.addProperty("rolls", 1);
        JsonArray junkEntries = new JsonArray();
        addItemEntry(junkEntries, "minecraft:lily_pad", 17);
        addItemEntry(junkEntries, "minecraft:bowl", 10);
        addItemEntry(junkEntries, "minecraft:fishing_rod", 2);
        addItemEntry(junkEntries, "minecraft:leather", 10);
        addItemEntry(junkEntries, "minecraft:leather_boots", 10);
        addItemEntry(junkEntries, "minecraft:rotten_flesh", 10);
        addItemEntry(junkEntries, "minecraft:stick", 5);
        addItemEntry(junkEntries, "minecraft:string", 5);
        addItemEntry(junkEntries, "minecraft:potion", 10);
        addItemEntry(junkEntries, "minecraft:bone", 10);
        addItemEntry(junkEntries, "minecraft:tripwire_hook", 10);
        addItemEntry(junkEntries, "minecraft:ink_sac", 1);
        junkPool.add("entries", junkEntries);
        junkPools.add(junkPool);
        junkTable.add("pools", junkPools);
        saveLootTable(fishingDir, "junk", junkTable);

        // 宝藏表 - 使用完整的 minecraft: 命名空间
        JsonObject treasureTable = new JsonObject();
        JsonArray treasurePools = new JsonArray();
        JsonObject treasurePool = new JsonObject();
        treasurePool.addProperty("rolls", 1);
        JsonArray treasureEntries = new JsonArray();
        addItemEntry(treasureEntries, "minecraft:bow", 17);
        addItemEntry(treasureEntries, "minecraft:enchanted_book", 10);
        addItemEntry(treasureEntries, "minecraft:fishing_rod", 10);
        addItemEntry(treasureEntries, "minecraft:name_tag", 10);
        addItemEntry(treasureEntries, "minecraft:saddle", 10);
        addItemEntry(treasureEntries, "minecraft:nautilus_shell", 10);
        addItemEntry(treasureEntries, "minecraft:heart_of_the_sea", 3);
        treasurePool.add("entries", treasureEntries);
        treasurePools.add(treasurePool);
        treasureTable.add("pools", treasurePools);
        saveLootTable(fishingDir, "treasure", treasureTable);
    }

    private void addItemEntry(JsonArray entries, String name, int weight) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "item");
        entry.addProperty("name", name);
        entry.addProperty("weight", weight);
        
        JsonArray functions = new JsonArray();
        JsonObject countFunc = new JsonObject();
        countFunc.addProperty("function", "set_count");
        JsonObject count = new JsonObject();
        count.addProperty("min", 1);
        count.addProperty("max", 3);
        countFunc.add("count", count);
        functions.add(countFunc);
        
        entry.add("functions", functions);
        entries.add(entry);
    }
    
    private void addPotionEntry(JsonArray entries, String effect, int duration, int amplifier, int weight) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "potion");
        entry.addProperty("effect", effect);
        entry.addProperty("duration", duration);
        entry.addProperty("amplifier", amplifier);
        entry.addProperty("weight", weight);
        entries.add(entry);
    }
    
    private void addSpecialEntry(JsonArray entries, String name, int weight) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "special");
        entry.addProperty("name", name);
        entry.addProperty("weight", weight);
        entries.add(entry);
    }
    
    private void saveLootTable(File dir, String name, JsonObject table) {
        File file = new File(dir, name + ".json");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(table, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("保存战利品表失败: " + name);
        }
    }
    
    // 内部类
    private static class LootTable {
        private final List<LootPool> pools = new ArrayList<>();
        
        void addPool(LootPool pool) {
            pools.add(pool);
        }
        
        List<LootPool> getPools() {
            return pools;
        }
    }
    
    private static class LootPool {
        private int rolls = 1;
        private final List<LootEntry> entries = new ArrayList<>();
        
        void setRolls(int rolls) {
            this.rolls = rolls;
        }
        
        int getRolls() {
            return rolls;
        }
        
        void addEntry(LootEntry entry) {
            entries.add(entry);
        }
        
        LootEntry getRandomEntry(Random random) {
            if (entries.isEmpty()) return null;
            
            int totalWeight = entries.stream().mapToInt(LootEntry::getWeight).sum();
            int roll = random.nextInt(totalWeight);
            
            int currentWeight = 0;
            for (LootEntry entry : entries) {
                currentWeight += entry.getWeight();
                if (roll < currentWeight) {
                    return entry;
                }
            }
            
            return entries.get(entries.size() - 1);
        }
    }
    
    private static class LootEntry {
        private final String type;
        private final int weight;
        private String itemName;
        private String lootTableName;
        private String specialItemName;
        private boolean potionType;
        private String potionEffect;
        private int potionDuration = 600;
        private int potionAmplifier = 0;
        private boolean enchantedBook;
        private int minCount = 1;
        private int maxCount = 1;
        private List<String> lore = new ArrayList<>();
        private JsonObject customData;
        
        // 新格式支持
        private String tagName;
        private boolean expand;
        private boolean isGroup;
        private List<LootEntry> childEntries = new ArrayList<>();
        private List<LootFunction> functions = new ArrayList<>();
        private boolean enchantRandomly;
        private JsonObject components;
        
        LootEntry(String type, int weight) {
            this.type = type;
            this.weight = weight;
        }
        
        String getType() { return type; }
        int getWeight() { return weight; }
        
        void setItemName(String name) { this.itemName = name; }
        String getItemName() { return itemName; }
        
        void setLootTableName(String name) { this.lootTableName = name; }
        String getLootTableName() { return lootTableName; }
        
        void setSpecialItemName(String name) { this.specialItemName = name; }
        String getSpecialItemName() { return specialItemName; }
        
        void setPotionType(boolean potion) { this.potionType = potion; }
        boolean isPotionType() { return potionType; }
        void setPotionEffect(String effect) { this.potionEffect = effect; }
        String getPotionEffect() { return potionEffect; }
        void setPotionDuration(int duration) { this.potionDuration = duration; }
        int getPotionDuration() { return potionDuration; }
        void setPotionAmplifier(int amplifier) { this.potionAmplifier = amplifier; }
        int getPotionAmplifier() { return potionAmplifier; }
        
        void setEnchantedBook(boolean book) { this.enchantedBook = book; }
        boolean isEnchantedBook() { return enchantedBook; }
        
        void setCountRange(int min, int max) {
            this.minCount = min;
            this.maxCount = max;
        }
        
        int getRandomCount(Random random) {
            return minCount == maxCount ? minCount : minCount + random.nextInt(maxCount - minCount + 1);
        }
        
        void setLore(List<String> lore) { this.lore = lore; }
        List<String> getLore() { return lore; }
        
        void setCustomData(JsonObject data) { this.customData = data; }
        JsonObject getCustomData() { return customData; }
        
        // 新格式 getter/setter
        void setTagName(String name) { this.tagName = name; }
        String getTagName() { return tagName; }
        void setExpand(boolean expand) { this.expand = expand; }
        boolean isExpand() { return expand; }
        void setGroup(boolean group) { this.isGroup = group; }
        boolean isGroup() { return isGroup; }
        void setChildEntries(List<LootEntry> entries) { this.childEntries = entries; }
        List<LootEntry> getChildEntries() { return childEntries; }
        void setFunctions(List<LootFunction> funcs) { this.functions = funcs; }
        List<LootFunction> getFunctions() { return functions; }
        void setEnchantRandomly(boolean enchant) { this.enchantRandomly = enchant; }
        boolean isEnchantRandomly() { return enchantRandomly; }
        void setComponents(JsonObject comps) { this.components = comps; }
        JsonObject getComponents() { return components; }
    }
    
    // 战利品函数接口
    private static class LootFunction {
        private final String function;
        private JsonObject params;
        
        LootFunction(String function) {
            this.function = function;
        }
        
        String getFunction() { return function; }
        void setParams(JsonObject params) { this.params = params; }
        JsonObject getParams() { return params; }
    }
    
    @FunctionalInterface
    private interface SpecialItemCreator {
        ItemStack create();
    }
}
