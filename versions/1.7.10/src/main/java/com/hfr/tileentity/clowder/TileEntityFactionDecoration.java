package com.hfr.tileentity.clowder;

import com.hfr.blocks.ModBlocks;
import com.hfr.clowder.Clowder;
import com.hfr.clowder.ClowderFlag;
import com.hfr.main.MainRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/** Decoration only: deliberately neither a machine nor an ITerritoryProvider. */
public final class TileEntityFactionDecoration extends TileEntity {
    private String factionId = "";
    private String factionName = "";
    private String customFlagHash = "";
    private ClowderFlag flag = ClowderFlag.NONE;
    private int color = 0xFFFFFF;
    private int ticks;

    public String getFactionId() { return factionId; }
    public String getFactionName() { return factionName; }
    public String getCustomFlagHash() { return customFlagHash; }
    public ClowderFlag getFlag() { return flag; }
    public int getColor() { return color; }

    public void bind(Clowder faction) {
        if(worldObj.isRemote || !factionId.isEmpty() || faction == null) return;
        factionId = faction.uuid;
        refreshDesign();
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override public void updateEntity() {
        // Stagger bounded appearance checks. Rendering never looks up factions or performs I/O.
        if((ticks++ + xCoord + yCoord + zCoord) % 20 != 0) return;
        if(worldObj.isRemote) MainRegistry.proxy.updateDecorationTexture(this);
        else if(refreshDesign()) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private boolean refreshDesign() {
        Clowder faction = Clowder.getClowderFromUuid(factionId);
        String name = faction == null ? "" : faction.name;
        String hash = faction == null || faction.customFlagHash == null ? "" : faction.customFlagHash;
        ClowderFlag design = faction == null || faction.flag == null ? ClowderFlag.NONE : faction.flag;
        int tint = faction == null ? 0xFFFFFF : faction.color;
        if(name.equals(factionName) && hash.equals(customFlagHash) && design == flag && tint == color) return false;
        factionName = name;
        customFlagHash = hash;
        flag = design;
        color = tint;
        return true;
    }

    @Override public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        MainRegistry.proxy.receiveDecoration(this, packet.func_148857_g());
    }

    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        factionId = nbt.getString("factionId");
        factionName = nbt.getString("factionName");
        customFlagHash = nbt.getString("customFlagHash");
        int ordinal = nbt.getInteger("flag");
        flag = ordinal >= 0 && ordinal < ClowderFlag.values().length ? ClowderFlag.values()[ordinal] : ClowderFlag.NONE;
        color = nbt.hasKey("color") ? nbt.getInteger("color") : 0xFFFFFF;
    }

    @Override public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("factionId", factionId);
        // A small presentation snapshot; only factionId owns the binding, even after deletion.
        nbt.setString("factionName", factionName);
        nbt.setString("customFlagHash", customFlagHash);
        nbt.setInteger("flag", flag.ordinal());
        nbt.setInteger("color", color);
    }

    @Override public AxisAlignedBB getRenderBoundingBox() {
        return com.hfr.main.MainRegistry.proxy.modelBounds(this);
    }
}
