#version 120
#include "/lib/common.glsl"
/*
const int colortex0Format = RGBA16F;
const bool colortex0MipmapEnabled = true;
*/
uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;
varying vec2 texcoord;

vec3 tonemap(vec3 x) {
	// ACES fit: soft highlights, rich darks
	return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
	vec3 color = texture2D(colortex0, texcoord).rgb;
	// bloom from the blurred mip levels of the frame
	vec3 bloom = vec3(0.0);
	for (int i = 2; i <= 6; i++) {
		float lod = float(i);
		vec3 s = texture2D(colortex0, texcoord, lod).rgb;
		bloom += max(s - vec3(0.85), vec3(0.0)) * (0.6 + lod * 0.25);
	}
	color += bloom * (BLOOM_STRENGTH * 0.35);
	color *= 1.15;
	color = tonemap(color);
	color = pow(color, vec3(1.0 / 2.2));
	// warm cozy grade + soft vignette
	color = mix(vec3(luma(color)), color, 1.08);
	color *= mix(vec3(1.0), vec3(1.04, 1.0, 0.93), 0.5 * WARMTH);
	vec2 v = texcoord - 0.5;
	color *= 1.0 - dot(v, v) * 0.45;
	gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}