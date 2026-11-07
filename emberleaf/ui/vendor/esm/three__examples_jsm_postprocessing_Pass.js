/**
 * Bundled by jsDelivr using Rollup v4.62.2 and esbuild v0.28.1.
 * Original file: /npm/three@0.156.1/examples/jsm/postprocessing/Pass.js
 *
 * Do NOT use SRI with dynamically generated files! More information: https://www.jsdelivr.com/using-sri-with-dynamic-files
 */
import{OrthographicCamera as i,BufferGeometry as a,Float32BufferAttribute as t,Mesh as n}from"./three.js";class o{constructor(){this.isPass=!0,this.enabled=!0,this.needsSwap=!0,this.clear=!1,this.renderToScreen=!1}setSize(){}render(){console.error("THREE.Pass: .render() must be implemented in derived pass.")}dispose(){}}const m=new i(-1,1,1,-1,0,1),s=new a;s.setAttribute("position",new t([-1,3,0,-1,-1,0,3,-1,0],3)),s.setAttribute("uv",new t([0,2,0,0,2,0],2));class h{constructor(e){this._mesh=new n(s,e)}dispose(){this._mesh.geometry.dispose()}render(e){e.render(this._mesh,m)}get material(){return this._mesh.material}set material(e){this._mesh.material=e}}export{h as FullScreenQuad,o as Pass};
