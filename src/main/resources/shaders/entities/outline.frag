#version 330 core

in vec2 fragUV;
in vec3 fragPos;
in vec3 fragNormal;
flat in int fragTextureIndex;
flat in vec4 instanceColor;
flat in vec3 fragMatAmbient;
flat in vec3 fragMatDiffuse;
flat in vec3 fragMatSpecular;
flat in vec3 fragMatEmissive;
flat in float fragMatShininess;
flat in float fragMatApplyLight;
in vec4 fragPosLightSpace;
out vec4 FragColor;

uniform vec4 color;
uniform int useInstancing;

void main() {
    vec4 base = useInstancing == 1 ? instanceColor : color;
    FragColor = base;
}