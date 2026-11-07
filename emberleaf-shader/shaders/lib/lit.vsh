// Shared vertex shader for lit geometry (terrain, entities, hand, block entities, water)
#include "/lib/common.glsl"

attribute vec4 mc_Entity;
attribute vec2 mc_midTexCoord;

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 normal;
varying vec3 shadowPos;
varying vec3 viewPos;
varying float blockId;
varying vec3 worldPos;

vec3 wave(vec3 world, float id, bool top) {
	#ifdef WAVING_PLANTS
	float t = frameTimeCounter;
	float strength = 0.0;
	if (id == 10001.0) strength = 0.045;               // leaves
	if (id == 10002.0 && top) strength = 0.11;         // grass, flowers (only the top moves)
	if (id == 10004.0) strength = top ? 0.13 : 0.06;    // tall plants
	if (strength > 0.0) {
		float s = sin(t * 1.7 + world.x * 0.6 + world.z * 0.4) + sin(t * 2.9 + world.z * 0.9) * 0.4;
		float c = cos(t * 1.3 + world.z * 0.7 + world.x * 0.3);
		strength *= 1.0 + rainStrength * 1.4;
		return vec3(s, 0.0, c) * strength;
	}
	#endif
	return vec3(0.0);
}

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	lmcoord = clamp((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy, 0.0, 1.0);
	lmcoord = clamp((lmcoord - 1.0 / 32.0) * 16.0 / 15.0, 0.0, 1.0);
	glcolor = gl_Color;
	blockId = mc_Entity.x;

	vec4 view = gl_ModelViewMatrix * gl_Vertex;
	vec4 player = gbufferModelViewInverse * view;
	worldPos = player.xyz + cameraPosition;
	bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
	player.xyz += wave(worldPos, blockId, top);
	view = gbufferModelView * player;
	viewPos = view.xyz;
	gl_Position = gl_ProjectionMatrix * view;

	normal = normalize(gl_NormalMatrix * gl_Normal);
	// shadow position with a small normal offset against acne
	vec3 worldNormal = mat3(gbufferModelViewInverse) * normal;
	vec4 sp = shadowProjection * (shadowModelView * vec4(player.xyz + worldNormal * 0.06, 1.0));
	sp.xyz = distort(sp.xyz);
	shadowPos = sp.xyz * 0.5 + 0.5;
	shadowPos.z -= 0.0008;
}
