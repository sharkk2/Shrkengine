#version 330 core

in vec2 fragUV;
in vec3 fragPos;
in vec3 fragNormal;
flat in int fragTextureIndex;
flat in vec4 instanceColor;
flat in vec3 fragMatAmbient;
flat in vec3 fragMatDiffuse;
flat in vec3 fragMatSpecular;
flat in vec3 fragMatEmissive;
flat in vec4 fragMatProps;
in vec4 fragPosLightSpace;
out vec4 FragColor;

uniform vec4 color;
uniform int atlasSize;
uniform float utime;
uniform vec3 cameraPos;
uniform float textureScale;
uniform vec3 skycolor;

uniform sampler2DShadow shadowMap;
uniform sampler2D texSampler;
uniform sampler2D normalMap;
uniform sampler2D specularMap;
uniform sampler2D metalnessMap;
uniform sampler2D aoMap;
uniform sampler2D roughnessMap;
uniform sampler2D opacityMap;
uniform sampler2D emissiveMap;

uniform int useInstancing;
uniform int useColorMask;
uniform int useNormalMap;
uniform int useSpecularMap;
uniform int useTexture;
uniform int useOpacityMap;
uniform int useMetalMap;
uniform int useEmissiveMap;
uniform int useAOMap;
uniform int useRoughnessMap;
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
    vec3 emissive;
    vec4 matProps;
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

bool equalsClr(vec4 a, vec3 b, float epsl) { return all(lessThan(abs(a.rgb - b), vec3(epsl))); }

