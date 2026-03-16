#version 330 core

in vec3 TexDir;
out vec4 FragColor;

uniform int useTexture;
uniform samplerCube skybox;
uniform vec3 sunDir;
int useDayNightCycle = 1;


void main() {
    if (useTexture == 1) {
        FragColor = texture(skybox, TexDir);
        return;
    }

    vec3 dir = normalize(TexDir);
    vec3 sDir = normalize(-sunDir); // i obviously didn't code this, claude did. and i have to invert the directional light direction for it to work with this shader

    // (−1 = midnight, 0 = horizon, 1 = noon)
    float sunHeight = sDir.y;
    vec3 zenithColor, midColor, horizonColor, hazeColor;

    if (useDayNightCycle == 1) {
        float dayBlend = clamp(sunHeight * 2.0, 0.0, 1.0);
        float sunsetBlend = clamp(1.0 - abs(sunHeight) * 3.0, 0.0, 1.0);

        vec3 dayZenith = vec3(0.08, 0.15, 0.45);
        vec3 dayMid = vec3(0.25, 0.52, 0.85);
        vec3 dayHorizon = vec3(0.72, 0.84, 0.98);
        vec3 dayHaze = vec3(0.9, 0.75, 0.6);
        vec3 sunsetZenith = vec3(0.05, 0.05, 0.25);
        vec3 sunsetMid = vec3(0.6, 0.25, 0.1);
        vec3 sunsetHorizon = vec3(1.0, 0.45, 0.1);
        vec3 sunsetHaze = vec3(1.0, 0.4, 0.15);
        vec3 nightZenith = vec3(0.01, 0.01, 0.05);
        vec3 nightMid = vec3(0.02, 0.02, 0.08);
        vec3 nightHorizon = vec3(0.04, 0.04, 0.12);
        vec3 nightHaze = vec3(0.03, 0.03, 0.1);

        zenithColor = mix(mix(nightZenith, sunsetZenith, dayBlend), dayZenith, dayBlend);
        midColor = mix(mix(nightMid,sunsetMid, dayBlend), dayMid, dayBlend);
        horizonColor = mix(mix(nightHorizon, sunsetHorizon, dayBlend), dayHorizon, dayBlend);
        hazeColor = mix(mix(nightHaze, sunsetHaze, dayBlend), dayHaze, dayBlend);
        zenithColor = mix(zenithColor, sunsetZenith,  sunsetBlend * 0.5);
        horizonColor = mix(horizonColor, sunsetHorizon, sunsetBlend * 0.8);
        hazeColor = mix(hazeColor, sunsetHaze, sunsetBlend * 0.9);
    } else {
        zenithColor = vec3(0.08, 0.15, 0.45);
        midColor = vec3(0.25, 0.52, 0.85);
        horizonColor = vec3(0.72, 0.84, 0.98);
        hazeColor = vec3(0.9, 0.75, 0.6);
    }

    float t = clamp(dir.y, 0.0, 1.0);
    vec3 skyColor = mix(horizonColor, midColor, smoothstep(0.0, 0.25, t));
    skyColor = mix(skyColor, zenithColor, smoothstep(0.15, 1.0, t));

    float hazeBand = exp(-t * 20.0) * clamp(1.0 - abs(dir.y) * 6.0, 0.0, 1.0);
    skyColor = mix(skyColor, hazeColor, hazeBand * 0.5);

    float sunDot = dot(dir, sDir);
    float sunAngle = acos(clamp(sunDot, -1.0, 1.0));

    float sunVisibility = useDayNightCycle == 1 ? clamp(sunHeight * 10.0, 0.0, 1.0) : 1.0;
    float discRadius = 0.035;
    float discEdge = smoothstep(discRadius, discRadius * 0.7, sunAngle);
    float limbDarken = sqrt(max(1.0 - pow(sunAngle / discRadius, 2.0), 0.0));
    limbDarken = mix(0.75, 1.0, limbDarken);
    vec3 sunDiscColor = useDayNightCycle == 1 ? mix(vec3(1.0, 0.4, 0.1), vec3(1.0, 0.97, 0.88), clamp(sunHeight * 3.0, 0.0, 1.0)):vec3(1.0, 0.97, 0.88);
    sunDiscColor *= limbDarken;

    float innerCorona = exp(-sunAngle * 80.0) * 0.9;
    float outerGlow = exp(-sunAngle * 12.0) * 0.4;
    float mie = pow(max(sunDot, 0.0), 6.0) * 0.6;

    vec3 innerCoronaColor = vec3(1.0, 0.95, 0.80);
    vec3 glowColor = vec3(1.0, 0.85, 0.55);
    vec3 mieColor  = vec3(1.0, 0.75, 0.45);

    vec3 sunContrib = vec3(0.0);
    sunContrib += mieColor * mie;
    sunContrib += glowColor * outerGlow;
    sunContrib += innerCoronaColor * innerCorona;
    sunContrib += sunDiscColor * discEdge;
    sunContrib *= sunVisibility;

    vec3 finalColor = skyColor + sunContrib;

    finalColor = finalColor / (finalColor + vec3(0.6));
    finalColor = pow(finalColor, vec3(1.0 / 2.2));

    FragColor = vec4(finalColor, 1.0);
}