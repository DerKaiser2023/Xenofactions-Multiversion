package com.hfr.tileentity;

import com.hfr.leaderboard.FactionLeaderboards;
import com.hfr.leaderboard.FactionLeaderboards.Metric;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

public final class TileEntityFactionLeaderboard extends TileEntity {
    private Metric metric = Metric.TERRITORY;
    private NBTTagList rows = new NBTTagList();
    private int ticks;

    public Metric getMetric() { return metric; }
    public int rowCount() { return rows.tagCount(); }
    public NBTTagCompound row(int index) { return rows.getCompoundTagAt(index); }

    public void configure(Metric selected) {
        if (selected == null || worldObj == null || worldObj.isRemote) return;
        metric = selected;
        refresh();
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public boolean refresh() {
        if (worldObj == null || worldObj.isRemote) return false;
        NBTTagList next = FactionLeaderboards.rows(worldObj, metric);
        if (rows.equals(next)) return false;
        rows = next;
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return true;
    }

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote && ticks++ % 20 == 0) refresh();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        Metric saved = Metric.parse(tag.getString("metric"));
        metric = saved == null ? Metric.TERRITORY : saved;
        rows = new NBTTagList(); // Rankings are transient and recomputed from faction data.
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("metric", metric.key());
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("metric", metric.key());
        tag.setTag("rows", rows.copy());
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        Metric received = Metric.parse(tag.getString("metric"));
        metric = received == null ? Metric.TERRITORY : received;
        NBTTagList receivedRows = tag.getTagList("rows", 10);
        rows = new NBTTagList();
        for (int i = 0; i < Math.min(FactionLeaderboards.ENTRIES, receivedRows.tagCount()); i++)
            rows.appendTag(receivedRows.getCompoundTagAt(i));
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(xCoord - 2, yCoord, zCoord - 2, xCoord + 3, yCoord + 3, zCoord + 3);
    }

    @Override
    public double getMaxRenderDistanceSquared() { return 4096D; }
}
