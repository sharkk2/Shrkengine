#version 330 core

layout(location = 0) in vec3 aPos;
layout(location = 3) in vec4 iModelRow0;
layout(location = 4) in vec4 iModelRow1;
layout(location = 5) in vec4 iModelRow2;
layout(location = 6) in vec4 iModelRow3;

uniform mat4 lightSpaceMatrix;
uniform mat4 model;
uniform int useInstancing;

void main() {
    mat4 finalModel = (useInstancing == 1)
    ? mat4(iModelRow0, iModelRow1, iModelRow2, iModelRow3)
    : model;
    gl_Position = lightSpaceMatrix * finalModel * vec4(aPos, 1.0);
}