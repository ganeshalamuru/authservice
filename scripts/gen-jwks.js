#!/usr/bin/env node
/*
 * gen-jwks.js — generate the `jwt_jwks` secret for authservice: an RS256 JWK Set with the signing
 * key's private params included. No external deps (uses Node's built-in `crypto`); needs Node 16+.
 *
 *   node gen-jwks.js                    # fresh 2048-bit key, pretty-printed to stdout
 *   node gen-jwks.js --compact          # single line (typical for the secret file)
 *   node gen-jwks.js --from-pem k.pem   # build from an existing PKCS#8 private-key PEM (preserve key)
 *   node gen-jwks.js --out FILE         # write to FILE instead of stdout
 *
 * Save the output as the extensionless file `$SECRETS_DIR/jwt_jwks` (config tree reads the whole
 * file, so pretty or compact both parse). The FIRST key in "keys" is the active signer — to rotate,
 * prepend a freshly generated key to the array and keep the old one until its tokens expire.
 */
'use strict';
const crypto = require('crypto');
const fs = require('fs');

const args = process.argv.slice(2);
const valueOf = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : undefined; };
const compact = args.includes('--compact');
const fromPem = valueOf('--from-pem');
const outFile = valueOf('--out');

const jwk = fromPem
  ? crypto.createPrivateKey(fs.readFileSync(fromPem)).export({ format: 'jwk' })
  : crypto.generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey.export({ format: 'jwk' });

// RFC 7638 thumbprint => stable kid (RSA members in lexicographic order: e, kty, n).
const kid = crypto.createHash('sha256')
  .update(JSON.stringify({ e: jwk.e, kty: jwk.kty, n: jwk.n }))
  .digest('base64url');

const key = {
  kty: jwk.kty, kid, use: 'sig', alg: 'RS256',
  n: jwk.n, e: jwk.e, d: jwk.d, p: jwk.p, q: jwk.q, dp: jwk.dp, dq: jwk.dq, qi: jwk.qi,
};
const json = JSON.stringify({ keys: [key] }, null, compact ? 0 : 2);

if (outFile) {
  fs.writeFileSync(outFile, json);
  process.stderr.write(`wrote ${outFile} (kid=${kid})\n`);
} else {
  process.stdout.write(json + '\n');
}
