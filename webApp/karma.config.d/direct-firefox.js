const path = require('path');
const launchScript = process.env.FURYBOOK_FIREFOX_LAUNCH_SCRIPT;
if (!launchScript || !path.isAbsolute(launchScript)) throw new Error('Set an absolute FURYBOOK_FIREFOX_LAUNCH_SCRIPT');
for (const key of ['FIREFOX_BIN', 'FURYBOOK_FIREFOX_PROFILE']) {
  if (!process.env[key] || !path.isAbsolute(process.env[key])) throw new Error('Set an absolute ' + key);
}
if (!Array.isArray(config.plugins)) throw new Error('Expected generated KGP plugin array');
config.plugins.push(require('karma-script-launcher'));
config.browsers = [launchScript];
config.hostname = '127.0.0.1';
config.listenAddress = '127.0.0.1';
config.port = 9876;
config.retryLimit = 0;
config.browserDisconnectTolerance = 0;
// The official Kotlin helper consumes args while parsing filters. Keep those args intact.
config.client.furybookIdbPhase = process.env.FURYBOOK_IDB_REOPEN_PHASE || 'normal';
if (config.browsers.length !== 1 || config.browsers[0] !== launchScript) throw new Error('Unexpected browser selection');
console.log('FURYBOOK_FINAL_BROWSER_CONFIG|' + JSON.stringify({browsers: config.browsers, hostname: config.hostname, port: config.port, phase: config.client.furybookIdbPhase}));
