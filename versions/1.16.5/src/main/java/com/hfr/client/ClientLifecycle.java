package com.hfr.client;

import com.hfr.main.Xenofactions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Xenofactions.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientLifecycle {
    private static final KeyBinding EDITOR = new KeyBinding("key.hfr.admineditor", GLFW.GLFW_KEY_F10, "key.categories.hfr.xenofactions");
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(EDITOR);
        event.enqueueWork(() -> ScreenManager.register(Xenofactions.EDITOR.get(), FoundationScreen::new));
    }
    @Mod.EventBusSubscriber(modid = Xenofactions.MODID, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft client = Minecraft.getInstance();
            while (EDITOR.consumeClick()) {
                if (client.player == null) continue;
                if (client.screen instanceof FoundationScreen) client.player.closeContainer();
                else if (client.screen == null) client.player.chat("/tdm editor gui");
            }
        }
    }
    private ClientLifecycle() { }
}
