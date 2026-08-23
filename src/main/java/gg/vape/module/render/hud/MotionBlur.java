package gg.vape.module.render.hud;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventFramePresent;
import gg.vape.event.impl.EventPostRenderTick;
import gg.vape.module.none.ClientSettings;
import gg.vape.value.BooleanValue;
import gg.vape.value.NumberValue;
import gg.vape.wrapper.impl.ForgeVersion;
import gg.vape.wrapper.impl.Minecraft;
import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

 
public class MotionBlur
extends HudModule {
    private static final int MODULE_COLOR = new Color(52, 120, 246).getRGB();
    private final NumberValue blurrinessValue;
    private final BooleanValue velocityAdaptiveValue;
    private final BooleanValue smoothBlurValue;
    private final BooleanValue fpsModulateValue;
    private final BooleanValue clearColorValue;
    private final BooleanValue applyOnMenuValue;
    private final BooleanValue applyOnGameMenuValue;
    private boolean initialized;
    private int shaderProgram;
    private int currentTexture;
    private int historyTexture;
    private int quadVao;
    private int quadVbo;
    private int textureWidth;
    private int textureHeight;
    private boolean first;
    private float velocityFactor;
    private float curBlurriness;
    private long lastFrameNanos;
    private float lastViewX;
    private float lastViewY;

    private static final String VERTEX_SHADER = "#version 330 core\n"
            + "layout (location = 0) in vec2 aPos;\n"
            + "layout (location = 1) in vec2 aTexCoord;\n"
            + "out vec2 TexCoord;\n"
            + "void main()\n"
            + "{\n"
            + "    gl_Position = vec4(aPos, 0.0, 1.0);\n"
            + "    TexCoord = aTexCoord;\n"
            + "}\n";

    private static final String FRAGMENT_SHADER = "#version 330 core\n"
            + "out vec4 FragColor;\n"
            + "in vec2 TexCoord;\n"
            + "uniform sampler2D currentTexture;\n"
            + "uniform sampler2D historyTexture;\n"
            + "uniform float blurriness;\n"
            + "uniform float velocity_factor;\n"
            + "uniform bool renderRGB;\n"
            + "uniform bool smooth_blur;\n"
            + "vec4 blurHistory(vec2 uv)\n"
            + "{\n"
            + "    float offset = 0.0006 * velocity_factor;\n"
            + "    vec4 sum = vec4(0.0);\n"
            + "    sum += texture(historyTexture, uv + vec2(-offset, 0.0)) * 0.25;\n"
            + "    sum += texture(historyTexture, uv + vec2( offset, 0.0)) * 0.25;\n"
            + "    sum += texture(historyTexture, uv + vec2(0.0, -offset)) * 0.25;\n"
            + "    sum += texture(historyTexture, uv + vec2(0.0,  offset)) * 0.25;\n"
            + "    return sum;\n"
            + "}\n"
            + "void main()\n"
            + "{\n"
            + "    vec4 current = texture(currentTexture, TexCoord);\n"
            + "    vec4 history = texture(historyTexture, TexCoord);\n"
            + "    float cur_blurriness = blurriness;\n"
            + "    vec4 blurredHistory = history;\n"
            + "    if (velocity_factor > 0.0) {\n"
            + "        float base_blurriness = blurriness * 0.5;\n"
            + "        cur_blurriness = base_blurriness + velocity_factor * base_blurriness;\n"
            + "        if (smooth_blur) {\n"
            + "            blurredHistory = blurHistory(TexCoord);\n"
            + "        }\n"
            + "    }\n"
            + "    vec4 blurredColor = mix(current, blurredHistory, cur_blurriness);\n"
            + "    if (renderRGB) {\n"
            + "        FragColor = blurredColor;\n"
            + "    } else {\n"
            + "        float value1 = current.r;\n"
            + "        FragColor = mix(vec4(value1), blurredHistory, cur_blurriness);\n"
            + "    }\n"
            + "}\n";

    public MotionBlur() {
        super("MotionBlur", HudModuleGroup.GAME, "motion_blur", MODULE_COLOR);
        this.blurrinessValue = NumberValue.create((Object)this,
                "Blurriness", "#.#", "", 0.0, 5.0, 10.0, 0.1,
                "How strong the motion blur trail is");
        this.velocityAdaptiveValue = BooleanValue.create(this,
                "Velocity Adaptive", true,
                "Scales the blur strength with camera movement speed");
        this.smoothBlurValue = BooleanValue.create(this,
                "Smooth Blur", false,
                "Smooths the blur trail (may blur the game UI too)");
        this.fpsModulateValue = BooleanValue.create(this,
                "FPS Modulate", true,
                "Reduces blur strength at low frame rates");
        this.clearColorValue = BooleanValue.create(this,
                "Clear Color", false,
                "Renders a faded grayscale trail instead of RGB");
        this.applyOnMenuValue = BooleanValue.create(this,
                "Apply On Menu", true,
                "Keep applying the blur while the Vape GUI is open");
        this.applyOnGameMenuValue = BooleanValue.create(this,
                "Apply On Game Menu", true,
                "Keep applying the blur while a game menu (pause/inventory/chat) is open");
        this.addValue(this.blurrinessValue, this.velocityAdaptiveValue,
                this.smoothBlurValue, this.fpsModulateValue,
                this.clearColorValue, this.applyOnMenuValue,
                this.applyOnGameMenuValue);
        this.velocityFactor = 0.0f;
        this.curBlurriness = 5.0f;
    }

    @Override
    public void onEnable() {
        this.first = true;
        this.velocityFactor = 0.0f;
        this.curBlurriness = this.blurrinessValue.getValue().floatValue();
        this.lastFrameNanos = 0L;
    }

    @Override
    public void onDisable() {
        this.destroyResources();
    }

    @EventHandler
    public void onPostRenderTick(EventPostRenderTick event) {
        if (!this.isEnabled() || ForgeVersion.MC_26_2.d()) {
            return;
        }
        if (ForgeVersion.MC_1_20_1.d()) {
            

            

            

            return;
        }
        try {
            this.renderMotionBlur();
        }
        catch (Throwable throwable) {
            

            Vape.logThrowable(throwable);
            this.destroyResources();
            this.setEnabled(false);
        }
    }

    @EventHandler
    public void onFramePresent(EventFramePresent event) {
        if (!this.isEnabled() || ForgeVersion.MC_26_2.d()) {
            return;
        }
        if (!ForgeVersion.MC_1_20_1.d()) {
            

            return;
        }
        try {
            this.renderMotionBlur();
        }
        catch (Throwable throwable) {
            

            Vape.logThrowable(throwable);
            this.destroyResources();
            this.setEnabled(false);
        }
    }

    private void renderMotionBlur() {
        if (!this.applyOnMenuValue.getEffectiveValue().booleanValue()
                && !ClientSettings.INSTANCE.isInputEnabled()) {
            return;
        }
        if (!this.applyOnGameMenuValue.getEffectiveValue().booleanValue()
                && Minecraft.currentScreen().getObject() != null) {
            return;
        }
        int width = Minecraft.J();
        int height = Minecraft.h();
        if (width <= 0 || height <= 0) {
            return;
        }
        int previousFramebuffer = GL11.glGetInteger((int)36006);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        gg.vape.wrapper.impl.GL11.X((int)2978, viewport);
        GL11.glViewport(0, 0, width, height);

        if (!this.initialized) {
            this.initializeTextures(width, height);
            this.initializeQuad();
            this.initializeShader();
            this.initialized = true;
            this.textureWidth = width;
            this.textureHeight = height;
            this.first = true;
        }
        if (this.textureWidth != width || this.textureHeight != height) {
            this.resizeTextures(width, height);
            this.first = true;
        }

        

        

        

        

        MotionBlur.bindFramebufferViaGame(36008, 0);
        MotionBlur.bindFramebufferViaGame(36009, 0);
        this.copyToCurrent();
        if (this.first) {
            this.copyToHistory();
            this.first = false;
        }

        if (this.velocityAdaptiveValue.getEffectiveValue().booleanValue()) {
            this.updateVelocityFactor();
        } else {
            this.velocityFactor = 1.0f;
        }
        if (this.fpsModulateValue.getEffectiveValue().booleanValue()) {
            this.updateFpsModulation();
        } else {
            this.curBlurriness = this.blurrinessValue.getValue().floatValue();
        }

        this.drawTexture();
        this.copyToHistory();

        

        

        

        MotionBlur.bindFramebufferViaGame(36160, previousFramebuffer);
        GL11.glViewport(viewport.get(0), viewport.get(1),
                viewport.get(2), viewport.get(3));
    }

     
    private static void bindFramebufferViaGame(int target, int framebufferId) {
        try {
            gg.vape.mapping.mappings.MGlStateManager m =
                    gg.vape.Vape.INSTANCE.getMappings().Dt;
            if (m != null) {
                gg.vape.mapping.mappings.MGlStateManager.a(m, target, framebufferId);
                return;
            }
        }
        catch (Throwable throwable) {
            

        }
        GL30.glBindFramebuffer((int)target, (int)framebufferId);
    }

    private void updateVelocityFactor() {
        float viewX = Minecraft.D().getPlayerViewX();
        float viewY = Minecraft.D().getPlayerViewY();
        float delta = Math.abs(viewX - this.lastViewX)
                + Math.abs(viewY - this.lastViewY);
        this.lastViewX = viewX;
        this.lastViewY = viewY;
        float target = Math.max(0.0f, Math.min(15.0f, delta * 10.0f - 1.0f)) / 15.0f;
        if (delta > 0.01f) {
            this.velocityFactor = target;
        }
    }

    private void updateFpsModulation() {
        long now = System.nanoTime();
        if (this.lastFrameNanos != 0L) {
            float fps = 1.0E9f / (float)(now - this.lastFrameNanos);
            float normalized = Math.max(0.0f, Math.min(1000.0f, fps)) / 1000.0f;
            float attenuation = (float)Math.pow(normalized, 0.2);
            this.curBlurriness = this.blurrinessValue.getValue().floatValue() * attenuation;
        } else {
            this.curBlurriness = this.blurrinessValue.getValue().floatValue();
        }
        this.lastFrameNanos = now;
    }

    private void initializeTextures(int width, int height) {
        this.currentTexture = GL11.glGenTextures();
        this.historyTexture = GL11.glGenTextures();
        this.configureTexture(this.currentTexture, width, height);
        this.configureTexture(this.historyTexture, width, height);
    }

    private void configureTexture(int textureId, int width, int height) {
        GL11.glBindTexture((int)3553, (int)textureId);
        GL11.glTexImage2D((int)3553, (int)0, (int)32856, width, height,
                (int)0, (int)6408, (int)5121, (java.nio.ByteBuffer)null);
        GL11.glTexParameteri((int)3553, (int)10241, (int)9729);
        GL11.glTexParameteri((int)3553, (int)10240, (int)9729);
        GL11.glTexParameteri((int)3553, (int)10242, (int)33071);
        GL11.glTexParameteri((int)3553, (int)10243, (int)33071);
        try {
            GL30.glGenerateMipmap((int)3553);
        }
        catch (Throwable ignored) {
            

        }
        GL11.glBindTexture((int)3553, (int)0);
    }

    private void resizeTextures(int width, int height) {
        this.configureTexture(this.currentTexture, width, height);
        this.configureTexture(this.historyTexture, width, height);
        this.textureWidth = width;
        this.textureHeight = height;
    }

    private void initializeQuad() {
        float[] vertexData = new float[]{
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 1.0f, 1.0f
        };
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertexData.length);
        vertexBuffer.put(vertexData).flip();
        this.quadVao = GL30.glGenVertexArrays();
        this.quadVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(this.quadVao);
        GL15.glBindBuffer((int)34962, (int)this.quadVbo);
        GL15.glBufferData((int)34962, vertexBuffer, (int)35044);
        GL20.glVertexAttribPointer((int)0, (int)2, (int)5126, (boolean)false,
                (int)16, (long)0L);
        GL20.glEnableVertexAttribArray((int)0);
        GL20.glVertexAttribPointer((int)1, (int)2, (int)5126, (boolean)false,
                (int)16, (long)8L);
        GL20.glEnableVertexAttribArray((int)1);
        GL15.glBindBuffer((int)34962, (int)0);
        GL30.glBindVertexArray((int)0);
    }

    private void initializeShader() {
        int vertexShader = this.compileShader(35633, VERTEX_SHADER);
        int fragmentShader = this.compileShader(35632, FRAGMENT_SHADER);
        this.shaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(this.shaderProgram, vertexShader);
        GL20.glAttachShader(this.shaderProgram, fragmentShader);
        GL20.glLinkProgram(this.shaderProgram);
        if (GL20.glGetProgrami(this.shaderProgram, 35714) == 0) {
            throw new IllegalStateException("MotionBlur shader link failed: "
                    + GL20.glGetProgramInfoLog(this.shaderProgram, 8224));
        }
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
    }

    private int compileShader(int type, String source) {
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, (CharSequence)source);
        GL20.glCompileShader(shaderId);
        if (GL20.glGetShaderi(shaderId, 35713) == 0) {
            throw new IllegalStateException("MotionBlur shader compile failed: "
                    + GL20.glGetShaderInfoLog(shaderId, 512));
        }
        return shaderId;
    }

    private void copyToCurrent() {
        MotionBlur.bindTextureViaGame(this.currentTexture);
        GL11.glCopyTexSubImage2D((int)3553, (int)0, (int)0, (int)0,
                (int)0, (int)0, this.textureWidth, this.textureHeight);
    }

    private void copyToHistory() {
        MotionBlur.bindTextureViaGame(this.historyTexture);
        GL11.glCopyTexSubImage2D((int)3553, (int)0, (int)0, (int)0,
                (int)0, (int)0, this.textureWidth, this.textureHeight);
    }

     
    private static void bindTextureViaGame(int textureId) {
        try {
            gg.vape.mapping.mappings.MGlStateManager m =
                    gg.vape.Vape.INSTANCE.getMappings().Dt;
            if (m != null) {
                gg.vape.mapping.mappings.MGlStateManager.bindTextureViaGame(m, textureId);
                return;
            }
        }
        catch (Throwable throwable) {
            

        }
        GL11.glBindTexture((int)3553, (int)textureId);
    }

    private void drawTexture() {
        boolean depthEnabled = GL11.glIsEnabled((int)2929);
        boolean blendEnabled = GL11.glIsEnabled((int)3042);
        GL11.glDisable((int)2929);
        GL11.glDisable((int)3042);
        int previousProgram = GL11.glGetInteger((int)35725);
        int previousActiveUnit = GL11.glGetInteger((int)34016);
        

        

        

        int savedUnit0 = MotionBlur.readBoundTexture(0);
        int savedUnit1 = MotionBlur.readBoundTexture(1);
        int savedSampler0 = MotionBlur.readBoundSampler(0);
        int savedSampler1 = MotionBlur.readBoundSampler(1);

        GL20.glUseProgram(this.shaderProgram);
        

        

        

        

        try {
            org.lwjgl.opengl.GL33.glBindSampler(0, 0);
            org.lwjgl.opengl.GL33.glBindSampler(1, 0);
        }
        catch (Throwable ignored) {
            

        }
        MotionBlur.setActiveTextureViaGame(0);
        MotionBlur.bindTextureViaGame(this.currentTexture);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"currentTexture"), 0);
        MotionBlur.setActiveTextureViaGame(1);
        MotionBlur.bindTextureViaGame(this.historyTexture);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"historyTexture"), 1);
        

        

        

        float blurredFactor = this.curBlurriness * 2.0f / 11.0f;
        if (blurredFactor > 0.95f) {
            blurredFactor = 0.95f;
        }
        GL20.glUniform1f(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"blurriness"), blurredFactor);
        GL20.glUniform1f(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"velocity_factor"), this.velocityFactor);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"renderRGB"), this.clearColorValue.getEffectiveValue() ? 0 : 1);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.shaderProgram,
                (CharSequence)"smooth_blur"), this.smoothBlurValue.getEffectiveValue() ? 1 : 0);

        GL30.glBindVertexArray(this.quadVao);
        GL11.glDrawArrays((int)5, (int)0, (int)4);
        GL30.glBindVertexArray((int)0);

        

        

        MotionBlur.setActiveTextureViaGame(0);
        MotionBlur.bindTextureViaGame(savedUnit0);
        MotionBlur.setActiveTextureViaGame(1);
        MotionBlur.bindTextureViaGame(savedUnit1);
        MotionBlur.restoreBoundSampler(0, savedSampler0);
        MotionBlur.restoreBoundSampler(1, savedSampler1);
        if (previousActiveUnit >= 33984 && previousActiveUnit <= 33991) {
            GL13.glActiveTexture((int)previousActiveUnit);
        }
        GL20.glUseProgram(previousProgram);
        if (depthEnabled) {
            GL11.glEnable((int)2929);
        }
        if (blendEnabled) {
            GL11.glEnable((int)3042);
        }
    }

     
    private static void setActiveTextureViaGame(int unit) {
        try {
            gg.vape.mapping.mappings.MGlStateManager m =
                    gg.vape.Vape.INSTANCE.getMappings().Dt;
            if (m != null) {
                gg.vape.mapping.mappings.MGlStateManager.m(m, 33984 + unit);
                return;
            }
        }
        catch (Throwable throwable) {
            

        }
        GL13.glActiveTexture((int)(33984 + unit));
    }

     
    private static int readBoundTexture(int unit) {
        try {
            GL13.glActiveTexture((int)(33984 + unit));
            int bound = GL11.glGetInteger((int)32873);
            return bound;
        }
        catch (Throwable throwable) {
            return 0;
        }
    }

     
    private static int readBoundSampler(int unit) {
        try {
            GL13.glActiveTexture((int)(33984 + unit));
            return GL11.glGetInteger((int)35085);
        }
        catch (Throwable throwable) {
            return 0;
        }
    }

     
    private static void restoreBoundSampler(int unit, int samplerId) {
        try {
            org.lwjgl.opengl.GL33.glBindSampler(unit, samplerId);
        }
        catch (Throwable throwable) {
            

        }
    }

    private void destroyResources() {
        if (this.shaderProgram != 0) {
            GL20.glDeleteProgram(this.shaderProgram);
        }
        if (this.currentTexture != 0) {
            GL11.glDeleteTextures(this.currentTexture);
        }
        if (this.historyTexture != 0) {
            GL11.glDeleteTextures(this.historyTexture);
        }
        if (this.quadVao != 0) {
            GL30.glDeleteVertexArrays(this.quadVao);
        }
        if (this.quadVbo != 0) {
            GL15.glDeleteBuffers(this.quadVbo);
        }
        this.shaderProgram = 0;
        this.currentTexture = 0;
        this.historyTexture = 0;
        this.quadVao = 0;
        this.quadVbo = 0;
        this.initialized = false;
    }
}
