import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';

const moduleUrl = new URL('../site/password-recovery.mjs', import.meta.url);
test('recovery implementation exists', () => assert.ok(existsSync(moduleUrl), 'Password recovery has not been implemented'));
const api = existsSync(moduleUrl) ? await import(moduleUrl.href) : {};
const response = (status, body) => ({ok: status >= 200 && status < 300, status, json: async () => body});

for (const [name, hash, expected] of [
  ['empty', '', {mode: 'request'}],
  ['recovery link', '#type=recovery&access_token=secret&refresh_token=refresh', {mode:'reset', token:'secret'}],
  ['signup remains signup', '#type=signup&access_token=secret', {mode:'request'}],
  ['token without recovery type', '#access_token=secret', {mode:'request'}],
  ['missing token', '#type=recovery', {mode:'invalid'}],
  ['expired link', '#error=access_denied&error_code=otp_expired', {mode:'invalid'}],
  ['error beats token', '#type=recovery&access_token=secret&error_code=bad_jwt', {mode:'invalid'}],
]) test(name, {skip: !api.parseRecoveryHash}, () => assert.deepEqual(api.parseRecoveryHash(hash), expected));

test('password policy: length, confirmation, no trimming', {skip:!api.validatePassword}, () => {
  assert.match(api.validatePassword('short','short'), /8/);
  assert.match(api.validatePassword('longpassword','otherpassword'), /совпадают/);
  assert.equal(api.validatePassword('  secret  ','  secret  '), null);
});

test('request sends only email and existing /app/ redirect with publishable key', {skip:!api.createRecoveryClient}, async () => {
  const calls = [];
  const client = api.createRecoveryClient(async (...args) => {calls.push(args); return response(200, {});});
  await client.requestReset(' person@example.com ', 'https://furyarchive.github.io/Fury-Book/app/');
  assert.equal(calls.length, 1);
  const [url, options] = calls[0];
  assert.equal(new URL(url).pathname, '/auth/v1/recover');
  assert.equal(new URL(url).searchParams.get('redirect_to'), 'https://furyarchive.github.io/Fury-Book/app/');
  assert.deepEqual(JSON.parse(options.body), {email:'person@example.com'});
  assert.equal(options.method,'POST');
  assert.match(options.headers.apikey, /^sb_publishable_/);
  assert.equal(options.headers.Authorization, undefined);
  assert.equal(options.credentials, 'omit');
});

test('invalid email never reaches API', {skip:!api.createRecoveryClient}, async () => {
  let calls = 0;
  const client = api.createRecoveryClient(async () => {calls++; return response(200, {});});
  await assert.rejects(client.requestReset('not-an-email', 'https://furyarchive.github.io/Fury-Book/app/'), /email/);
  assert.equal(calls,0);
});

test('verifies the emailed token on server, never an existing local session', {skip:!api.createRecoveryClient}, async () => {
  let request;
  const client = api.createRecoveryClient(async (...args) => {request=args; return response(200,{id:'user', email:'person@example.com'});});
  const user = await client.verifyRecovery('emailed-token');
  assert.equal(user.email,'person@example.com');
  assert.equal(request[0], 'https://xmmekahkeervdvrywmys.supabase.co/auth/v1/user');
  assert.equal(request[1].method,'GET');
  assert.equal(request[1].headers.Authorization,'Bearer emailed-token');
});

test('missing recovery token cannot verify or change password', {skip:!api.createRecoveryClient}, async () => {
  let calls = 0;
  const client = api.createRecoveryClient(async () => {calls++; return response(200, {});});
  await assert.rejects(client.verifyRecovery(''), /ссылк/i);
  await assert.rejects(client.updatePassword('', 'long-password'), /ссылк/i);
  assert.equal(calls,0);
});

test('password update only modifies password of recovery user', {skip:!api.createRecoveryClient}, async () => {
  let request;
  const client = api.createRecoveryClient(async (...args) => {request=args; return response(200,{id:'user'});});
  await client.updatePassword('emailed-token', '  long-password  ');
  assert.equal(request[0], 'https://xmmekahkeervdvrywmys.supabase.co/auth/v1/user');
  assert.equal(request[1].method,'PUT');
  assert.equal(request[1].headers.Authorization,'Bearer emailed-token');
  assert.deepEqual(JSON.parse(request[1].body),{password:'  long-password  '});
});

for (const [code, status, message] of [
  ['over_email_send_rate_limit',429,/слишком|часто/i],
  ['bad_jwt',401,/ссылк/i],
  ['same_password',422,/отличаться/i],
  ['weak_password',422,/пароль/i],
  ['email_address_not_authorized',400,/отправк|почт/i],
]) test(`API error ${code}`, {skip:!api.createRecoveryClient}, async () => {
  const client = api.createRecoveryClient(async () => response(status,{code,message:'untrusted server detail'}));
  await assert.rejects(client.requestReset('person@example.com','https://furyarchive.github.io/Fury-Book/app/'), message);
});

test('unknown email has the same successful request outcome', {skip:!api.createRecoveryClient}, async () => {
  const client = api.createRecoveryClient(async () => response(404,{code:'user_not_found'}));
  await assert.doesNotReject(client.requestReset('unknown@example.com','https://furyarchive.github.io/Fury-Book/app/'));
});

test('network failure is readable and does not include credentials', {skip:!api.createRecoveryClient}, async () => {
  const client = api.createRecoveryClient(async () => {throw new TypeError('Failed to fetch');});
  await assert.rejects(client.verifyRecovery('private-token'), /соединение|интернет/i);
});

test('logout affects only the recovery session', {skip:!api.createRecoveryClient}, async () => {
  let request;
  const client = api.createRecoveryClient(async (...args) => {request=args; return response(204,null);});
  await client.closeRecovery('emailed-token');
  assert.equal(new URL(request[0]).searchParams.get('scope'),'local');
  assert.equal(request[1].method,'POST');
});

// The app bundle must not start on a recovery callback: its normal bootstrap syncs characters.
const { runInNewContext } = await import('node:vm');
const entryUrl = new URL('../webApp/src/jsMain/resources/index.html', import.meta.url);
test('web entry intercepts recovery before starting the character app', () => {
  assert.ok(existsSync(entryUrl), 'Recovery callback routing is missing');
  const html = readFileSync(entryUrl,'utf8');
  const script = html.match(/<script data-auth-entry>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script, 'Missing guarded app entry script');
  for (const [hash, expectRecovery] of [
    ['#type=recovery&access_token=secret', true],
    ['#error_code=otp_expired&error=access_denied', true],
    ['#type=signup&access_token=signup', false],
    ['', false],
  ]) {
    const redirected = [], loaded = [];
    runInNewContext(script, {
      URLSearchParams,
      window: {location:{hash,replace: url => redirected.push(url)}},
      document:{createElement: () => ({}), body:{appendChild: el => loaded.push(el.src)}},
    });
    assert.equal(redirected.length, Number(expectRecovery),hash);
    assert.deepEqual(loaded, expectRecovery ? [] : ['fury-book.js'],hash);
    if (expectRecovery) assert.equal(redirected[0], '../reset-password.html'+hash);
  }
});
