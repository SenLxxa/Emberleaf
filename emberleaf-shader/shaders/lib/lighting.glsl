// Forward lighting used by terrain, entities, hand and block entities.
// Needs: lmcoord (block, sky light), normal (view space), shadowPos (distorted, 0..1), viewPos.

uniform sampler2DShadow shadowtex0;
uniform sampler2DShadow shadowtex1;
uniform sampler2D shadowcolor0;

float sampleShadow(vec3 pos) {
	if (pos.x <= 0.0 || pos.x >= 1.0 || pos.y <= 0.0 || pos.y >= 1.0 || pos.z >= 1.0) return 1.0;
	float texel = 1.0 / float(shadowMapResolution);
	float sum = 0.0;
	float n = 0.0;
	#if SHADOW_QUALITY == 1
	const int r = 0;
	#elif SHADOW_QUALITY == 2
	const int r = 1;
	#else
	const int r = 2;
	#endif
	for (int x = -r; x <= r; x++) {
		for (int y = -r; y <= r; y++) {
			sum += shadow2D(shadowtex1, pos + vec3(vec2(x, y) * texel * 1.2, 0.0)).r;
			n += 1.0;
		}
	}
	return sum / n;
}

vec3 emberLight(vec3 albedo, vec2 lm, vec3 normal, vec3 shadowPos, float ao) {
	vec3 L = normalize(shadowLightPosition);
	float ndl = clamp(dot(normalize(normal), L), 0.0, 1.0);
	float sky = lm.y * lm.y;
	float shadow = ndl > 0.0 ? sampleShadow(shadowPos) : 0.0;
	// soft wrap so faces turned away are not pitch black
	float wrap = clamp(dot(normalize(normal), L) * 0.5 + 0.5, 0.0, 1.0);
	vec3 direct = sunColor() * ndl * shadow * smoothstep(0.0, 0.3, lm.y);
	vec3 ambient = ambientColor() * (0.35 + wrap * 0.25) * mix(0.18, 1.0, sky);
	float block = pow(lm.x, 2.6);
	vec3 torch = torchColor() * block * (1.0 - dayFactor() * sky * 0.55);
	vec3 minimum = vec3(0.035, 0.035, 0.05);
	return albedo * (direct + ambient * ao + torch + minimum);
}

vec3 applyFog(vec3 color, vec3 viewPos) {
	float dist = length(viewPos);
	float start = isEyeInWater == 1 ? 2.0 : far * 0.45;
	float end = isEyeInWater == 1 ? 24.0 : far * 1.02;
	float f = smoothstep(start, end, dist);
	vec3 fogTint = isEyeInWater == 1 ? vec3(0.08, 0.24, 0.30) : mix(fogColor * 1.05, vec3(1.0, 0.72, 0.48) * 0.9, sunsetFactor() * 0.35);
	return mix(color, pow(fogTint, vec3(2.2)), f * (isEyeInWater == 1 ? 0.95 : 0.85));
}
