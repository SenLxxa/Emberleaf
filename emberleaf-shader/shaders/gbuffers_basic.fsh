#version 120
uniform sampler2D lightmap;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 viewPos;
/* RENDERTARGETS: 0 */
void main() {
	vec4 c = glcolor * texture2D(lightmap, lmcoord);
	gl_FragData[0] = vec4(pow(c.rgb, vec3(2.2)), c.a);
}