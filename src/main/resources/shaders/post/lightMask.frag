#version 430 core
out vec4 FragColor;
in vec2 TexCoord;

uniform sampler2D depthTexture;
uniform vec2 lightScreenPos;
uniform float sunRadius;
uniform float nearPlane;
uniform float farPlane;
uniform float aspectRatio;

float linearizeDepth(float d) {
    float z = d * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) / (farPlane + nearPlane - z * (farPlane - nearPlane));
}

void main() {
    float depth = texture(depthTexture, TexCoord).r;
    float linDepth = linearizeDepth(depth);
    float isSky = (linDepth >= farPlane * 0.999) ? 1.0 : 0.0;

    vec2 diff = (TexCoord - lightScreenPos) * vec2(aspectRatio, 1.0);
    float sunDisc = smoothstep(sunRadius, sunRadius * 0.7, length(diff));

    FragColor = vec4(max(isSky, sunDisc));
}