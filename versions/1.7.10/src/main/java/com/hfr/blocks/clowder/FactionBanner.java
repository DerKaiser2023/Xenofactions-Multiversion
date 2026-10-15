package com.hfr.blocks.clowder;

import com.hfr.clowder.Clowder;
import com.hfr.tileentity.clowder.TileEntityFactionDecoration;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Facing is the clicked wall's outward face (vanilla side indices 2..5). */
public final class FactionBanner extends BlockContainer {
    public FactionBanner() { super(Material.cloth); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new TileEntityFactionDecoration(); }
    @Override public int getRenderType() { return -1; }
    @Override public boolean isOpaqueCube() { return false; }
    @Override public boolean renderAsNormalBlock() { return false; }
    @Override public int onBlockPlaced(World world, int x, int y, int z, int side, float hx, float hy, float hz, int meta) { return side; }

    public static void bind(World world, int x, int y, int z, EntityLivingBase placer) {
        if(!world.isRemote && placer instanceof EntityPlayer) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if(tile instanceof TileEntityFactionDecoration)
                ((TileEntityFactionDecoration)tile).bind(Clowder.getClowderFromPlayer((EntityPlayer)placer));
        }
    }

    @Override public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        bind(world, x, y, z, placer);
    }

    @Override public boolean canPlaceBlockOnSide(World world, int x, int y, int z, int side) {
        return super.canPlaceBlockAt(world, x, y, z) && supported(world, x, y, z, side);
    }

    private boolean supported(World world, int x, int y, int z, int side) {
        if(side < 2 || side > 5) return false;
        ForgeDirection direction = ForgeDirection.getOrientation(side);
        return world.isSideSolid(x - direction.offsetX, y, z - direction.offsetZ, direction);
    }

    @Override public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if(!world.isRemote && !world.restoringBlockSnapshots && !supported(world, x, y, z, world.getBlockMetadata(x, y, z))) {
            dropBlockAsItem(world, x, y, z, 0, 0);
            world.setBlockToAir(x, y, z);
        }
    }

    @Override public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        switch(world.getBlockMetadata(x, y, z)) {
        case 2: setBlockBounds(.1875F, .03125F, .9375F, .8125F, .96875F, 1); break;
        case 3: setBlockBounds(.1875F, .03125F, 0, .8125F, .96875F, .0625F); break;
        case 4: setBlockBounds(.9375F, .03125F, .1875F, 1, .96875F, .8125F); break;
        default: setBlockBounds(0, .03125F, .1875F, .0625F, .96875F, .8125F); break;
        }
    }
    @Override public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) { return null; }
    @Override public int damageDropped(int meta) { return 0; }
}
