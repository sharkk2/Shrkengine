#version 330 core
layout(location = 0) in vec2 aPos;  
layout(location = 1) in vec2 aTex;

uniform vec2 offset;
uniform vec2 scale;
uniform float angle;

out vec2 fragPos;
out vec2 TexCoord;

void main() {
    float cosA = cos(angle);
    float sinA = sin(angle);
    vec2 rotated = vec2(
    aPos.x * cosA - aPos.y * sinA,
    aPos.x * sinA + aPos.y * cosA
    );

    vec2 scaled = rotated * scale;
    vec2 finalPos = scaled + offset;

    gl_Position = vec4(finalPos, 0.0, 1.0);

    fragPos = rotated;
    TexCoord = aTex;
}
