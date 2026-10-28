#!/opt/node/bin/node
// termfold-browser: lets agents use TermFold's preview browser.
//
//   termfold-browser open http://localhost:5173     termfold-browser snapshot
//   termfold-browser click 12                       termfold-browser fill 15 "hi@example.com"
//   termfold-browser screenshot                     termfold-browser console --errors
//   termfold-browser mcp                            (an MCP server on stdio, for agents)
//
// It talks to the TermFold app over loopback, with the key the app writes to
// ~/.termfold/browser.json. Installed by TermFold; changes here are overwritten.
'use strict';
const fs = require('fs');
const http = require('http');
const path = require('path');
const os = require('os');

const VERSION = '1';

function config() {
  const file = path.join(os.homedir() || '/root', '.termfold', 'browser.json');
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    throw new Error('The TermFold browser is not available (no ' + file + '). Is the TermFold app open?');
  }
}

function call(action, args) {
  const { port, token, automation_session_key, agent_session_key } = config();
  // The agent's working folder: screenshots and relative uploads go to its project.
  const base = {
    cwd: process.cwd(),
    session_key: automation_session_key || agent_session_key || '',
  };
  const body = Buffer.from(JSON.stringify({ token, action, args: Object.assign(base, args || {}) }));
  return new Promise((resolve, reject) => {
    const req = http.request(
      { host: '127.0.0.1', port, path: '/v1', method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': body.length } },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => {
          try {
            resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
          } catch (e) {
            reject(new Error('Unreadable answer from TermFold.'));
          }
        });
      },
    );
    req.setTimeout(180000, () => req.destroy(new Error('TermFold did not answer in time.')));
    req.on('error', (e) => reject(new Error('Could not reach the TermFold browser (' + e.message + '). Is the TermFold app open?')));
    req.end(body);
  });
}

// ---- Tools -------------------------------------------------------------------------------------

const REF = { type: 'integer', description: 'Element number from browser_snapshot, e.g. 12.' };
const TEXT_TARGET = { type: 'string', description: 'Visible text or label of the element, used when no ref is given.' };
const SELECTOR = { type: 'string', description: 'CSS selector, used when no ref is given.' };

