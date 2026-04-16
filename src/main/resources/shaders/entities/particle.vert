#version 330 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 3) in vec4 iModelRow0;
layout(location = 4) in vec4 iModelRow1;
layout(location = 5) in vec4 iModelRow2;
layout(location = 6) in vec4 iModelRow3;
layout(location = 7) in int iTextureIndex;
layout(location = 8) in vec4 iColor;

out vec2 fragUV;
out vec3 fragPos;
out vec3 fragNormal;
flat out int fragTextureIndex;
flat out vec4 instanceColor;

uniform mat4 projection;
uniform mat4 view;
uniform int useInstancing;

void main() {
    mat4 finalModel = mat4(iModelRow0, iModelRow1, iModelRow2, iModelRow3);
    float scaleX = length(vec3(finalModel[0]));
    float scaleY = length(vec3(finalModel[1]));
    float scaleZ = length(vec3(finalModel[2]));

    float modelAngle = atan(finalModel[0][1], finalModel[0][0]);
    float cosA = cos(modelAngle);
    float sinA = sin(modelAngle);

    mat4 modelView = view * finalModel;
    modelView[0][0] = scaleX * cosA; modelView[0][1] = scaleX * sinA; modelView[0][2] = 0.0;
    modelView[1][0] = scaleY * -sinA; modelView[1][1] = scaleY * cosA; modelView[1][2] = 0.0;
    modelView[2][0] = 0.0; modelView[2][1] = 0.0; modelView[2][2] = scaleZ;

    vec4 worldPos = finalModel * vec4(aPos, 1.0);
    fragPos = worldPos.xyz;
    fragNormal = vec3(0.0, 0.0, 1.0);
    gl_Position = projection * modelView * vec4(aPos, 1.0);
    fragUV = aUV;
    fragTextureIndex = iTextureIndex;
    instanceColor = iColor;
}