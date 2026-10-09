# Bundled Earth resources

The current 1.7.10 reference already tracks `earth-16k-clean.xfmap` and `earth-16k-populated.xfmap` under `versions/1.7.10/src/main/resources/assets/hfr/earthmaps/bundled/`. The multiversion target packages these existing resources in its normal remapped production JAR.

Build with `versions/1.7.10/gradlew.bat build`, or the root `build1_7_10` task. Output follows the repository convention `versions/1.7.10/build/libs/hfr-<version>+mc1.7.10-forge.jar`. Check both `.xfmap` resources in that production archive.

The original repository's separate `jarWithEarthMaps`, `includeBundledEarthMaps`, `earthCleanMap` and `earthPopulatedMap` build conveniences are not present here. They are unnecessary to package the currently tracked packs. To replace a pack, author and validate the real archive with `tools/1.7.10/build_xfmap.py`, update the target resource deliberately and use the normal production build. The runtime repository reads and verifies those archives directly; no generated index is required for these tracked resources.

A packaged archive does not prove its terrain, required content mods or Minecraft world are usable. Validate the manifest, required mods, profile and imported map in Minecraft. 1.16.5 does not implement this Earth world/pack pipeline.
