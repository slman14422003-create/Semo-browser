(function () {
  if (window.__novaSafari) return;
  window.__novaSafari = 1;
  var def = function (o, k, v) {
    try { Object.defineProperty(o, k, { get: function () { return v; }, configurable: true }); } catch (e) {}
  };
  var proto = Object.getPrototypeOf(navigator);
  def(proto, 'platform', 'iPhone');
  def(proto, 'vendor', 'Apple Computer, Inc.');
  def(proto, 'vendorSub', '');
  def(proto, 'productSub', '20030107');
  def(proto, 'maxTouchPoints', 5);
  def(proto, 'userAgentData', undefined);
  def(proto, 'deviceMemory', undefined);
  def(proto, 'webdriver', false);
  try { delete window.chrome; } catch (e) {}
  var css = 'html{-webkit-text-size-adjust:100%;text-size-adjust:100%;-webkit-tap-highlight-color:transparent;-webkit-overflow-scrolling:touch}' +
    'body{-webkit-font-smoothing:antialiased;-webkit-touch-callout:none}' +
    'a,button,[role=button],input,select,textarea{-webkit-tap-highlight-color:transparent}' +
    'input,textarea{-webkit-appearance:none;appearance:none}' +
    'button,[type=button],[type=submit]{-webkit-appearance:none}';
  var add = function () {
    var s = document.createElement('style');
    s.textContent = css;
    (document.head || document.documentElement).appendChild(s);
  };
  if (document.documentElement) add();
  else document.addEventListener('readystatechange', add, { once: true });
})();
