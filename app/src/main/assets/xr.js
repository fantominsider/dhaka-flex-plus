/* Self-contained WebXR video viewer. No tracking, CDN, telemetry, or third-party scripts. */
'use strict';
(async function () {
  const qs = new URLSearchParams(location.search);
  const token = qs.get('t') || '';
  const $ = id => document.getElementById(id);
  const video = $('preview'), enter = $('enter'), exit = $('exit'), status = $('status');
  const panel = $('panel'), canvas = $('xr-canvas');
  let cfg, session = null, space = null, gl = null, shader = null, texture = null;
  let quad = null, globe360 = null, globe180 = null, frameActive = false;
  const report = msg => status.textContent = msg;

  try {
    const response = await fetch('/config?t=' + encodeURIComponent(token), {cache:'no-store'});
    if (!response.ok) throw new Error('Local video service unavailable (HTTP ' + response.status + ')');
    cfg = await response.json();
    $('title').textContent = cfg.title || 'Video';
    $('modeInfo').textContent = 'Playback layout: ' + cfg.mode + '. For best compatibility, start with an MP4/H.264 or HEVC file supported by your headset browser. MKV, DTS and some HLS streams may need a specialized VR player.';
    video.src = '/stream?t=' + encodeURIComponent(token);
  } catch (err) {
    report('Failed to connect: ' + err.message + '. Return to the Android app and reopen the XR viewer.');
    return;
  }

  $('play').onclick = () => video.paused ? video.play().catch(e => report(e.message)) : video.pause();
  $('back').onclick = () => { if (Number.isFinite(video.duration)) video.currentTime = Math.max(0,video.currentTime-30); };
  $('next').onclick = () => { if (Number.isFinite(video.duration)) video.currentTime = Math.min(video.duration,video.currentTime+30); };
  video.addEventListener('error', () => report('Browser cannot play this stream. Try a compatible MP4 or the native Android / external player.'));

  if (!navigator.xr || !navigator.xr.isSessionSupported) {
    report('This browser has no WebXR support. You can still watch the normal 2D video above.');
    return;
  }
  let supported = false;
  try { supported = await navigator.xr.isSessionSupported('immersive-vr'); }
  catch (e) { report('WebXR check failed: ' + e.message); }
  if (!supported) {
    report('Immersive VR is not offered by this browser. Open the dedicated PICO / Meta headset browser.');
    return;
  }
  enter.disabled = false;
  report('Ready for immersive VR. Browser-based WebXR is detected.');
  enter.onclick = async () => {
    // Both playback and the XR request originate in a direct user gesture.
    if (video.paused) video.play().catch(e => report('Video: ' + e.message));
    let requested;
    try { requested = navigator.xr.requestSession('immersive-vr', {optionalFeatures: []}); }
    catch (e) { report('XR request blocked: ' + e.message); return; }
    try {
      session = await requested;
      await initGraphics();
      space = await session.requestReferenceSpace('local');
      session.updateRenderState({baseLayer:new XRWebGLLayer(session,gl,{alpha:false,depth:false,antialias:false})});
      panel.style.display = 'none';canvas.style.display = 'block';
      session.addEventListener('select', () => { if (video.paused) video.play().catch(() => {}); else video.pause(); });
      session.addEventListener('end', () => { session = null;frameActive = false;panel.style.display = 'block';canvas.style.display = 'none';report('Exited immersive mode.'); });
      frameActive = true;
      session.requestAnimationFrame(drawFrame);
    } catch (e) { report('Cannot launch immersive viewer: ' + e.message); if (session) await session.end().catch(() => {}); }
  };
  exit.onclick = () => {if (session) session.end();};

  async function initGraphics() {
    if (!gl) {
      gl = canvas.getContext('webgl', {alpha:false,antialias:false,xrCompatible:true});
      if (!gl) throw new Error('WebGL unavailable');
      if (gl.makeXRCompatible) await gl.makeXRCompatible();
      createGeometry();createProgram();
      texture = gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([0,0,0,255]));
    }
  }
  function compile(type,src) {
    const obj=gl.createShader(type);gl.shaderSource(obj,src);gl.compileShader(obj);
    if (!gl.getShaderParameter(obj,gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(obj));
    return obj;
  }
  function createProgram() {
    const vs=compile(gl.VERTEX_SHADER,`
      attribute vec3 position;attribute vec2 uv;uniform mat4 mvp;varying vec2 texUV;
      void main(){texUV=uv;gl_Position=mvp*vec4(position,1.0);}`);
    const fs=compile(gl.FRAGMENT_SHADER,`
      precision mediump float;varying vec2 texUV;uniform sampler2D screen;
      uniform float eye;uniform float stereo;uniform float overUnder;
      void main(){vec2 t=texUV;
        if(stereo>0.5){if(overUnder>0.5)t.y=t.y*.5+(1.0-eye)*.5;
          else t.x=t.x*.5+eye*.5;}
        gl_FragColor=texture2D(screen,t);
      }`);
    shader=gl.createProgram();gl.attachShader(shader,vs);gl.attachShader(shader,fs);gl.linkProgram(shader);
    if (!gl.getProgramParameter(shader,gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(shader));
    gl.useProgram(shader);
    shader.pos=gl.getAttribLocation(shader,'position');shader.uv=gl.getAttribLocation(shader,'uv');
    shader.mvp=gl.getUniformLocation(shader,'mvp');shader.eye=gl.getUniformLocation(shader,'eye');
    shader.stereo=gl.getUniformLocation(shader,'stereo');shader.overUnder=gl.getUniformLocation(shader,'overUnder');
    gl.uniform1i(gl.getUniformLocation(shader,'screen'),0);
  }
  function buffer(v) {
    const result={buffer:gl.createBuffer(),count:v.length/5};gl.bindBuffer(gl.ARRAY_BUFFER,result.buffer);
    gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(v),gl.STATIC_DRAW);return result;
  }
  function createGeometry() {
    // Fixed cinema screen, 5.76m wide x 3.24m tall, at 4.8m distance.
    quad=buffer([
      -2.88,-1.62,-4.8, 0,0, 2.88,-1.62,-4.8, 1,0, 2.88,1.62,-4.8, 1,1,
      -2.88,-1.62,-4.8, 0,0, 2.88,1.62,-4.8, 1,1, -2.88,1.62,-4.8, 0,1
    ]);
    globe360=makeSphere(360);globe180=makeSphere(180);
  }
  function makeSphere(degrees) {
    const lat=48,lon=96,radius=25, data=[];
    function sample(u,v){
      const yaw=(u-.5)*(degrees*Math.PI/180), pitch=(v-.5)*Math.PI;
      const x=Math.sin(yaw)*Math.cos(pitch)*radius;
      const y=Math.sin(pitch)*radius;
      const z=-Math.cos(yaw)*Math.cos(pitch)*radius;
      return [x,y,z,u,1-v];
    }
    for(let j=0;j<lat;j++)for(let i=0;i<lon;i++){
      const a=sample(i/lon,j/lat),b=sample((i+1)/lon,j/lat),
            c=sample((i+1)/lon,(j+1)/lat),d=sample(i/lon,(j+1)/lat);
      data.push(...a,...b,...c,...a,...c,...d);
    }
    return buffer(data);
  }
  function multiply(a,b){
    const out=new Float32Array(16);
    for(let c=0;c<4;c++)for(let r=0;r<4;r++){
      out[c*4+r]=a[0*4+r]*b[c*4+0]+a[1*4+r]*b[c*4+1]+
                 a[2*4+r]*b[c*4+2]+a[3*4+r]*b[c*4+3];
    }
    return out;
  }
  function drawFrame(time,frame){
    if(!frameActive||!session)return;
    session.requestAnimationFrame(drawFrame);
    const pose=frame.getViewerPose(space);if(!pose)return;
    const layer=session.renderState.baseLayer;
    gl.bindFramebuffer(gl.FRAMEBUFFER,layer.framebuffer);
    gl.useProgram(shader);
    gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);
    if (video.readyState>=video.HAVE_CURRENT_DATA) {
      try {
        gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);
        gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,video);
      }catch(e){/* codec/CORS issues leave previous frame displayed */}
    }
    const chosen = cfg.mode || 'theater';
    const stereo=chosen.endsWith('-sbs')||chosen.endsWith('-tb');
    gl.uniform1f(shader.stereo,stereo?1:0);
    gl.uniform1f(shader.overUnder,chosen.endsWith('-tb')?1:0);
    const mesh=chosen.startsWith('vr360')?globe360:chosen.startsWith('vr180')?globe180:quad;
    gl.bindBuffer(gl.ARRAY_BUFFER,mesh.buffer);
    gl.enableVertexAttribArray(shader.pos);gl.vertexAttribPointer(shader.pos,3,gl.FLOAT,false,20,0);
    gl.enableVertexAttribArray(shader.uv);gl.vertexAttribPointer(shader.uv,2,gl.FLOAT,false,20,12);
    gl.disable(gl.CULL_FACE);gl.disable(gl.DEPTH_TEST);gl.enable(gl.SCISSOR_TEST);
    for(const view of pose.views){
      const vp=layer.getViewport(view);gl.viewport(vp.x,vp.y,vp.width,vp.height);
      gl.scissor(vp.x,vp.y,vp.width,vp.height);gl.clearColor(.007,.009,.017,1);gl.clear(gl.COLOR_BUFFER_BIT);
      gl.uniform1f(shader.eye,view.eye==='right'?1:0);
      gl.uniformMatrix4fv(shader.mvp,false,multiply(view.projectionMatrix,view.transform.inverse.matrix));
      gl.drawArrays(gl.TRIANGLES,0,mesh.count);
    }
    gl.disable(gl.SCISSOR_TEST);
  }
})();
