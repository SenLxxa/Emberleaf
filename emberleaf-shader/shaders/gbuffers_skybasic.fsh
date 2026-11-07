#version 120
#include "/lib/common.glsl"
#include "/lib/sky.glsl"

uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform float viewWidth;
uniform float viewHeight;
uniform int renderStage;

varying vec4 glcolor;

/* RENDERTARGETS: 0 */
void main() {
	#ifdef MC_RENDER_STAGE_STARS
	if (renderStage == MC_RENDER_STAGE_STARS) discard; // we draw our own stars
	#endif
	vec4 ndc = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
	vec4 view = gbufferProjectionInverse * ndc;
	vec3 dir = normalize(view.xyz / view.w);
	vec3 worldDir = normalize(mat3(gbufferModelViewInverse) * dir);
	vec3 color = skyGradient(dir) + starField(worldDir);
	gl_FragData[0] = vec4(color, 1.0);
}
