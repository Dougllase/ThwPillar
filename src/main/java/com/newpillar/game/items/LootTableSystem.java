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
    private final Map<String, LootTable> lootTables = new HashMap<>();
    private final Random random = new Random();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // 特殊物品ID映射
    private final Map<String, SpecialItemCreator> specialItems = new HashMap<>();
    
    public LootTableSystem(NewPillar plugin) {
        this.plugin = plugin;
        initSpecialItems();
        loadLootTables();
    }
    
    private void initSpecialItems() {
        // 注册特殊物品创建器
        specialItems.put("bruce", this::createBruce);
        specialItems.put("blue_screen", this::createBlueScreen);
        specialItems.put("fly_mace", this::createFlyMace);
        specialItems.put("invisible_scarf", this::createInvisibleScarf);
        specialItems.put("meow_axe", this::createMeowAxe);
        specialItems.put("pixie", this::createPixie);
        specialItems.put("rocket_boots", this::createRocketBoots);
        specialItems.put("running_shoes", this::createRunningShoes);
        specialItems.put("witch_apple", this::createWitchApple);
        specialItems.put("yanpai", this::createYanpai);
        specialItems.put("clock", this::createClock);
        specialItems.put("hongbao", this::createHongbao);
        specialItems.put("hypnosis_app", this::createHypnosisApp);
        specialItems.put("bones_without_chicken_feet", this::createBonesWithoutChickenFeet);
        specialItems.put("knockback_stick", this::createKnockbackStick);
        specialItems.put("godly_pickaxe", this::createGodlyPickaxe);
        specialItems.put("spawner", this::createSpawner);
        specialItems.put("lucky_block", this::createLuckyBlock);
    }
    
    private void loadLootTables() {
        File lootTableDir = new File(plugin.getDataFolder(), "loot_tables");
        if (!lootTableDir.exists()) {
            lootTableDir.mkdirs();
            createDefaultLootTables(lootTableDir);
        }
        
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
            case "special" -> {
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
                    ItemStack item = createItemFromEntry(entry);
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
     * 生成战利品（使用主表 main）
     * 供调试指令使用
     */
    public ItemStack generateLoot() {
        return getRandomLoot("main");
    }
    
    private ItemStack createItemFromEntry(LootEntry entry) {
        return switch (entry.getType()) {
            case "item" -> createItemWithFunctions(entry);
            case "loot_table" -> getRandomLoot(entry.getLootTableName());
            case "special" -> createSpecialItem(entry.getSpecialItemName());
            case "potion" -> createPotion(entry);
            case "enchanted_book" -> createEnchantedBook();
            case "tag" -> createTagItem(entry);
            case "group" -> createGroupItem(entry);
            default -> null;
        };
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
            // 使用原版药水类型
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
            potion.setItemMeta(meta);
        }
        
        return potion;
    }
    
    private ItemStack createTagItem(LootEntry entry) {
        // tag 类型 - 从配置中加载物品列表
        // 暂时使用 all_item 作为回退
        plugin.getLogger().info("Tag 类型战利品表: " + entry.getTagName());
        // 返回一个随机普通物品作为占位
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
        SpecialItemCreator creator = specialItems.get(name.toLowerCase());
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
        ItemStack item = new ItemStack(Material.LEATHER_BOOTS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§4§l火箭靴");
            meta.setLore(Arrays.asList("§7让你飞得更高！", "§e穿上后生效"));
            setCustomItemId(meta, "rocket_boots");
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createRunningShoes() {
        ItemStack item = new ItemStack(Material.GOLDEN_BOOTS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l跑鞋");
            meta.setLore(Arrays.asList("§7跑得更快！", "§e穿上后生效"));
            setCustomItemId(meta, "running_shoes");
            item.setItemMeta(meta);
        }
        return item;
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
    
    private void setCustomItemId(ItemMeta meta, String id) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "item_id");
        container.set(key, PersistentDataType.STRING, id);
    }
    
    private void createDefaultLootTables(File dir) {
        // 创建主战利品表
        createMainLootTable(dir);
        // 创建普通物品表
        createAllItemsLootTable(dir);
        // 创建药水表
        createPotionLootTable(dir);
        // 创建特殊物品表
        createSpecialLootTable(dir);
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
