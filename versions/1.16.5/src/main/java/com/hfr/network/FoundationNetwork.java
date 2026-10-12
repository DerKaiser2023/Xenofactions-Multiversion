package com.hfr.network;

import com.hfr.client.ClientPackets;
import com.hfr.main.Xenofactions;
import com.hfr.server.EditorSessions;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public final class FoundationNetwork {
    private static final String PROTOCOL = "foundation-3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Xenofactions.MODID, "foundation"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private FoundationNetwork() { }
    public static void register() {
        CHANNEL.registerMessage(0, Action.class, Action::encode, Action::decode, Action::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Border.class, Border::encode, Border::decode, Border::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, TraderAction.class, TraderAction::encode, TraderAction::decode, TraderAction::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, TraderSnapshot.class, TraderSnapshot::encode, TraderSnapshot::decode, TraderSnapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void traderAction(int container, String token, String action, long revision, int slot, int quantity) {
        CHANNEL.sendToServer(new TraderAction(container, token, action, revision, slot, quantity));
    }
    public static void traderSnapshot(ServerPlayerEntity player, int container, CompoundNBT data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TraderSnapshot(container, data));
    }
    public static final class TraderAction {
        final int container, slot, quantity; final String token, action; final long revision;
        TraderAction(int container, String token, String action, long revision, int slot, int quantity) { this.container = container; this.token = token; this.action = action; this.revision = revision; this.slot = slot; this.quantity = quantity; }
        static void encode(TraderAction p, PacketBuffer b) { b.writeVarInt(p.container); b.writeUtf(p.token, 36); b.writeUtf(p.action, 16); b.writeLong(p.revision); b.writeVarInt(p.slot); b.writeVarInt(p.quantity); }
        static TraderAction decode(PacketBuffer b) { return new TraderAction(b.readVarInt(), b.readUtf(36), b.readUtf(16), b.readLong(), b.readVarInt(), b.readVarInt()); }
        static void handle(TraderAction p, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context c = supplier.get(); c.enqueueWork(() -> {
                ServerPlayerEntity player = c.getSender(); if (player != null) com.hfr.shop.PlayerTraderService.action(player, p.container, p.token, p.action, p.revision, p.slot, p.quantity);
            }); c.setPacketHandled(true);
        }
    }
    public static final class TraderSnapshot {
        public final int container; public final CompoundNBT data;
        TraderSnapshot(int container, CompoundNBT data) { this.container = container; this.data = data.copy(); }
        static void encode(TraderSnapshot p, PacketBuffer b) { b.writeVarInt(p.container); b.writeNbt(p.data); }
        static TraderSnapshot decode(PacketBuffer b) { int id = b.readVarInt(); CompoundNBT data = b.readNbt(); return new TraderSnapshot(id, data == null ? new CompoundNBT() : data); }
        static void handle(TraderSnapshot p, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context c = supplier.get(); c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.trader(p))); c.setPacketHandled(true);
        }
    }
    public static void action(int container, String token, int action) { CHANNEL.sendToServer(new Action(container, token, action)); }
    public static void snapshot(ServerPlayerEntity player, int container, CompoundNBT data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(container, data));
    }
    public static void border(ServerPlayerEntity player, ResourceLocation dimension, boolean popup, int pattern, int color, String name, String title, int[] segments) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Border(dimension, popup, pattern, color, name, title, segments));
    }
    public static final class Border {
        public final ResourceLocation dimension;
        public final boolean popup;
        public final int pattern, color;
        public final String name, title;
        public final int[] segments;
        Border(ResourceLocation dimension, boolean popup, int pattern, int color, String name, String title, int[] segments) {
            this.dimension = dimension; this.popup = popup; this.pattern = pattern; this.color = color; this.name = name; this.title = title; this.segments = segments;
        }
        static void encode(Border packet, PacketBuffer buffer) {
            buffer.writeResourceLocation(packet.dimension); buffer.writeBoolean(packet.popup);
            if (packet.popup) { buffer.writeInt(packet.pattern); buffer.writeInt(packet.color); buffer.writeUtf(packet.name); buffer.writeUtf(packet.title); }
            buffer.writeVarIntArray(packet.segments);
        }
        static Border decode(PacketBuffer buffer) {
            ResourceLocation dimension = buffer.readResourceLocation(); boolean popup = buffer.readBoolean();
            int pattern = -1, color = 0xFFFFFF; String name = "", title = "";
            if (popup) { pattern = buffer.readInt(); color = buffer.readInt(); name = buffer.readUtf(32767); title = buffer.readUtf(32767); }
            int[] segments = buffer.readVarIntArray(128 * 5);
            if (segments.length % 5 != 0) throw new IllegalArgumentException("Invalid faction border segments.");
            return new Border(dimension, popup, pattern, color, name, title, segments);
        }
        static void handle(Border packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.hfr.client.ClientBorderFeedback.receive(packet)));
            context.setPacketHandled(true);
        }
    }
    public static final class Action {
        final int container, action; final String token;
        Action(int container, String token, int action) { this.container = container; this.token = token; this.action = action; }
        static void encode(Action packet, PacketBuffer buffer) { buffer.writeVarInt(packet.container); buffer.writeUtf(packet.token, 36); buffer.writeVarInt(packet.action); }
        static Action decode(PacketBuffer buffer) { return new Action(buffer.readVarInt(), buffer.readUtf(36), buffer.readVarInt()); }
        static void handle(Action packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> { ServerPlayerEntity player = context.getSender(); if (player != null) EditorSessions.action(player, packet.container, packet.token, packet.action); });
            context.setPacketHandled(true);
        }
    }
    public static final class Snapshot {
        public final int container; public final CompoundNBT data;
        Snapshot(int container, CompoundNBT data) { this.container = container; this.data = data.copy(); }
        static void encode(Snapshot packet, PacketBuffer buffer) { buffer.writeVarInt(packet.container); buffer.writeNbt(packet.data); }
        static Snapshot decode(PacketBuffer buffer) { int container = buffer.readVarInt(); CompoundNBT data = buffer.readNbt(); return new Snapshot(container, data == null ? new CompoundNBT() : data); }
        static void handle(Snapshot packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.receive(packet)));
            context.setPacketHandled(true);
        }
    }
}
