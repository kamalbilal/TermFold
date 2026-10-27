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
    throw new Error('TermFold PM memory is not available (no ' + file + '). Is the TermFold app open?');
  }
}

function call(action, args) {
  const { port, token, pm_session_id } = config();
  const body = Buffer.from(
    JSON.stringify({
      token,
      action,
      args: Object.assign({ session_id: pm_session_id || '' }, args || {}),
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
  { name: 'pm_memory_search', action: 'pm_memory_search', description: 'Semantic search this PM session memory.', props: { query: { type: 'string' }, limit: { type: 'integer' } }, required: ['query'] },
  { name: 'pm_memory_write', action: 'pm_memory_write', description: 'Write a finding or decision to PM session memory.', props: { kind: { type: 'string' }, title: { type: 'string' }, body: { type: 'string' }, search_text: { type: 'string' } }, required: ['kind', 'title'] },
  { name: 'pm_memory_get', action: 'pm_memory_get', description: 'Fetch one PM memory chunk by id.', props: { id: { type: 'integer' } }, required: ['id'] },
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
            serverInfo: { name: 'termfold-pm-memory', version: VERSION },
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
