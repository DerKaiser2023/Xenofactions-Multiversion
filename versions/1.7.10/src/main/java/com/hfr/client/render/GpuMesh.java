package com.hfr.client.render;

import java.nio.FloatBuffer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Owns one packed position/UV/normal buffer, or a retained display list on non-VBO hardware. */
final class GpuMesh {
    private int buffer, list;
    private final boolean arb;
    // OBJ UVs are ordinary 2D coordinates. Keep their prepared data for shader
    // integrations that supply extra attributes through Forge's Tessellator hooks.
    private final float[] shaderVertices;
    GpuMesh(float[] vertices,boolean shaderFallback) {
        shaderVertices=shaderFallback?vertices:null;
        ContextCapabilities caps = GLContext.getCapabilities();
        arb = !caps.OpenGL15 && caps.GL_ARB_vertex_buffer_object;
        if(caps.OpenGL15 || arb) {
            int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            try {
                buffer = arb ? ARBVertexBufferObject.glGenBuffersARB() : GL15.glGenBuffers();
                if(buffer == 0) throw new IllegalStateException("No retained buffer allocated");
                bind(buffer);
                FloatBuffer data = BufferUtils.createFloatBuffer(vertices.length); data.put(vertices).flip();
                if(arb) ARBVertexBufferObject.glBufferDataARB(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
                else GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
                int bytes=arb?ARBVertexBufferObject.glGetBufferParameteriARB(GL15.GL_ARRAY_BUFFER,GL15.GL_BUFFER_SIZE)
                    :GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER,GL15.GL_BUFFER_SIZE);
                if(bytes != (long)vertices.length*4) throw new IllegalStateException("Incomplete retained buffer upload");
            } catch(RuntimeException failure) {
                release();
                com.hfr.main.MainRegistry.logger.warn("Retained XF VBO upload failed; using display list",failure);
            }
            finally { bind(previous); }
        }
        if(buffer == 0) {
            list = GL11.glGenLists(1);
            if(list == 0) throw new IllegalStateException("No retained geometry allocation available");
            GL11.glNewList(list, GL11.GL_COMPILE);
            GL11.glBegin(GL11.GL_TRIANGLES);
            for(int i=0;i<vertices.length;i+=10) {
                GL11.glNormal3f(vertices[i+7], vertices[i+8], vertices[i+9]);
                GL11.glTexCoord4f(vertices[i+3],vertices[i+4],vertices[i+5],vertices[i+6]);
                GL11.glVertex3f(vertices[i],vertices[i+1],vertices[i+2]);
            }
            GL11.glEnd(); GL11.glEndList();
        }
        if(RenderMetrics.ENABLED) RenderMetrics.uploads++;
    }
    private void bind(int id) {
        if(arb) ARBVertexBufferObject.glBindBufferARB(GL15.GL_ARRAY_BUFFER,id);
        else GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,id);
    }
    void draw(int first, int count) {
        if(count == 0) return;
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        try {
        if(shaderVertices != null && GLContext.getCapabilities().OpenGL20
                && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0) {
            Tessellator tessellator=Tessellator.instance;
            tessellator.startDrawing(GL11.GL_TRIANGLES);
            for(int i=first*10;i<(first+count)*10;i+=10) {
                tessellator.setNormal(shaderVertices[i+7],shaderVertices[i+8],shaderVertices[i+9]);
                tessellator.addVertexWithUV(shaderVertices[i],shaderVertices[i+1],shaderVertices[i+2],
                    shaderVertices[i+3],shaderVertices[i+4]);
            }
            tessellator.draw();
        }
        else if(list != 0) { GL11.glCallList(list); } // Fallback meshes are created per logical part.
        else if(buffer != 0) {
            int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            try {
                bind(buffer);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
                GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
                GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
                GL11.glVertexPointer(3,GL11.GL_FLOAT,40,0L);
                GL11.glTexCoordPointer(4,GL11.GL_FLOAT,40,12L);
                GL11.glNormalPointer(GL11.GL_FLOAT,40,28L);
                GL11.glDrawArrays(GL11.GL_TRIANGLES,first,count);
            } finally { GL11.glPopClientAttrib(); bind(previous); }
        }
        } finally { GL11.glPopAttrib(); }
        if(RenderMetrics.ENABLED) RenderMetrics.draws++;
    }
    void release() {
        if(buffer != 0) {
            if(arb) ARBVertexBufferObject.glDeleteBuffersARB(buffer); else GL15.glDeleteBuffers(buffer);
            buffer=0; if(RenderMetrics.ENABLED) RenderMetrics.deletes++;
        }
        if(list != 0) { GL11.glDeleteLists(list,1); list=0; if(RenderMetrics.ENABLED) RenderMetrics.deletes++; }
    }
}