const TOOLS = [
  { name: 'browser_open', action: 'open', description: 'Open a page in the TermFold browser the user can see: a URL, a local port like 5173 (localhost), or a project file path like ./index.html. Use new_tab to keep the current tab.', props: { url: { type: 'string', description: 'URL, port or file path.' }, new_tab: { type: 'boolean' }, tab_id: { type: 'integer' } }, required: ['url'] },
  { name: 'browser_snapshot', action: 'snapshot', description: 'Read the page: a short numbered list of the buttons, links and fields on screen. Use the numbers with the other tools. Use find to look for a control anywhere on the page, changed to see only what differs since the last snapshot, scope to read one part, all for everything. The full list is saved to /tmp/termfold-browser/snapshot.txt.', props: { find: { type: 'string', description: 'Only controls whose label, value or link contains this.' }, scope: { type: 'string', description: 'Element number or CSS selector: read only inside it.' }, changed: { type: 'boolean', description: 'Only what differs from the last snapshot.' }, all: { type: 'boolean', description: 'Include off-screen controls.' }, links: { type: 'boolean', description: 'Show link addresses.' } } },
  { name: 'browser_outline', action: 'outline', description: 'List only the headings of the page: its shape for very few tokens.', props: {} },
  { name: 'browser_click', action: 'click', description: 'Tap an element with a real touch (it is scrolled into view first). Give ref, or text, or selector, or x/y in CSS pixels.', props: { ref: REF, text: TEXT_TARGET, selector: SELECTOR, x: { type: 'number' }, y: { type: 'number' } } },
  { name: 'browser_fill', action: 'fill', description: 'Replace the contents of a text field (input, textarea or editable element) with text. Set submit to press Enter afterwards. If it is a password on a real website, warn the user it passed through you and recommend changing it after use.', props: { ref: REF, selector: SELECTOR, text: { type: 'string', description: 'What to type.' }, submit: { type: 'boolean' } }, required: ['text'] },
  { name: 'browser_type', action: 'type', description: 'Type text with real key presses into whatever is focused (after a click).', props: { text: { type: 'string' } }, required: ['text'] },
  { name: 'browser_press', action: 'press', description: 'Press a key or combination: Enter, Tab, Escape, Backspace, ArrowDown, PageDown, Control+A, Shift+Tab...', props: { key: { type: 'string' } }, required: ['key'] },
  { name: 'browser_select', action: 'select', description: 'Choose an option in a dropdown (select element) by its label or value.', props: { ref: REF, selector: SELECTOR, value: { type: 'string' } }, required: ['value'] },
  { name: 'browser_check', action: 'check', description: 'Tick or untick a checkbox, radio button or switch.', props: { ref: REF, text: TEXT_TARGET, selector: SELECTOR, checked: { type: 'boolean', description: 'true to tick (default), false to untick.' } } },
  { name: 'browser_hover', action: 'hover', description: 'Move the pointer over an element to show hover menus or tooltips.', props: { ref: REF, text: TEXT_TARGET, selector: SELECTOR } },
  { name: 'browser_scroll', action: 'scroll', description: 'Scroll the page with a real swipe: direction up/down (amount in screens, default 0.8) or top/bottom; or give ref/text/selector to bring an element into view.', props: { direction: { type: 'string', enum: ['up', 'down', 'top', 'bottom'] }, amount: { type: 'number' }, ref: REF, text: TEXT_TARGET, selector: SELECTOR } },
  { name: 'browser_wait', action: 'wait', description: 'Wait for text, selector, URL substring, network_idle, or captcha_cleared (user solves on device).', props: { text: { type: 'string' }, selector: SELECTOR, url: { type: 'string' }, network_idle: { type: 'boolean' }, captcha_cleared: { type: 'boolean' }, quiet_ms: { type: 'number' }, timeout: { type: 'number', description: 'Seconds, default 10.' }, tab_id: { type: 'integer' } } },
  { name: 'browser_screenshot', action: 'screenshot', description: 'Screenshot of what the browser shows (or of one element: give ref, text or selector). Not kept; set save only when the user wants it kept in the project. Set if_changed to skip it when the page looks the same as the last one.', props: { ref: REF, text: TEXT_TARGET, selector: SELECTOR, save: { type: 'boolean' }, if_changed: { type: 'boolean' } }, image: true },
  { name: 'browser_text', action: 'text', description: 'Readable text of the page, or of one part with selector. Cut at max characters (default 8000).', props: { selector: SELECTOR, max: { type: 'number' } } },
  { name: 'browser_console', action: 'console', description: 'Read the page console: errors, warnings and console.log output since the page loaded.', props: { errors_only: { type: 'boolean' } } },
  { name: 'browser_network', action: 'network', description: 'Network capture summary (no bodies). op: list (default), preserve, preserve_status, clear. List: since_cursor and/or since_generation (from health network_generation), failed_only, xhr_only, url_contains, limit (max 80). Bodies via browser_network_detail.', props: { op: { type: 'string', enum: ['list', 'preserve', 'preserve_status', 'clear'] }, enabled: { type: 'boolean' }, since_cursor: { type: 'integer' }, since_generation: { type: 'integer' }, failed_only: { type: 'boolean' }, xhr_only: { type: 'boolean' }, url_contains: { type: 'string' }, limit: { type: 'integer' } } },
  { name: 'browser_network_detail', action: 'network_detail', description: 'One captured request: entry_id + part (overview|headers|request_body|response_body). Bodies truncated for tokens.', props: { entry_id: { type: 'integer' }, part: { type: 'string' } }, required: ['entry_id'] },
  { name: 'browser_network_rules', action: 'network_rules', description: 'Session-scoped Chrome-like network rules. op: list, add_block, add_override, remove, clear. Match URL patterns with * and ?. Do not patch window.fetch. add_override takes url_pattern, body, optional status and headers (response headers, e.g. CORS).', props: { op: { type: 'string', enum: ['list', 'add_block', 'add_override', 'remove', 'clear'] }, url_pattern: { type: 'string' }, body: { type: 'string' }, status: { type: 'number' }, headers: { type: 'object' }, id: { type: 'string', description: 'Rule id for remove.' } }, required: ['op'] },
  { name: 'browser_eval', action: 'eval', description: 'Run JavaScript in the page (DevTools-console context). console.log during eval appears in Preview console (Gecko). Use await_ms (0–60000) or await:true (10s) to resolve returned promises. Multi-statement scripts OK.', props: { script: { type: 'string' }, await_ms: { type: 'integer', description: 'Milliseconds to wait when the script returns a Promise (default 0).' }, await: { type: 'boolean', description: 'Shorthand: wait up to 10s for a Promise.' } }, required: ['script'] },
  { name: 'browser_navigate', action: null, description: 'Go back, forward, or reload the page.', props: { action: { type: 'string', enum: ['back', 'forward', 'reload'] } }, required: ['action'] },
  { name: 'browser_viewport', action: 'viewport', description: 'Lay the page out at phone width, desktop width, or fit to the pane, to check responsive design.', props: { mode: { type: 'string', enum: ['phone', 'desktop', 'fit'] } }, required: ['mode'] },
  { name: 'browser_upload', action: 'upload', description: 'Choose a file from the Linux environment for a file field (<input type=file>).', props: { ref: REF, selector: SELECTOR, text: TEXT_TARGET, path: { type: 'string', description: 'File path, absolute or relative to the project.' } }, required: ['path'] },
  { name: 'browser_health', action: 'health', description: 'JSON summary: URL, title, viewport, console errors, failed requests, visible controls, captcha_pending.', props: {} },
  { name: 'browser_tab_list', action: 'tab_list', description: 'List open preview tabs with id, title, url, and which is active.', props: {} },
  { name: 'browser_tab_new', action: 'tab_new', description: 'Open a new tab, optionally with a URL.', props: { url: { type: 'string' } } },
  { name: 'browser_tab_switch', action: 'tab_switch', description: 'Switch the active tab.', props: { tab_id: { type: 'integer' } }, required: ['tab_id'] },
  { name: 'browser_tab_close', action: 'tab_close', description: 'Close a tab by id.', props: { tab_id: { type: 'integer' } }, required: ['tab_id'] },
  { name: 'browser_dismiss_captcha', action: 'dismiss_captcha', description: 'Dismiss a false-positive CAPTCHA banner so the agent can continue.', props: {} },
];

