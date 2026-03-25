#version 150

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

in vec2 texCoord;
in vec2 sampleStep;

out vec4 fragColor;

void main() {
    float radius = max(Radius, 1.0);
    vec4 accum = vec4(0.0);
    float weightSum = 0.0;

    // Gaussian-like blur for smooth glow
    for (float offset = -radius; offset <= radius; offset += 1.0) {
        float weight = exp(-(offset * offset) / (2.0 * radius * radius / 4.0));
        accum += texture(InSampler, texCoord + sampleStep * offset) * weight;
        weightSum += weight;
    }

    vec4 result = accum / max(weightSum, 0.0001);

    // Preserve alpha channel but blend colors
    fragColor = vec4(result.rgb, result.a);
}
