package com.hfr.asm.earth;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

/** Only inserts the client pre-load safety gate; supplies no height/renderer/world engine patches. */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"com.hfr.asm.earth"})
public final class XFEarthCorePlugin implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass() { return new String[]{"com.hfr.asm.earth.XFEarthLaunchTransformer"}; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String,Object> data) { }
    @Override public String getAccessTransformerClass() { return null; }
}
