package com.ccs.voxelgun.render;

import android.opengl.GLES30;

/** Off-screen render target with one or more colour attachments and an optional depth texture. */
public final class Framebuffer {
    private final int[] handle = new int[1];
    private final int[] colorTextures;
    private final int[] drawBuffers;
    private int depthTexture;
    private boolean ownsDepth;
    private int width;
    private int height;

    /** How many colour attachments the last {@link #resize} actually allocated. */
    private int activeAttachments;

    public Framebuffer(int attachments) {
        colorTextures = new int[attachments];
        drawBuffers = new int[attachments];
        for (int i = 0; i < attachments; i++) {
            drawBuffers[i] = GLES30.GL_COLOR_ATTACHMENT0 + i;
        }
        GLES30.glGenFramebuffers(1, handle, 0);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int colorTexture(int index) {
        return colorTextures[index];
    }

    public int depthTexture() {
        return depthTexture;
    }

    public int handle() {
        return handle[0];
    }

    /**
     * (Re)allocates the attachments.
     *
     * @param internalFormats one GL sized internal format per colour attachment
     * @param filter          GL_NEAREST or GL_LINEAR for every colour attachment
     * @param withDepth       allocate and own a depth texture
     */
    public void resize(int width, int height, int[] internalFormats, int filter, boolean withDepth) {
        // Callers may ask for fewer attachments than the buffer can hold — the G-buffer drops its
        // motion vectors when nothing reads them — so the active count follows the format list.
        if (this.width == width && this.height == height && colorTextures[0] != 0
                && activeAttachments == internalFormats.length) {
            return;
        }
        activeAttachments = Math.min(internalFormats.length, colorTextures.length);
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        release(false);

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, handle[0]);
        for (int i = 0; i < activeAttachments; i++) {
            int[] texture = new int[1];
            GLES30.glGenTextures(1, texture, 0);
            colorTextures[i] = texture[0];
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTextures[i]);
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, internalFormats[i], this.width,
                    this.height);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,
                    GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T,
                    GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0 + i,
                    GLES30.GL_TEXTURE_2D, colorTextures[i], 0);
        }

        if (withDepth) {
            int[] texture = new int[1];
            GLES30.glGenTextures(1, texture, 0);
            depthTexture = texture[0];
            ownsDepth = true;
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTexture);
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_DEPTH_COMPONENT24, this.width,
                    this.height);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,
                    GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T,
                    GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
                    GLES30.GL_TEXTURE_2D, depthTexture, 0);
        }

        if (activeAttachments > 0) {
            GLES30.glDrawBuffers(activeAttachments, drawBuffers, 0);
        }
        int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete framebuffer: 0x" + Integer.toHexString(status));
        }
    }

    /** Reuses a depth texture owned by another framebuffer (shared depth for forward passes). */
    public void attachExternalDepth(int texture) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, handle[0]);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
                GLES30.GL_TEXTURE_2D, texture, 0);
        depthTexture = texture;
        ownsDepth = false;
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    public void bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, handle[0]);
        GLES30.glViewport(0, 0, width, height);
        if (activeAttachments > 0) {
            GLES30.glDrawBuffers(activeAttachments, drawBuffers, 0);
        }
    }

    public static void bindDefault(int width, int height) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, width, height);
    }

    public void release(boolean deleteFramebuffer) {
        for (int i = 0; i < colorTextures.length; i++) {
            if (colorTextures[i] != 0) {
                GLES30.glDeleteTextures(1, new int[]{colorTextures[i]}, 0);
                colorTextures[i] = 0;
            }
        }
        if (depthTexture != 0 && ownsDepth) {
            GLES30.glDeleteTextures(1, new int[]{depthTexture}, 0);
        }
        depthTexture = 0;
        ownsDepth = false;
        if (deleteFramebuffer) {
            GLES30.glDeleteFramebuffers(1, handle, 0);
        }
    }
}
