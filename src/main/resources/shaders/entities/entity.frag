#version 330 core

in vec2 fragUV;
in vec3 fragPos;
in vec3 fragNormal;
flat in int fragTextureIndex;
flat in vec4 instanceColor;
flat in vec3 fragMatAmbient;
flat in vec3 fragMatDiffuse;
flat in vec3 fragMatSpecular;
flat in float fragMatShininess;
flat in float fragMatApplyLight;
in vec4 fragPosLightSpace;
out vec4 FragColor;

uniform vec4 color;
uniform sampler2D texSampler;
uniform sampler2D shadowMap;
uniform int useTexture;
uniform int useInstancing;
uniform int atlasSize;
uniform float utime;
uniform int useColorMask;
uniform vec3 cameraPos;
uniform float textureScale;

#define MAX_POINT_LIGHTS 16

struct PointLight {
    vec3 position;
    vec3 color;
    float range;
    float intensity;
};

struct Material {
    vec3 ambient;
    vec3 diffuse;
    vec3 specular;
    float shininess;
    float applyLight;
};

struct DirectionalLight {
    vec3 direction;
    vec3 color;
    vec3 ambient;
    int passShadow;
    int enabled;
};

struct Fog {
    vec3 color;
    float start;
    float end;
    float density;
    int mode;
};

uniform int numPointLights;
uniform PointLight pointLights[MAX_POINT_LIGHTS];
uniform Material material;
uniform Fog fog;
uniform DirectionalLight dirLight;
uniform sampler2D specularMap;
uniform int useSpecularMap;

bool equalsClr(vec4 a, vec3 b, float epsl) { return all(lessThan(abs(a.rgb - b), vec3(epsl))); }

float computeFog() {
    float dist = length(cameraPos - fragPos);
    float fogFactor;
    if (fog.mode == 0) {
        fogFactor = 1.0 - clamp((dist - fog.start) / (fog.end - fog.start), 0.0, 1.0);
    } else {
        fogFactor = exp(-pow(dist * fog.density, 2.0));
    }

    return clamp(fogFactor, 0.0, 1.0);
}

vec3 computeFogColor(vec3 camViewDir, float fogFactor) {
    float phaseExp = 6.0;
    float scatterScale = 0.4;
    vec3 result = fog.color;

    for (int i = 0; i < numPointLights; i++) {
        vec3 toLightDir = normalize(pointLights[i].position - cameraPos);
        float phase = pow(max(dot(camViewDir, toLightDir), 0.0), phaseExp);
        float fragLightDist = length(pointLights[i].position - fragPos);
        float localAttenuation = clamp(1.0 - (fragLightDist / pointLights[i].range), 0.0, 1.0);
        float fogDensity = 1.0 - fogFactor;
        result += pointLights[i].color * phase * localAttenuation * fogDensity * scatterScale;
    }
    return min(result, vec3(1.0));
}

float computeShadow(vec4 posLightSpace, vec3 norm, vec3 lightDir) {
    vec3 projCoords = posLightSpace.xyz / posLightSpace.w;
    projCoords = projCoords * 0.5 + 0.5;
    if (projCoords.z > 1.0) return 0.0;

    float bias = max(0.004 * (1.0 - dot(norm, lightDir)), 0.001);

    float shadow = 0.0;
    vec2 texelSize = 1.0 / textureSize(shadowMap, 0);
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            float pcfDepth = texture(shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;
            shadow += (projCoords.z - bias) > pcfDepth ? 1.0 : 0.0;
        }
    }
    return shadow / 9.0;
}

vec3 applyLighting(vec3 baseColor, vec3 matAmbient, vec3 matDiffuse, vec3 matSpecular, float matShininess) {
    vec3 norm = normalize(fragNormal);
    vec3 viewDir = normalize(cameraPos - fragPos);

    // dirlight
    vec3 ambient = dirLight.ambient * matAmbient * baseColor;
    vec3 result = ambient;
    float shadow;
    vec3 resolvedSpecular = useSpecularMap == 1 ? texture(specularMap, fragUV).rgb : matSpecular;
    if (dirLight.enabled == 1) {
        vec3 lightDir = normalize(-dirLight.direction);
        float diff = max(dot(norm, lightDir), 0.0);
        vec3 diffuse = diff * matDiffuse * baseColor * dirLight.color;

        vec3 reflectDir = reflect(-lightDir, norm);
        float spec = pow(max(dot(viewDir, reflectDir), 0.0), matShininess);
        vec3 specular = spec * resolvedSpecular * dirLight.color;

        shadow = (dirLight.passShadow == 1) ? computeShadow(fragPosLightSpace, norm, lightDir) : 0.0;
        result += (1.0 - shadow) * (diffuse + specular);
    }

    // point lights
    for (int i = 0; i < numPointLights; i++) {
        vec3 lightVec = pointLights[i].position - fragPos;
        float dist = length(lightVec);
        vec3 plLightDir = normalize(lightVec);

        float plDiff = max(dot(norm, plLightDir), 0.0);
        vec3 plDiffuse = plDiff * matDiffuse * baseColor;

        vec3 plReflectDir = reflect(-plLightDir, norm);
        float plSpec = pow(max(dot(viewDir, plReflectDir), 0.0), matShininess);
        vec3 plSpecular = plSpec * resolvedSpecular;

        float attenuation = clamp(1.0 - (dist / pointLights[i].range), 0.0, 1.0);
        attenuation *= attenuation;
        result += (plDiffuse + plSpecular) * attenuation * pointLights[i].intensity * pointLights[i].color;
    }

    return result;
}

void main() {
    vec4 base = useInstancing == 1 ? instanceColor : color;
    if (useTexture == 1) {
        float tileSize = 1.0 / float(atlasSize);

        int xIndex = fragTextureIndex % atlasSize;
        int yIndex = fragTextureIndex / atlasSize;

        vec2 offset = vec2(float(xIndex), float(yIndex)) * tileSize;
        vec2 uv = fragUV;
        if(textureScale > 1) {uv = fract(fragUV * textureScale);}
        vec2 finalUV = offset + uv * tileSize;
        base = texture(texSampler, finalUV);
        if (base.a < 0.05) discard;
        if (equalsClr(base, vec3(0.216), 0.1) && useColorMask == 1) {
            FragColor = vec4(0.5 + 0.5 * sin(utime), 0.5 + 0.5 * sin(utime + 2.0), 0.5 + 0.5 * sin(utime + 4.0), 1.0);
            return;
        }
    }
    vec3 matAmbient = useInstancing == 1 ? fragMatAmbient:material.ambient;
    vec3 matDiffuse = useInstancing == 1 ? fragMatDiffuse:material.diffuse;
    vec3 matSpecular = useInstancing == 1 ? fragMatSpecular:material.specular;
    float matShininess = useInstancing == 1 ? fragMatShininess:material.shininess;
    float applyLight = useInstancing == 1 ? fragMatApplyLight:material.applyLight;
    if (applyLight == 1.0) base.rgb = applyLighting(base.rgb, matAmbient, matDiffuse, matSpecular, matShininess);

    float fogFactor = computeFog();
    vec3 camViewDir = normalize(fragPos - cameraPos);
    vec3 litFogColor = computeFogColor(camViewDir, fogFactor);
    vec3 finalColor = mix(litFogColor, base.rgb, fogFactor);
    FragColor = vec4(finalColor, base.a);
}