const resourcePath = require('path').resolve(__dirname, '../../../../webApp/build/processedResources/js/main/fcp');
config.files.push({ pattern: resourcePath + '/**/*', included: false, served: true, watched: false });
config.proxies['/fcp/'] = '/absolute' + resourcePath + '/';
// Full real-catalog/controller integration measured4.251s in Firefox; the default2s
// Mocha unit-test limit expires before its final assertions. Keep a bounded10s gate.
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: process.env.FURYBOOK_T18C_MATRIX === "1" ? 1800000 : 10000 });
