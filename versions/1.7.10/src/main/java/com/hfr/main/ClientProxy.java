 package com.hfr.main;

import com.hfr.render.RenderWallImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityBreakingFX;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFireworkSparkFX;
import net.minecraft.client.particle.EntityFlameFX;
import net.minecraft.client.particle.EntityLavaFX;
import net.minecraft.client.particle.EntityReddustFX;
import net.minecraft.client.particle.EntitySmokeFX;
import net.minecraft.client.renderer.entity.RenderSnowball;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.AdvancedModelLoader;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.hfr.handler.ExplosionSound;
import com.hfr.items.ModItems;
import com.hfr.render.block.*;
import com.hfr.render.hud.*;
import com.hfr.render.living.*;
import com.hfr.render.mob.*;
import com.hfr.render.mob.ModelSheep.ModelSheepWooly;
import com.hfr.render.tileentity.*;
import com.hfr.tileentity.*;
import com.hfr.tileentity.clowder.*;
import com.hfr.tileentity.machine.*;
import com.hfr.tileentity.prop.*;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class ClientProxy extends ServerProxy
{
	@Override public void openEarthMap() { Minecraft.getMinecraft().displayGuiScreen(new com.hfr.client.earth.GuiXFEarthMap()); }
	@Override public void receiveEarthMap(final int request, final int dimension, final String profile, final String identity, final String error) {
		final Minecraft mc = Minecraft.getMinecraft();
		final World receivingWorld = mc.theWorld;
		mc.func_152344_a(new Runnable() { @Override public void run() {
			if (receivingWorld != null && mc.theWorld == receivingWorld)
				com.hfr.client.journeymap.XFEarthJourneyMapImport.receive(request, dimension, profile, identity, error);
			if (receivingWorld != null && mc.theWorld == receivingWorld && mc.currentScreen instanceof com.hfr.client.earth.GuiXFEarthMap)
				((com.hfr.client.earth.GuiXFEarthMap)mc.currentScreen).receive(request, dimension, profile, identity, error);
		} });
	}
	@Override public void openWallArtGui(TileEntityWallImage tile) { Minecraft.getMinecraft().displayGuiScreen(new com.hfr.inventory.gui.GuiWallArt(tile)); }
	@Override public void acceptWallArtChunk(String hash, int index, int total, int fullLength, byte[] bytes) { com.hfr.client.wallart.WallArtTextureCache.accept(hash,index,total,fullLength,bytes); }
	@Override
	public void receiveClaimOverlay(final int dimension, final int generation, final int part, final int parts, final List<com.hfr.clowder.ClaimOverlayData.Claim> claims) {
		Minecraft.getMinecraft().func_152344_a(new Runnable() { @Override public void run() {
			com.hfr.client.journeymap.ClientClaimOverlayCache.accept(dimension, generation, part, parts, claims);
		} });
	}

	public static KeyBinding filter = new KeyBinding("Toggle chat filter", 62, "xRadar");

	public static KeyBinding flushLog = new KeyBinding("Flush extended debugging log", 0, "xRadar");
	public static KeyBinding tdmMenu = new KeyBinding("key.hfr.tdmmenu", Keyboard.KEY_L, "key.categories.hfr.xenofactions");
	public static KeyBinding tdmBuyMenu = new KeyBinding("key.hfr.tdmbuy", Keyboard.KEY_B, "key.categories.hfr.xenofactions");
	public static KeyBinding xenofactionsMenu = new KeyBinding("key.hfr.xfmenu", Keyboard.KEY_K, "key.categories.hfr.xenofactions");
	public static KeyBinding adminEditor = new KeyBinding("key.hfr.admineditor", Keyboard.KEY_F10, "key.categories.hfr.xenofactions");
	public static KeyBinding borderVisuals = new KeyBinding("key.hfr.bordervisuals", Keyboard.KEY_RMENU, "key.categories.hfr.xenofactions");
	
	@Override
	public void registerRenderInfo()
	{
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.hfr.client.earth.XFEarthCreateWorldHandler());
		new EventHandlerClient().register();
		com.hfr.client.earth.EarthMapTileCache.registerReloadListener();
		com.hfr.client.journeymap.XFJourneyMapIntegration.register();
		com.hfr.client.journeymap.XFEarthJourneyMapImport.register();
		
		RenderingRegistry.addNewArmourRendererPrefix("5");

		RenderingRegistry.registerBlockHandler(new RenderPalisade());
		RenderingRegistry.registerBlockHandler(new RenderWall());
		RenderingRegistry.registerBlockHandler(new RenderBerliner());

		RenderingRegistry.registerBlockHandler(new RenderSandbags());

		RenderingRegistry.registerBlockHandler(new RenderWallArtBlock());

		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityWallImage.class, new RenderWallImage());

		ClientRegistry.registerKeyBinding(filter);

		ClientRegistry.registerKeyBinding(flushLog);
		ClientRegistry.registerKeyBinding(tdmMenu);
		ClientRegistry.registerKeyBinding(tdmBuyMenu);
		ClientRegistry.registerKeyBinding(xenofactionsMenu);
		ClientRegistry.registerKeyBinding(adminEditor);
		ClientRegistry.registerKeyBinding(borderVisuals);
		
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDebug.class, new RenderDebug());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMachineNet.class, new RenderNet());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMachineUni.class, new RenderUni());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityFlag.class, new RenderFlag());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityCap.class, new RenderCap());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityFlagBig.class, new RenderFlagBig());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityProp.class, new RenderProp());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityStatue.class, new RenderProp());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMachineGrainmill.class, new RenderMill());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMachineFactory.class, new RenderFactory());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityMachineTemple.class, new RenderTemple());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityConquerer.class, new RenderConquerer());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityOfficerChest.class, new RenderChest());

		RenderingRegistry.registerEntityRenderingHandler(EntityCow.class, new RenderCow(new ModelBuffalo(), 1.0F));
		RenderingRegistry.registerEntityRenderingHandler(EntityPig.class, new RenderPig(new ModelPig(), 1.0F));
		RenderingRegistry.registerEntityRenderingHandler(EntitySheep.class, new RenderSheep(new ModelSheep(), new ModelSheepWooly(), 1.0F));
		RenderingRegistry.registerEntityRenderingHandler(EntityChicken.class, new RenderChicken(new ModelChicken(), 0.5F));
		//todo villager -> human

	}
	
	@Override
	public void registerTileEntitySpecialRenderer() { }
	
	//please fucking end my life i'm begging you
	//everything is pain
	//make it stop
	
	//sfx stands for special effects, not sound effects
	@Override
	public void spawnSFX(World world, double posX, double posY, double posZ, int type, Object payload) {
		
		int part = Minecraft.getMinecraft().gameSettings.particleSetting;

		switch(type) {
			
		/// CLOWDER BORDER ///
		case SFX_BORDER:
			if(!com.hfr.client.journeymap.ClientBorderVisuals.enabled()) return;
			if(!(payload instanceof int[]))
				return;
			
			int[] pl = (int[]) payload;
			
			if(pl.length != 5)
				return;

			int x1 = pl[0];
			int z1 = pl[1];
			int x2 = pl[2];
			int z2 = pl[3];
			int color = pl[4];

		    /*float r = Math.max(((color & 0xFF0000) >> 16) / 256F, 0.01F);
		    float g = Math.max(((color & 0xFF00) >> 8) / 256F, 0.01F);
		    float b = Math.max((color & 0xFF) / 256F, 0.01F);*/
			
			/*int a = 5;
			
			if(part == 1)
				a = 2;
			
			if(part == 2)
				a = 1;*/
			
			int a = Math.max(1, Math.min(16,
					(int)Math.ceil(Math.max(Math.abs((long)x2 - x1), Math.abs((long)z2 - z1)) / 6D)));
			
			for(int i = 0; i < a; i++) {
				double along = (i + world.rand.nextDouble()) / a;
				double xs = x1 + ((long)x2 - x1) * along;
				double zs = z1 + ((long)z2 - z1) * along;
				double ys = world.getHeightValue((int)Math.floor(xs), (int)Math.floor(zs - 0.01D)) + 0.25D;
				
				/*EntityReddustFX fx = new EntityReddustFX(world, xs, ys, zs, r, g, b);
				fx.motionY = 0.1;
				Minecraft.getMinecraft().effectRenderer.addEffect(fx);*/
				
				EntityFireworkSparkFX blast = new EntityFireworkSparkFX(world, xs, ys, zs, 0.0, 0.4 * world.rand.nextGaussian() + 0.6, 0.0, Minecraft.getMinecraft().effectRenderer);
				blast.setColour(color);
				Minecraft.getMinecraft().effectRenderer.addEffect(blast);
			}
			
			break;
			
			/// CUMMIES ///
			case 3:
				
				EntityBreakingFX cum = new EntityBreakingFX(world, posX, posY, posZ, Items.snowball);
				cum.motionX = ((double[])payload)[0];
				cum.motionY = ((double[])payload)[1];
				cum.motionZ = ((double[])payload)[2];
				Minecraft.getMinecraft().effectRenderer.addEffect(cum);
				break;
			
		default: break;
		
		}
	}
	
	@Override
	public void updateFlag(ResourceLocation flag, ResourceLocation overlay, int color, String name, String title) {
		RenderFlagOverlay.flag = flag;
		RenderFlagOverlay.overlay = overlay;
		RenderFlagOverlay.color = color;
		RenderFlagOverlay.tintFlag = true;
		RenderFlagOverlay.customFlagName = "";
		RenderFlagOverlay.title = name;
		RenderFlagOverlay.zone = title;
		RenderFlagOverlay.startingTime = System.currentTimeMillis();
	}
	




	public void effectNT(NBTTagCompound nbt) {
		
		String type = nbt.getString("type");
		World world = Minecraft.getMinecraft().theWorld;
		
		if(type.equals("clowderNotif")) {
			
			EventHandlerClient.lookup.put(nbt.getString("player"), nbt.getString("clowder"));
			return;
		}
		
		 if(type.equals("explosion")) {
			
			try {
				float strength = nbt.getFloat("strength");
				double x = nbt.getDouble("posX");
				double y = nbt.getDouble("posY") + 0.5;
				double z = nbt.getDouble("posZ");
				
				for(int i = 0; i < strength * 10; i++) {
					
					EntityFlameFX flames = new EntityFlameFX(world, x, y, z,
							world.rand.nextGaussian() * 0.1 * strength,
							world.rand.nextGaussian() * 0.1 * strength,
							world.rand.nextGaussian() * 0.1 * strength);
					
					ReflectionHelper.setPrivateValue(EntityFX.class, flames, 100, "particleMaxAge", "field_70547_e");
					
					Minecraft.getMinecraft().effectRenderer.addEffect(flames);
				}
				
				for(int i = 0; i < strength * 25; i++) {
					
					EntityLavaFX flames = new EntityLavaFX(world, x, y, z);
	
					ReflectionHelper.setPrivateValue(EntityFX.class, flames, 200, "particleMaxAge", "field_70547_e");
					ReflectionHelper.setPrivateValue(Entity.class, flames, world.rand.nextGaussian() * 0.1 * strength, "motionX", "field_70159_w");
					ReflectionHelper.setPrivateValue(Entity.class, flames, world.rand.nextDouble() * 0.25 * strength, "motionY", "field_70181_x");
					ReflectionHelper.setPrivateValue(Entity.class, flames, world.rand.nextGaussian() * 0.1 * strength, "motionZ", "field_70179_y");
					
					Minecraft.getMinecraft().effectRenderer.addEffect(flames);
				}
				
				for(int i = 0; i < strength * 50; i++) {
					
					EntityCloudFX flames = new EntityCloudFX(world, x, y, z,
							world.rand.nextGaussian() * 0.025 * strength,
							world.rand.nextGaussian() * 0.025 * strength,
							world.rand.nextGaussian() * 0.025 * strength);
	
					ReflectionHelper.setPrivateValue(EntityFX.class, flames, 100, "particleMaxAge", "field_70547_e");
					ReflectionHelper.setPrivateValue(EntityFX.class, flames, 10, "particleScale", "field_70544_f");
					ReflectionHelper.setPrivateValue(EntityCloudFX.class, flames, 10, "field_70569_a");
					
					Minecraft.getMinecraft().effectRenderer.addEffect(flames);
				}
			} catch(Exception ex) {}
		}
	}

	public void spawnEFX(double posX, double posY, double posZ, float pow) {
		ExplosionSound.handleClient(Minecraft.getMinecraft().thePlayer, (int)posX, (int)posY, (int)posZ, pow);
}
}
