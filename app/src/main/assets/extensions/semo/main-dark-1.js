(function(){function semoHub(){var K=Symbol.for('semo.h'),d=document,h=d[K];if(h)return h;var CH=1,VP=2,TH=4,CAP=600;var THEME=['class','data-theme','data-mode','data-color-mode','data-bs-theme','style'];var HEADATTR=['content','name','href','rel'];var subs=[],mo=null,seen=0,pending=false,timer=0,tracking=0,queue=[];function idle(f){if(window.requestIdleCallback)window.requestIdleCallback(f,{timeout:800});else setTimeout(f,120);}
function attach(){var de=d.documentElement,hd=d.head,bd=d.body;if(de&&!(seen&1)){seen|=1;mo.observe(de,{attributes:true,attributeFilter:THEME});}
if(hd&&!(seen&2)){seen|=2;mo.observe(hd,{attributes:true,subtree:true,attributeFilter:HEADATTR});}
if(bd&&!(seen&4)){seen|=4;mo.observe(bd,{attributes:true,attributeFilter:THEME});}}
function plan(){if(pending)return;pending=true;idle(run);}
function run(){pending=false;var now=Date.now(),wait=-1,list=subs.slice(),i,s,rem;for(i=0;i<list.length;i++){s=list[i];if(!s.due)continue;rem=s.gap-(now-s.last);if(rem>0){wait=wait<0?rem:Math.min(wait,rem);continue;}
s.due=0;s.last=now;try{s.fn();}catch(e){}}
if(wait>=0&&!timer)timer=setTimeout(function(){timer=0;plan();},wait);}
function onMut(recs){var f=0,i,j,r,n,a;for(i=0;i<recs.length;i++){r=recs[i];n=r.target.nodeName;if(r.type==='childList'){f|=CH;if(n==='HEAD')f|=VP;if(tracking&&r.addedNodes.length){for(j=0;j<r.addedNodes.length;j++){a=r.addedNodes[j];if(a.nodeType===1)queue.push(a);}}}else f|=(n==='META'||n==='LINK')?VP:TH;}
if(queue.length>CAP)queue.splice(0,queue.length-CAP);attach();for(i=0;i<subs.length;i++)if(subs[i].m&f)subs[i].due=1;plan();}
function start(){if(mo)return;seen=0;mo=new MutationObserver(onMut);mo.observe(d,{childList:true,subtree:true});attach();}
h={CH:CH,VP:VP,TH:TH,q:queue,on:function(mask,fn,gap){var s={m:mask,fn:fn,gap:gap||0,last:0,due:0};subs.push(s);start();return s;},off:function(s){var i=subs.indexOf(s);if(i>=0)subs.splice(i,1);if(!subs.length&&mo){mo.disconnect();mo=null;seen=0;}},track:function(on){tracking+=on?1:-1;if(tracking<0)tracking=0;if(!tracking)queue.length=0;},poke:function(fn){idle(fn);}};Object.defineProperty(d,K,{value:h});return h;}
try{(function(){(function(){if(window.top!==window)return;var FORCE=!!(document.documentElement&&document.documentElement.hasAttribute("data-semo-force"));var ATTR="data-semo-dark";var STYLE_ID="__semo_dark_css";var applied=false;var CSS="html["+ATTR+"]{filter:invert(1) hue-rotate(180deg)!important;background-color:#fff!important}"+"html["+ATTR+"] img[data-semo-keep],html["+ATTR+"] video,html["+ATTR+"] picture[data-semo-keep],"+"html["+ATTR+"] canvas,html["+ATTR+"] [data-semo-keep]{filter:invert(1) hue-rotate(180deg)!important}"+"html["+ATTR+"] [data-semo-keep] img,html["+ATTR+"] [data-semo-keep] video{filter:none!important}";function isHtml(){var t=(document.contentType||"").toLowerCase();return t==="text/html"||t==="application/xhtml+xml"||t==="";}
function lum(r,g,b){function f(v){v/=255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);}
return 0.2126*f(r)+0.7152*f(g)+0.0722*f(b);}
function parse(c){var m=/rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:\s*[,/]\s*([\d.]+)(%?))?/i.exec(c||"");if(!m)return null;var a=m[4]===undefined?1:parseFloat(m[4])/(m[5]?100:1);return[parseFloat(m[1]),parseFloat(m[2]),parseFloat(m[3]),a];}
function pageIsLight(){var de=document.documentElement;var body=document.body;var layers=[];if(body)layers.push(parse(getComputedStyle(body).backgroundColor));layers.push(parse(getComputedStyle(de).backgroundColor));for(var i=0;i<layers.length;i++){var p=layers[i];if(p&&p[3]>0.5)return lum(p[0],p[1],p[2])>0.35;}
var scheme=(getComputedStyle(de).colorScheme||"").toLowerCase();var mq=window.matchMedia&&matchMedia("(prefers-color-scheme: dark)").matches;if(mq&&scheme.indexOf("dark")>=0)return false;var fg=parse(getComputedStyle(body||de).color);if(fg&&lum(fg[0],fg[1],fg[2])>0.6)return false;return true;}
function ensureStyle(){if(document.getElementById(STYLE_ID))return;var s=document.createElement("style");s.id=STYLE_ID;s.textContent=CSS;(document.head||document.documentElement).appendChild(s);}
var MIN=9000,BG='[style*="background-image"]';var hub=semoHub();function markImg(im){try{if(im.hasAttribute("data-semo-keep"))return;var w=im.naturalWidth||im.width,h=im.naturalHeight||im.height;if(w*h>=MIN)im.setAttribute("data-semo-keep","1");}catch(e){}}
function markBg(el){try{if(el.hasAttribute("data-semo-keep")||el.childElementCount!==0||(el.textContent||"").trim())return;var r=el.getBoundingClientRect();if(r.width*r.height>=MIN)el.setAttribute("data-semo-keep","1");}catch(e){}}
function visit(n){var t=n.nodeName;if(t==="IMG")markImg(n);else if(t==="PICTURE")n.setAttribute("data-semo-keep","1");else if(n.hasAttribute("style")&&(n.getAttribute("style")||"").indexOf("background-image")>=0)markBg(n);if(!n.firstElementChild)return;var l=n.querySelectorAll("img,picture,"+BG),i,m;for(i=0;i<l.length&&i<200;i++){m=l[i];if(m.nodeName==="IMG")markImg(m);else if(m.nodeName==="PICTURE")m.setAttribute("data-semo-keep","1");else markBg(m);}}
function drain(){if(!applied)return;var q=hub.q,t0=Date.now(),n;while(q.length&&Date.now()-t0<6){n=q.shift();if(n&&n.isConnected)visit(n);}
if(q.length)hub.poke(drain);}
function markMedia(){try{var imgs=document.images;for(var i=0;i<imgs.length&&i<400;i++)markImg(imgs[i]);var pics=document.getElementsByTagName("picture");for(var j=0;j<pics.length&&j<100;j++)pics[j].setAttribute("data-semo-keep","1");}catch(e){}
drain();}
function on(){if(applied){drain();return;}
applied=true;ensureStyle();document.documentElement.setAttribute(ATTR,"1");markMedia();}
function off(){if(!applied)return;applied=false;document.documentElement.removeAttribute(ATTR);}
function decide(){try{if(!document.documentElement||!isHtml())return;if(FORCE){on();return;}
if(pageIsLight())on();else off();}catch(e){}}
hub.track(true);hub.on(hub.TH,decide,200);hub.on(hub.CH,drain,400);document.addEventListener("load",function(e){if(applied&&e.target&&e.target.nodeName==="IMG")markImg(e.target);},true);if(FORCE)decide();if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",decide,{once:true});else decide();window.addEventListener("load",function(){decide();setTimeout(markMedia,600);},{once:true});setTimeout(decide,1200);})();}).call(window)}catch(e){}})();
