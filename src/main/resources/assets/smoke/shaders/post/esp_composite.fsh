#version 150

uniform sampler2D EdgesSampler;
uniform sampler2D MaskSampler;
uniform sampler2D GlowSampler;

layout(std140) uniform SmokeEspConfig {
    float FillAlpha;
    float GlowMode;
    float OutlineWidth;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 edges = texture(EdgesSampler, texCoord);
    vec4 mask = texture(MaskSampler, texCoord);
    vec4 glow = texture(GlowSampler, texCoord);

    float inside = step(0.001, mask.a);
    float border = step(0.001, edges.a);

    // Keep the glow inside the silhouette and off the border so the outline stays crisp.
    float inner = inside * (1.0 - border);

    // glow.a is the blurred edge mask; boost to keep it visible after normalization.
    float glowStrength = clamp(glow.a * 2.0, 0.0, 1.0);

    // Add a small base fill so the glow shows up across the body at higher opacity.
    const float baseFill = 0.25;
    float shaped = clamp(baseFill + (1.0 - baseFill) * glowStrength, 0.0, 1.0);

    float alpha = inner * FillAlpha * shaped;
    if (alpha <= 0.0) {
        discard;
    }

    fragColor = vec4(mask.rgb, alpha);
}
