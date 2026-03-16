#version 330 core

in vec2 fragUV;
in vec4 vertColor;

out vec4 FragColor;
uniform sampler2D fontTex;

void main() {
    float alpha = texture(fontTex, fragUV).r;
    if(alpha < 0.1) discard; // skip almost transparent pixels
    FragColor = vec4(vertColor.rgb, vertColor.a * alpha);
}
