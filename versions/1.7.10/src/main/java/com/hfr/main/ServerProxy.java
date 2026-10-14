package com.hfr.main;

import java.util.List;

import com.hfr.clowder.ClaimOverlayData.Claim;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import com.hfr.tileentity.TileEntityWallImage;

public class ServerProxy
{
	public void registerRenderInfo() { }
	
	public void registerTileEntitySpecialRenderer() { }
	
	public static final int SFX_BORDER = 1;
	public void spawnSFX(World world, double posX, double posY, double posZ, int type, Object payload) { }
	
	public void updateFlag(ResourceLocation flag, ResourceLocation overlay, int color, String name, String title) { }



	public void effectNT(NBTTagCompound nbt) { }
	public void receiveClaimOverlay(int dimension, int generation, int part, int parts, List<Claim> claims) { }
	public void openWallArtGui(TileEntityWallImage tile) { }
	public void openEarthMap() { }
	public void receiveEarthMap(int request, int dimension, String profile, String identity, String error) { }
	public void acceptWallArtChunk(String hash, int index, int total, int fullLength, byte[] bytes) { }

	public void spawnEFX(double posX, double posY, double posZ, float pow) { }
}
