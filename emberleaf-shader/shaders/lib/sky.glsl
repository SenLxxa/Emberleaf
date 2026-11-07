// Emberleaf sky: warm horizon, deep blue zenith, glowing sunsets and a galaxy night with twinkling stars.
// dir is a normalised view-space direction.

vec3 skyGradient(vec3 dir) {
	vec3 up = normalize(upPosition);
	vec3 sunDir = normalize(sunPosition);
	float h = dot(dir, up);
	float day = dayFactor();
	float dusk = sunsetFactor();

	vec3 zenithDay = vec3(0.24, 0.46, 0.86);
	vec3 horizonDay = vec3(0.72, 0.82, 0.94);
	vec3 zenithNight = vec3(0.012, 0.016, 0.05);
	vec3 horizonNight = vec3(0.05, 0.05, 0.12);
	vec3 duskHorizon = vec3(1.0, 0.50, 0.24);
	vec3 duskZenith = vec3(0.30, 0.26, 0.52);

	float t = pow(clamp(h, 0.0, 1.0), 0.45);
	vec3 dayCol = mix(horizonDay, zenithDay, t);
	vec3 nightCol = mix(horizonNight, zenithNight, t);
	vec3 col = mix(nightCol, dayCol, day);
	vec3 duskCol = mix(duskHorizon, duskZenith, pow(clamp(h, 0.0, 1.0), 0.6));
	col = mix(col, duskCol, dusk * 0.75);

	// glow around the sun, much wider at sunset
	float s = max(dot(dir, sunDir), 0.0);
	col += vec3(1.0, 0.62, 0.32) * pow(s, 6.0) * (0.25 + dusk * 0.9) * smoothstep(-0.2, 0.05, dot(sunDir, up));
	col += vec3(1.0, 0.85, 0.6) * pow(s, 64.0) * 0.6 * day;

	// below the horizon fade into a soft haze
	col = mix(col, mix(horizonNight, horizonDay * 0.8, day) * 0.7, smoothstep(0.0, -0.25, h));
	// rain: grey it out
	col = mix(col, vec3(luma(col)) * 0.75, rainStrength * 0.8);
	return pow(col, vec3(2.2));
}

vec3 starField(vec3 worldDir) {
	#ifdef STARS
	float night = 1.0 - dayFactor();
	if (night <= 0.001) return vec3(0.0);
	vec3 col = vec3(0.0);
	// stars: hash on a fine grid of directions
	vec3 p = worldDir * 260.0;
	vec3 cell = floor(p);
	float r = fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
	if (r > 0.992) {
		vec3 local = fract(p) - 0.5;
		float d = length(local);
		float twinkle = 0.65 + 0.35 * sin(frameTimeCounter * (2.0 + r * 9.0) + r * 60.0);
		float size = r > 0.9985 ? 0.48 : 0.32;
		vec3 tint = mix(vec3(0.75, 0.82, 1.0), vec3(1.0, 0.86, 0.66), fract(r * 731.0));
		col += tint * smoothstep(size, 0.0, d) * twinkle * 3.0;
	}
	// milky way: a soft band of purple and blue with dust lanes
	vec3 galaxyNormal = normalize(vec3(0.35, 0.25, 0.9));
	float band = exp(-pow(dot(worldDir, galaxyNormal) / 0.22, 2.0));
	vec2 q = vec2(atan(worldDir.z, worldDir.x), worldDir.y) * 3.0;
	float clouds = noise2(q * 2.0) * 0.6 + noise2(q * 5.0) * 0.3 + noise2(q * 11.0) * 0.1;
	float dust = smoothstep(0.45, 0.75, noise2(q * 4.0 + 7.0));
	vec3 galaxy = mix(vec3(0.20, 0.12, 0.36), vec3(0.40, 0.30, 0.62), clouds) * band * clouds * (1.0 - dust * 0.6);
	col += galaxy * 0.55;
	return col * night * (1.0 - rainStrength) * smoothstep(-0.05, 0.15, worldDir.y);
	#else
	return vec3(0.0);
	#endif
}
