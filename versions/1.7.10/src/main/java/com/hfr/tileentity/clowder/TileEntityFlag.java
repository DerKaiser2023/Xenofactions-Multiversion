package com.hfr.tileentity.clowder;

import static net.minecraftforge.common.util.ForgeDirection.UP;

import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import com.hfr.clowder.Clowder;
import com.hfr.clowder.ClowderFlag;
import com.hfr.clowder.CityLevel;
import com.hfr.clowder.CityCenterRelocationManager;
import com.hfr.clowder.ClaimTerrainService;
import com.hfr.clowder.ClaimNodeSystem.Eligibility;
import com.hfr.config.XFConfig;
import com.hfr.data.ClowderData;
import com.hfr.clowder.ClowderTerritory;
import com.hfr.clowder.ClowderTerritory.CoordPair;
import com.hfr.clowder.ClowderTerritory.Ownership;
import com.hfr.clowder.ClowderTerritory.TerritoryMeta;
import com.hfr.clowder.ClowderTerritory.Zone;
import com.hfr.items.ModItems;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.tile.CityCenterPacket;
import com.hfr.main.MainRegistry;
import com.hfr.tileentity.machine.TileEntityMachineBase;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;

public class TileEntityFlag extends TileEntityMachineBase implements ITerritoryProvider {

	public Clowder owner;
	public boolean isClaimed = true;
	public float height = 1.0F;
	public float speed = 1F / (20F * 30F);
	public int mode = 0;
	public String name = "";
	public String ownerName = "";
	public CityLevel cityLevel = CityLevel.SETTLEMENT;
	private String cityId = "";

	public String getCityId() {
		if(cityId == null || cityId.isEmpty())
			cityId = ClowderTerritory.cityId(ClowderTerritory.getDimensionId(worldObj), xCoord, yCoord, zCoord);
		return cityId;
	}

	/** Returns persisted identity without synthesizing a coordinate-based legacy ID. */
	public String getStableCityId() { return cityId == null ? "" : cityId; }

	public void setCityId(String id) { cityId = id == null ? "" : id; markDirty(); }
	/** Relocation restore must not run setOwner's faction-accounting side effects. */
	public void restoreOwnerForRelocation(Clowder faction) {
		owner = faction;
		ownerName = faction == null ? "" : faction.name;
		markDirty();
	}
	public void createCityId() { cityId = UUID.randomUUID().toString(); markDirty(); }
	
	private int timer = 0;
	
	@SideOnly(Side.CLIENT)
	public ClowderFlag flag;
	@SideOnly(Side.CLIENT)
	public int color;
	@SideOnly(Side.CLIENT)
	public float prestige;
	@SideOnly(Side.CLIENT)
	public float prestigeReq;
	@SideOnly(Side.CLIENT)
	public float prestigeGen;
	public String customFlagHash = "";

	public TileEntityFlag() {
		super(0);
	}

