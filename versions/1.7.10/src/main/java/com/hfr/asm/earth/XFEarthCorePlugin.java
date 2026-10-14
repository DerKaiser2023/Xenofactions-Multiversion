package com.hfr.asm.earth;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

/** Pre-load safety plus optional native subscription/transport hooks; no replacement height engine. */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"com.hfr.asm.earth"})
public final class XFEarthCorePlugin implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass() { return new String[]{"com.hfr.asm.earth.XFEarthLaunchTransformer","com.hfr.asm.earth.XFEarthNetworkTransformer"}; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String,Object> data) { }
    @Override public String getAccessTransformerClass() { return null; }
}
