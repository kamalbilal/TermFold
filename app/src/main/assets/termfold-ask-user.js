#!/opt/node/bin/node
'use strict';
const fs = require('fs');
const http = require('http');
const path = require('path');
const os = require('os');

const VERSION = '1';
/** Match [AskUserTimeouts.NODE_HTTP_MS] on Android — user may read many options for hours. */
const HTTP_WAIT_MS = 24 * 60 * 60 * 1000;

function config() {
  const file = path.join(os.homedir() || '/root', '.termfold', 'browser.json');
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    throw new Error('TermFold is not reachable (no ' + file + '). Open the agent chat in the app.');
  }
}

function call(args) {
  const cfg = config();
  const { port, token } = cfg;
  const sessionKey = cfg.agent_session_key || cfg.automation_session_key || '';
  const body = Buffer.from(
    JSON.stringify({
      token,
      action: 'ask_user',
      args: Object.assign({ session_key: sessionKey }, args || {}),
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
    req.setTimeout(HTTP_WAIT_MS, () =>
      req.destroy(new Error('TermFold ask_user HTTP wait exceeded (should not happen under 24h).')),
    );
    req.on('error', (e) => reject(new Error('Could not reach TermFold (' + e.message + ').')));
    req.end(body);
  });
}

const TOOL = {
  name: 'ask_user',
  description:
    'Ask the user one or more multiple-choice questions in the TermFold chat UI (radio/checkbox, Other text, Continue/Skip). ' +
    'Works in Agent, Plan, and Ask modes. Prefer this over typing questions in chat when options would help. ' +
    'When the user asked for several questions in one message, pass them all in questions[] (one card, one Continue). ' +
    'Returns immediately with outcome termfold_deferred; TermFold stops the agent turn. ' +
    'The user submits answers as their next chat message — do not retry or run other tools until that message arrives.',
  props: {
    title: { type: 'string', description: 'Optional card title (default: Questions)' },
    toolCallId: { type: 'string', description: 'Optional correlation id' },
    questions: {
      type: 'array',
      description: 'Each item: id, prompt, options[{id,label}], optional allowMultiple',
      items: { type: 'object' },
    },
  },
  required: ['questions'],
};

function mcp() {
  const send = (msg) => process.stdout.write(JSON.stringify(msg) + '\n');
  const rl = require('readline').createInterface({ input: process.stdin });
  const tools = [
    {
      name: TOOL.name,
      description: TOOL.description,
      inputSchema: { type: 'object', properties: TOOL.props, required: TOOL.required },
    },
  ];
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
            serverInfo: { name: 'termfold-ask-user', version: VERSION },
          },
        });
      } else if (method === 'ping') {
        send({ jsonrpc: '2.0', id, result: {} });
      } else if (method === 'tools/list') {
        send({ jsonrpc: '2.0', id, result: { tools } });
      } else if (method === 'tools/call') {
        if (params.name !== TOOL.name) throw new Error('Unknown tool ' + params.name);
        const res = await call(params.arguments || {});
        if (res.error) {
          send({
            jsonrpc: '2.0',
            id,
            result: { content: [{ type: 'text', text: JSON.stringify(res) }], isError: true },
          });
        } else {
          send({
            jsonrpc: '2.0',
            id,
            result: { content: [{ type: 'text', text: JSON.stringify(res) }], isError: false },
          });
        }
      } else {
        send({ jsonrpc: '2.0', id, error: { code: -32601, message: 'Method not found: ' + method } });
      }
    } catch (e) {
      send({ jsonrpc: '2.0', id, error: { message: String(e.message || e) } });
    }
  });
}

if (process.argv.includes('mcp')) mcp();