function resolveUrl(url) {
  if (!url) return url;
  // Relative file paths are relative to where the agent is working.
  if (url.startsWith('./') || url.startsWith('../') || (!url.includes('://') && !url.startsWith('/') && !url.startsWith('~') && /\.(html?|svg)$/i.test(url))) {
    return path.resolve(process.cwd(), url);
  }
  return url;
}

async function runTool(tool, args) {
  args = Object.assign({}, args || {});
  let action = tool.action;
  if (tool.name === 'browser_navigate') action = args.action;
  if (action === 'open') args.url = resolveUrl(args.url);
  if (tool.image) args.image = true;
  return call(action, args);
}

// ---- MCP server --------------------------------------------------------------------------------

function mcp() {
  const send = (msg) => process.stdout.write(JSON.stringify(msg) + '\n');
  const rl = require('readline').createInterface({ input: process.stdin });
  rl.on('line', async (line) => {
    if (!line.trim()) return;
    let msg;
    try {
      msg = JSON.parse(line);
    } catch (e) {
      return;
    }
    const { id, method, params } = msg;
    if (id === undefined) return; // notifications
    try {
      if (method === 'initialize') {
        send({ jsonrpc: '2.0', id, result: {
          protocolVersion: (params && params.protocolVersion) || '2025-06-18',
          capabilities: { tools: {} },
          serverInfo: { name: 'termfold-browser', version: VERSION },
          instructions: 'The TermFold browser is a real browser on the user\'s screen. Open a page, read it with browser_snapshot, act on elements by their number, and check your work with browser_screenshot and browser_console.',
        } });
      } else if (method === 'ping') {
        send({ jsonrpc: '2.0', id, result: {} });
      } else if (method === 'tools/list') {
        send({ jsonrpc: '2.0', id, result: { tools: TOOLS.map((t) => ({
          name: t.name,
          description: t.description,
          inputSchema: { type: 'object', properties: t.props, required: t.required || [] },
        })) } });
      } else if (method === 'tools/call') {
        const tool = TOOLS.find((t) => t.name === params.name);
        if (!tool) throw new Error('Unknown tool ' + params.name);
        const res = await runTool(tool, params.arguments);
        const content = [];
        if (res.error) content.push({ type: 'text', text: res.error });
        if (res.text) content.push({ type: 'text', text: res.text });
        if (res.image) content.push({ type: 'image', data: res.image, mimeType: res.mime || 'image/jpeg' });
        if (!content.length) content.push({ type: 'text', text: 'Done.' });
        send({ jsonrpc: '2.0', id, result: { content, isError: !!res.error } });
      } else {
        send({ jsonrpc: '2.0', id, error: { code: -32601, message: 'Method not found: ' + method } });
      }
    } catch (e) {
      if (method === 'tools/call') send({ jsonrpc: '2.0', id, result: { content: [{ type: 'text', text: e.message }], isError: true } });
      else send({ jsonrpc: '2.0', id, error: { code: -32603, message: e.message } });
    }
  });
}

