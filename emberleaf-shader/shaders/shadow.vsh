#version 120
#include "/lib/common.glsl"
attribute vec4 mc_Entity;
attribute vec2 mc_midTexCoord;
uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
varying vec2 texcoord;
varying vec4 glcolor;
void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	glcolor = gl_Color;
	vec4 view = gl_ModelViewMatrix * gl_Vertex;
	#ifdef WAVING_PLANTS
	vec4 player = shadowModelViewInverse * view;
	vec3 world = player.xyz + cameraPosition;
	float id = mc_Entity.x;
	bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
	float strength = id == 10001.0 ? 0.045 : (id == 10002.0 && top) ? 0.11 : id == 10004.0 ? (top ? 0.13 : 0.06) : 0.0;
	if (strength > 0.0) {
		float t = frameTimeCounter;
		player.x += (sin(t * 1.7 + world.x * 0.6 + world.z * 0.4) + sin(t * 2.9 + world.z * 0.9) * 0.4) * strength * (1.0 + rainStrength * 1.4);
		player.z += cos(t * 1.3 + world.z * 0.7 + world.x * 0.3) * strength * (1.0 + rainStrength * 1.4);
		view = shadowModelView * player;
	}
	#endif
	gl_Position = gl_ProjectionMatrix * view;
	gl_Position.xyz = distort(gl_Position.xyz);
}