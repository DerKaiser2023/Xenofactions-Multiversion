package com.hfr.api;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import com.hfr.util.XFLog;
import net.minecraft.entity.player.EntityPlayerMP;

/** Final public chat bodies for optional external bridges; never faction/alliance/private chat. */
public final class PublicChat {
    public static final String FILTERED_CHAT_KEY = "hfr.chat.public.filtered";
    private static final CopyOnWriteArrayList<BiConsumer<EntityPlayerMP, String>> LISTENERS = new CopyOnWriteArrayList<>();

    private PublicChat() { }

    /** Register once during mod initialization. Callbacks run on the chat dispatch thread. */
    public static void addListener(BiConsumer<EntityPlayerMP, String> listener) {
        LISTENERS.addIfAbsent(Objects.requireNonNull(listener, "listener"));
    }

    public static void removeListener(BiConsumer<EntityPlayerMP, String> listener) {
        LISTENERS.remove(listener);
    }

    /** Called by the public dispatcher only, after moderation, filtering and Minecraft delivery. */
    public static void publish(EntityPlayerMP sender, String message) {
        if (sender.worldObj.isRemote || message == null || message.isEmpty()) return;
        for (BiConsumer<EntityPlayerMP, String> listener : LISTENERS) {
            try {
                listener.accept(sender, message);
            } catch (RuntimeException failure) {
                XFLog.error("An external public chat listener failed", failure);
            }
        }
    }
}