#version 330 core
in vec2 fragPos;
in vec2 TexCoord;

out vec4 FragColor;

uniform vec4 color;
uniform sampler2D texSampler;
uniform bool useTexture;

void main() {
    vec4 colour = color;
    if (length(fragPos) > 1.0) discard;
    if (useTexture) {
        color *= texture(texSampler, TexCoord);
    }

    FragColor = colour;
}
