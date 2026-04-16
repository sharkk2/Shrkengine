#version 330 core

in vec2 fragUV;
in vec3 fragPos;
in vec3 fragNormal;
flat in int fragTextureIndex;
flat in vec4 instanceColor;
out vec4 FragColor;

uniform vec4 color;
uniform int atlasSize;
uniform float utime;
uniform vec3 cameraPos;

uniform sampler2D texSampler;
uniform int useTexture;
#define MAX_POINT_LIGHTS 16

struct PointLight {
    vec3 position;
    vec3 color;
    float range;
    float intensity;
};

struct Fog {
    vec3 color;
    float start;
    float end;
    float density;
    int mode;
};

struct Material {
    vec3 ambient;
    vec3 emissive;
    float applyLight;
};


uniform int numPointLights;
uniform PointLight pointLights[MAX_POINT_LIGHTS];
uniform Fog fog;
uniform Material material;


float computeFog() {
    vec3 diff = cameraPos - fragPos;
    float distSq = dot(diff, diff);
    float fogFactor = 1.0;
    if (fog.mode == 0) {
        float startSq = fog.start * fog.start;
        float endSq = fog.end * fog.end;
        fogFactor = 1.0 - clamp((distSq - startSq) / (endSq - startSq), 0.0, 1.0);
    } else if (fog.mode == 1) {
        fogFactor = exp(-distSq * fog.density * fog.density);
    }

    return clamp(fogFactor, 0.0, 1.0);
}



vec3 applyLighting(vec3 baseColor, vec3 matAmbient) {
    vec3 result = baseColor * matAmbient;
    for (int i = 0; i < numPointLights; i++) {
        vec3 lightVec = pointLights[i].position - fragPos;
        float distSq = dot(lightVec, lightVec);
        float rangeSq = pointLights[i].range * pointLights[i].range;
        if (distSq > rangeSq) continue;
        float brightness = 1.0 - (distSq / rangeSq);
        brightness = clamp(brightness, 0.0, 1.0);
        brightness *= brightness;
        result += baseColor * brightness * pointLights[i].intensity * pointLights[i].color;
    }
    return result;
}

void main() {
    vec4 base = instanceColor;
    if (useTexture == 1) {
        float tileSize = 1.0 / float(atlasSize);
        int xIndex = fragTextureIndex % atlasSize;
        int yIndex = fragTextureIndex / atlasSize;
        vec2 offset = vec2(float(xIndex), float(yIndex)) * tileSize;
        vec2 finalUV = offset + fragUV * tileSize;
        base = texture(texSampler, finalUV);
        if (base.a < 0.005) discard;
    }

    if (base.a < 0.002) discard;
    if (material.applyLight == 1) base.rgb = applyLighting(base.rgb, material.ambient);
    float fogFactor = 1.0;
    fogFactor = computeFog();
    vec3 finalColor = mix(fog.color, base.rgb, fogFactor);
    finalColor += material.emissive;
    float camDist = length(cameraPos - fragPos);
    float nearFade = smoothstep(0.5, 1.5, camDist);

    FragColor = vec4(finalColor, instanceColor.a * nearFade);
}