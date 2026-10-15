package com.hfr.blocks.clowder;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import com.hfr.tileentity.clowder.TileEntityFactionDecoration;

/** 0..3: core yaw; 4..13: pole segments at core Y + metadata - 3. */
public final class LargeFactionFlag extends BlockContainer {
    private boolean removing;
    public LargeFactionFlag() { super(Material.iron); }
    @Override public boolean hasTileEntity(int meta) { return meta < 4; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return meta < 4 ? new TileEntityFactionDecoration() : null; }
    @Override public int getRenderType() { return -1; }
    @Override public boolean isOpaqueCube() { return false; }
    @Override public boolean renderAsNormalBlock() { return false; }
    @Override public int damageDropped(int meta) { return 0; }
    public static int rotation(EntityLivingBase placer) { return MathHelper.floor_double(placer.rotationYaw * 4F / 360F + .5D) & 3; }
    public static int clothX(int rotation) { return rotation == 1 ? -1 : rotation == 3 ? 1 : 0; }
    public static int clothZ(int rotation) { return rotation == 0 ? -1 : rotation == 2 ? 1 : 0; }

    @Override public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return super.canPlaceBlockAt(world, x, y, z) && y >= 1 && y + 10 < world.getHeight()
            && world.isSideSolid(x, y - 1, z, ForgeDirection.UP);
    }

    @Override public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        if(world.isRemote) return;
        world.setBlockMetadataWithNotify(x, y, z, rotation(placer), 2);
        for(int level = 1; level <= 10; level++) world.setBlock(x, y + level, z, this, level + 3, 2);
        FactionBanner.bind(world, x, y, z, placer);
    }

    @Override public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        int meta = world.getBlockMetadata(x, y, z);
        if(meta < 4) setBlockBounds(0, .0625F, 0, 1, 1, 1);
        else setBlockBounds(.4375F, 0, .4375F, .5625F, meta == 13 ? .25F : 1, .5625F);
    }

    @Override public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        if(world.getBlockMetadata(x, y, z) < 4) return foot(x, y, z);
        setBlockBoundsBasedOnState(world, x, y, z);
        return super.getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    private AxisAlignedBB foot(int x, int y, int z) {
        return AxisAlignedBB.getBoundingBox(x, y + .0625D, z, x + 1, y + .3125D, z + 1);
    }

    private AxisAlignedBB shaft(int x, int y, int z) {
        return AxisAlignedBB.getBoundingBox(x + .4375D, y + .25D, z + .4375D, x + .5625D, y + 1, z + .5625D);
    }

    @Override public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        if(world.getBlockMetadata(x, y, z) >= 4) return super.collisionRayTrace(world, x, y, z, start, end);
        MovingObjectPosition footHit = foot(x, y, z).calculateIntercept(start, end);
        MovingObjectPosition shaftHit = shaft(x, y, z).calculateIntercept(start, end);
        MovingObjectPosition hit = footHit == null ? shaftHit : shaftHit == null ? footHit
            : start.squareDistanceTo(footHit.hitVec) <= start.squareDistanceTo(shaftHit.hitVec) ? footHit : shaftHit;
        return hit == null ? null : new MovingObjectPosition(x, y, z, hit.sideHit, hit.hitVec);
    }

    @Override public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB query, List boxes, Entity entity) {
        int meta = world.getBlockMetadata(x, y, z);
        if(meta >= 4) {
            super.addCollisionBoxesToList(world, x, y, z, query, boxes, entity);
            return;
        }
        // The tapered foot and narrow shaft, rather than an invisible full column.
        AxisAlignedBB foot = foot(x, y, z);
        AxisAlignedBB shaft = shaft(x, y, z);
        if(query.intersectsWith(foot)) boxes.add(foot);
        if(query.intersectsWith(shaft)) boxes.add(shaft);
    }

    @Override public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if(world.isRemote || removing || world.restoringBlockSnapshots) return;
        int meta = world.getBlockMetadata(x, y, z);
        int baseY = meta < 4 ? y : y - (meta - 3);
        if(meta < 4) {
            if(!world.isSideSolid(x, y - 1, z, ForgeDirection.UP)) world.func_147480_a(x, y, z, true);
        } else if(world.getBlock(x, baseY, z) != this || world.getBlockMetadata(x, baseY, z) >= 4) {
            world.setBlockToAir(x, y, z);
        }
    }

    @Override public void breakBlock(World world, int x, int y, int z, Block old, int meta) {
        if(!world.isRemote && !removing && !world.restoringBlockSnapshots) {
            int baseY = meta < 4 ? y : y - (meta - 3);
            // Every segment is in the same chunk. Break any segment for one normal item drop.
            if(meta < 4 || (world.getBlock(x, baseY, z) == this && world.getBlockMetadata(x, baseY, z) < 4)) {
                removing = true;
                try {
                    for(int level = 10; level >= 0; level--)
                        if(baseY + level != y && world.getBlock(x, baseY + level, z) == this
                            && (level == 0 ? world.getBlockMetadata(x, baseY, z) < 4 : world.getBlockMetadata(x, baseY + level, z) == level + 3))
                            world.setBlockToAir(x, baseY + level, z);
                } finally { removing = false; }
            }
        }
        super.breakBlock(world, x, y, z, old, meta);
    }
}
