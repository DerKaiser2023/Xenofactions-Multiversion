package com.hfr.main;

import com.hfr.lib.RefStrings;
import com.hfr.client.render.GpuModel;
import com.hfr.client.render.GpuModelManager;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

public class ResourceManager {

	public static final int id_palisade = 10002;
	public static final int id_wall = 10003;
	public static final int id_berliner = 10004;

	public static final int id_sandbags = 10006;

	public static final GpuModel devon = GpuModelManager.obj("models/truck_busted.obj");
	public static final ResourceLocation devon_tex = new ResourceLocation(RefStrings.MODID, "textures/models/truck_busted.png");

	public static final GpuModel net = GpuModelManager.obj("models/net.obj");
	public static final ResourceLocation net_tex = new ResourceLocation(RefStrings.MODID, "textures/models/net.png");
	public static final ResourceLocation net_jammed_tex = new ResourceLocation(RefStrings.MODID, "textures/models/net_jammed.png");

	public static final GpuModel uni = GpuModelManager.obj("models/uni.obj");
	public static final ResourceLocation uni_tex = new ResourceLocation(RefStrings.MODID, "textures/models/uni.png");

	public static final GpuModel temple = GpuModelManager.obj("models/props/temple.obj",true);
	public static final ResourceLocation temple_tex = new ResourceLocation(RefStrings.MODID, "textures/models/temple.png");

	public static final GpuModel factory = GpuModelManager.obj("models/factory.obj",true);
	public static final ResourceLocation factory_tex = new ResourceLocation(RefStrings.MODID, "textures/models/factory.png");

	public static final GpuModel flag = GpuModelManager.obj("models/flag.obj",true);
	public static final ResourceLocation flag_tex = new ResourceLocation(RefStrings.MODID, "textures/models/flag.png");
	public static final ResourceLocation flag_conq_tex = new ResourceLocation(RefStrings.MODID, "textures/models/flag_conq.png");

	public static final GpuModel cap = GpuModelManager.obj("models/cap.obj",true);
	public static final ResourceLocation cap_tex = new ResourceLocation(RefStrings.MODID, "textures/models/cap.png");
	public static final ResourceLocation cap_point_tex = new ResourceLocation(RefStrings.MODID, "textures/models/cap_point.png");

	public static final GpuModel flag_big = GpuModelManager.obj("models/flag_big.obj",true);

	public static final GpuModel statue = GpuModelManager.obj("models/ModelStatue.obj");

    public static final GpuModel mill = GpuModelManager.create(com.hfr.client.render.GpuTmtModels::mill);
    public static final GpuModel hesco = GpuModelManager.create(com.hfr.client.render.GpuTmtModels::hesco);
    public static final GpuModel tent = GpuModelManager.create(com.hfr.client.render.GpuTmtModels::tent);

	public static final ResourceLocation tmt_hescoblock = new ResourceLocation(RefStrings.MODID, "textures/models/tmt/Skin_Block_hescoblock.png");

	public static final ResourceLocation tmt_tptent = new ResourceLocation(RefStrings.MODID, "textures/models/tmt/Skin_Block_tptent.png");
	public static final ResourceLocation tmt_statue = new ResourceLocation(RefStrings.MODID, "textures/models/tmt/SkinSamothrace.png");
	public static final ResourceLocation tmt_mill = new ResourceLocation(RefStrings.MODID, "textures/models/tmt/Skin_Grainmill.png");
	public static volatile IModelCustom palisade = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/palisade.obj"));
	public static volatile IModelCustom wall = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/wall.obj"));
	public static volatile IModelCustom berliner = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/berliner.obj"));

	public static volatile IModelCustom sandbags = AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID, "models/props/sandbags.obj"));
    public static void reloadBlockModels() {
        palisade=reloadBlock("palisade",palisade);wall=reloadBlock("wall",wall);
        berliner=reloadBlock("berliner",berliner);sandbags=reloadBlock("sandbags",sandbags);
        if(Minecraft.getMinecraft().renderGlobal!=null)
            Minecraft.getMinecraft().renderGlobal.loadRenderers();
    }
    private static IModelCustom reloadBlock(String name,IModelCustom previous) {
        try {return AdvancedModelLoader.loadModel(new ResourceLocation(RefStrings.MODID,"models/props/"+name+".obj"));}
        catch(RuntimeException failure){MainRegistry.logger.error("Could not reload chunk model "+name+"; retaining previous model",failure);return previous;}
    }
}
