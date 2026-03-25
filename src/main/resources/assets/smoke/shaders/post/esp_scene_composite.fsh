#version 150

uniform sampler2D SceneSampler;
uniform sampler2D OverlaySampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec4 overlay = texture(OverlaySampler, texCoord);

    // Blend the overlay over the scene
    // Use alpha blending: result = overlay * overlayAlpha + scene * (1 - overlayAlpha)
    vec3 color = mix(scene.rgb, overlay.rgb, overlay.a);

    // The overlay contains transparent pixels outside the entity
    fragColor = vec4(color, 1.0);
}
