package com.hfr.blocks.machine;

import com.hfr.blocks.ModBlocks;
import com.hfr.shop.PlayerTraderService;
import com.hfr.tdm.TDMServerTaskQueue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/** An access point to the world market. Legacy inventories are sealed migration archives only. */
public class PlayerTrader extends BlockContainer {
    public PlayerTrader() { super(Material.iron); }
    @Override public TileEntity createNewTileEntity(World world, int metadata) { return new Tile(); }

    @Override public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && stack.hasTagCompound() && stack.getTagCompound().hasKey("PlayerTraderRecovery", 10)) {
            Tile tile = (Tile)world.getTileEntity(x, y, z);
            if (tile != null) {
                NBTTagCompound recovery = stack.getTagCompound().getCompoundTag("PlayerTraderRecovery");
                tile.legacyArchive = (NBTTagCompound)recovery.getCompoundTag("Archive").copy();
                tile.source = recovery.getString("Source"); tile.marketMigrated = recovery.getBoolean("Migrated");
                tile.markDirty(); PlayerTraderService.migrate(tile);
            }
        }
    }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                                 int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof Tile) PlayerTraderService.open((EntityPlayerMP)player, (Tile)tile);
        }
        return true;
    }
    @Override public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!world.isRemote && tile instanceof Tile && PlayerTraderService.migrate((Tile)tile) != null) return false;
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }
    @Override public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!world.isRemote && tile instanceof Tile && PlayerTraderService.migrate((Tile)tile) != null) return;
        super.onBlockExploded(world, x, y, z, explosion);
    }
    @Override public void dropBlockAsItemWithChance(World world, int x, int y, int z, int metadata, float chance, int fortune) {
        TileEntity tile = world.getTileEntity(x, y, z);
        // Explosions ask for drops before onBlockExploded; a preserved block must not also drop a copy.
        if (!world.isRemote && tile instanceof Tile && PlayerTraderService.migrate((Tile)tile) != null) return;
        super.dropBlockAsItemWithChance(world, x, y, z, metadata, chance, fortune);
    }
    @Override public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        if (!world.isRemote) {
            TileEntity raw = world.getTileEntity(x, y, z);
            if (raw instanceof Tile) {
                Tile tile = (Tile)raw;
                // A direct mod/admin replacement can bypass removedByPlayer. Preserve a recoverable NBT item.
                if (PlayerTraderService.migrate(tile) != null && tile.legacyArchive != null) {
                    ItemStack recovery = new ItemStack(this);
                    NBTTagCompound tag = new NBTTagCompound(), saved = new NBTTagCompound();
                    saved.setTag("Archive", tile.legacyArchive.copy()); saved.setString("Source", tile.legacySource());
                    saved.setBoolean("Migrated", tile.marketMigrated); tag.setTag("PlayerTraderRecovery", saved);
                    recovery.setTagCompound(tag);
                    world.spawnEntityInWorld(new EntityItem(world, x + .5, y + .5, z + .5, recovery));
                }
                PlayerTraderService.broken(tile);
            }
        }
        super.breakBlock(world, x, y, z, block, metadata);
    }

    public static class Tile extends TileEntity {
        public NBTTagCompound legacyArchive;
        public boolean marketMigrated;
        public String source = "", migrationProblem = "";
        @Override public boolean canUpdate() { return false; }
        @Override public void validate() {
            super.validate();
            if (worldObj == null || worldObj.isRemote || legacyArchive == null) return;
            TDMServerTaskQueue.schedule(new Runnable() { @Override public void run() {
                if (valid() && !worldObj.isRemote) PlayerTraderService.migrate(Tile.this);
            }});
        }
        public String legacySource() {
            if (source.isEmpty() && worldObj != null) {
                source = worldObj.provider.dimensionId + ":" + xCoord + ":" + yCoord + ":" + zCoord;
            }
            return source;
        }
        @Override public void readFromNBT(NBTTagCompound tag) {
            super.readFromNBT(tag);
            legacyArchive = !tag.getBoolean("GlobalMarketTerminal") ? (NBTTagCompound)tag.copy()
                : tag.hasKey("LegacyInventory", 10) ? (NBTTagCompound)tag.getCompoundTag("LegacyInventory").copy() : null;
            source = tag.getString("LegacySource"); marketMigrated = tag.getBoolean("MarketMigrated");
        }
        @Override public void writeToNBT(NBTTagCompound tag) {
            super.writeToNBT(tag); tag.setBoolean("GlobalMarketTerminal", true);
            tag.setBoolean("MarketMigrated", marketMigrated); tag.setString("LegacySource", legacySource());
            if (legacyArchive != null) tag.setTag("LegacyInventory", legacyArchive.copy());
        }
        public boolean valid() { return !isInvalid() && worldObj != null && worldObj.getChunkProvider().chunkExists(xCoord >> 4, zCoord >> 4)
                && worldObj.getBlock(xCoord, yCoord, zCoord) == ModBlocks.player_trader
                && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this; }
    }
}
