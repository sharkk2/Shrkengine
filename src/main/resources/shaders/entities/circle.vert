#version 330 core
layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aTex;

uniform vec2 offset;
uniform vec2 scale;
uniform float angle;

out vec2 fragPos;
out vec2 TexCoord;

void main() {
    vec2 centered = aPos - vec2(0.5, 0.5);
    vec2 scaled = centered * scale;
    float cosA = cos(angle);
    float sinA = sin(angle);
    vec2 rotated = vec2(
    scaled.x * cosA - scaled.y * sinA,
    scaled.x * sinA + scaled.y * cosA
    );

    vec2 finalPos = rotated + offset;
    gl_Position = vec4(finalPos, 0.0, 1.0);
    fragPos = rotated;
    TexCoord = aTex;
}