// ---- Command line ------------------------------------------------------------------------------

const HELP = `termfold-browser: use TermFold's browser (the preview the user sees).

  open <url|port|file>          open a page (5173, localhost:3000, ./index.html, https://...)
  snapshot [--find <text>] [--scope <ref|css>] [--changed] [--all] [--links]
                                numbered buttons, links and fields on screen (short); the full list is
                                saved to /tmp/termfold-browser/snapshot.txt to grep. --changed shows only
                                what differs from the last snapshot
  outline                       just the page's headings
  click <ref|"text">            tap an element (number from snapshot, or its visible text)
  fill <ref> <text> [--submit]  replace a field's contents (and press Enter)
  type <text>                   type into the focused element
  press <key>                   Enter, Tab, Escape, ArrowDown, Control+A ...
  select <ref> <option>         choose in a dropdown
  check <ref> [--off]           tick (or untick) a checkbox or switch
  hover <ref>                   move the pointer over an element
  scroll <up|down|top|bottom|ref> [amount]
  wait <text> [seconds]         wait for text to appear (--selector <css> for a selector)
  screenshot [ref|"text"] [--if-changed] [--save]
                                screenshot (of one element if given) to a temporary file; prints its path
                                (open it to look). --if-changed skips it when nothing changed;
                                --save keeps a full-quality copy in the project's screenshots/ folder
  text [--selector <css>] [--max <chars>]
                                readable text of the page or one part (default 8000 characters)
  console [--errors]            console messages and errors
  network list [--failed] [--xhr] [--since-cursor N] [--url Q] [--limit N]
  network preserve on|off|status
  network detail <id> [--part headers|request_body|response_body|overview]
  network-rules list|add_block|add_override|remove|clear
                                session-only URL block / body override (not a fetch patch)
  eval <javascript> [--await-ms N] [--await]
                                run JavaScript in the page; optional promise wait
  back | forward | reload
  viewport <phone|desktop|fit>
  upload <ref> <file>           choose a file for a file field
  mcp                           run as an MCP server on stdio`;

function target(value) {
  if (value === undefined) return {};
  return /^\d+$/.test(value) ? { ref: parseInt(value, 10) } : { text: value };
}

