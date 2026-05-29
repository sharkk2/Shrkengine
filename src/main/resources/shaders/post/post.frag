#version 430 core

out vec4 FragColor;
in vec2 TexCoord;

uniform sampler2D screenTexture;
uniform float exposure;
uniform float saturation;
uniform sampler2D bloomBlur;
uniform float bloomStrength;
uniform float gamma;
uniform int useBloom;
uniform int useHDR;
uniform int useLUT;
uniform sampler3D lutTexture;

uniform int useGodRays;
uniform vec2 lightScreenPos;
uniform float godRayDensity;
uniform float godRayDecay;
uniform float godRayWeight;
uniform float godRayExposure;
uniform float godRayThreshold;
uniform usampler2D stencilView;

#define GOD_RAY_SAMPLES 64
uniform sampler2D depthTexture;

float rand(vec2 seed) {
    float dot_product = dot(seed, vec2(12.9898, 78.233));
    return fract(sin(dot_product) * 43758.5453);
}


vec3 computeGodRays(vec2 uv) {
    vec2 dir = uv - lightScreenPos;
    float len = length(dir);
    dir /= len + 1e-5;

    vec2 delta = dir * (len / float(GOD_RAY_SAMPLES)) * godRayDensity;
    float jitter = rand(uv);
    vec2 tc = uv - delta * jitter;

    float decay = 1.0;
    vec3 result = vec3(0.0);

    for (int i = 0; i < GOD_RAY_SAMPLES; i++) {
        if (decay < 0.01) break;
        tc -= delta;
        if (tc.x < 0.0 || tc.x > 1.0 || tc.y < 0.0 || tc.y > 1.0) break;
        uint stencil = texture(stencilView, tc).r;
        if (stencil == 1u) {decay * godRayDecay; continue;}
        float depth = texture(depthTexture, tc).r;
        if (depth >= godRayThreshold) { result += texture(screenTexture, tc).rgb * (decay * godRayWeight); }
        decay *= godRayDecay;
    }

    result = min(result, vec3(5.0));
    return result * godRayExposure;
}

void main() {
    vec3 color = texture(screenTexture, TexCoord).rgb;
    if (useGodRays == 1) {
        color += computeGodRays(TexCoord);
    }
    if (useHDR == 1) {
        vec3 bloom = texture(bloomBlur, TexCoord).rgb;
        if (useBloom == 1) color += bloom * bloomStrength;
        color = vec3(1.0) - exp(-color * exposure);

        float luminance = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(vec3(luminance), color, saturation);
        color = pow(color, vec3(1.0 / gamma));
    }

    if (useLUT == 1) {
        float lutSize = float(textureSize(lutTexture, 0).x);
        vec3 lutCoord = color * (lutSize - 1.0) / lutSize + 0.5 / lutSize;
        color = texture(lutTexture, lutCoord).rgb;
    }

    float dist = distance(TexCoord, vec2(0.5, 0.5));
    float vignette = smoothstep(0.9, 0.45f, dist);
    FragColor = vec4(color * vignette, 1.0);
}