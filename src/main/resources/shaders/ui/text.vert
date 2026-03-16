#version 330 core
layout(location = 0) in vec2 vPos;
layout(location = 1) in vec2 vUV;
layout(location = 2) in vec4 vColor;

out vec2 fragUV;
out vec4 vertColor;

void main() {
    gl_Position = vec4(vPos, 0.0, 1.0);
    fragUV = vUV;
    vertColor = vColor;
}
