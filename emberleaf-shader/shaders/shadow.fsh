#version 120
uniform sampler2D gtexture;
varying vec2 texcoord;
varying vec4 glcolor;
void main() {
	vec4 c = texture2D(gtexture, texcoord) * glcolor;
	if (c.a < 0.1) discard;
	gl_FragData[0] = c;
}