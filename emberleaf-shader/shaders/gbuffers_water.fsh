#version 120
#include "/lib/common.glsl"
#include "/lib/lighting.glsl"
#include "/lib/sky.glsl"

uniform sampler2D gtexture;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 normal;
varying vec3 shadowPos;
varying vec3 viewPos;
varying float blockId;
varying vec3 worldPos;

float waves(vec2 p) {
	float t = frameTimeCounter;
	return noise2(p * 0.55 + vec2(t * 0.32, t * 0.21)) * 0.6 + noise2(p * 1.4 - vec2(t * 0.45, -t * 0.3)) * 0.3
		+ noise2(p * 3.1 + vec2(t * 0.8, t * 0.1)) * 0.1;
}

/* RENDERTARGETS: 0 */
void main() {
	vec4 tex = texture2D(gtexture, texcoord) * glcolor;
	vec3 worldNormal = mat3(gbufferModelViewInverse) * normalize(normal);
	if (blockId == 10003.0) {
		vec3 n = normalize(normal);
		if (worldNormal.y > 0.5) {
			vec2 p = worldPos.xz;
			float e = 0.06;
			float hx = waves(p + vec2(e, 0.0)) - waves(p - vec2(e, 0.0));
			float hz = waves(p + vec2(0.0, e)) - waves(p - vec2(0.0, e));
			n = normalize(mat3(gbufferModelView) * normalize(vec3(-hx * 3.5, 1.0, -hz * 3.5)));
		}
		vec3 V = normalize(-viewPos);
		float fres = 0.03 + 0.97 * pow(1.0 - clamp(dot(n, V), 0.0, 1.0), 5.0);
		vec3 R = reflect(-V, n);
		float sky = lmcoord.y * lmcoord.y;
		vec3 reflection = skyGradient(R) * mix(0.15, 1.0, sky);
		vec3 deep = pow(vec3(0.07, 0.25, 0.32), vec3(2.2)) * (ambientColor() * 1.4 * mix(0.3, 1.0, sky) + torchColor() * pow(lmcoord.x, 2.6));
		float shadow = sampleShadow(shadowPos);
		float spec = pow(max(dot(R, normalize(shadowLightPosition)), 0.0), 220.0) * shadow * sky;
		vec3 color = mix(deep, reflection, fres) + sunColor() * spec * 3.0;
		float alpha = mix(0.62, 0.95, fres);
		color = applyFog(color, viewPos);
		gl_FragData[0] = vec4(color, alpha);
		return;
	}
	// stained glass, ice, slime …: normally lit, keep their transparency
	if (tex.a < 0.02) discard;
	vec3 color = emberLight(pow(tex.rgb, vec3(2.2)), lmcoord, normal, shadowPos, 1.0);
	gl_FragData[0] = vec4(applyFog(color, viewPos), tex.a);
}
