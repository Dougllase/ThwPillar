package com.newpillar.game.items;

import com.newpillar.game.enums.MapType;

import com.newpillar.game.enums.GameStatus;

import com.newpillar.game.enums.PlayerState;

import com.newpillar.game.PlayerData;

import com.newpillar.game.GameManager;

import com.newpillar.NewPillar;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemSystem {
   private final NewPillar plugin;
   private final GameManager gameManager;
   private final Random random = new Random();
   private final Gson gson = new Gson();
   private ScheduledTask itemTask;
   private int lootTimer = 0;
   private final List<ItemSystem.LootEntry> lootTable = new ArrayList<>();

   public ItemSystem(NewPillar plugin, GameManager gameManager) {
      this.plugin = plugin;
      this.gameManager = gameManager;
      this.initLootTableFromDataPack();
   }

   private void initLootTable() {
      this.addLoot(Material.WOODEN_SWORD, 15, 1, 1);
      this.addLoot(Material.STONE_SWORD, 12, 1, 1);
      this.addLoot(Material.IRON_SWORD, 8, 1, 1);
      this.addLoot(Material.GOLDEN_SWORD, 6, 1, 1);
      this.addLoot(Material.DIAMOND_SWORD, 3, 1, 1);
      this.addLoot(Material.NETHERITE_SWORD, 1, 1, 1);
      this.addLoot(Material.WOODEN_AXE, 15, 1, 1);
      this.addLoot(Material.STONE_AXE, 12, 1, 1);
      this.addLoot(Material.IRON_AXE, 8, 1, 1);
      this.addLoot(Material.GOLDEN_AXE, 6, 1, 1);
      this.addLoot(Material.DIAMOND_AXE, 3, 1, 1);
      this.addLoot(Material.BOW, 10, 1, 1);
      this.addLoot(Material.CROSSBOW, 8, 1, 1);
      this.addLoot(Material.TRIDENT, 2, 1, 1);
      this.addLoot(Material.MACE, 1, 1, 1);
      this.addLoot(Material.WOODEN_PICKAXE, 12, 1, 1);
      this.addLoot(Material.STONE_PICKAXE, 10, 1, 1);
      this.addLoot(Material.IRON_PICKAXE, 6, 1, 1);
      this.addLoot(Material.GOLDEN_PICKAXE, 4, 1, 1);
      this.addLoot(Material.DIAMOND_PICKAXE, 2, 1, 1);
      this.addLoot(Material.WOODEN_SHOVEL, 12, 1, 1);
      this.addLoot(Material.STONE_SHOVEL, 10, 1, 1);
      this.addLoot(Material.IRON_SHOVEL, 6, 1, 1);
      this.addLoot(Material.SHEARS, 8, 1, 1);
      this.addLoot(Material.FLINT_AND_STEEL, 8, 1, 1);
      this.addLoot(Material.FISHING_ROD, 6, 1, 1);
      this.addLoot(Material.BRUSH, 4, 1, 1);
      this.addLoot(Material.LEATHER_HELMET, 10, 1, 1);
      this.addLoot(Material.LEATHER_CHESTPLATE, 10, 1, 1);
      this.addLoot(Material.LEATHER_LEGGINGS, 10, 1, 1);
      this.addLoot(Material.LEATHER_BOOTS, 10, 1, 1);
      this.addLoot(Material.CHAINMAIL_HELMET, 8, 1, 1);
      this.addLoot(Material.CHAINMAIL_CHESTPLATE, 8, 1, 1);
      this.addLoot(Material.CHAINMAIL_LEGGINGS, 8, 1, 1);
      this.addLoot(Material.CHAINMAIL_BOOTS, 8, 1, 1);
      this.addLoot(Material.IRON_HELMET, 6, 1, 1);
      this.addLoot(Material.IRON_CHESTPLATE, 6, 1, 1);
      this.addLoot(Material.IRON_LEGGINGS, 6, 1, 1);
      this.addLoot(Material.IRON_BOOTS, 6, 1, 1);
      this.addLoot(Material.GOLDEN_HELMET, 5, 1, 1);
      this.addLoot(Material.GOLDEN_CHESTPLATE, 5, 1, 1);
      this.addLoot(Material.GOLDEN_LEGGINGS, 5, 1, 1);
      this.addLoot(Material.GOLDEN_BOOTS, 5, 1, 1);
      this.addLoot(Material.DIAMOND_HELMET, 2, 1, 1);
      this.addLoot(Material.DIAMOND_CHESTPLATE, 2, 1, 1);
      this.addLoot(Material.DIAMOND_LEGGINGS, 2, 1, 1);
      this.addLoot(Material.DIAMOND_BOOTS, 2, 1, 1);
      this.addLoot(Material.TURTLE_HELMET, 3, 1, 1);
      this.addLoot(Material.ELYTRA, 1, 1, 1);
      this.addLoot(Material.APPLE, 20, 2, 4);
      this.addLoot(Material.GOLDEN_APPLE, 5, 1, 2);
      this.addLoot(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 1);
      this.addLoot(Material.BREAD, 18, 2, 4);
      this.addLoot(Material.COOKED_BEEF, 15, 2, 4);
      this.addLoot(Material.COOKED_PORKCHOP, 15, 2, 4);
      this.addLoot(Material.COOKED_CHICKEN, 15, 2, 4);
      this.addLoot(Material.COOKED_MUTTON, 15, 2, 4);
      this.addLoot(Material.COOKED_RABBIT, 12, 2, 4);
      this.addLoot(Material.COOKED_SALMON, 15, 2, 4);
      this.addLoot(Material.COOKED_COD, 15, 2, 4);
      this.addLoot(Material.MELON_SLICE, 16, 4, 8);
      this.addLoot(Material.CARROT, 18, 2, 4);
      this.addLoot(Material.GOLDEN_CARROT, 6, 1, 2);
      this.addLoot(Material.POTATO, 16, 2, 4);
      this.addLoot(Material.BAKED_POTATO, 14, 2, 4);
      this.addLoot(Material.POISONOUS_POTATO, 8, 1, 2);
      this.addLoot(Material.PUMPKIN_PIE, 12, 1, 2);
      this.addLoot(Material.COOKIE, 14, 2, 6);
      this.addLoot(Material.CAKE, 6, 1, 1);
      this.addLoot(Material.BEETROOT, 14, 2, 4);
      this.addLoot(Material.BEETROOT_SOUP, 8, 1, 1);
      this.addLoot(Material.RABBIT_STEW, 6, 1, 1);
      this.addLoot(Material.MUSHROOM_STEW, 8, 1, 1);
      this.addLoot(Material.DRIED_KELP, 14, 4, 8);
      this.addLoot(Material.SWEET_BERRIES, 16, 2, 6);
      this.addLoot(Material.GLOW_BERRIES, 12, 2, 4);
      this.addLoot(Material.ARROW, 18, 8, 16);
      this.addLoot(Material.FEATHER, 14, 4, 8);
      this.addLoot(Material.FLINT, 14, 4, 8);
      this.addLoot(Material.STRING, 14, 4, 8);
      this.addLoot(Material.GUNPOWDER, 12, 2, 6);
      this.addLoot(Material.LEATHER, 12, 2, 4);
      this.addLoot(Material.CLAY_BALL, 12, 4, 8);
      this.addLoot(Material.BRICK, 10, 4, 8);
      this.addLoot(Material.NETHER_BRICK, 10, 4, 8);
      this.addLoot(Material.PAPER, 12, 2, 6);
      this.addLoot(Material.BOOK, 10, 1, 3);
      this.addLoot(Material.SLIME_BALL, 10, 2, 4);
      this.addLoot(Material.BLAZE_ROD, 8, 1, 3);
      this.addLoot(Material.NETHER_STAR, 1, 1, 1);
      this.addLoot(Material.ENDER_PEARL, 8, 1, 2);
      this.addLoot(Material.ENDER_EYE, 6, 1, 2);
      this.addLoot(Material.SHULKER_SHELL, 4, 1, 2);
      this.addLoot(Material.POPPED_CHORUS_FRUIT, 8, 2, 6);
      this.addLoot(Material.DRAGON_BREATH, 4, 1, 2);
      this.addLoot(Material.PHANTOM_MEMBRANE, 6, 1, 3);
      this.addLoot(Material.NAUTILUS_SHELL, 5, 1, 2);
      this.addLoot(Material.HEART_OF_THE_SEA, 2, 1, 1);
      this.addLoot(Material.HONEYCOMB, 8, 2, 4);
      this.addLoot(Material.HONEY_BOTTLE, 8, 1, 2);
      this.addLoot(Material.OAK_PLANKS, 20, 16, 32);
      this.addLoot(Material.SPRUCE_PLANKS, 18, 16, 32);
      this.addLoot(Material.BIRCH_PLANKS, 18, 16, 32);
      this.addLoot(Material.JUNGLE_PLANKS, 16, 16, 32);
      this.addLoot(Material.ACACIA_PLANKS, 16, 16, 32);
      this.addLoot(Material.DARK_OAK_PLANKS, 16, 16, 32);
      this.addLoot(Material.MANGROVE_PLANKS, 14, 16, 32);
      this.addLoot(Material.CHERRY_PLANKS, 14, 16, 32);
      this.addLoot(Material.BAMBOO_PLANKS, 14, 16, 32);
      this.addLoot(Material.COBBLESTONE, 20, 16, 32);
      this.addLoot(Material.DIRT, 18, 16, 32);
      this.addLoot(Material.SAND, 16, 16, 32);
      this.addLoot(Material.GRAVEL, 16, 8, 16);
      this.addLoot(Material.OAK_LOG, 18, 8, 16);
      this.addLoot(Material.BIRCH_LOG, 18, 8, 16);
      this.addLoot(Material.SPRUCE_LOG, 16, 8, 16);
      this.addLoot(Material.JUNGLE_LOG, 14, 8, 16);
      this.addLoot(Material.ACACIA_LOG, 14, 8, 16);
      this.addLoot(Material.DARK_OAK_LOG, 14, 8, 16);
      this.addLoot(Material.STONE, 18, 16, 32);
      this.addLoot(Material.GRANITE, 14, 8, 16);
      this.addLoot(Material.DIORITE, 14, 8, 16);
      this.addLoot(Material.ANDESITE, 14, 8, 16);
      this.addLoot(Material.DEEPSLATE, 16, 16, 32);
      this.addLoot(Material.COBBLED_DEEPSLATE, 16, 16, 32);
      this.addLoot(Material.TUFF, 12, 8, 16);
      this.addLoot(Material.CALCITE, 10, 8, 16);
      this.addLoot(Material.FIRE_CHARGE, 10, 2, 4);
      this.addLoot(Material.TNT, 8, 2, 4);
      this.addLoot(Material.SHIELD, 10, 1, 1);
      this.addLoot(Material.TOTEM_OF_UNDYING, 2, 1, 1);
      this.addLoot(Material.EXPERIENCE_BOTTLE, 10, 4, 8);
      this.addLoot(Material.SADDLE, 8, 1, 1);
      this.addLoot(Material.NAME_TAG, 10, 1, 2);
      this.addLoot(Material.CHEST, 14, 2, 4);
      this.addLoot(Material.CRAFTING_TABLE, 12, 1, 2);
      this.addLoot(Material.FURNACE, 10, 1, 2);
      this.addLoot(Material.BLAST_FURNACE, 8, 1, 1);
      this.addLoot(Material.SMOKER, 8, 1, 1);
      this.addLoot(Material.BARREL, 10, 1, 2);
      this.addLoot(Material.TORCH, 20, 16, 32);
      this.addLoot(Material.LANTERN, 12, 4, 8);
      this.addLoot(Material.SOUL_LANTERN, 10, 4, 8);
      this.addLoot(Material.SEA_LANTERN, 8, 4, 8);
      this.addLoot(Material.GLOWSTONE, 10, 4, 8);
      this.addLoot(Material.SHROOMLIGHT, 8, 4, 8);
      this.addLoot(Material.OCHRE_FROGLIGHT, 6, 4, 8);
      this.addLoot(Material.VERDANT_FROGLIGHT, 6, 4, 8);
      this.addLoot(Material.PEARLESCENT_FROGLIGHT, 6, 4, 8);
      this.addLoot(Material.CRYING_OBSIDIAN, 6, 2, 4);
      this.addLoot(Material.LADDER, 16, 8, 16);
      this.addLoot(Material.SCAFFOLDING, 12, 4, 8);
      this.addLoot(Material.BUCKET, 10, 1, 2);
      this.addLoot(Material.WATER_BUCKET, 8, 1, 1);
      this.addLoot(Material.LAVA_BUCKET, 6, 1, 1);
      this.addLoot(Material.MILK_BUCKET, 8, 1, 1);
      this.addLoot(Material.POWDER_SNOW_BUCKET, 6, 1, 1);
      this.addLoot(Material.COMPASS, 10, 1, 1);
      this.addLoot(Material.CLOCK, 8, 1, 1);
      this.addLoot(Material.SPYGLASS, 8, 1, 1);
      this.addLoot(Material.LEAD, 10, 1, 2);
      this.addLoot(Material.REDSTONE, 14, 8, 16);
      this.addLoot(Material.PISTON, 10, 2, 4);
      this.addLoot(Material.STICKY_PISTON, 8, 2, 4);
      this.addLoot(Material.REPEATER, 10, 2, 4);
      this.addLoot(Material.COMPARATOR, 10, 2, 4);
      this.addLoot(Material.REDSTONE_TORCH, 14, 4, 8);
      this.addLoot(Material.REDSTONE_LAMP, 10, 2, 4);
      this.addLoot(Material.DAYLIGHT_DETECTOR, 8, 1, 2);
      this.addLoot(Material.OBSERVER, 8, 2, 4);
      this.addLoot(Material.PAINTING, 10, 1, 2);
      this.addLoot(Material.ITEM_FRAME, 10, 1, 2);
      this.addLoot(Material.GLOW_ITEM_FRAME, 8, 1, 2);
      this.addLoot(Material.FLOWER_POT, 12, 1, 3);
      this.addLoot(Material.ARMOR_STAND, 10, 1, 2);
      this.addLoot(Material.COAL, 18, 4, 8);
      this.addLoot(Material.CHARCOAL, 16, 4, 8);
      this.addLoot(Material.IRON_INGOT, 12, 2, 6);
      this.addLoot(Material.GOLD_INGOT, 10, 2, 6);
      this.addLoot(Material.DIAMOND, 5, 1, 3);
      this.addLoot(Material.EMERALD, 8, 2, 6);
      this.addLoot(Material.LAPIS_LAZULI, 12, 4, 8);
      this.addLoot(Material.COPPER_INGOT, 14, 4, 8);
      this.addLoot(Material.AMETHYST_SHARD, 10, 2, 6);
      this.addLoot(Material.QUARTZ, 12, 4, 8);
      this.addLoot(Material.RAW_IRON, 14, 4, 8);
      this.addLoot(Material.RAW_GOLD, 12, 4, 8);
      this.addLoot(Material.RAW_COPPER, 14, 4, 8);
      this.addLoot(Material.NETHERITE_SCRAP, 3, 1, 2);
      this.addLoot(Material.NETHERITE_INGOT, 1, 1, 1);
      this.addLoot(Material.GHAST_TEAR, 6, 1, 2);
      this.addLoot(Material.NETHER_WART, 12, 2, 6);
      this.addLoot(Material.WARPED_FUNGUS, 10, 2, 4);
      this.addLoot(Material.CRIMSON_FUNGUS, 10, 2, 4);
      this.addLoot(Material.CHORUS_FRUIT, 10, 4, 8);
      this.addLoot(Material.END_CRYSTAL, 3, 1, 2);
      this.addLoot(Material.PRISMARINE_SHARD, 10, 4, 8);
      this.addLoot(Material.PRISMARINE_CRYSTALS, 8, 2, 6);
      this.addLoot(Material.SEA_PICKLE, 12, 2, 6);
      this.addLoot(Material.KELP, 14, 4, 8);
      this.addLoot(Material.DRIED_KELP, 14, 4, 8);
      this.addLoot(Material.TURTLE_EGG, 6, 1, 2);
      this.addLoot(Material.TURTLE_SCUTE, 6, 1, 2);
      this.addLoot(Material.WHEAT_SEEDS, 16, 4, 8);
      this.addLoot(Material.PUMPKIN_SEEDS, 14, 4, 8);
      this.addLoot(Material.MELON_SEEDS, 14, 4, 8);
      this.addLoot(Material.BEETROOT_SEEDS, 14, 4, 8);
      this.addLoot(Material.COCOA_BEANS, 12, 4, 8);
      this.addLoot(Material.SUGAR_CANE, 14, 4, 8);
      this.addLoot(Material.WHEAT, 14, 4, 8);
      this.addLoot(Material.HAY_BLOCK, 10, 2, 4);
      this.addLoot(Material.WHITE_DYE, 10, 2, 4);
      this.addLoot(Material.ORANGE_DYE, 10, 2, 4);
      this.addLoot(Material.MAGENTA_DYE, 10, 2, 4);
      this.addLoot(Material.LIGHT_BLUE_DYE, 10, 2, 4);
      this.addLoot(Material.YELLOW_DYE, 10, 2, 4);
      this.addLoot(Material.LIME_DYE, 10, 2, 4);
      this.addLoot(Material.PINK_DYE, 10, 2, 4);
      this.addLoot(Material.GRAY_DYE, 10, 2, 4);
      this.addLoot(Material.LIGHT_GRAY_DYE, 10, 2, 4);
      this.addLoot(Material.CYAN_DYE, 10, 2, 4);
      this.addLoot(Material.PURPLE_DYE, 10, 2, 4);
      this.addLoot(Material.BLUE_DYE, 10, 2, 4);
      this.addLoot(Material.BROWN_DYE, 10, 2, 4);
      this.addLoot(Material.GREEN_DYE, 10, 2, 4);
      this.addLoot(Material.RED_DYE, 10, 2, 4);
      this.addLoot(Material.BLACK_DYE, 10, 2, 4);
      this.addLoot(Material.BONE_MEAL, 14, 4, 8);
      this.addLoot(Material.INK_SAC, 12, 2, 4);
      this.addLoot(Material.GLOW_INK_SAC, 8, 1, 3);
      this.addLoot(Material.WHITE_WOOL, 14, 8, 16);
      this.addLoot(Material.ORANGE_WOOL, 12, 8, 16);
      this.addLoot(Material.MAGENTA_WOOL, 12, 8, 16);
      this.addLoot(Material.LIGHT_BLUE_WOOL, 12, 8, 16);
      this.addLoot(Material.YELLOW_WOOL, 12, 8, 16);
      this.addLoot(Material.LIME_WOOL, 12, 8, 16);
      this.addLoot(Material.PINK_WOOL, 12, 8, 16);
      this.addLoot(Material.GRAY_WOOL, 12, 8, 16);
      this.addLoot(Material.LIGHT_GRAY_WOOL, 12, 8, 16);
      this.addLoot(Material.CYAN_WOOL, 12, 8, 16);
      this.addLoot(Material.PURPLE_WOOL, 12, 8, 16);
      this.addLoot(Material.BLUE_WOOL, 12, 8, 16);
      this.addLoot(Material.BROWN_WOOL, 12, 8, 16);
      this.addLoot(Material.GREEN_WOOL, 12, 8, 16);
      this.addLoot(Material.RED_WOOL, 12, 8, 16);
      this.addLoot(Material.BLACK_WOOL, 12, 8, 16);
      this.addLoot(Material.WHITE_CONCRETE, 10, 8, 16);
      this.addLoot(Material.ORANGE_CONCRETE, 10, 8, 16);
      this.addLoot(Material.MAGENTA_CONCRETE, 10, 8, 16);
      this.addLoot(Material.LIGHT_BLUE_CONCRETE, 10, 8, 16);
      this.addLoot(Material.YELLOW_CONCRETE, 10, 8, 16);
      this.addLoot(Material.LIME_CONCRETE, 10, 8, 16);
      this.addLoot(Material.PINK_CONCRETE, 10, 8, 16);
      this.addLoot(Material.GRAY_CONCRETE, 10, 8, 16);
      this.addLoot(Material.LIGHT_GRAY_CONCRETE, 10, 8, 16);
      this.addLoot(Material.CYAN_CONCRETE, 10, 8, 16);
      this.addLoot(Material.PURPLE_CONCRETE, 10, 8, 16);
      this.addLoot(Material.BLUE_CONCRETE, 10, 8, 16);
      this.addLoot(Material.BROWN_CONCRETE, 10, 8, 16);
      this.addLoot(Material.GREEN_CONCRETE, 10, 8, 16);
      this.addLoot(Material.RED_CONCRETE, 10, 8, 16);
      this.addLoot(Material.BLACK_CONCRETE, 10, 8, 16);
      this.addLoot(Material.GLASS, 12, 8, 16);
      this.addLoot(Material.GLASS_PANE, 10, 8, 16);
      this.addLoot(Material.OAK_SAPLING, 12, 2, 4);
      this.addLoot(Material.BIRCH_SAPLING, 12, 2, 4);
      this.addLoot(Material.SPRUCE_SAPLING, 10, 2, 4);
      this.addLoot(Material.JUNGLE_SAPLING, 8, 2, 4);
      this.addLoot(Material.ACACIA_SAPLING, 8, 2, 4);
      this.addLoot(Material.DARK_OAK_SAPLING, 8, 2, 4);
      this.addLoot(Material.MANGROVE_PROPAGULE, 8, 2, 4);
      this.addLoot(Material.CHERRY_SAPLING, 6, 2, 4);
      this.addLoot(Material.AZALEA, 8, 1, 2);
      this.addLoot(Material.FLOWERING_AZALEA, 6, 1, 2);
      this.addLoot(Material.POPPY, 12, 2, 4);
      this.addLoot(Material.DANDELION, 12, 2, 4);
      this.addLoot(Material.BLUE_ORCHID, 10, 2, 4);
      this.addLoot(Material.ALLIUM, 10, 2, 4);
      this.addLoot(Material.AZURE_BLUET, 10, 2, 4);
      this.addLoot(Material.RED_TULIP, 10, 2, 4);
      this.addLoot(Material.ORANGE_TULIP, 10, 2, 4);
      this.addLoot(Material.WHITE_TULIP, 10, 2, 4);
      this.addLoot(Material.PINK_TULIP, 10, 2, 4);
      this.addLoot(Material.OXEYE_DAISY, 10, 2, 4);
      this.addLoot(Material.CORNFLOWER, 10, 2, 4);
      this.addLoot(Material.LILY_OF_THE_VALLEY, 8, 2, 4);
      this.addLoot(Material.SUNFLOWER, 8, 1, 2);
      this.addLoot(Material.LILAC, 8, 1, 2);
      this.addLoot(Material.ROSE_BUSH, 8, 1, 2);
      this.addLoot(Material.PEONY, 8, 1, 2);
      this.addLoot(Material.CACTUS, 10, 2, 4);
      this.addLoot(Material.BROWN_MUSHROOM, 12, 2, 4);
      this.addLoot(Material.RED_MUSHROOM, 12, 2, 4);
      this.addLoot(Material.SNOWBALL, 14, 8, 16);
      this.addLoot(Material.EGG, 14, 8, 16);
      this.addLoot(Material.MINECART, 10, 1, 2);
      this.addLoot(Material.CHEST_MINECART, 8, 1, 2);
      this.addLoot(Material.HOPPER_MINECART, 6, 1, 1);
      this.addLoot(Material.RAIL, 12, 8, 16);
      this.addLoot(Material.POWERED_RAIL, 8, 4, 8);
      this.addLoot(Material.DETECTOR_RAIL, 8, 4, 8);
      this.addLoot(Material.ACTIVATOR_RAIL, 8, 4, 8);
      this.addLoot(Material.OAK_BOAT, 12, 1, 2);
      this.addLoot(Material.BIRCH_BOAT, 10, 1, 2);
      this.addLoot(Material.SPRUCE_BOAT, 10, 1, 2);
      this.addLoot(Material.JUNGLE_BOAT, 8, 1, 2);
      this.addLoot(Material.ACACIA_BOAT, 8, 1, 2);
      this.addLoot(Material.DARK_OAK_BOAT, 8, 1, 2);
      this.addLoot(Material.MANGROVE_BOAT, 6, 1, 2);
      this.addLoot(Material.CHERRY_BOAT, 6, 1, 2);
      this.addLoot(Material.BAMBOO_RAFT, 6, 1, 2);
      this.addLoot(Material.MUSIC_DISC_13, 4, 1, 1);
      this.addLoot(Material.MUSIC_DISC_CAT, 4, 1, 1);
      this.addLoot(Material.MUSIC_DISC_BLOCKS, 3, 1, 1);
      this.addLoot(Material.MUSIC_DISC_CHIRP, 3, 1, 1);
      this.addLoot(Material.MUSIC_DISC_FAR, 3, 1, 1);
      this.addLoot(Material.MUSIC_DISC_MALL, 3, 1, 1);
      this.addLoot(Material.MUSIC_DISC_MELLOHI, 3, 1, 1);
      this.addLoot(Material.MUSIC_DISC_STAL, 3, 1, 1);
      this.addLoot(Material.MUSIC_DISC_STRAD, 3, 1, 1);
      this.addLoot(Material.MUSIC_DISC_WARD, 3, 1, 1);
      this.addLoot(Material.MUSIC_DISC_11, 2, 1, 1);
      this.addLoot(Material.MUSIC_DISC_WAIT, 3, 1, 1);
      this.addLoot(Material.MUSIC_DISC_OTHERSIDE, 2, 1, 1);
      this.addLoot(Material.MUSIC_DISC_5, 2, 1, 1);
      this.addLoot(Material.MUSIC_DISC_PIGSTEP, 2, 1, 1);
      this.addLoot(Material.MUSIC_DISC_RELIC, 2, 1, 1);
      this.addLoot(Material.MUSIC_DISC_PRECIPICE, 2, 1, 1);
      this.addLoot(Material.CREEPER_HEAD, 4, 1, 1);
      this.addLoot(Material.ZOMBIE_HEAD, 4, 1, 1);
      this.addLoot(Material.SKELETON_SKULL, 5, 1, 2);
      this.addLoot(Material.WITHER_SKELETON_SKULL, 2, 1, 1);
      this.addLoot(Material.DRAGON_HEAD, 1, 1, 1);
      this.addLoot(Material.SHULKER_BOX, 5, 1, 1);
      this.addLoot(Material.WHITE_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.ORANGE_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.MAGENTA_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.LIGHT_BLUE_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.YELLOW_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.LIME_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.PINK_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.GRAY_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.LIGHT_GRAY_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.CYAN_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.PURPLE_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.BLUE_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.BROWN_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.GREEN_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.RED_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.BLACK_SHULKER_BOX, 3, 1, 1);
      this.addLoot(Material.WHITE_CANDLE, 10, 4, 8);
      this.addLoot(Material.ORANGE_CANDLE, 10, 4, 8);
      this.addLoot(Material.MAGENTA_CANDLE, 10, 4, 8);
      this.addLoot(Material.LIGHT_BLUE_CANDLE, 10, 4, 8);
      this.addLoot(Material.YELLOW_CANDLE, 10, 4, 8);
      this.addLoot(Material.LIME_CANDLE, 10, 4, 8);
      this.addLoot(Material.PINK_CANDLE, 10, 4, 8);
      this.addLoot(Material.GRAY_CANDLE, 10, 4, 8);
      this.addLoot(Material.LIGHT_GRAY_CANDLE, 10, 4, 8);
      this.addLoot(Material.CYAN_CANDLE, 10, 4, 8);
      this.addLoot(Material.PURPLE_CANDLE, 10, 4, 8);
      this.addLoot(Material.BLUE_CANDLE, 10, 4, 8);
      this.addLoot(Material.BROWN_CANDLE, 10, 4, 8);
      this.addLoot(Material.GREEN_CANDLE, 10, 4, 8);
      this.addLoot(Material.RED_CANDLE, 10, 4, 8);
      this.addLoot(Material.BLACK_CANDLE, 10, 4, 8);
      this.addLoot(Material.COPPER_BLOCK, 10, 4, 8);
      this.addLoot(Material.CUT_COPPER, 8, 4, 8);
      this.addLoot(Material.COPPER_INGOT, 12, 4, 8);
      this.addLoot(Material.ECHO_SHARD, 6, 1, 3);
      this.addLoot(Material.DISC_FRAGMENT_5, 4, 1, 2);
      this.addLoot(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 3, 1, 1);
      this.addLoot(Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 3, 1, 1);
      this.addLoot(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 3, 1, 1);
      this.addLoot(Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, 3, 1, 1);
      this.addLoot(Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, 3, 1, 1);
      this.addLoot(Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 1, 1, 1);
      this.addLoot(Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 2, 1, 1);
      this.addLoot(Material.WIND_CHARGE, 8, 2, 4);
      this.addLoot(Material.HEAVY_CORE, 2, 1, 1);
      this.addLoot(Material.WOLF_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.CAT_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.PARROT_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.CHICKEN_SPAWN_EGG, 6, 1, 1);
      this.addLoot(Material.COW_SPAWN_EGG, 6, 1, 1);
      this.addLoot(Material.PIG_SPAWN_EGG, 6, 1, 1);
      this.addLoot(Material.SHEEP_SPAWN_EGG, 6, 1, 1);
      this.addLoot(Material.RABBIT_SPAWN_EGG, 5, 1, 1);
      this.addLoot(Material.HORSE_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.DONKEY_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.LLAMA_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.OCELOT_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.FOX_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.PANDA_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.POLAR_BEAR_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.TURTLE_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.DOLPHIN_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.COD_SPAWN_EGG, 5, 1, 1);
      this.addLoot(Material.SALMON_SPAWN_EGG, 5, 1, 1);
      this.addLoot(Material.TROPICAL_FISH_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.PUFFERFISH_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.SQUID_SPAWN_EGG, 5, 1, 1);
      this.addLoot(Material.GLOW_SQUID_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.AXOLOTL_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.TADPOLE_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.FROG_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.GOAT_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.ALLAY_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.BAT_SPAWN_EGG, 5, 1, 1);
      this.addLoot(Material.BEE_SPAWN_EGG, 5, 1, 1);
      this.addLoot(Material.BLAZE_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.CAVE_SPIDER_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.CREEPER_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.DROWNED_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.ELDER_GUARDIAN_SPAWN_EGG, 1, 1, 1);
      this.addLoot(Material.ENDERMAN_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.ENDERMITE_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.EVOKER_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.GHAST_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.GUARDIAN_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.HOGLIN_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.HUSK_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.MAGMA_CUBE_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.MOOSHROOM_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.PHANTOM_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.PIGLIN_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.PIGLIN_BRUTE_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.PILLAGER_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.RAVAGER_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.SKELETON_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.SLIME_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.SPIDER_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.STRAY_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.STRIDER_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.VEX_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.VILLAGER_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.VINDICATOR_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.WITCH_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.WITHER_SKELETON_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.ZOGLIN_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.ZOMBIE_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.ZOMBIE_VILLAGER_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.ZOMBIFIED_PIGLIN_SPAWN_EGG, 3, 1, 1);
      this.addLoot(Material.IRON_GOLEM_SPAWN_EGG, 2, 1, 1);
      this.addLoot(Material.SNOW_GOLEM_SPAWN_EGG, 4, 1, 1);
      this.addLoot(Material.WARDEN_SPAWN_EGG, 1, 1, 1);
      this.addLoot(Material.WITHER_SPAWN_EGG, 1, 1, 1);
      this.addLoot(Material.ENDER_DRAGON_SPAWN_EGG, 1, 1, 1);
      this.plugin.getLogger().info("战利品表已初始化，共 " + this.lootTable.size() + " 种物品");
   }

   /**
    * 从数据包加载物品标签
    * 支持从插件resource自动创建数据包文件
    */
   private void initLootTableFromDataPack() {
      // 确保数据包文件存在（从resource复制）
      this.extractLootTableFromResource("loot_tables/all_item.json");
      
      // 尝试从插件数据文件夹加载物品标签
      File itemTagFile = new File(this.plugin.getDataFolder(), "loot_tables/all_item.json");
      if (itemTagFile.exists()) {
         try (FileReader reader = new FileReader(itemTagFile)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json.has("pools")) {
               JsonArray pools = json.getAsJsonArray("pools");
               int loadedCount = 0;
               
               for (int p = 0; p < pools.size(); p++) {
                  JsonObject pool = pools.get(p).getAsJsonObject();
                  if (pool.has("entries")) {
                     JsonArray entries = pool.getAsJsonArray("entries");
                     for (int e = 0; e < entries.size(); e++) {
                        JsonObject entry = entries.get(e).getAsJsonObject();
                        
                        // 处理不同类型的条目
                        String type = entry.has("type") ? entry.get("type").getAsString() : "";
                        
                        if ("tag".equals(type) && entry.has("name")) {
                           // 物品标签类型 - 需要加载对应的标签文件
                           String tagName = entry.get("name").getAsString();
                           loadedCount += this.loadItemsFromTag(tagName);
                        } else if ("item".equals(type) && entry.has("name")) {
                           // 单个物品类型
                           String itemName = entry.get("name").getAsString();
                           Material material = this.parseMaterial(itemName);
                           if (material != null) {
                              int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 10;
                              this.addLoot(material, weight, 1, 1);
                              loadedCount++;
                           }
                        }
                     }
                  }
               }
               
               if (loadedCount > 0) {
                  this.plugin.getLogger().info("[ItemSystem] 从数据包加载了 " + loadedCount + " 种物品");
                  return;
               }
            }
         } catch (IOException e) {
            this.plugin.getLogger().warning("[ItemSystem] 无法从数据包加载物品: " + e.getMessage());
         }
      }
      
      // 如果数据包加载失败，使用硬编码
      this.plugin.getLogger().info("[ItemSystem] 数据包加载失败，使用硬编码物品表");
      this.initLootTable();
   }
   
   /**
    * 从物品标签加载物品
    */
   private int loadItemsFromTag(String tagName) {
      int count = 0;
      // 将标签名转换为文件路径
      // yw-pillar:item -> tags/item/item.json
      if (tagName.contains(":")) {
         tagName = tagName.split(":")[1];
      }
      
      File tagFile = new File(this.plugin.getDataFolder(), "tags/item/" + tagName + ".json");
      if (!tagFile.exists()) {
         // 尝试从resource提取
         this.extractLootTableFromResource("tags/item/" + tagName + ".json");
      }
      
      if (tagFile.exists()) {
         try (FileReader reader = new FileReader(tagFile)) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            if (json.has("values")) {
               JsonArray values = json.getAsJsonArray("values");
               for (int i = 0; i < values.size(); i++) {
                  String itemName = values.get(i).getAsString();
                  Material material = this.parseMaterial(itemName);
                  if (material != null) {
                     this.addLoot(material, 10, 1, 1);
                     count++;
                  }
               }
            }
         } catch (IOException e) {
            this.plugin.getLogger().warning("[ItemSystem] 无法加载物品标签 " + tagName + ": " + e.getMessage());
         }
      }
      return count;
   }
   
   /**
    * 解析物品名称
    */
   private Material parseMaterial(String itemName) {
      // 移除命名空间前缀
      if (itemName.contains(":")) {
         itemName = itemName.split(":")[1];
      }
      
      Material material = Material.getMaterial(itemName.toUpperCase());
      if (material == null) {
         material = Material.matchMaterial(itemName);
      }
      return material;
   }
   
   /**
    * 从插件resource提取战利品表文件
    */
   private void extractLootTableFromResource(String resourcePath) {
      File targetFile = new File(this.plugin.getDataFolder(), resourcePath);
      if (targetFile.exists()) {
         return; // 已存在，不需要提取
      }
      
      // 确保父目录存在
      targetFile.getParentFile().mkdirs();
      
      // 从resource复制
      try (InputStream is = this.plugin.getResource(resourcePath)) {
         if (is != null) {
            java.nio.file.Files.copy(is, targetFile.toPath());
            this.plugin.getLogger().info("[ItemSystem] 已从resource提取: " + resourcePath);
         }
      } catch (IOException e) {
         this.plugin.getLogger().warning("[ItemSystem] 无法提取 " + resourcePath + ": " + e.getMessage());
      }
   }

   private void addLoot(Material material, int weight, int minAmount, int maxAmount) {
      if (material != null) {
         this.lootTable.add(new ItemSystem.LootEntry(material, weight, minAmount, maxAmount));
      }
   }

   public void start() {
      // 海洋地图：不启动定时给予物品，改为钓鱼获取
      if (this.gameManager.getCurrentMapType() == MapType.SEA) {
         this.plugin.getLogger().info("[海洋地图] 禁用定时给予物品系统，玩家需要通过钓鱼获取物品");
         return;
      }

      this.lootTimer = this.plugin.getConfig().getInt("timers.loot_time", 10);
      World world = this.gameManager.getGameWorld();
      if (world != null) {
         this.itemTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, world, 0, 0, scheduledTask -> {
            if (this.gameManager.getGameStatus() != GameStatus.PLAYING) {
               scheduledTask.cancel();
            } else {
               // Made in Heaven时间加速支持
               int multiplier = this.gameManager.getTimeAccelerationMultiplier();
               this.lootTimer -= multiplier;
               if (this.lootTimer > 0) {
                  String actionBar = "§a物品: §6§l" + this.lootTimer;

                  for (Player player : Bukkit.getOnlinePlayers()) {
                     player.sendActionBar(Component.text(actionBar));
                  }
               }

               if (this.lootTimer <= 0) {
                  this.giveItemsToPlayers();
                  this.lootTimer = this.plugin.getConfig().getInt("timers.loot_time", 10);
               }
            }
         }, 1L, 20L);
      }
   }

   public void stop() {
      if (this.itemTask != null) {
         this.itemTask.cancel();
      }
   }

   /**
    * 给予玩家物品
    * 已统一使用 LootTableSystem 的 main 战利品表
    * 如果薛定谔的猫机制启用，则使用预生成的物品队列
    */
   private void giveItemsToPlayers() {
      for (UUID uuid : this.gameManager.getAlivePlayers()) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && player.isOnline()) {
            PlayerData data = this.gameManager.getPlayerData(uuid);
            if (data != null && data.getState() == PlayerState.INGAME) {
               ItemStack stack;
               
               // 检查薛定谔的猫机制是否启用
               if (this.plugin.getSchrodingerCatManager().isEnabled()) {
                  // 从薛定谔的猫机制获取下一个物品
                  stack = this.plugin.getSchrodingerCatManager().getNextItem();
               } else {
                  // 使用配置的随机池获取战利品
                  String mainPool = plugin.getConfig().getString("loot_pools.main_pool", "main");
                  stack = this.plugin.getLootTableSystem().getRandomLoot(mainPool);
               }
               
               if (stack != null) {
                  // 确保数量为1（定时给予的物品每次只给1个）
                  stack.setAmount(1);

                  // 检查是否是长矛，如果是则触发成就
                  ItemMeta meta = stack.getItemMeta();
                  if (meta != null && meta.hasDisplayName()) {
                     String displayName = meta.getDisplayName();
                     if (displayName.contains("长♂矛")) {
                        plugin.getAchievementSystem().grantItemAchievement(player, "spear");
                     }
                  }

                  Bukkit.getRegionScheduler().execute(this.plugin, player.getLocation(), () -> {
                     player.getInventory().addItem(new ItemStack[]{stack});
                  });
               }
            }
         }
      }
   }

   private ItemSystem.LootEntry getRandomLootEntry() {
      if (this.lootTable.isEmpty()) {
         return null;
      } else {
         int totalWeight = 0;

         for (ItemSystem.LootEntry entry : this.lootTable) {
            totalWeight += entry.weight;
         }

         int randomValue = this.random.nextInt(totalWeight);
         int currentWeight = 0;

         for (ItemSystem.LootEntry entry : this.lootTable) {
            currentWeight += entry.weight;
            if (randomValue < currentWeight) {
               return entry;
            }
         }

         return this.lootTable.get(this.lootTable.size() - 1);
      }
   }

   public int getLootTableSize() {
      return this.lootTable.size();
   }

   /**
    * 获取随机战利品（用于海洋地图钓鱼等其他系统）
    * 使用配置的 main_pool 随机池
    * @return 随机的ItemStack，如果战利品表为空则返回null
    */
   public ItemStack getRandomLoot() {
      // 使用配置的随机池获取战利品
      String mainPool = plugin.getConfig().getString("loot_pools.main_pool", "main");
      return this.plugin.getLootTableSystem().getRandomLoot(mainPool);
   }

   private static class LootEntry {
      final Material material;
      final int weight;
      final int minAmount;
      final int maxAmount;

      LootEntry(Material material, int weight, int minAmount, int maxAmount) {
         this.material = material;
         this.weight = weight;
         this.minAmount = minAmount;
         this.maxAmount = maxAmount;
      }

      int getRandomAmount(Random random) {
         return this.minAmount == this.maxAmount ? this.minAmount : random.nextInt(this.maxAmount - this.minAmount + 1) + this.minAmount;
      }
   }
}
