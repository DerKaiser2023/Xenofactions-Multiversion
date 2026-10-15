package com.hfr.packet.daily;

import com.hfr.daily.DailyRewardService;
import com.hfr.tdm.TDMServerTaskQueue;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Intent only: clients cannot send rewards, dates, streaks or animation outcomes. */
public final class DailyActionPacket implements IMessage {
    private String token = "";
    private long revision;
    private int action;
    public DailyActionPacket() { }
    public DailyActionPacket(String token, long revision, int action) { this.token = token; this.revision = revision; this.action = action; }
    public void fromBytes(ByteBuf buf) {
        token = ByteBufUtils.readUTF8String(buf);
        if (token.length() != 36) throw new IllegalArgumentException("Invalid daily session");
        revision = buf.readLong(); action = buf.readUnsignedByte();
        if (action > 2) throw new IllegalArgumentException("Invalid daily action");
    }
    public void toBytes(ByteBuf buf) { ByteBufUtils.writeUTF8String(buf, token); buf.writeLong(revision); buf.writeByte(action); }
    public static final class Handler implements IMessageHandler<DailyActionPacket, IMessage> {
        public IMessage onMessage(final DailyActionPacket message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            TDMServerTaskQueue.schedule(new Runnable() {
                public void run() { DailyRewardService.action(player, message.token, message.revision, message.action); }
            });
            return null;
        }
    }
}
