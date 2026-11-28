function semoHub(){var K=Symbol.for('semo.h'),d=document,h=d[K];if(h)return h;var CH=1,VP=2,TH=4,CAP=600;var THEME=['class','data-theme','data-mode','data-color-mode','data-bs-theme','style'];var HEADATTR=['content','name','href','rel'];var subs=[],mo=null,seen=0,pending=false,timer=0,tracking=0,queue=[];function idle(f){if(window.requestIdleCallback)window.requestIdleCallback(f,{timeout:800});else setTimeout(f,120);}
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
