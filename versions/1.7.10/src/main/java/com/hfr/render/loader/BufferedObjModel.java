package com.hfr.render.loader;

import com.hfr.main.MainRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.client.model.obj.Face;
import net.minecraftforge.client.model.obj.GroupObject;
import net.minecraftforge.client.model.obj.WavefrontObject;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBVertexBufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/** Direct draws only. Never pass this facade to a chunk/caller-owned Tessellator batch. */
@SideOnly(Side.CLIENT)
public final class BufferedObjModel implements IModelCustom {
    private static final String[] ALL = new String[0];
    private final ResourceLocation resource;
    private final boolean custom;
    private IModelCustom source;
    private final List<Part> parts = new ArrayList<Part>();
    private boolean attempted;
    private boolean ready;
    private boolean core;

    public BufferedObjModel(ResourceLocation resource, boolean custom) {
        this.resource = resource;
        this.custom = custom;
    }

    private static void requireRenderThread() {
        if (!Minecraft.getMinecraft().func_152345_ab())
            throw new IllegalStateException("OBJ buffers require the Minecraft GL thread");
        // LWJGL also verifies that this thread owns a current context.
        GLContext.getCapabilities();
    }

    /** Drop the entire old generation on the GL thread; parse/upload lazily on the next draw. */
    public void reload() {
        requireRenderThread();
        release();
        source = null;
        attempted = false;
    }

    private void release() {
        for (Part part : parts) if (part.buffer != 0) delete(part.buffer);
        parts.clear();
        ready = false;
    }

