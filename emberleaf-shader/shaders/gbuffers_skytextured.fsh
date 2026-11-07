#version 120
#include "/lib/common.glsl"
uniform sampler2D gtexture;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 viewPos;
/* RENDERTARGETS: 0 */
void main() {
	vec4 c = texture2D(gtexture, texcoord) * glcolor;
	// sun and moon glow a little brighter so the bloom catches them
	gl_FragData[0] = vec4(pow(c.rgb, vec3(2.2)) * mix(1.6, 4.0, dayFactor()), c.a);
}