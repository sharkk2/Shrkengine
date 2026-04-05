#version 330 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 2) in vec3 aNormal;
layout(location = 3) in vec4 iModelRow0;
layout(location = 4) in vec4 iModelRow1;
layout(location = 5) in vec4 iModelRow2;
layout(location = 6) in vec4 iModelRow3;
layout(location = 7) in int iTextureIndex;
layout(location = 8) in vec4 iColor;
layout(location = 9)  in vec3 iMatAmbient;
layout(location = 10) in vec3 iMatDiffuse;
layout(location = 11) in vec3 iMatSpecular;
layout(location = 12) in vec3 iMatEmissive;
layout(location = 13) in float iMatShininess;
layout(location = 14) in float iMatApplyLight;
layout(location = 15) in float iMatRainbowEffect;

out vec2 fragUV;
out vec3 fragPos;
out vec3 fragNormal;
uniform mat4 lightSpaceMatrix;
out vec4 fragPosLightSpace;
flat out int fragTextureIndex;
flat out vec4 instanceColor;
flat out vec3 fragMatAmbient;
flat out vec3 fragMatDiffuse;
flat out vec3 fragMatSpecular;
flat out vec3 fragMatEmissive;
flat out float fragMatShininess;
flat out float fragMatApplyLight;
flat out float fragMatRainbowEffect;


uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;
uniform int useInstancing;
uniform int textureIndex;

void main() {
    mat4 finalModel = (useInstancing == 1) ? mat4(iModelRow0, iModelRow1, iModelRow2, iModelRow3) : model;
    vec4 worldPos = finalModel * vec4(aPos, 1.0);
    fragPos = worldPos.xyz;
    mat3 normalMatrix = transpose(inverse(mat3(finalModel)));
    fragNormal = normalize(normalMatrix * aNormal);
    gl_Position = projection * view * worldPos;
    fragUV = aUV;
    fragTextureIndex = (useInstancing == 1) ? iTextureIndex : textureIndex;
    fragPosLightSpace = lightSpaceMatrix * vec4(fragPos, 1.0);
    instanceColor = (useInstancing == 1) ? iColor : vec4(1.0);
    fragMatAmbient = iMatAmbient;
    fragMatDiffuse = iMatDiffuse;
    fragMatSpecular = iMatSpecular;
    fragMatEmissive = (useInstancing == 1) ? iMatEmissive : vec3(0.0);
    fragMatShininess = iMatShininess;
    fragMatApplyLight = iMatApplyLight;
    fragMatRainbowEffect = iMatRainbowEffect;
    fragMatEmissive = iMatEmissive;
}