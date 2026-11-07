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
	if (c.a < 0.02) discard;
	// soft, sunlit clouds: warm at sunset, dim at night
	vec3 lit = mix(ambientColor() * 1.6, sunColor() * 0.55 + ambientColor() * 0.7, dayFactor());
	vec3 color = pow(c.rgb, vec3(2.2)) * lit;
	float fade = 1.0 - smoothstep(far * 1.5, far * 4.0, length(viewPos));
	gl_FragData[0] = vec4(color, c.a * 0.8 * fade);
}