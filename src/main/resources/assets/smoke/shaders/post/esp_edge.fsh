#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float present(vec4 sample) {
    return step(0.0001, sample.a);
}

void main() {
    vec4 center = texture(InSampler, texCoord);
    vec4 left = texture(InSampler, texCoord - vec2(oneTexel.x, 0.0));
    vec4 right = texture(InSampler, texCoord + vec2(oneTexel.x, 0.0));
    vec4 up = texture(InSampler, texCoord - vec2(0.0, oneTexel.y));
    vec4 down = texture(InSampler, texCoord + vec2(0.0, oneTexel.y));

    float centerMask = present(center);
    float total = clamp(
        abs(centerMask - present(left))
        + abs(centerMask - present(right))
        + abs(centerMask - present(up))
        + abs(centerMask - present(down)),
        0.0,
        1.0
    );

    // Output only a thin edge mask; the glow fill is created by blurring this mask.
    float edge = total;
    fragColor = vec4(center.rgb * edge, edge);
}
