package com.hfr.packet.client;

import com.google.gson.Gson;
import com.hfr.main.MainRegistry;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.effect.EarthMapSnapshotPacket;
import com.hfr.tdm.TDMServerTaskQueue;
import com.hfr.world.earth.XFEarthProfile;
import com.hfr.world.earth.XFEarthProfileLoader;
import com.hfr.world.earth.XFEarthRegistry;
import com.hfr.world.earth.map.XFEarthMapData;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/** Read-only metadata request; all world/save access runs on the server tick thread. */
public final class EarthMapRequestPacket implements IMessage {
    private int request, dimension;
    public EarthMapRequestPacket() { }
    public EarthMapRequestPacket(int request, int dimension) { this.request = request; this.dimension = dimension; }
    @Override public void fromBytes(ByteBuf buf) { request = buf.readInt(); dimension = buf.readInt(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(request); buf.writeInt(dimension); }

    public static final class Handler implements IMessageHandler<EarthMapRequestPacket, IMessage> {
        @Override public IMessage onMessage(final EarthMapRequestPacket packet, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            TDMServerTaskQueue.schedule(new Runnable() { @Override public void run() {
                if (player.isDead || player.playerNetServerHandler.netManager == null
                        || !player.playerNetServerHandler.netManager.isChannelOpen()) return;
                World world = player.worldObj;
                if (world.provider.dimensionId != packet.dimension) return;
                String json = "", identity = "", error = "";
                if (packet.dimension != 0 || XFEarthRegistry.get() == null
                        || world.getWorldInfo().getTerrainType() != XFEarthRegistry.get()) {
                    error = "gui.hfr.earthmap.unavailable";
                } else try {
                    File root = world.getSaveHandler().getWorldDirectory();
                    XFEarthProfile profile = XFEarthProfileLoader.load(root);
                    json = new Gson().toJson(profile);
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    digest.update((root.getCanonicalPath() + "\n" + json).getBytes("UTF-8"));
                    File install = new File(root, "xfearth-install.json");
                    if (install.isFile()) {
                        if (install.length() > 65536) throw new IllegalArgumentException("Oversize Earth installation metadata");
                        FileInputStream in = new FileInputStream(install);
                        try { byte[] bytes = new byte[4096]; int total = 0;
                            for (int n; (n = in.read(bytes)) != -1;) {
                                total += n;
                                if (total > 65536) throw new IllegalArgumentException("Oversize Earth installation metadata");
                                digest.update(bytes, 0, n);
                            }
                        } finally { in.close(); }
                    }
                    StringBuilder hash = new StringBuilder();
                    for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                    identity = hash.toString();
                    new XFEarthMapData(profile, identity); // Validate the optional imagery identifier before sending.
                    if (json.length() > 16384) throw new IllegalArgumentException("Oversize Earth profile");
                } catch (Exception failure) {
                    json = ""; error = "gui.hfr.earthmap.profile_missing";
                    if (MainRegistry.logger != null) MainRegistry.logger.warn("[XF EARTH MAP] Cannot describe active profile: " + failure.getMessage());
                }
                PacketDispatcher.wrapper.sendTo(new EarthMapSnapshotPacket(packet.request, packet.dimension, json, identity, error), player);
            } });
            return null;
        }
    }
}
