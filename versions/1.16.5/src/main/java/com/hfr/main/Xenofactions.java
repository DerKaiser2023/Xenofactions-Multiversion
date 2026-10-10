package com.hfr.main;

import com.hfr.config.FoundationConfig;
import com.hfr.inventory.FoundationContainer;
import com.hfr.items.AdminSelectionWand;
import com.hfr.network.FoundationNetwork;
import com.hfr.server.FoundationLifecycle;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Xenofactions.MODID)
public final class Xenofactions {
    public static final String MODID = "hfr";
    public static final Logger LOGGER = LogManager.getLogger("Xenofactions");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    private static final DeferredRegister<net.minecraft.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    private static final DeferredRegister<net.minecraft.tileentity.TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, MODID);
    private static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, MODID);
    // The original registry registers getUnlocalizedName(), including the item. prefix.
    public static final RegistryObject<Item> WAND = ITEMS.register("item.world_border_wand", AdminSelectionWand::new);
    public static final RegistryObject<net.minecraft.block.Block> CITY = BLOCKS.register("tile.clowder_flag", com.hfr.blocks.CityCenterBlock::new);
    public static final RegistryObject<Item> CITY_ITEM = ITEMS.register("tile.clowder_flag", () -> new com.hfr.items.CityCenterItem(CITY.get()));
    public static final RegistryObject<Item> PRESTIGE = ITEMS.register("item.province_point", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> DEBUG = ITEMS.register("item.debug", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MACE = ITEMS.register("item.mace", com.hfr.items.RaidingMace::new);
    public static final RegistryObject<Item> MULTITOOL = ITEMS.register("item.mult_alloy", com.hfr.items.RaidingMace::new);
    public static final RegistryObject<Item> CLAIM_MAP = ITEMS.register("item.clowder_map", com.hfr.items.ClaimMapItem::new);
    public static final RegistryObject<net.minecraft.block.Block> FOUNDATION = BLOCKS.register("tile.uni_foundation", () -> new com.hfr.blocks.FoundationBlock(.15, 1));
    public static final RegistryObject<net.minecraft.block.Block> ASPHALT = BLOCKS.register("tile.asphalt", () -> new com.hfr.blocks.FoundationBlock(.25, 30));
    public static final RegistryObject<Item> FOUNDATION_ITEM = ITEMS.register("tile.uni_foundation", () -> new net.minecraft.item.BlockItem(FOUNDATION.get(), new Item.Properties()));
    public static final RegistryObject<Item> ASPHALT_ITEM = ITEMS.register("tile.asphalt", () -> new net.minecraft.item.BlockItem(ASPHALT.get(), new Item.Properties()));
    public static final RegistryObject<net.minecraft.block.Block> TENT = BLOCKS.register("tile.tp_tent", com.hfr.blocks.WarpTentBlock::new);
    public static final RegistryObject<Item> TENT_ITEM = ITEMS.register("tile.tp_tent", () -> new com.hfr.items.WarpTentItem(TENT.get()));
    public static final RegistryObject<net.minecraft.tileentity.TileEntityType<com.hfr.tile.WarpTentTile>> TENT_TILE = TILES.register("warp_tent", () -> net.minecraft.tileentity.TileEntityType.Builder.of(com.hfr.tile.WarpTentTile::new, TENT.get()).build(null));
    public static final RegistryObject<net.minecraft.block.Block> CONQUEST = BLOCKS.register("tile.clowder_conquerer", com.hfr.blocks.ConquestBlock::new);
    public static final RegistryObject<Item> CONQUEST_ITEM = ITEMS.register("tile.clowder_conquerer", () -> new com.hfr.items.ConquestItem(CONQUEST.get()));
    public static final RegistryObject<net.minecraft.tileentity.TileEntityType<com.hfr.tile.ConquestTile>> CONQUEST_TILE = TILES.register("conquest_flag", () -> net.minecraft.tileentity.TileEntityType.Builder.of(com.hfr.tile.ConquestTile::new, CONQUEST.get()).build(null));
    public static final RegistryObject<net.minecraft.block.Block> OFFICER_CHEST = BLOCKS.register("tile.officer_chest", com.hfr.blocks.OfficerChestBlock::new);
    public static final RegistryObject<Item> OFFICER_CHEST_ITEM = ITEMS.register("tile.officer_chest", () -> new net.minecraft.item.BlockItem(OFFICER_CHEST.get(), new Item.Properties()));
    public static final RegistryObject<net.minecraft.tileentity.TileEntityType<com.hfr.tile.OfficerChestTile>> OFFICER_CHEST_TILE = TILES.register("officer_chest", () -> net.minecraft.tileentity.TileEntityType.Builder.of(com.hfr.tile.OfficerChestTile::new, OFFICER_CHEST.get()).build(null));
    public static final RegistryObject<net.minecraft.tileentity.TileEntityType<com.hfr.tile.CityCenterTile>> CITY_TILE = TILES.register("city_center", () -> net.minecraft.tileentity.TileEntityType.Builder.of(com.hfr.tile.CityCenterTile::new, CITY.get()).build(null));
    public static final RegistryObject<ContainerType<com.hfr.inventory.CityCenterContainer>> CITY_MENU = CONTAINERS.register("city_center", () -> IForgeContainerType.create((id, inventory, buffer) -> new com.hfr.inventory.CityCenterContainer(id, buffer.readNbt(), inventory.player.level)));
    public static final RegistryObject<ContainerType<FoundationContainer>> EDITOR = CONTAINERS.register("admin_editor",
            () -> IForgeContainerType.create((id, inventory, buffer) -> new FoundationContainer(id, buffer.readNbt())));
    public Xenofactions() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus); BLOCKS.register(bus); TILES.register(bus); CONTAINERS.register(bus);
        bus.addListener(this::setup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FoundationConfig.SPEC, "xenofactions-foundation.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, com.hfr.config.GameplayConfig.SPEC, "xenofactions-gameplay.toml");
        MinecraftForge.EVENT_BUS.register(new FoundationLifecycle());
        MinecraftForge.EVENT_BUS.register(new com.hfr.server.PlayerLifecycle());
        MinecraftForge.EVENT_BUS.register(new com.hfr.server.FactionEvents());
        MinecraftForge.EVENT_BUS.register(new com.hfr.server.ChatModeration());
    }
    private void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) { event.enqueueWork(() -> { FoundationNetwork.register(); net.minecraftforge.common.crafting.CraftingHelper.register(new com.hfr.config.SurvivalRecipeCondition.Serializer()); }); }
}
