#version 330 core

in vec2 fragPos;
in vec2 TexCoord;

out vec4 FragColor;

uniform vec4 color;
uniform sampler2D texSampler;
uniform bool useTexture;

void main() {
    vec4 colour = color;
    if (useTexture) {
        vec4 texColor = texture(texSampler, TexCoord);
        if (texColor.a <= 0.0) {
            discard;
        }
        colour *= texColor;
    }

    FragColor = colour;
}