	@Override
	public String getName() {
		return "container.flagPole";
	}

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord, 0, zCoord, xCoord + 1, 256, zCoord + 1);
	}

	@Override
	public void updateEntity() {


		
		if(!worldObj.isRemote) {
			
			//remove disbanded clowders
			if(owner != null && CityCenterRelocationManager.isOrphaned(this)) {
				MainRegistry.logger.info("Deleting clowder from flag " + xCoord + " " + yCoord + " " + zCoord + " due to clowder not being in the clowder list! (disband?)");
				owner = null;
			}
			
			/*if(Clowder.clowders.size() == 0) {
				ClowderData.getData(worldObj);
				return;
			}*/
			
			/// CAPTURE START ///

			float prev = height;
			Clowder prevC = owner;
			
			if(!isClaimed && canSeeSky()) {
				List<EntityPlayer> entities = worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getBoundingBox(xCoord - 4, yCoord - 1, zCoord - 4, xCoord + 5, yCoord + 2, zCoord + 5));
				
				Clowder capturer = null;
				for(EntityPlayer player : entities) {
					
					Clowder clow = Clowder.getClowderFromPlayer(player);
					
					if(clow != null) {
						capturer = clow;
						break;
					}
				}
				
				if(capturer != null) {
					
					//he who owns the flag now can raise it.
					//if the flag reaches the end of the pole, the ownership will be locked
					if(capturer == owner) {
						height += speed;
						
						if(height >= 1) {
							MainRegistry.logger.info("Locking flag " + xCoord + " " + yCoord + " " + zCoord + " for being hoisted! (captured and raised!)");
							isClaimed = true;
							height = 1;
							this.markDirty();
						}
						
					//he who does not own the flag can lower it
					//once it reaches the bottom, it will be his
					} else {
						
						height -= speed;
						
						if(height <= 0) {
							
							setOwner(capturer);
							height = 0;
							this.markDirty();
						}
					}
					
				//if there is nobody capturing the flag, it will simply descend
				} else {
					
					height -= speed;
					
					if(height <= 0) {
						height = 0;
					}
				}
			}
			
			if(!isClaimed || owner == null) {
				this.setMode(0);
				timer = 0;
				this.markDirty();
			} else {
				
				if(timer > 0)
					timer--;
				
				if(mode > 0) {
					
					if(timer <= 0) {
						
						if(consumeToken()) {
							timer = getTime();
						} else {
							this.setMode(0);
							timer = 0;
						}
						
						this.markDirty();
					}
				}
			}
			
			if(prev == 1F && height != 1F) {
				this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "hfr:block.flagCapture", 100.0F, 1.0F);

				MainRegistry.logger.info("Unlocking flag " + xCoord + " " + yCoord + " " + zCoord + " for being unhoisted! (captured!)");
				
				if(owner != null)
					owner.notifyCapture(worldObj, xCoord, zCoord, "City Center " + name);
			}
			
			if(prevC != owner)
				this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "hfr:block.flagChange", 3.0F, 1.0F);
			
			if(prev != 1F && height == 1F) {
				this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "hfr:block.flagHoist", 3.0F, 1.0F);
				MainRegistry.logger.info("Hoisintg flag " + xCoord + " " + yCoord + " " + zCoord + "!");
				generateClaim();
			}
			
			/// CAPTURE END ///
			
			if(!canSeeSky()) {
				
				if(isClaimed) {
					MainRegistry.logger.info("Unlocking flag " + xCoord + " " + yCoord + " " + zCoord + " for being obstructed!");
				}
				
				isClaimed = false;
				setOwner(null);
				
				if(height >= speed * 2)
					height -= speed * 2;
			} else if(owner != null) {
				//generateClaim();
			}
			
			ownerName = owner == null ? "" : owner.name;
			if(worldObj.getTotalWorldTime() % 20 == 0)
				{
					CityCenterPacket packet = new CityCenterPacket(xCoord, yCoord, zCoord, name, ownerName);
					packet.flagHash = owner == null ? "" : owner.customFlagHash;
					PacketDispatcher.wrapper.sendToAllAround(packet, new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 250));
				}

			syncPrestigeGauges();
			this.updateGauge(mode, 2, 25);
			this.updateGauge((int) (height * 100F), 3, 100);
			
		} else {

			if(mode > 0) {
				double x = xCoord + 0.5 + worldObj.rand.nextGaussian() * 0.25D;
				double y = yCoord + 0.125 + worldObj.rand.nextDouble() * 0.5D;
				double z = zCoord + 0.5 + worldObj.rand.nextGaussian() * 0.25D;

			    float r = Math.max(((color & 0xFF0000) >> 16) / 256F, 0.01F);
			    float g = Math.max(((color & 0xFF00) >> 8) / 256F, 0.01F);
			    float b = Math.max((color & 0xFF) / 256F, 0.01F);
				
				worldObj.spawnParticle("reddust", x, y, z, r, g, b);
			}
		}
	}
	
	public void syncPrestigeGauges() {
		if(owner != null) {
			this.updateGauge(owner.flag.ordinal(), 0, 250);
			this.updateGauge(owner.color, 1, 250);
			this.updateGauge(Float.floatToIntBits(owner.getPrestige()), 4, 20);
			this.updateGauge(Float.floatToIntBits(owner.getPrestigeReq()), 5, 20);
			this.updateGauge(Float.floatToIntBits(owner.getPrestigeGen()), 7, 20);
			this.updateGauge(cityLevel.ordinal(), 6, 20);
		} else {
			this.updateGauge(ClowderFlag.NONE.ordinal(), 0, 250);
			this.updateGauge(0xFFFFFF, 1, 250);
			this.updateGauge(0, 4, 20);
			this.updateGauge(0, 5, 20);
			this.updateGauge(0, 7, 20);
		}
	}

	public void processGauge(int val, int id) {
		
		switch(id) {
		case 0: flag = ClowderFlag.values()[val]; break;
		case 1: color = val; break;
		case 2: mode = val; break;
		case 3: height = val * 0.01F; break;
		case 4: prestige = Float.intBitsToFloat(val); break;
		case 5: prestigeReq = Float.intBitsToFloat(val); break;
		case 7: prestigeGen = Float.intBitsToFloat(val); break;
		case 6: cityLevel = CityLevel.byOrdinal(val); break;
		}
	}
	
	public float getGenRate() {
		return owner == null ? 0 : Clowder.flagRate();
	}
	
	public static float getGenRateFromMode(int mode) {
		
		if(mode != 0)
			return Clowder.flagRate();
		
		return 0;
	}
	
	public float getCost() {
		return XFConfig.cityUpkeep(cityLevel);
	}
	
	public static float getCostFromMode(int mode) {
		return XFConfig.cityUpkeep(CityLevel.SETTLEMENT);
	}
	
	public void setOwner(Clowder c) {
		
		if(owner == c)
			return;

		if(owner != c)
			MainRegistry.logger.info("Changing owner for flag " + xCoord + " " + yCoord + " " + zCoord + " from " + (owner != null ? owner.name : "null") + " to " + (c != null ? c.name : "null"));
		
		if(owner != null) {
			
			//more of a failsafe since in all natural cases, the mode is 0 during this happening
			owner.addPrestigeGen(-getGenRate(), worldObj);
			owner.addPrestigeReq(-getCost(), worldObj);
			owner.flags--;

			owner.multPrestige(0.95F, worldObj);
		}
		
		owner = c;

		if(owner != null) {
			owner.addPrestigeGen(getGenRate(), worldObj);
			owner.addPrestigeReq(getCost(), worldObj);
			owner.flags++;
		}
		
		this.markDirty();
	}
	
	public void setMode(int mode) {
		// City Centers always use the current city level for radius/upkeep.
		this.mode = owner == null ? 0 : 1;
		this.markDirty();
	}

	public boolean upgradeCity() {
		String error = getCityUpgradeError();
		if(error != null) { terrainClaimNotice = error; return false; }
		return commitCityExpansion(cityLevel.next(), true);
	}

	/** Checked before quoting and again when the repeated command commits. */
	public String getCityUpgradeError() {
		String error = getCityExpansionError();
		if(error != null) return error;
		CityLevel next = cityLevel.next();
		if(next == null) return name + " is already a Capital.";
		float cost = XFConfig.cityUpgradeCost(next);
		if(owner.getPrestige() < cost || Math.max(0F, owner.getPrestigeReq() - getCost()) + XFConfig.cityUpkeep(next) > owner.getPrestige() - cost)
			return "Upgrade requires " + cost + " prestige and sequential upkeep capacity.";
		return null;
	}

	private String getCityExpansionError() {
		if(worldObj == null || worldObj.isRemote || isInvalid() || owner == null || CityCenterRelocationManager.isOrphaned(this) || !isClaimed)
			return "This is not an active owned City Center.";
		if(!XFConfig.canClaimInDimension(worldObj.provider.dimensionId)) return "City claims are disabled in this dimension.";
		for(int cx = (xCoord - 2) >> 4; cx <= (xCoord + 2) >> 4; cx++)
			for(int cz = (zCoord - 2) >> 4; cz <= (zCoord + 2) >> 4; cz++)
				if(!worldObj.getChunkProvider().chunkExists(cx, cz)) return "City Center foundation chunks are unavailable; stay nearby and retry.";
		if(worldObj.getTileEntity(xCoord, yCoord, zCoord) != this || !canSeeSky())
			return "The City Center requires its solid, sky-accessible 5 by 5 foundation.";
		TerritoryMeta center = ClowderTerritory.getMetaFromIntCoords(worldObj, xCoord, zCoord);
		if(!ownsCityMeta(center)) return "The City Center no longer owns its center claim.";
		return null;
	}

	private boolean ownsCityMeta(TerritoryMeta meta) {
		return meta != null && meta.owner != null && meta.owner.zone == Zone.FACTION && meta.owner.owner == owner
			&& meta.dimensionId == worldObj.provider.dimensionId && getCityId().equals(meta.cityId)
			&& meta.flagX == xCoord && meta.flagY == yCoord && meta.flagZ == zCoord;
	}

	/** Retry the already paid level, using the same non-destructive expansion as an upgrade. */
	public boolean retryCityExpansion() {
		String error = getCityExpansionError();
		if(error != null) { terrainClaimNotice = error; return false; }
		return commitCityExpansion(cityLevel, false);
	}

	private final class CityExpansion {
		final Map<CoordPair, TerritoryMeta> previous = new HashMap<CoordPair, TerritoryMeta>();
		final Map<CoordPair, TerritoryMeta> replacement = new HashMap<CoordPair, TerritoryMeta>();
		final List<CoordPair> deferred = new ArrayList<CoordPair>();
		int granted, rejected, blocked;
		void stage(CoordPair key, TerritoryMeta old, CityLevel level) {
			TerritoryMeta meta = new TerritoryMeta(new Ownership(Zone.FACTION, owner), xCoord, yCoord, zCoord);
			meta.dimensionId = worldObj.provider.dimensionId; meta.cityId = getCityId();
			meta.name = name; meta.cityName = name; meta.cityLevel = level.ordinal();
			previous.put(key, old); replacement.put(key, meta);
		}
		String notice() {
			return "City expansion: " + granted + " nodes added; " + rejected + " rejected for insufficient connected land/platform; "
				+ deferred.size() + " deferred for terrain sampling; " + blocked + " occupied/protected nodes retained. "
				+ (rejected == 0 && deferred.isEmpty() && blocked == 0 ? "All nodes within the radius are owned by this city."
					: "The configured radius is a limit, not a fully granted area. Use /c city terrain retry to retry this level without another charge.");
		}
	}

	private CityExpansion prepareCityExpansion(CityLevel target) {
		CityExpansion expansion = new CityExpansion();
		// Update every existing node's level without pruning old territory or repairing foreign claims.
		for(Map.Entry<CoordPair, TerritoryMeta> entry : ClowderTerritory.territories.entrySet())
			if(ownsCityMeta(entry.getValue())) expansion.stage(entry.getKey(), entry.getValue(), target);
		for(CoordPair key : ClowderTerritory.getCityClaimCoordinates(worldObj.provider.dimensionId, xCoord, zCoord, XFConfig.cityRadius(target))) {
			TerritoryMeta old = ClowderTerritory.getMetaFromCoords(key);
			if(ownsCityMeta(old)) continue;
			if(old != null && (old.owner == null || old.owner.zone != Zone.WILDERNESS)) { expansion.blocked++; continue; }
			ClaimTerrainService.Decision decision = ClaimTerrainService.decision(worldObj, key);
			if(decision.eligibility == Eligibility.INELIGIBLE) { expansion.rejected++; continue; }
			if(decision.eligibility == Eligibility.UNCLASSIFIED) { expansion.deferred.add(key); continue; }
			expansion.stage(key, old, target); expansion.granted++;
		}
		return expansion;
	}

	private boolean commitCityExpansion(CityLevel target, boolean charge) {
		CityExpansion expansion = null;
		CityLevel previousLevel = cityLevel;
		try {
			// Terrain capture may be queued here, but no grant intent or city/financial mutation occurs yet.
			ClowderData data = ClowderData.getData(worldObj);
			expansion = prepareCityExpansion(target);
			ClowderTerritory.territories.putAll(expansion.replacement);
			cityLevel = target;
			markDirty(); data.markDirty();
			// Last fallible commit step. Accounting restores itself on failure.
			if(charge && !owner.chargeCityUpgrade(previousLevel, target, worldObj))
				throw new IllegalStateException("Prestige or upkeep capacity changed before the upgrade completed.");
		} catch(RuntimeException failure) {
			cityLevel = previousLevel;
			if(expansion != null) for(Map.Entry<CoordPair, TerritoryMeta> entry : expansion.previous.entrySet()) {
				if(entry.getValue() == null) ClowderTerritory.territories.remove(entry.getKey());
				else ClowderTerritory.territories.put(entry.getKey(), entry.getValue());
			}
			terrainClaimNotice = "City expansion could not be committed; see the server log.";
			MainRegistry.logger.error("City upgrade/expansion failed; previous level, claims and balances retained", failure);
			return false;
		}
		terrainClaimNotice = expansion.notice();
		com.hfr.dynmap.XFDynmapIntegration.markDirty();
		// Only a committed level may own deferred grants. Resuming never calls upgradeCity or charges prestige.
		try {
			for(CoordPair key : expansion.deferred)
				ClaimTerrainService.allowGrant(worldObj, key, owner, xCoord, yCoord, zCoord, name, getCityId());
		} catch(RuntimeException failure) {
			MainRegistry.logger.error("City level committed; deferred expansion scheduling interrupted", failure);
			terrainClaimNotice += " Deferred scheduling was interrupted; retry terrain expansion at this level.";
		}
		try {
			syncPrestigeGauges();
		} catch(RuntimeException failure) {
			// Normal tile ticks retry these gauges; a transport failure cannot undo a committed upgrade.
			MainRegistry.logger.error("City level committed; immediate client gauge refresh interrupted", failure);
		}
		return true;
	}
	
	private boolean consumeToken() {
		
		for(int i = 0; i < slots.length; i++) {
			
			if(slots[i] != null && slots[i].getItem() == ModItems.province_point) {
				this.decrStackSize(i, 1);
				return true;
			}
		}
		
		return true;
	}
	
	private int getTime() {
		
		switch(mode) {
		case 1: return 200;
		case 2: return 500;
		case 3: return 1000;
		default: return 0;
		}
	}
	
	@Override
	public int getRadius() {
		
		return owner == null ? 0 : XFConfig.cityRadius(cityLevel);
	}

	@Override
	public Clowder getOwner() {
		return owner;
	}
	
	public void generateClaim() {
        int terrainRejected=0,terrainDeferred=0;
		
		int rad = Math.min(getRadius(), CityLevel.maxRadius());
		String placementError = ClowderTerritory.getCityPlacementError(worldObj, xCoord >> 4, zCoord >> 4);
		if(placementError != null && ClowderTerritory.getMetaFromIntCoords(worldObj, xCoord, zCoord) == null)
			return;
		
		java.util.Set<CoordPair> expected = ClowderTerritory.getCityClaimCoordinates(worldObj.provider.dimensionId, xCoord, zCoord, rad);
		for(int x = -CityLevel.maxRadius(); x <= CityLevel.maxRadius(); x++) {
			for(int z = -CityLevel.maxRadius(); z <= CityLevel.maxRadius(); z++) {
				CoordPair loc = ClowderTerritory.getCoordPair(worldObj, xCoord + x * 16, zCoord + z * 16);
				TerritoryMeta meta = ClowderTerritory.getMetaFromCoords(loc);
				if(expected.contains(loc)) {
					if(meta == null || !meta.checkPersistence(worldObj, loc) || (meta.flagX == xCoord && meta.flagY == yCoord && meta.flagZ == zCoord)) {
                        if(!com.hfr.clowder.ClaimTerrainService.existingClaim(loc)&&owner!=null){
                            com.hfr.clowder.ClaimTerrainService.Decision terrain=com.hfr.clowder.ClaimTerrainService.decision(worldObj,loc);
                            if(terrain.eligibility==com.hfr.clowder.ClaimNodeSystem.Eligibility.INELIGIBLE)terrainRejected++;
                            else if(terrain.eligibility==com.hfr.clowder.ClaimNodeSystem.Eligibility.UNCLASSIFIED)terrainDeferred++;
                        }
						ClowderTerritory.setOwnerForCoord(worldObj, loc, owner, xCoord, yCoord, zCoord, name, getCityId());
						TerritoryMeta newMeta = ClowderTerritory.getMetaFromCoords(loc);
						if(newMeta != null) {
							newMeta.cityLevel = cityLevel.ordinal();
							newMeta.cityName = name;
						}
					}
				} else if(meta != null && meta.flagX == xCoord && meta.flagY == yCoord && meta.flagZ == zCoord) {
					ClowderTerritory.removeZoneForCoord(worldObj, loc);
				}
			}
		}

        terrainClaimNotice=terrainRejected==0&&terrainDeferred==0?"":"City expansion: "+terrainRejected+" nodes rejected for insufficient connected land/platform; "+terrainDeferred+" deferred for bounded terrain sampling. Existing claims retained. Use /c city terrain to review or retry.";
	}

    public String terrainClaimNotice="";
	
	public boolean bordersWilderness() {
		
		//no longer needed
		return true;

		/*int rad = getRadius();
		
		for(int x = -rad; x <= rad; x++) {
			for(int z = -CityLevel.maxRadius(); z <= CityLevel.maxRadius(); z++) {
				
				double dist = Math.sqrt(Math.pow(x, 2) + Math.pow(z, 2));
				
				if(dist < rad && dist > rad - 1) {
					
					if(checkBorder(xCoord + x * 16, zCoord + z * 16))
						return true;
				}
			}
		}
		
		return false;*/
	}
	
	public boolean checkBorder(int x, int z) {

		CoordPair loc = ClowderTerritory.getCoordPair(worldObj, x, z);
		Ownership owner = ClowderTerritory.getOwnerFromCoords(loc);
		if(owner.zone != Zone.FACTION || owner.owner != this.owner)
			return false;
		
		CoordPair loc1 = ClowderTerritory.getCoordPair(worldObj, x + 16, z);
		Ownership owner1 = ClowderTerritory.getOwnerFromCoords(loc1);
		if(owner1.zone == Zone.WILDERNESS || owner1.owner != this.owner)
			return true;
		
		CoordPair loc2 = ClowderTerritory.getCoordPair(worldObj, x - 16, z);
		Ownership owner2 = ClowderTerritory.getOwnerFromCoords(loc2);
		if(owner2.zone == Zone.WILDERNESS || owner2.owner != this.owner)
			return true;
		
		CoordPair loc3 = ClowderTerritory.getCoordPair(worldObj, x, z + 16);
		Ownership owner3 = ClowderTerritory.getOwnerFromCoords(loc3);
		if(owner3.zone == Zone.WILDERNESS || owner3.owner != this.owner)
			return true;
		
		CoordPair loc4 = ClowderTerritory.getCoordPair(worldObj, x, z - 16);
		Ownership owner4 = ClowderTerritory.getOwnerFromCoords(loc4);
		if(owner4.zone == Zone.WILDERNESS || owner4.owner != this.owner)
			return true;
		
		return false;
	}
	
	public boolean canSeeSky() {

		for(int i = -2; i <= 2; i++)
			for(int j = -2; j <= 2; j++)
				
				if(!worldObj.canBlockSeeTheSky(xCoord + i, yCoord + 1, zCoord + j) ||
					!worldObj.getBlock(xCoord + i, yCoord - 1, zCoord + j).isSideSolid(worldObj, xCoord + i, yCoord - 1, zCoord + j, UP))
					return false;
		
		if(yCoord < 45)
			return false;
		if(yCoord > 200) //upped to 200, 100 was retarded
			return false;
		
		return true;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return true;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int p_94128_1_) {
		return new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		NBTTagList list = nbt.getTagList("items", 10);

		String own = nbt.getString("owner");
		this.owner = Clowder.getClowderFromName(own);
		boolean isNull = nbt.getBoolean("isNull");
		
		if(owner == null) {

			String id = nbt.getString("clow_uuid");
			this.owner = Clowder.getClowderFromUUID(id);
			
			if(!isNull) {
				
				if(owner == null) {
					MainRegistry.logger.info("Owner (" + id + ") of flag " + xCoord + " " + yCoord + " " + zCoord + " was saved NN but finalized as null!");
				}
			}
			
			if(owner == null && !id.isEmpty())
				MainRegistry.logger.info("Owner (" + id + ") of flag " + xCoord + " " + yCoord + " " + zCoord + " was set in NBT but not found in te clowder list!");
		}
		
		this.isClaimed = nbt.getBoolean("isClaimed");
		this.height = nbt.getFloat("height");
		this.mode = nbt.getInteger("mode");
		this.timer = nbt.getInteger("timer");
		this.name = nbt.getString("name");
		this.ownerName = owner == null ? "" : owner.name;
		this.cityLevel = CityLevel.byOrdinal(nbt.getInteger("cityLevel"));
		this.cityId = nbt.getString("cityId");
		
		slots = new ItemStack[getSizeInventory()];
		
		for(int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound nbt1 = list.getCompoundTagAt(i);
			byte b0 = nbt1.getByte("slot");
			if(b0 >= 0 && b0 < slots.length)
			{
				slots[b0] = ItemStack.loadItemStackFromNBT(nbt1);
			}
		}
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		if(owner != null) {
			nbt.setBoolean("isNull", false);
			nbt.setString("clow_uuid", owner.uuid);
		} else {
			nbt.setBoolean("isNull", true);
		}
		
		nbt.setBoolean("isClaimed", isClaimed);
		nbt.setFloat("height", height);
		nbt.setInteger("mode", mode);
		nbt.setInteger("timer", timer);
		nbt.setString("name", name);
		nbt.setInteger("cityLevel", cityLevel.ordinal());
		nbt.setString("cityId", getCityId());
		
		NBTTagList list = new NBTTagList();
		
		for(int i = 0; i < slots.length; i++)
		{
			if(slots[i] != null)
			{
				NBTTagCompound nbt1 = new NBTTagCompound();
				nbt1.setByte("slot", (byte)i);
				slots[i].writeToNBT(nbt1);
				list.appendTag(nbt1);
			}
		}
		nbt.setTag("items", list);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public String getClaimName() {
		return name;
	}

	@Override
	public void setClaimName(String name) {
		this.name = name;
	}
}