float computeFog() {
    float dist = length(cameraPos - fragPos);
    float fogFactor;
    if (fog.mode == 0) {
        fogFactor = 1.0 - clamp((dist - fog.start) / (fog.end - fog.start), 0.0, 1.0);
    } else if (fog.mode == 1) {
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

const vec2 poissonDisk[16] = vec2[](
   vec2(-0.94201624, -0.39906216),
   vec2( 0.94558609, -0.76890725),
   vec2(-0.094184101, -0.92938870),
   vec2( 0.34495938, 0.29387760),
   vec2(-0.91588581, 0.45771432),
   vec2(-0.81544232, -0.87912464),
   vec2(-0.38277543, 0.27676845),
   vec2( 0.97484398, 0.75648379),
   vec2( 0.44323325, -0.97511554),
   vec2( 0.53742981, -0.47373420),
   vec2(-0.26496911, -0.41893023),
   vec2( 0.79197514, 0.19090188),
   vec2(-0.24188840, 0.99706507),
   vec2(-0.81409955, 0.91437590),
   vec2( 0.19984126, 0.78641367),
   vec2( 0.14383161, -0.14100790)
);

float rand(vec4 seed) {
    float dot_product = dot(seed, vec4(12.9898, 78.233, 45.164, 94.673));
    return fract(sin(dot_product) * 43758.5453);
}

float computeShadow(vec4 posLightSpace, vec3 norm, vec3 lightDir) {
    vec3 projCoords = posLightSpace.xyz / posLightSpace.w;
    projCoords = projCoords * 0.5 + 0.5;
    if (projCoords.z > 1.0) return 1.0;

    // i literally have no idea how softening and pcf work and i couldnt understand how so this is very likely to produce problems later <:
    float currentDot = max(dot(norm, lightDir), 0.0);
    float tanAcos = sqrt(1.0 - currentDot * currentDot) / currentDot;
    float bias = clamp(0.0005 * tanAcos, 0.0005, 0.015);
    float angle = rand(vec4(gl_FragCoord.xyy, 1.0)) * 6.28318;
    float s = sin(angle);
    float c = cos(angle);
    mat2 rotation = mat2(c, s, -s, c);
    vec2 texelSize = 1.0 / textureSize(shadowMap, 0);
    float spread = 2.0;
    float shadow = 0.0;
    for (int i = 0; i < 16; i++) {
        vec2 offset = rotation * poissonDisk[i] * texelSize * spread;
        shadow += texture(shadowMap, vec3(projCoords.xy + offset, projCoords.z - bias));
    }
    return shadow / 16.0;
}

vec3 applyLighting(vec3 baseColor, vec3 matAmbient, vec3 matDiffuse, vec3 matSpecular, float matShininess) {
    vec3 norm;
    // for ref: https://learnopengl.com/Advanced-Lighting/Normal-Mapping
    if (useNormalMap == 1) {
        vec3 N = normalize(fragNormal);
        vec3 dp1 = dFdx(fragPos);
        vec3 dp2 = dFdy(fragPos);
        vec2 duv1 = dFdx(fragUV);
        vec2 duv2 = dFdy(fragUV);
        float det = duv1.x * duv2.y - duv2.x * duv1.y;
        float invDet = (abs(det) > 0.00001) ? 1.0 / det : 1.0;
        vec3 T = normalize((dp1 * duv2.y - dp2 * duv1.y) * invDet);
        T = normalize(T - dot(T, N) * N);
        vec3 B = cross(N, T);
        mat3 TBN = mat3(T, B, N);
        vec3 sampledNormal = texture(normalMap, fragUV).rgb * 2.0 - 1.0;
        norm = normalize(TBN * sampledNormal);
    } else {
        norm = normalize(fragNormal);
    }

    float effectiveShininess = matShininess;
    if (useRoughnessMap == 1) {
        float roughness = texture(roughnessMap, fragUV).r;
        effectiveShininess = exp2(12.0 * (1.0 - roughness) + 1.0);
        effectiveShininess = max(effectiveShininess, 1.0);
    }

    float metalness = 0.0;
    if (useMetalMap == 1) {
        metalness = texture(metalnessMap, fragUV).r;
    }

    vec3 metallicSpecular = mix(matSpecular, baseColor, metalness);
    vec3 metallicDiffuse = matDiffuse * (1.0 - metalness);

    float ao = 1.0;
    if (useAOMap == 1) {
        ao = texture(aoMap, fragUV).r;
    }

    vec3 viewDir = normalize(cameraPos - fragPos);
    vec3 resolvedSpecular = useSpecularMap == 1 ? texture(specularMap, fragUV).rgb : metallicSpecular;
    vec3 ambient = dirLight.ambient * matAmbient * baseColor * ao;
    vec3 result = ambient;

    // directional light
    if (dirLight.enabled == 1) {
        vec3 lightDir = normalize(-dirLight.direction);
        float diff = max(dot(norm, lightDir), 0.0);
        vec3 diffuse = diff * metallicDiffuse * baseColor * dirLight.color;

        vec3 specular = vec3(0.0);
        if (diff > 0.0) {
            vec3 halfDir = normalize(lightDir + viewDir);
            float spec = pow(max(dot(norm, halfDir), 0.0), effectiveShininess);
            specular = spec * resolvedSpecular * dirLight.color;
        }

        float pointLightFill = 0.0;
        for (int i = 0; i < numPointLights; i++) {
            vec3 lightVec = pointLights[i].position - fragPos;
            float dist = length(lightVec);
            float atten = clamp(1.0 - (dist / pointLights[i].range), 0.0, 1.0);
            atten *= atten;
            pointLightFill = max(pointLightFill, atten * pointLights[i].intensity);
        }

        float visibility = (dirLight.passShadow == 1) ? computeShadow(fragPosLightSpace, norm, lightDir) : 1.0;
        visibility = max(visibility, clamp(pointLightFill, 0.0, 1.0));
        result += visibility * (diffuse + specular);
    }

    // point lights
    for (int i = 0; i < numPointLights; i++) {
        vec3 lightVec = pointLights[i].position - fragPos;
        float dist = length(lightVec);
        vec3 plLightDir = normalize(lightVec);
        float plDiff = max(dot(norm, plLightDir), 0.0);
        vec3 plDiffuse = plDiff * metallicDiffuse * baseColor;
        vec3 plSpecular = vec3(0.0);
        if (plDiff > 0.0) {
            vec3 plHalfDir = normalize(plLightDir + viewDir);
            float plSpec = pow(max(dot(norm, plHalfDir), 0.0), effectiveShininess);
            plSpecular = plSpec * resolvedSpecular;
        }

        float linear = 2.0 / pointLights[i].range;
        float quadratic = 1.0 / (pointLights[i].range * pointLights[i].range);
        float attenuation = 1.0 / (1.0 + linear * dist + quadratic * dist * dist);
        attenuation *= clamp(1.0 - (dist / pointLights[i].range), 0.0, 1.0);

        result += (plDiffuse + plSpecular) * attenuation * pointLights[i].intensity * pointLights[i].color;
    }

    result = mix(result, result * ao, 0.5);
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
        if (equalsClr(base, vec3(0.216), 0.001) && useColorMask == 1) {
            FragColor = vec4(0.5 + 0.5 * sin(utime), 0.5 + 0.5 * sin(utime + 2.0), 0.5 + 0.5 * sin(utime + 4.0), 1.0);
            return;
        }
    }

    vec3 matAmbient = useInstancing == 1 ? fragMatAmbient:material.ambient;
    vec3 matDiffuse = useInstancing == 1 ? fragMatDiffuse:material.diffuse;
    vec3 matSpecular = useInstancing == 1 ? fragMatSpecular:material.specular;
    vec3 matEmissive = useInstancing == 1 ? fragMatEmissive:material.emissive;
    float matShininess = useInstancing == 1 ? fragMatProps.x:material.matProps.x;
    float matEmStrength = useInstancing == 1 ? fragMatProps.w:material.matProps.w;
    float applyLight = useInstancing == 1 ? fragMatProps.y:material.matProps.y;
    float rainbowEffect = useInstancing == 1 ? fragMatProps.z:material.matProps.z;
    float alpha = useInstancing == 1 ? instanceColor.a : color.a;
    if (useOpacityMap == 1) {
        alpha *= texture(opacityMap, fragUV).r;
    }
    if (alpha < 0.02) discard;

    if (applyLight == 1) base.rgb = applyLighting(base.rgb, matAmbient, matDiffuse, matSpecular, matShininess);
    float fogFactor = computeFog();
    vec3 camViewDir = normalize(fragPos - cameraPos);
    vec3 litFogColor = computeFogColor(camViewDir, fogFactor);
    vec3 finalColor = mix(litFogColor, base.rgb, fogFactor);
    vec3 emissive = matEmissive;
    if (useEmissiveMap == 1) {
        vec3 sampled = texture(emissiveMap, fragUV).rgb;
        emissive = length(sampled) < 0.001 ? finalColor : sampled;
    }
    finalColor += emissive * matEmStrength;

    if (rainbowEffect == 1) {
        vec3 rainbow = vec3(
        0.5 + 0.5 * sin(utime),
        0.5 + 0.5 * sin(utime + 2.0),
        0.5 + 0.5 * sin(utime + 4.0)
        );

        float lum = dot(rainbow, vec3(0.2126, 0.7152, 0.0722));
        rainbow = rainbow / max(lum, 0.001);
        rainbow *= floor(matEmissive);
        vec3 finalColor = mix(litFogColor, rainbow, fogFactor);
        FragColor = vec4(finalColor, alpha);
        return;
    }
    FragColor = vec4(finalColor, alpha);
}