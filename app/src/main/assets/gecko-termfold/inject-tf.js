if(!window.__tf)window.__tf=(function(){
var next=1,SHADOW_MAX=4;
function visible(el){var r=el.getBoundingClientRect();if(r.width<1||r.height<1)return false;var s=getComputedStyle(el);return s.visibility!=='hidden'&&s.display!=='none'&&parseFloat(s.opacity||'1')>0.02;}
function clean(t){return (t||'').replace(/\s+/g,' ').trim();}
function name(el){var n=el.getAttribute('aria-label')||'';
if(!n&&el.labels&&el.labels.length)n=el.labels[0].innerText;
if(!n&&el.getAttribute('aria-labelledby')){var l=document.getElementById(el.getAttribute('aria-labelledby'));if(l)n=l.innerText;}
if(!n)n=el.getAttribute('placeholder')||el.getAttribute('alt')||el.getAttribute('title')||'';
var t=el.tagName;
if(!n&&t!=='INPUT'&&t!=='TEXTAREA'&&t!=='SELECT')n=el.innerText||el.textContent;
if(!n&&t==='INPUT'&&/^(submit|button|reset)$/i.test(el.type))n=el.value;
if(!n&&el.querySelector){var im=el.querySelector('img[alt],svg[aria-label],[aria-label]');if(im)n=im.getAttribute('alt')||im.getAttribute('aria-label');}
if(!n&&el.name)n=el.name;
n=clean(n);return n.length>80?n.slice(0,77)+'...':n;}
function role(el){var r=el.getAttribute('role');if(r)return r;var t=el.tagName.toLowerCase();
if(t==='a')return 'link';if(t==='button'||t==='summary')return 'button';if(t==='select')return 'select';if(t==='textarea')return 'textbox';
if(t==='input'){var y=(el.type||'text').toLowerCase();if(y==='checkbox'||y==='radio')return y;if(/^(submit|button|reset|image)$/i.test(y))return 'button';if(y==='range')return 'slider';if(y==='file')return 'file';return y==='text'?'textbox':'textbox:'+y;}
if(el.isContentEditable)return 'textbox';return 'clickable';}
var SEL='a[href],button,input:not([type=hidden]),textarea,select,summary,[role=button],[role=link],[role=checkbox],[role=radio],[role=tab],[role=menuitem],[role=switch],[role=option],[role=combobox],[role=textbox],[role=slider],[contenteditable=""],[contenteditable=true],[onclick],[tabindex]:not([tabindex="-1"])';
function ref(el){if(!el.dataset.tfRef){while(document.querySelector('[data-tf-ref="'+next+'"]'))next++;el.dataset.tfRef=String(next++);}return +el.dataset.tfRef;}
function scrollBucket(){var se=document.scrollingElement||document.documentElement;return Math.floor((se.scrollTop||0)/400);}
function hint(el){return role(el)+'|'+name(el)+'|b'+scrollBucket();}
function describe(el){var d={ref:ref(el),role:role(el),name:name(el),hint:hint(el)};var exp=el.getAttribute('aria-expanded');if(exp==='true'||exp==='false')d.expanded=exp==='true';if(d.role==='combobox'&&exp==null)d.expanded=!!el.querySelector('[role=listbox],[role=menu]');return d;}
function collect(root,depth,out,cap){if(!root||out.length>=cap)return;var els=root.querySelectorAll?root.querySelectorAll(SEL):[];for(var i=0;i<els.length&&out.length<cap;i++){var el=els[i];if(!visible(el)||el.closest('[aria-hidden=true]'))continue;out.push(el);}
if(depth>=SHADOW_MAX)return;for(var j=0;j<els.length&&out.length<cap;j++){var sh=els[j].shadowRoot;if(sh)collect(sh,depth+1,out,cap);}}
function resolveFrame(o){if(!o||!o.frame)return {root:document,error:null};
if(o.frame.ref!=null){var fr=document.querySelector('[data-tf-ref="'+o.frame.ref+'"]');if(!fr||fr.tagName!=='IFRAME')return {error:'frame not found'};try{var d=fr.contentDocument;if(!d)return {error:'cross_origin_frame'};return {root:d};}catch(e){return {error:'cross_origin_frame'};}}
if(o.frame.index!=null){var frames=document.querySelectorAll('iframe');var idx=+o.frame.index;if(idx<0||idx>=frames.length)return {error:'frame not found'};try{var d2=frames[idx].contentDocument;if(!d2)return {error:'cross_origin_frame'};return {root:d2};}catch(e){return {error:'cross_origin_frame'};}}
if(o.frame.url){var frames2=document.querySelectorAll('iframe'),q=String(o.frame.url).toLowerCase(),k;for(k=0;k<frames2.length;k++){try{var u=frames2[k].contentWindow.location.href;if(u.toLowerCase().indexOf(q)>=0)return {root:frames2[k].contentDocument};}catch(e){}}return {error:'frame not found'};}
return {root:document,error:null};}
function captchaDetect(root){root=root||document;var txt=(root.body&&root.body.innerText||'').toLowerCase();
if(/verify you are human|complete the security check|unusual traffic|are you a robot/.test(txt))return true;
var ifr=root.querySelectorAll('iframe');for(var i=0;i<ifr.length;i++){var s=(ifr[i].src||'').toLowerCase();if(/recaptcha|hcaptcha|challenges\\.cloudflare\\.com|captcha/.test(s))return true;}
var nodes=root.querySelectorAll('[aria-label],[title],[class],[id]');for(var j=0;j<nodes.length;j++){var a=String(nodes[j].getAttribute('aria-label')||nodes[j].title||nodes[j].className||nodes[j].id||'').toLowerCase();if(a.indexOf('captcha')>=0)return true;}
return false;}
function snapshot(o){o=o||{};var fr=resolveFrame(o);if(fr.error)return {error:fr.error};
var root=fr.root;if(o.scope){root=find(o.scope,fr.root);if(!root)return {error:'scope not found'};}
var raw=[];collect(root,0,raw,500);var out=[],shadowCapped=false;
for(var i=0;i<raw.length&&out.length<400;i++){var el=raw[i];
var r=el.getBoundingClientRect();var it=describe(el);
if(el.tagName==='SELECT'){var op=el.options[el.selectedIndex];it.value=op?clean(op.text):'';}
else if(el.tagName==='TEXTAREA'||(el.tagName==='INPUT'&&it.role.indexOf('textbox')===0)){it.value=el.type==='password'?(el.value?'****':''):String(el.value).slice(0,80);}
if(el.type==='checkbox'||el.type==='radio')it.checked=!!el.checked;
if(el.getAttribute('aria-checked'))it.checked=el.getAttribute('aria-checked')==='true';
if(el.disabled||el.getAttribute('aria-disabled')==='true')it.disabled=true;
if(el.tagName==='A')it.href=el.getAttribute('href');
it.visible=r.bottom>0&&r.top<innerHeight;
if(it.role==='combobox'&&it.expanded){var opts=el.querySelectorAll('[role=option]');if(opts.length)it.options=[].map.call(opts,function(o){return clean(o.innerText).slice(0,60);}).slice(0,20);}
out.push(it);}
var heads=[];document.querySelectorAll('h1,h2,h3').forEach(function(h){if(heads.length<25&&visible(h))heads.push(h.tagName.toLowerCase()+': '+clean(h.innerText).slice(0,100));});
var se=document.scrollingElement||document.documentElement;
return {title:document.title,url:location.href,scrollY:Math.round(se.scrollTop),scrollHeight:se.scrollHeight,viewHeight:innerHeight,headings:heads,items:out,shadowCapped:shadowCapped,captcha:captchaDetect(document)};}
function find(a,root){root=root||document;
if(a.ref!=null){var e=root.querySelector('[data-tf-ref="'+a.ref+'"]');if(e)return e;}
if(a.selector){try{var e2=root.querySelector(a.selector);if(e2)return e2;}catch(x){}}
if(a.text){var q=String(a.text).toLowerCase(),els=[],c=collect;collect(root,0,els,800);
for(var i=0;i<els.length;i++){if(!visible(els[i]))continue;var n=name(els[i]).toLowerCase();if(n===q)return els[i];}
var best=null;for(var j=0;j<els.length;j++){if(!visible(els[j]))continue;var n2=name(els[j]).toLowerCase();if(!best&&n2.indexOf(q)>=0)best=els[j];}
if(best)return best;}
return null;}
function vv(){return window.visualViewport||{offsetLeft:0,offsetTop:0,width:innerWidth,height:innerHeight};}
function point(a){var fr=resolveFrame(a);if(fr.error)return {error:fr.error};var el=find(a,fr.root);if(!el)return {error:'not found'};
el.scrollIntoView({block:'center',inline:'center',behavior:'instant'});
var r=el.getBoundingClientRect(),v=vv(),d=describe(el);
d.x=r.left+r.width/2-v.offsetLeft;d.y=r.top+r.height/2-v.offsetTop;d.vw=v.width;d.vh=v.height;
d.type=el.type||'';d.disabled=!!el.disabled||el.getAttribute('aria-disabled')==='true';return d;}
function viewport(){var v=vv();return {vw:v.width,vh:v.height};}
function fill(a){var fr=resolveFrame(a);if(fr.error)return {error:fr.error};var el=find(a,fr.root);if(!el)return {error:'not found'};
el.focus();var f=el.tagName==='INPUT'||el.tagName==='TEXTAREA';
if(f){try{el.select();}catch(x){}}else if(el.isContentEditable){var s=getSelection(),g=document.createRange();g.selectNodeContents(el);s.removeAllRanges();s.addRange(g);}
var ok=false;try{ok=a.text===''?document.execCommand('delete'):document.execCommand('insertText',false,a.text);}catch(x){}
if(!ok||(f&&el.value!==a.text)){if(f){var p=el.tagName==='INPUT'?HTMLInputElement.prototype:HTMLTextAreaElement.prototype;
Object.getOwnPropertyDescriptor(p,'value').set.call(el,a.text);el.dispatchEvent(new Event('input',{bubbles:true}));}
else if(el.isContentEditable){el.textContent=a.text;el.dispatchEvent(new Event('input',{bubbles:true}));}}
el.dispatchEvent(new Event('change',{bubbles:true}));
var d=describe(el);d.password=el.type==='password';d.value=d.password?'':String(f?el.value:clean(el.innerText)).slice(0,120);return d;}
function select(a){var fr=resolveFrame(a);if(fr.error)return {error:fr.error};var el=find(a,fr.root);if(!el)return {error:'not found'};if(el.tagName!=='SELECT')return {error:'That element is not a dropdown (select). Click it and pick the option instead.'};
var v=String(a.value).toLowerCase(),hit=null,i;
for(i=0;i<el.options.length;i++){var o=el.options[i];if(o.value.toLowerCase()===v||clean(o.text).toLowerCase()===v){hit=o;break;}}
if(!hit)for(i=0;i<el.options.length;i++){if(clean(el.options[i].text).toLowerCase().indexOf(v)>=0){hit=el.options[i];break;}}
if(!hit)return {error:'No option matches "'+a.value+'".',options:[].map.call(el.options,function(o){return clean(o.text);}).slice(0,50)};
el.value=hit.value;el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));
var d=describe(el);d.value=clean(hit.text);return d;}
function state(a){var fr=resolveFrame(a);if(fr.error)return {error:fr.error};var el=find(a,fr.root);if(!el)return {error:'not found'};var d=describe(el);d.checked=(el.type==='checkbox'||el.type==='radio')?!!el.checked:el.getAttribute('aria-checked')==='true';return d;}
function hover(a){var fr=resolveFrame(a);if(fr.error)return {error:fr.error};var el=find(a,fr.root);if(!el)return {error:'not found'};['pointerover','pointerenter','mouseover','mouseenter','mousemove'].forEach(function(t){el.dispatchEvent(new MouseEvent(t,{bubbles:t.indexOf('enter')<0}));});return describe(el);}
function pos(){var se=document.scrollingElement||document.documentElement;return {scrollY:Math.round(se.scrollTop),scrollHeight:se.scrollHeight,viewHeight:innerHeight};}
function text(o){o=o||{};var root=o.selector?document.querySelector(o.selector):document.body;if(!root)return {error:'not found'};var t=root.innerText||'';var max=o.max||8000;return {title:document.title,url:location.href,text:t.length>max?t.slice(0,max)+'\n...(cut at '+max+' of '+t.length+' characters; pass a selector or a larger max)':t};}
function outline(){var heads=[];document.querySelectorAll('h1,h2,h3,h4').forEach(function(h){if(heads.length<60&&visible(h)){var l=+h.tagName.charAt(1);heads.push('  '.repeat(l-1)+h.tagName.toLowerCase()+': '+clean(h.innerText).slice(0,90));}});return {title:document.title,url:location.href,headings:heads};}
function box(a){var fr=resolveFrame(a);if(fr.error)return {error:fr.error};var el=find(a,fr.root);if(!el)return {error:'not found'};el.scrollIntoView({block:'center',inline:'center',behavior:'instant'});var r=el.getBoundingClientRect();return {x:r.left,y:r.top,w:r.width,h:r.height,vw:vv().width};}
function has(a){if(a.captcha_cleared)return {found:!captchaDetect(document)};
if(a.url){var u=location.href;return {found:u.indexOf(String(a.url))>=0};}
if(a.selector){try{var e=document.querySelector(a.selector);return {found:!!e&&visible(e)};}catch(x){return {found:false};}}
if(a.text)return {found:!!document.body&&document.body.innerText.toLowerCase().indexOf(String(a.text).toLowerCase())>=0};
return {found:document.readyState==='complete'};}
return {snapshot:snapshot,point:point,viewport:viewport,fill:fill,select:select,state:state,hover:hover,pos:pos,text:text,has:has,outline:outline,box:box,captchaDetect:function(){return captchaDetect(document)}};
})();

(function installTermfoldConsoleHook() {
  var s = document.createElement("script");
  s.textContent =
    "(function(){if(window.__termfoldConsoleHook)return;window.__termfoldConsoleHook=true;function emit(level,args){var msg=Array.prototype.map.call(args,function(a){try{return typeof a==='object'?JSON.stringify(a):String(a);}catch(e){return String(a);}}).join(' ');document.dispatchEvent(new CustomEvent('termfold-console',{detail:{level:level,text:msg}}));}['log','info','warn','error','debug'].forEach(function(level){var orig=console[level];if(!orig)return;console[level]=function(){emit(level,arguments);return orig.apply(console,arguments);};});})();";
  try {
    (document.head || document.documentElement).appendChild(s);
    s.remove();
  } catch (e) {
    /* CSP */
  }
})();

