(function (root) {
  'use strict';
  var RAW = 'co.uk org.uk ac.uk gov.uk me.uk ltd.uk plc.uk net.uk sch.uk com.sa net.sa org.sa gov.sa edu.sa med.sa sch.sa pub.sa com.eg edu.eg gov.eg org.eg net.eg sci.eg eun.eg co.ae net.ae org.ae gov.ae ac.ae sch.ae com.au net.au org.au edu.au gov.au asn.au id.au co.jp ne.jp or.jp ac.jp go.jp ad.jp ed.jp gr.jp co.nz net.nz org.nz govt.nz ac.nz school.nz com.br net.br org.br gov.br edu.br co.in net.in org.in gov.in ac.in res.in firm.in gen.in ind.in com.cn net.cn org.cn gov.cn edu.cn com.tr net.tr org.tr gov.tr edu.tr co.za org.za net.za gov.za ac.za co.kr or.kr ne.kr go.kr ac.kr com.mx org.mx gob.mx edu.mx net.mx com.ar net.ar org.ar gob.ar edu.ar com.pk net.pk org.pk gov.pk edu.pk com.jo net.jo org.jo gov.jo edu.jo com.lb net.lb org.lb gov.lb edu.lb com.kw net.kw org.kw gov.kw edu.kw com.qa net.qa org.qa gov.qa edu.qa com.bh net.bh org.bh gov.bh edu.bh com.om net.om org.om gov.om edu.om co.ma net.ma org.ma gov.ma ac.ma com.dz net.dz org.dz gov.dz edu.dz com.tn net.tn org.tn gov.tn com.ly net.ly org.ly gov.ly edu.ly com.iq net.iq org.iq gov.iq edu.iq com.ps net.ps org.ps gov.ps edu.ps com.sy net.sy org.sy gov.sy edu.sy com.ye net.ye org.ye gov.ye edu.ye com.sd net.sd org.sd gov.sd edu.sd com.sg net.sg org.sg gov.sg edu.sg com.hk net.hk org.hk gov.hk edu.hk com.tw net.tw org.tw gov.tw edu.tw com.my net.my org.my gov.my edu.my co.id or.id go.id ac.id web.id co.th or.th go.th ac.th in.th com.vn net.vn org.vn gov.vn edu.vn com.ph net.ph org.ph gov.ph edu.ph com.ng org.ng gov.ng edu.ng net.ng co.ke or.ke go.ke ac.ke co.il org.il net.il gov.il ac.il com.ru net.ru org.ru com.ua net.ua org.ua gov.ua com.pl net.pl org.pl com.es org.es nom.es asso.fr com.fr gouv.fr github.io gitlab.io blogspot.com herokuapp.com pages.dev workers.dev netlify.app vercel.app web.app firebaseapp.com cloudfront.net amazonaws.com s3.amazonaws.com azurewebsites.net appspot.com onrender.com fly.dev r2.dev wordpress.com myshopify.com *.ck !www.ck';
  var rules = Object.create(null), exc = Object.create(null);
  RAW.split(' ').forEach(function (r) {
    if (!r) return;
    if (r.charAt(0) === '!') exc[r.slice(1)] = 1; else rules[r] = 1;
  });
  function registrable(host) {
    host = String(host || '').toLowerCase().replace(/\.$/, '');
    if (!host || host.indexOf(':') >= 0 || /^\d+\.\d+\.\d+\.\d+$/.test(host)) return host;
    var l = host.split('.'), n = l.length;
    if (n < 2) return host;
    var suffixLen = 1;
    for (var i = 0; i < n; i++) {
      var cand = l.slice(i).join('.');
      if (exc[cand]) { suffixLen = n - i - 1; break; }
      if (rules[cand] || (i + 1 < n && rules['*.' + l.slice(i + 1).join('.')])) { suffixLen = n - i; break; }
    }
    if (n <= suffixLen) return host;
    return l.slice(n - suffixLen - 1).join('.');
  }
  var api = { registrable: registrable };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.__semoPsl = api;
})(typeof self !== 'undefined' ? self : this);
