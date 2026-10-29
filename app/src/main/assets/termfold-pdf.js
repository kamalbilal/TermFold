#!/opt/node/bin/node
'use strict';
const fs = require('fs');
const http = require('http');
const path = require('path');
const os = require('os');
const { spawnSync } = require('child_process');

const VERSION = '2';

function config() {
  const file = path.join(os.homedir() || '/root', '.termfold', 'browser.json');
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    throw new Error('TermFold PDF Tools is not available (no ' + file + '). Is the TermFold app open?');
  }
}

function call(action, args) {
  const cfg = config();
  const { port, token, pdf_tools_session_id } = cfg;
  const body = Buffer.from(
    JSON.stringify({
      token,
      action,
      args: Object.assign({ session_id: pdf_tools_session_id || '' }, args || {}),
    }),
  );
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        host: '127.0.0.1',
        port,
        path: '/v1',
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Content-Length': body.length },
      },
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
    req.setTimeout(120000, () => req.destroy(new Error('TermFold did not answer in time.')));
    req.on('error', (e) => reject(new Error('Could not reach TermFold (' + e.message + ').')));
    req.end(body);
  });
}

function runPdfsys(argv) {
  const r = spawnSync('/usr/local/bin/pdfsys', argv, { encoding: 'utf8', timeout: 300000 });
  const text = (r.stdout || '') + (r.stderr || '');
  if (r.status !== 0) return { error: text.trim() || 'pdfsys failed' };
  try {
    return JSON.parse(text);
  } catch (e) {
    return { ok: true, raw: text };
  }
}

const TOOLS = [
  {
    name: 'pdf_inspect',
    action: 'pdf_tools_inspect',
    description: 'Refresh inspect.json for the active PDF Tools session (hashes and structure only).',
    props: {},
    required: [],
  },
  {
    name: 'pdf_model',
    action: 'pdf_tools_model',
    description: 'Write work/model.json and return page/element ids, form field names, and whether in-place replace is possible. Do not include document body in chat.',
    props: {},
    required: [],
  },
  {
    name: 'pdf_fields',
    action: 'pdf_tools_fields',
    description: 'Get or merge fields.json for the active session. op=get (default) or set. Do not invent values the user did not confirm.',
    props: {
      op: { type: 'string' },
      fields: { type: 'object' },
    },
    required: [],
  },
  {
    name: 'pdf_plan',
    action: 'pdf_tools_plan',
    description: 'Submit a plan of operations for user approval. Overlay kinds are rejected.',
    props: { plan: { type: 'object' } },
    required: ['plan'],
  },
  {
    name: 'pdf_apply',
    action: 'pdf_tools_apply',
    description: 'Apply an approved plan id to input/source.pdf and write out/output.pdf. Fails without OWN_TEMPLATE/OWN_DATA_DOC, user approval, or fields.json. Does not generate a placeholder letter when a source PDF exists.',
    props: { plan_id: { type: 'string' }, plan: { type: 'object' } },
    required: ['plan_id'],
  },
  {
    name: 'pdf_verify',
    action: 'pdf_tools_verify',
    description: 'Verify output.pdf: page count, QA report (SSIM), hashes. Fails if work/qa/report.json pass is false when source exists.',
    props: {},
    required: [],
  },
  {
    name: 'pdf_qa',
    action: 'pdf_tools_qa',
    description: 'Run work/qa_loop.py for Path B recreate. Writes work/qa/report.json; required before telling the user the file is ready.',
    props: {},
    required: [],
  },
  {
    name: 'pdf_refuse',
    action: 'pdf_tools_refuse',
    description: 'Record a refusal for third-party issued or unknown authorization.',
    props: { reason: { type: 'string' } },
    required: [],
  },
];

function mcp() {
  const send = (msg) => process.stdout.write(JSON.stringify(msg) + '\n');
  const rl = require('readline').createInterface({ input: process.stdin });
  const tools = TOOLS.map((t) => ({
    name: t.name,
    description: t.description,
    inputSchema: { type: 'object', properties: t.props, required: t.required || [] },
  }));
  rl.on('line', async (line) => {
    if (!line.trim()) return;
    let msg;
    try {
      msg = JSON.parse(line);
    } catch (e) {
      return;
    }
    const { id, method, params } = msg;
    if (id === undefined) return;
    try {
      if (method === 'initialize') {
        send({
          jsonrpc: '2.0',
          id,
          result: {
            protocolVersion: (params && params.protocolVersion) || '2025-06-18',
            capabilities: { tools: {} },
            serverInfo: { name: 'termfold-pdf', version: VERSION },
          },
        });
      } else if (method === 'ping') {
        send({ jsonrpc: '2.0', id, result: {} });
      } else if (method === 'tools/list') {
        send({ jsonrpc: '2.0', id, result: { tools } });
      } else if (method === 'tools/call') {
        const tool = TOOLS.find((t) => t.name === params.name);
        if (!tool) throw new Error('Unknown tool ' + params.name);
        let res;
        if (tool.name === 'pdf_inspect') {
          const cfg = config();
          const sid = cfg.pdf_tools_session_id;
          res = runPdfsys(['inspect', `/root/.termfold/pdf-tools/${sid}/input/source.pdf`, '--out', `/root/.termfold/pdf-tools/${sid}/inspect.json`]);
        } else if (tool.name === 'pdf_model') {
          const cfg = config();
          const sid = cfg.pdf_tools_session_id;
          const root = `/root/.termfold/pdf-tools/${sid}`;
          res = runPdfsys(['model', `${root}/input/source.pdf`, '--session', root]);
        } else {
          res = await call(tool.action, params.arguments || {});
        }
        const text = JSON.stringify(res);
        send({
          jsonrpc: '2.0',
          id,
          result: { content: [{ type: 'text', text }], isError: !!res.error },
        });
      } else {
        send({ jsonrpc: '2.0', id, error: { code: -32601, message: 'Method not found: ' + method } });
      }
    } catch (e) {
      send({ jsonrpc: '2.0', id, error: { message: String(e.message || e) } });
    }
  });
}

if (process.argv.includes('mcp')) mcp();
