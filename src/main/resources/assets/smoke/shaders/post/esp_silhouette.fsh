#version 150

uniform sampler2D InSampler;

layout(std140) uniform SmokeEspConfig {
    float FillAlpha;
    float OutlineWidth;
};

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float presence(vec4 sampleColor) {
    return step(0.001, sampleColor.a);
}

vec4 sampleMask(vec2 direction) {
    return texture(InSampler, texCoord + direction * oneTexel * OutlineWidth);
}

void accumulate(vec4 sampleColor, inout float outlinePresence, inout vec3 outlineColor, inout float colorWeight) {
    float mask = presence(sampleColor);
    outlinePresence = max(outlinePresence, mask);
    outlineColor += sampleColor.rgb * mask;
    colorWeight += mask;
}

void main() {
    vec4 center = texture(InSampler, texCoord);
    float inside = presence(center);

    float outlinePresence = 0.0;
    vec3 outlineColor = vec3(0.0);
    float colorWeight = 0.0;

    // Sample surrounding pixels for outline detection
    accumulate(sampleMask(vec2(-1.0, 0.0)), outlinePresence, outlineColor, colorWeight);
    accumulate(sampleMask(vec2(1.0, 0.0)), outlinePresence, outlineColor, colorWeight);
    accumulate(sampleMask(vec2(0.0, -1.0)), outlinePresence, outlineColor, colorWeight);
    accumulate(sampleMask(vec2(0.0, 1.0)), outlinePresence, outlineColor, colorWeight);
    accumulate(sampleMask(vec2(-1.0, -1.0)), outlinePresence, outlineColor, colorWeight);
    accumulate(sampleMask(vec2(-1.0, 1.0)), outlinePresence, outlineColor, colorWeight);
    accumulate(sampleMask(vec2(1.0, -1.0)), outlinePresence, outlineColor, colorWeight);
    accumulate(sampleMask(vec2(1.0, 1.0)), outlinePresence, outlineColor, colorWeight);

    // Calculate outline (pixels that are outside but adjacent to inside)
    float outline = (1.0 - inside) * outlinePresence;
    vec3 averagedOutlineColor = colorWeight > 0.0 ? outlineColor / colorWeight : center.rgb;
    float fill = inside * FillAlpha;

    if (outline > 0.0) {
        fragColor = vec4(averagedOutlineColor, 1.0);
        return;
    }

    if (fill > 0.0) {
        fragColor = vec4(center.rgb, fill);
        return;
    }

    discard;
}