async function cli(argv) {
  const [cmd, ...rest] = argv;
  const flag = (f) => { const i = rest.indexOf(f); if (i >= 0) { rest.splice(i, 1); return true; } return false; };
  const option = (f) => { const i = rest.indexOf(f); if (i >= 0) { const v = rest[i + 1]; rest.splice(i, 2); return v; } return undefined; };
  let action = cmd;
  let args = {};
  switch (cmd) {
    case undefined: case 'help': case '-h': case '--help':
      console.log(HELP); return 0;
    case 'mcp':
      mcp(); return null;
    case 'open': args = { url: resolveUrl(rest.join(' ')) }; break;
    case 'back': case 'forward': case 'reload': case 'status': case 'outline': break;
    case 'snapshot': {
      const find = option('--find'); const scope = option('--scope');
      args = { all: flag('--all'), links: flag('--links'), changed: flag('--changed') };
      if (find) args.find = find;
      if (scope) args.scope = scope;
      break;
    }
    case 'text': {
      const selector = option('--selector'); const max = option('--max');
      args = {};
      if (selector) args.selector = selector;
      if (max) args.max = parseInt(max, 10);
      break;
    }
    case 'click': case 'hover': args = target(rest.join(' ') || undefined); break;
    case 'fill': { const submit = flag('--submit'); args = Object.assign(target(rest[0]), { text: rest.slice(1).join(' '), submit }); if (args.text === undefined) args.text = ''; if (!('ref' in args)) { args = { selector: rest[0], text: rest.slice(1).join(' '), submit }; } break; }
    case 'type': args = { text: rest.join(' ') }; break;
    case 'press': args = { key: rest.join('+') }; break;
    case 'select': args = Object.assign(target(rest[0]), { value: rest.slice(1).join(' ') }); break;
    case 'check': { const off = flag('--off'); args = Object.assign(target(rest.join(' ')), { checked: !off }); break; }
    case 'scroll': {
      const first = rest[0] || 'down';
      if (['up', 'down', 'top', 'bottom'].includes(first)) args = { direction: first, amount: rest[1] ? parseFloat(rest[1]) : undefined };
      else args = target(first);
      break;
    }
    case 'wait': { const sel = option('--selector'); args = sel ? { selector: sel } : {}; if (rest[0]) args.text = rest[0]; if (rest[1]) args.timeout = parseFloat(rest[1]); break; }
    case 'screenshot': { const save = flag('--save'); const ifChanged = flag('--if-changed'); args = Object.assign(target(rest.join(' ') || undefined), { save, if_changed: ifChanged }); break; }
    case 'console': args = { errors_only: flag('--errors') }; break;
    case 'network': {
      const sub = rest[0] || 'list';
      if (sub === 'preserve') {
        args = { op: 'preserve', enabled: rest[1] === 'on' || rest[1] === 'true' };
        if (rest[1] === 'off' || rest[1] === 'false') args.enabled = false;
        if (rest[1] === 'status') args = { op: 'preserve_status' };
      } else if (sub === 'detail') {
        action = 'network_detail';
        args = { entry_id: parseInt(rest[1], 10), part: option('--part') || 'overview' };
      } else if (sub === 'clear') {
        args = { op: 'clear' };
      } else {
        args = {
          op: 'list',
          failed_only: flag('--failed'),
          xhr_only: flag('--xhr'),
          since_cursor: option('--since-cursor') ? parseInt(option('--since-cursor'), 10) : undefined,
          since_generation: option('--since-generation') ? parseInt(option('--since-generation'), 10) : undefined,
          url_contains: option('--url'),
          limit: option('--limit') ? parseInt(option('--limit'), 10) : undefined,
        };
      }
      break;
    }
    case 'network-rules': {
      action = 'network_rules';
      const op = rest[0];
      args = { op: op };
      const pattern = option('--pattern') || option('--url');
      if (pattern) args.url_pattern = pattern;
      const body = option('--body');
      if (body) args.body = body;
      const id = option('--id');
      if (id) args.id = id;
      if (op === 'add_block' && !args.url_pattern && rest[1]) args.url_pattern = rest[1];
      if (op === 'add_override' && !args.url_pattern && rest[1]) {
        args.url_pattern = rest[1];
        if (!args.body && rest[2]) args.body = rest.slice(2).join(' ');
      }
      if (op === 'remove' && !args.id && rest[1]) args.id = rest[1];
      break;
    }
    case 'eval': {
      const awaitMs = option('--await-ms');
      const awaitFlag = flag('--await');
      const scriptParts = [];
      for (let i = 0; i < rest.length; i++) {
        if (rest[i] === '--await-ms' || rest[i] === '--await') {
          if (rest[i] === '--await-ms') i++;
          continue;
        }
        scriptParts.push(rest[i]);
      }
      args = { script: scriptParts.join(' ') };
      if (awaitMs) args.await_ms = parseInt(awaitMs, 10);
      if (awaitFlag) args.await = true;
      break;
    }
    case 'viewport': args = { mode: rest[0] }; break;
    case 'upload': args = Object.assign(target(rest[0]), { path: path.resolve(process.cwd(), rest[1] || '') }); break;
    default:
      console.error('Unknown command: ' + cmd + '\n\n' + HELP); return 2;
  }
  const res = await call(action, args);
  if (res.text) console.log(res.text);
  if (res.error) { console.error(res.error); return 1; }
  return 0;
}

const result = cli(process.argv.slice(2));
Promise.resolve(result).then((code) => { if (code !== null && code !== undefined) process.exitCode = code; }).catch((e) => { console.error(e.message); process.exitCode = 1; });