    private boolean prepare() {
        requireRenderThread();
        if (source == null)
            source = custom ? new HFRWavefrontObject(resource) : AdvancedModelLoader.loadModel(resource);
        ContextCapabilities caps = GLContext.getCapabilities();
        // Shader integrations may require attributes supplied by their Tessellator hooks.
        if (caps.OpenGL20 && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0) return false;
        if (attempted) return ready;
        attempted = true;
        core = caps.OpenGL15;
        if (!core && !caps.GL_ARB_vertex_buffer_object) return false;
        int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            if (source.getClass() == HFRWavefrontObject.class) {
                for (S_GroupObject group : ((HFRWavefrontObject)source).groupObjects) {
                    if (group == null) throw new IllegalArgumentException("Null OBJ group");
                    int count = 0;
                    for (S_Face face : group.faces) if (face.vertices.length == 3) count += 3;
                    Capture capture = new Capture(count);
                    // Preserve S_Face's triangle-only emission, UVs and per-vertex normal rules.
                    group.render(capture);
                    upload(group.name, group.glDrawingMode, capture);
                }
            } else if (source.getClass() == WavefrontObject.class) {
                for (GroupObject group : ((WavefrontObject)source).groupObjects) {
                    if (group == null) throw new IllegalArgumentException("Null OBJ group");
                    int count = 0;
                    for (Face face : group.faces) count += face.vertices.length;
                    Capture capture = new Capture(count);
                    // Forge uses face normals and a 0.0005 UV inset, unlike S_Face.
                    group.render(capture);
                    upload(group.name, group.glDrawingMode, capture);
                }
            } else {
                return false; // Preserve any other loader's own rendering/caching implementation.
            }
            // renderAll historically uses the final group's primitive for the entire batch.
            // Mixed primitives/UV presence need that original batch's semantics; do not reinterpret them.
            if (!parts.isEmpty()) {
                Part last = parts.get(parts.size() - 1);
                for (Part part : parts) if (part.count != 0 &&
                        (part.mode != last.mode || part.textured != last.textured))
                    throw new IllegalArgumentException("Mixed OBJ batch attributes");
            }
            ready = true;
        } catch (RuntimeException failure) {
            release();
            if (MainRegistry.logger != null)
                MainRegistry.logger.warn("OBJ VBO fallback for " + resource + ": " + failure.getMessage());
        } finally {
            bind(previous);
        }
        return ready;
    }

    private void upload(String name, int mode, Capture capture) {
        int primitive = mode == GL11.GL_TRIANGLES ? 3 : mode == GL11.GL_QUADS ? 4 : 0;
        if (capture.count != 0 && (primitive == 0 || capture.count % primitive != 0 || capture.mixedTexture))
            throw new IllegalArgumentException("Unsupported OBJ batch");
        Part part = new Part(name, mode, capture.count, capture.textured);
        parts.add(part); // Own the ID even if upload fails.
        if (capture.count == 0) return;
        capture.data.flip();
        part.buffer = core ? GL15.glGenBuffers() : ARBVertexBufferObject.glGenBuffersARB();
        if (part.buffer == 0) throw new IllegalStateException("No OBJ buffer allocated");
        bind(part.buffer);
        if (core) GL15.glBufferData(GL15.GL_ARRAY_BUFFER, capture.data, GL15.GL_STATIC_DRAW);
        else ARBVertexBufferObject.glBufferDataARB(GL15.GL_ARRAY_BUFFER, capture.data, GL15.GL_STATIC_DRAW);
        long bytes = core ? GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE)
                : ARBVertexBufferObject.glGetBufferParameteriARB(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE);
        if (bytes != capture.data.remaining()) throw new IllegalStateException("Incomplete OBJ upload");
    }

    private void bind(int id) {
        if (core) GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
        else ARBVertexBufferObject.glBindBufferARB(GL15.GL_ARRAY_BUFFER, id);
    }

    private void delete(int id) {
        if (core) GL15.glDeleteBuffers(id);
        else ARBVertexBufferObject.glDeleteBuffersARB(id);
    }

    private void draw(int selection, String[] names) {
        if (!prepare()) {
            switch (selection) {
                case 0: source.renderAll(); break;
                case 1: source.renderOnly(names); break;
                default: source.renderAllExcept(names); break;
            }
            return;
        }
        int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        // Saves array enables, pointers and their buffer bindings on every texture unit,
        // plus client active texture. LWJGL's tracker follows push/popClientAttrib too.
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            for (Part part : parts) {
                int matches = 0;
                for (String name : names) if (name.equalsIgnoreCase(part.name)) matches++;
                int repeats = selection == 0 ? 1 : selection == 1 ? matches : matches == 0 ? 1 : 0;
                if (part.count == 0 || repeats == 0) continue;
                bind(part.buffer);
                GL11.glVertexPointer(3, GL11.GL_FLOAT, 24, 0L);
                GL11.glNormalPointer(GL11.GL_BYTE, 24, 20L);
                if (part.textured) {
                    GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 24, 12L);
                } else GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                for (int i = 0; i < repeats; i++) GL11.glDrawArrays(part.mode, 0, part.count);
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopClientAttrib();
            bind(previous);
        }
    }

    @Override public String getType() { return "obj"; }
    @Override public void renderAll() { draw(0, ALL); }
    @Override public void renderOnly(String... names) { draw(1, names); }
    @Override public void renderPart(String name) { renderOnly(name); }
    @Override public void renderAllExcept(String... names) { draw(2, names); }

    private static final class Part {
        final String name;
        final int mode, count;
        final boolean textured;
        int buffer;
        Part(String name, int mode, int count, boolean textured) {
            this.name = name; this.mode = mode; this.count = count; this.textured = textured;
        }
    }

    /** Runs the authoritative face emitters once without starting/drawing the shared Tessellator. */
    private static final class Capture extends Tessellator {
        final ByteBuffer data;
        int count;
        byte nx, ny, nz;
        double u, v;
        boolean textured, mixedTexture;
        Capture(int vertices) { data = BufferUtils.createByteBuffer(Math.multiplyExact(vertices, 24)); }
        @Override public void setNormal(float x, float y, float z) {
            nx = (byte)(int)(x * 127F); ny = (byte)(int)(y * 127F); nz = (byte)(int)(z * 127F);
        }
        @Override public void setTextureUV(double u, double v) { this.u = u; this.v = v; }
        @Override public void addVertexWithUV(double x, double y, double z, double u, double v) {
            if (count != 0 && !textured) mixedTexture = true;
            textured = true;
            setTextureUV(u, v);
            put(x, y, z);
        }
        @Override public void addVertex(double x, double y, double z) {
            if (textured) mixedTexture = true;
            put(x, y, z);
        }
        private void put(double x, double y, double z) {
            data.putFloat((float)x).putFloat((float)y).putFloat((float)z);
            data.putFloat((float)u).putFloat((float)v);
            data.put(nx).put(ny).put(nz).put((byte)0);
            count++;
        }
    }
}
