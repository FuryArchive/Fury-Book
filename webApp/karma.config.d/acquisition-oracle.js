// Test-only inputs are served directly from their unchanged source locations. They are never jsMain resources.
const oraclePath = require('path').resolve(__dirname, '../../../../rulesets/dubl-3.69/acquisition');
config.files.push({ pattern: oraclePath + '/expected_prices.json', included: false, served: true, watched: false });
config.files.push({ pattern: oraclePath + '/author_expected_price_updates.json', included: false, served: true, watched: false });
config.proxies['/acquisition-oracle/'] = '/absolute' + oraclePath + '/';
// The existing resource fragment selects the explicit bounded full-matrix timeout after this fragment.
