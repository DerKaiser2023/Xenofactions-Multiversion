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
    private static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, MODID);
    // The original registry registers getUnlocalizedName(), including the item. prefix.
    public static final RegistryObject<Item> WAND = ITEMS.register("item.world_border_wand", AdminSelectionWand::new);
    public static final RegistryObject<ContainerType<FoundationContainer>> EDITOR = CONTAINERS.register("admin_editor",
            () -> IForgeContainerType.create((id, inventory, buffer) -> new FoundationContainer(id, buffer.readNbt())));
    public Xenofactions() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus); CONTAINERS.register(bus);
        bus.addListener(this::setup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FoundationConfig.SPEC, "xenofactions-foundation.toml");
        MinecraftForge.EVENT_BUS.register(new FoundationLifecycle());
        MinecraftForge.EVENT_BUS.register(new com.hfr.server.PlayerLifecycle());
    }
    private void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) { event.enqueueWork(FoundationNetwork::register); }
}
