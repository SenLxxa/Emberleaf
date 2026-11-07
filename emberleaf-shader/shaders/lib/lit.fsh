// Shared fragment shader for lit geometry
#include "/lib/common.glsl"
#include "/lib/lighting.glsl"

uniform sampler2D gtexture;
uniform vec4 entityColor;
uniform int heldBlockLightValue;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 normal;
varying vec3 shadowPos;
varying vec3 viewPos;
varying float blockId;
varying vec3 worldPos;

/* RENDERTARGETS: 0 */
void main() {
	vec4 albedo = texture2D(gtexture, texcoord) * glcolor;
	if (albedo.a < 0.1) discard;
	#ifdef ENTITY
	albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
	#endif
	vec3 base = pow(albedo.rgb, vec3(2.2)); // light in linear space
	float ao = 1.0;
	vec2 lm = lmcoord;
	#ifdef HAND
	lm.x = max(lm.x, float(heldBlockLightValue) / 15.0 * 0.9);
	#endif
	vec3 color = emberLight(base, lm, normal, shadowPos, ao);
	// emissive spots (lava, glowstone, torches) keep their own brightness and feed the bloom
	if (blockId == 10010.0) color = mix(color, base * 2.4, 0.85);
	// wet ground darkens and gets a little sheen in the rain
	if (rainStrength > 0.0 && lm.y > 0.9) color *= 1.0 - rainStrength * 0.15;
	color = applyFog(color, viewPos);
	gl_FragData[0] = vec4(color, albedo.a);
}
