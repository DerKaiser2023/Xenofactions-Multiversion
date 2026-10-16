package com.hfr.main;

import com.hfr.lib.RefStrings;
import com.hfr.render.loader.BufferedObjModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

public class ResourceManager {

	public static final int id_palisade = 10002;
	public static final int id_wall = 10003;
	public static final int id_berliner = 10004;

	public static final int id_sandbags = 10006;

	public static final IModelCustom devon = new BufferedObjModel(new ResourceLocation(RefStrings.MODID, "models/truck_busted.obj"), false);
	public static final ResourceLocation devon_tex = new ResourceLocation(RefStrings.MODID, "textures/models/truck_busted.png");

	public static final IModelCustom net = new BufferedObjModel(new ResourceLocation(RefStrings.MODID, "models/net.obj"), false);
	public static final ResourceLocation net_tex = new ResourceLocation(RefStrings.MODID, "textures/models/net.png");
	public static final ResourceLocation net_jammed_tex = new ResourceLocation(RefStrings.MODID, "textures/models/net_jammed.png");

	public static final IModelCustom uni = new BufferedObjModel(new ResourceLocation(RefStrings.MODID, "models/uni.obj"), false);
	public static final ResourceLocation uni_tex = new ResourceLocation(RefStrings.MODID, "textures/models/uni.png");

	public static final IModelCustom temple = new BufferedObjModel(new ResourceLocation(RefStrings.MODID, "models/props/temple.obj"), true);
	public static final ResourceLocation temple_tex = new ResourceLocation(RefStrings.MODID, "textures/models/temple.png");

	public static final IModelCustom factory = new BufferedObjModel(new ResourceLocation(RefStrings.MODID, "models/factory.obj"), true);
	public static final ResourceLocation factory_tex = new ResourceLocation(RefStrings.MODID, "textures/models/factory.png");

	public static final IModelCustom flag = new BufferedObjModel(new ResourceLocation(RefStrings.MODID, "models/flag.obj"), true);
	public static final ResourceLocation flag_tex = new ResourceLocation(RefStrings.MODID, "textures/models/flag.png");
	public static final ResourceLocation flag_conq_tex = new ResourceLocation(RefStrings.MODID, "textures/models/flag_conq.png");

	public static final IModelCustom cap = new BufferedObjModel(new ResourceLocation(RefStrings.MODID, "models/cap.obj"), true);
	public static final ResourceLocation cap_tex = new ResourceLocation(RefStrings.MODID, "textures/models/cap.png");
	public static final ResourceLocation cap_point_tex = new ResourceLocation(RefStrings.MODID, "textures/models/cap_point.png");

	public static final IModelCustom flag_big = new BufferedObjModel(new ResourceLocation(RefStrings.MODID, "models/flag_big.obj"), true);

	public static final IModelCustom statue = new BufferedObjModel(new ResourceLocation(RefStrings.MODID, "models/ModelStatue.obj"), false);

	public static final ResourceLocation tmt_hescoblock = new ResourceLocation(RefStrings.MODID, "textures/models/tmt/Skin_Block_hescoblock.png");

	public static final ResourceLocation tmt_tptent = new ResourceLocation(RefStrings.MODID, "textures/models/tmt/Skin_Block_tptent.png");
	public static final ResourceLocation tmt_statue = new ResourceLocation(RefStrings.MODID, "textures/models/tmt/SkinSamothrace.png");
	public static final ResourceLocation tmt_mill = new ResourceLocation(RefStrings.MODID, "textures/models/tmt/Skin_Grainmill.png");
	public static volatile IModelCustom palisade = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/palisade.obj"));
	public static volatile IModelCustom wall = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/wall.obj"));
	public static volatile IModelCustom berliner = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/berliner.obj"));

	public static volatile IModelCustom sandbags = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/sandbags.obj"));
    /** Registered once by ClientProxy; block models retain their actual Forge type. */
    public static void registerReloadListener() {
        ((IReloadableResourceManager)Minecraft.getMinecraft()
                .getResourceManager()).registerReloadListener(new IResourceManagerReloadListener() {
            @Override public void onResourceManagerReload(IResourceManager manager) {
                for (IModelCustom model : new IModelCustom[] { devon, net, uni, temple, factory, flag, cap, flag_big, statue })
                    ((BufferedObjModel)model).reload();
                palisade = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/palisade.obj"));
                wall = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/wall.obj"));
                berliner = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/berliner.obj"));
                sandbags = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/sandbags.obj"));
            }
        });
    }
}
