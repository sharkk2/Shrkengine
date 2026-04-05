#version 330 core
in vec2 TexCoord;
out vec4 FragColor;
uniform sampler2D image;
uniform int horizontal;

float weight[5] = float[](0.227027, 0.194595, 0.121622, 0.054054, 0.016216);

void main() {
    vec2 texOffset = 1.0 / textureSize(image, 0);
    vec3 result = texture(image, TexCoord).rgb * weight[0];
    for (int i = 1; i < 5; i++) {
        vec2 offset = horizontal == 1 ? vec2(texOffset.x * i, 0.0) : vec2(0.0, texOffset.y * i);
        result += texture(image, TexCoord + offset).rgb * weight[i];
        result += texture(image, TexCoord - offset).rgb * weight[i];
    }
    FragColor = vec4(result, 1.0);
}