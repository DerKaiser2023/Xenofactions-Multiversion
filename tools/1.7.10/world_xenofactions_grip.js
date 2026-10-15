// script.name=XenoFactions Earth with offline GRIP roads
// script.description=Run the external Earth generator with roads before saving a new project. WorldPainter 2.27.0.
// script.param.sourceDirectory.type=string
// script.param.sourceDirectory.displayName=MinecraftEarthMapXF checkout directory
// script.param.roadDirectory.type=string
// script.param.roadDirectory.displayName=Prepared roads directory
// script.param.profile.type=string
// script.param.profile.default=smoke
// script.param.material.type=string
// script.param.material.displayName=Explicit material (gravel or cobblestone)
// script.param.exportDirectory.type=string
// script.param.exportDirectory.displayName=Optional NEW export staging directory (outside player saves)
// script.param.preflightOnly.type=boolean
// script.param.preflightOnly.default=false
var integrationDir = new java.io.File(scriptDir);
function option(name, fallback) {
    if (typeof params !== "undefined" && params.containsKey(name)) return String(params.get(name));
    return fallback;
}
var checkout = new java.io.File(option("sourceDirectory", typeof sourceDirectory === "undefined" ? "" : String(sourceDirectory)));
var gripDirectory = new java.io.File(option("roadDirectory", typeof roadDirectory === "undefined" ? "" : String(roadDirectory)));
var gripMaterial = option("material", typeof material === "undefined" ? "" : String(material));
var gripProfile = option("profile", typeof profile === "undefined" ? "smoke" : String(profile));
var onlyPreflight = option("preflightOnly", typeof preflightOnly === "undefined" ? "false" : String(preflightOnly)) === "true";
var exportPath = option("exportDirectory", typeof exportDirectory === "undefined" ? "" : String(exportDirectory));
var gripExport = exportPath ? new java.io.File(exportPath) : null;
if (gripExport && gripExport.exists()) throw new Error("Export staging directory exists; select a new directory outside player saves");
if (!checkout.isDirectory() || !new java.io.File(gripDirectory,"roads.json").isFile()) throw new Error("Select the source checkout and prepared road directory");
if (gripMaterial !== "gravel" && gripMaterial !== "cobblestone") throw new Error("Choose gravel or cobblestone explicitly; no verified XF asphalt export mapping is available");
load(new java.io.File(integrationDir,"worldpainter_grip_roads.js").toURI().toURL());
var coreFile = new java.io.File(checkout,"world_xenofactions_core.js");
var core = String(new java.lang.String(java.nio.file.Files.readAllBytes(coreFile.toPath()),"UTF-8"));
// The original core remains byte-identical so its terrain fingerprint stays valid.
// Fail closed if the inspected save boundary changes; never run an unhooked generator.
var saveBoundary = /wp\.saveWorld\(\s*world\s*\)/g;
var boundaries = core.match(saveBoundary);
if (!boundaries || boundaries.length !== 1 || core.indexOf("var manifest = {") < 0) throw new Error("Unsupported external core: need its single wp.saveWorld(world) after the full profile manifest");
core = core.replace(saveBoundary,
    'outputName = outputName.replace(/\\.world$/, "_grip.world");\n'
    + 'if (file("generated/" + outputName).exists()) throw new Error("GRIP output already exists; preserve it or move it aside");\n'
    + 'XFRoads.apply(world, manifest, gripDirectory, gripMaterial, api.vegetationLayers, [api.riverLayer]);\n'
    + 'XFRoads.saving(world, gripExport)');
// All external assets and manifest fingerprints still resolve in their original checkout.
scriptDir = checkout.getAbsolutePath();
eval(core);
var canonicalProfile = PROFILE_ALIASES[gripProfile] || gripProfile;
var chosen = PROFILES[canonicalProfile];
if (!onlyPreflight && chosen && new java.io.File(checkout,"generated/earth_1-" + chosen.effectiveScale
        + "_xenofactions_1.7.10_" + chosen.name + "_grip.world").exists()) throw new Error("GRIP output already exists; preserve it or move it aside");
runXenoEarth(gripProfile, onlyPreflight);
