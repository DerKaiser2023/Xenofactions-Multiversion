package com.hfr.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

public final class FoundationBlock extends Block {
    private final double speed;
    public FoundationBlock(double speed, float resistance) { super(Properties.of(Material.STONE).strength(5, resistance)); this.speed = speed; }
    @Override public void stepOn(World world, BlockPos pos, Entity entity) {
        if (entity instanceof PlayerEntity) { Vector3d motion = entity.getDeltaMovement(); double angle = Math.atan2(motion.x, motion.z); entity.setDeltaMovement(motion.add(Math.sin(angle) * speed, 0, Math.cos(angle) * speed)); }
        super.stepOn(world, pos, entity);
    }
}
