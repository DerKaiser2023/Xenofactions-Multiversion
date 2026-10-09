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
    private static final String PROTOCOL = "foundation-1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Xenofactions.MODID, "foundation"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private FoundationNetwork() { }
    public static void register() {
        CHANNEL.registerMessage(0, Action.class, Action::encode, Action::decode, Action::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void action(int container, String token, int action) { CHANNEL.sendToServer(new Action(container, token, action)); }
    public static void snapshot(ServerPlayerEntity player, int container, CompoundNBT data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(container, data));
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
