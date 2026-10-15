package com.hfr.blocks.clowder;

import com.hfr.blocks.ModBlocks;
import com.hfr.clowder.CityLevel;
import com.hfr.clowder.CityCenterRelocationManager;
import com.hfr.config.XFConfig;
import com.hfr.clowder.Clowder;
import com.hfr.clowder.ClowderTerritory;
import com.hfr.clowder.ClowderTerritory.CoordPair;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.client.CityCenterGuiPacket;
import com.hfr.tileentity.clowder.TileEntityFlag;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class Flag extends BlockContainer {

	public Flag(Material p_i45386_1_) {
		super(p_i45386_1_);
	}

	@Override
	public TileEntity createNewTileEntity(World p_149915_1_, int p_149915_2_) {
		return new TileEntityFlag();
	}

	@Override
	public int getRenderType(){
		return -1;
	}

	@Override
	public boolean isOpaqueCube() {
		return false;
	}

	@Override
	public boolean renderAsNormalBlock() {
		return false;
	}

	@Override
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
		if(world.isRemote)
			return true;

		if(world.getBlock(x, y, z) != ModBlocks.clowder_flag || player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 64.0D)
			return false;

		TileEntity tile = world.getTileEntity(x, y, z);
		if(!(tile instanceof TileEntityFlag))
			return false;

		TileEntityFlag flag = (TileEntityFlag)tile;
		if(flag.owner == null || !flag.canSeeSky()) {
			world.func_147480_a(x, y, z, false);
			return true;
		}

		if(!player.isSneaking() && player instanceof net.minecraft.entity.player.EntityPlayerMP) {
			PacketDispatcher.wrapper.sendTo(new CityCenterGuiPacket(world.provider.dimensionId, x, y, z), (net.minecraft.entity.player.EntityPlayerMP)player);
			return true;
		}

		return true;
	}

	@Override
	public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack itemStack) {

		int i = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;

		if(i == 0)
		{
			world.setBlockMetadataWithNotify(x, y, z, 2, 2);
		}
		if(i == 1)
		{
			world.setBlockMetadataWithNotify(x, y, z, 5, 2);
		}
		if(i == 2)
		{
			world.setBlockMetadataWithNotify(x, y, z, 3, 2);
		}
		if(i == 3)
		{
			world.setBlockMetadataWithNotify(x, y, z, 4, 2);
		}

        // The item commits founding after Forge accepts the provisional block.

		super.onBlockPlacedBy(world, x, y, z, player, itemStack);
	}

    /** Checked before placement and after all Forge placement listeners, before any faction mutation. */
    public static String foundingError(World world, int x, int y, int z, EntityPlayer player, ItemStack stack) {
        Clowder faction=Clowder.getClowderFromPlayer(player);
        if(faction==null || !Clowder.clowders.contains(faction))
            return "City Centers require a faction. Use /c create first, then /c claim <city name>.";
        // Founding has always required membership, without a leader/officer rank restriction.
        if(!XFConfig.canClaimInDimension(world.provider.dimensionId))return "City claims are disabled in this dimension.";
        String name=stack.hasTagCompound()?stack.stackTagCompound.getString("cityName").trim():"";
        if(name.isEmpty())return "City Centers require a name. Use /c claim <city name>.";
        if(!ClowderTerritory.isCityNameAvailable(name,null))return "A city named "+name+" already exists.";
        CoordPair center=ClowderTerritory.getCoordPair(world,x,z);
        String spacing=ClowderTerritory.getCityPlacementError(world,center.x,center.z);
        if(spacing!=null)return spacing;
        for(CoordPair key:ClowderTerritory.getCityClaimCoordinates(world.provider.dimensionId,x,z,XFConfig.cityRadius(CityLevel.SETTLEMENT))) {
            ClowderTerritory.TerritoryMeta meta=ClowderTerritory.territories.get(key);
            if(meta!=null)return "The City Center radius intersects existing territory or a protected zone.";
        }
        if(!faction.activeWars.isEmpty())return "A City Center cannot be founded while your faction is at war.";
        if(CityCenterRelocationManager.hasPending(faction))return "Complete or cancel your faction's pending City Center relocation first.";
        boolean first=faction.getOwnedCityCount()==0;
        float cost=first?0F:faction.getCityFoundingCost(),upkeep=XFConfig.cityUpkeep(CityLevel.SETTLEMENT);
        if(faction.getPrestige()<cost || (!first && faction.getPrestigeReq()+upkeep>faction.getPrestige()-cost))
            return "Founding a City Center requires "+cost+" prestige and "+upkeep+" upkeep capacity.";
        if(y<45 || y>200)return "City Centers must be between Y 45 and Y 200.";
        for(int cx=(x-2)>>4;cx<=(x+2)>>4;cx++)for(int cz=(z-2)>>4;cz<=(z+2)>>4;cz++)
            if(!world.getChunkProvider().chunkExists(cx,cz))return "Required foundation chunks are not loaded; stay nearby and retry.";
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
            if(!world.canBlockSeeTheSky(x+dx,y+1,z+dz) || !world.getBlock(x+dx,y-1,z+dz).isSideSolid(world,x+dx,y-1,z+dz,net.minecraftforge.common.util.ForgeDirection.UP))
                return "The City Center requires a solid, sky-accessible 5 by 5 foundation.";
        com.hfr.clowder.ClaimTerrainService.Decision terrain=com.hfr.clowder.ClaimTerrainService.grantDecision(world,center);
        return terrain.eligibility==com.hfr.clowder.ClaimNodeSystem.Eligibility.ELIGIBLE?null:terrain.rejection();
    }

    /** Called synchronously after placement approval and foundingError; the item owns block rollback. */
    public static void foundCity(World world, int x, int y, int z, EntityPlayer player, ItemStack stack, TileEntityFlag flag) {
        Clowder faction=Clowder.getClowderFromPlayer(player);
        float cost=faction.getOwnedCityCount()==0?0F:faction.getCityFoundingCost();
        float prestige=faction.getPrestige(),generation=faction.getPrestigeGen(),upkeep=faction.getPrestigeReq();
        int flags=faction.flags,founded=faction.citiesFounded;
        String capital=faction.capitalCityId;boolean designated=faction.capitalDesignated;
        flag.name=stack.stackTagCompound.getString("cityName").trim();flag.createCityId();
        try {
            // Resolve the center and all available eligible neighbors before charging founding costs.
            flag.restoreOwnerForRelocation(faction);flag.setMode(1);flag.isClaimed=true;
            java.util.Set<CoordPair> claims=ClowderTerritory.rebuildCityClaims(world,ClowderTerritory.territories,faction,flag.getCityId(),
                world.provider.dimensionId,x,y,z,flag.name,flag.cityLevel.ordinal(),flag.getRadius());
            flag.restoreOwnerForRelocation(null);flag.setOwner(faction);
            faction.addPrestige(-cost,world);faction.markCityFounded(world);
            if(!faction.capitalDesignated){faction.capitalCityId=flag.getCityId();faction.capitalDesignated=true;}
            faction.save(world);flag.markDirty();com.hfr.dynmap.XFDynmapIntegration.markDirty();
            com.hfr.clowder.ClaimTerrainService.invalidate(world,x,z);world.markBlockForUpdate(x,y,z);
            int excluded=ClowderTerritory.getCityClaimCoordinates(world.provider.dimensionId,x,z,flag.getRadius()).size()-claims.size();
            if(excluded>0)player.addChatMessage(new ChatComponentText("City expansion: "+excluded+" nodes lack confirmed loaded land/platform surface."));
        } catch(RuntimeException failure) {
            // Avoid setOwner(null), which applies the intentional city-loss prestige penalty.
            flag.restoreOwnerForRelocation(null);flag.isClaimed=false;
            ClowderTerritory.removeClaimsForCityId(world,flag.getCityId(),false);
            faction.flags=flags;faction.citiesFounded=founded;faction.capitalCityId=capital;faction.capitalDesignated=designated;
            faction.addPrestige(prestige-faction.getPrestige(),world);
            faction.addPrestigeGen(generation-faction.getPrestigeGen(),world);
            faction.addPrestigeReq(upkeep-faction.getPrestigeReq(),world);
            throw failure;
        }
    }

	public boolean noProximity(World world, int x, int y, int z) {

		int range = 4;

		for(int ix = x - range; ix <= x + range; ix++) {
			for(int iy = y - 3; iy <= y + 3; iy++) {
				for(int iz = z - range; iz <= z + range; iz++) {

					if(ix == x && iy == y && iz == z)
						continue;

					if(world.getBlock(ix, iy, iz) == ModBlocks.clowder_conquerer) {
						return false;
					}
				}
			}
		}

		return true;
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block b, int i)
	{
		if(CityCenterRelocationManager.isGuardedRemoval(world, x, y, z)) {
			super.breakBlock(world, x, y, z, b, i);
			return;
		}
		TileEntity tile = world.getTileEntity(x, y, z);

		if(tile instanceof TileEntityFlag) {
			TileEntityFlag flag = (TileEntityFlag)tile;
			if(flag.owner != null && flag.getCityId().equals(flag.owner.relocationCityId))
				CityCenterRelocationManager.clear(flag.owner, world);

			if(flag.owner != null)
				flag.setOwner(null);

			flag.height = 0.0F;
			ClowderTerritory.removeClaimsForCity(world, x, y, z);
		}

		super.breakBlock(world, x, y, z, b, i);
    }

}
