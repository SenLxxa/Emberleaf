// Emberleaf Glut – gemeinsame Einstellungen und Hilfsfunktionen

#define SHADOW_QUALITY 2 // [1 2 3] 1 = schnell, 3 = sehr weich
#define BLOOM_STRENGTH 0.6 // [0.0 0.2 0.4 0.6 0.8 1.0 1.4]
#define WARMTH 1.0 // [0.0 0.5 1.0 1.5]
#define WAVING_PLANTS
#define STARS

const int shadowMapResolution = 2048; // [1024 2048 3072]
const float shadowDistance = 96.0; // [64.0 96.0 128.0 160.0]
const float sunPathRotation = -25.0;
const bool shadowHardwareFiltering = true;

uniform int worldTime;
uniform float rainStrength;
uniform float frameTimeCounter;
uniform vec3 sunPosition;
uniform vec3 upPosition;
uniform vec3 shadowLightPosition;
uniform vec3 cameraPosition;
uniform vec3 fogColor;
uniform vec3 skyColor;
uniform float far;
uniform int isEyeInWater;

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

// 0 at night, 1 at noon; "sunset" peaks while the sun is near the horizon
float sunHeight() { return dot(normalize(sunPosition), normalize(upPosition)); }
float dayFactor() { return smoothstep(-0.08, 0.25, sunHeight()); }
float sunsetFactor() { float h = sunHeight(); return clamp(1.0 - abs(h - 0.04) * 5.0, 0.0, 1.0); }

vec3 sunColor() {
	vec3 noon = vec3(1.0, 0.93, 0.80);
	vec3 golden = vec3(1.0, 0.56, 0.26);
	vec3 moon = vec3(0.36, 0.44, 0.70) * 0.55;
	vec3 day = mix(noon, golden, sunsetFactor() * 0.85);
	return mix(moon, day * 2.1, dayFactor()) * (1.0 - rainStrength * 0.7);
}

vec3 ambientColor() {
	vec3 day = vec3(0.50, 0.60, 0.78);
	vec3 dusk = vec3(0.55, 0.42, 0.42);
	vec3 night = vec3(0.10, 0.12, 0.22);
	vec3 c = mix(night, mix(day, dusk, sunsetFactor() * 0.7), dayFactor());
	return mix(c, vec3(luma(c)) * 0.9, rainStrength * 0.6);
}

vec3 torchColor() { return vec3(1.0, 0.62, 0.30) * mix(1.25, 1.55, WARMTH * 0.5); }

// shadow space distortion: more detail near the player
vec3 distort(vec3 pos) {
	float factor = length(pos.xy) * 0.88 + 0.12;
	return vec3(pos.xy / factor, pos.z * 0.25);
}

float hash12(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * 0.1031);
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.x + p3.y) * p3.z);
}

float noise2(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}
