#!/opt/node/bin/node
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
  const { port, token, automation_session_key } = config();
  const body = Buffer.from(
    JSON.stringify({
      token,
      action,
      args: Object.assign({ session_key: automation_session_key || '' }, args || {}),
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

const TOOLS = [
  { name: 'session_memory_search', action: 'memory_session_search', description: 'Semantic search this automation session memory.', props: { query: { type: 'string' }, limit: { type: 'integer' } }, required: ['query'] },
  { name: 'session_log_recent', action: 'memory_session_recent', description: 'Recent browser and automation events in this session.', props: { limit: { type: 'integer' } } },
  { name: 'nav_memory_search', action: 'memory_nav_search', description: 'Search global site navigation memory (no credentials).', props: { site_key: { type: 'string' }, query: { type: 'string' }, limit: { type: 'integer' } }, required: ['site_key', 'query'] },
  { name: 'nav_memory_write', action: 'memory_nav_write', description: 'Write navigation-only memory for a site (never passwords).', props: { site_key: { type: 'string' }, kind: { type: 'string' }, title: { type: 'string' }, body: { type: 'string' }, search_text: { type: 'string' } }, required: ['site_key', 'kind', 'title'] },
  { name: 'session_secret_put', action: 'memory_secret_put', description: 'Store a named secret for this session only.', props: { name: { type: 'string' }, description: { type: 'string' }, payload: { type: 'string' } }, required: ['name', 'payload'] },
  { name: 'session_secret_get', action: 'memory_secret_get', description: 'Retrieve a session secret by name.', props: { name: { type: 'string' } }, required: ['name'] },
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
            serverInfo: { name: 'termfold-automation-memory', version: VERSION },
          },
        });
      } else if (method === 'ping') {
        send({ jsonrpc: '2.0', id, result: {} });
      } else if (method === 'tools/list') {
        send({ jsonrpc: '2.0', id, result: { tools } });
      } else if (method === 'tools/call') {
        const tool = TOOLS.find((t) => t.name === params.name);
        if (!tool) throw new Error('Unknown tool ' + params.name);
        const res = await call(tool.action, params.arguments || {});
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
