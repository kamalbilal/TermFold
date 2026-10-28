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
    throw new Error('TermFold PR Review is not available (no ' + file + '). Is the TermFold app open?');
  }
}

function call(action, args) {
  const cfg = config();
  const { port, token, pr_review_session_id } = cfg;
  const body = Buffer.from(
    JSON.stringify({
      token,
      action,
      args: Object.assign({ session_id: pr_review_session_id || '' }, args || {}),
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

const REPORT_PROPS = {
  schemaVersion: { type: 'integer' },
  summary: { type: 'string' },
  risk: { type: 'string', enum: ['low', 'medium', 'high'] },
  findings: { type: 'array' },
  coverage: { type: 'object' },
  missingTests: { type: 'array' },
  openQuestions: { type: 'array' },
};

const TOOLS = [
  {
    name: 'submit_pr_review_report',
    action: 'pr_review_submit_report',
    description:
      'Submit the structured PR review report for this turn. Call once when the review is ready, ' +
      'before asking which finding ids to fix. Arguments must match schemaVersion 1.',
    props: {
      report: { type: 'object', properties: REPORT_PROPS, required: ['schemaVersion', 'summary', 'risk', 'findings'] },
      user_message_id: { type: 'integer', description: 'Optional; TermFold assigns the active turn if omitted.' },
    },
    required: ['report'],
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
            serverInfo: { name: 'termfold-pr-review', version: VERSION },
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
