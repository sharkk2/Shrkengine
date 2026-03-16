#version 330 core

out vec4 FragColor;
in vec2 TexCoord;

uniform sampler2D screenTexture;

void main()
{
    vec3 color = texture(screenTexture, TexCoord).rgb;
    float dist = distance(TexCoord, vec2(0.5, 0.5));
    float vignette = smoothstep(0.8, 0.4, dist);

    FragColor = vec4(color * vignette, 1.0);
}
