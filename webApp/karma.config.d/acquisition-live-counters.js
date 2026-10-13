// Additive native Karma observability only. Keep the official Kotlin reporter/results unchanged.
// Opt-in task evidence only; unrelated/ordinary test invocations keep their existing reporters.
if (process.env.FURYBOOK_T18C_NATIVE_COUNTERS === '1') {
const counterFs = require('fs');
const counterPath = require('path');
const counterEvidence = process.env.FURYBOOK_FIREFOX_EVIDENCE;
const counterRoot = counterPath.resolve(__dirname, '../../../../.superpowers/sdd/32bab12f44441964/t18c-harness/runtime-runs');
if (!counterEvidence || !counterPath.isAbsolute(counterEvidence) ||
    counterPath.dirname(counterEvidence) !== counterRoot || !/^[A-Za-z0-9_.-]+$/.test(counterPath.basename(counterEvidence))) {
    throw new Error('T18C counters require the exact task-private runtime evidence directory');
}
const counterFile = counterPath.join(counterEvidence, 'native-browser-counters.jsonl');
const counterMarker = /T18C_WEB_(?:CASE|ORIGINAL_INPUT_PROGRESS|BINDING_PROGRESS|JS_PRESENTATION_DATA_COMPLETE|SAMPLE_CASE|SAMPLE_INPUT_PROGRESS|SAMPLE_PROGRESS|SAMPLE_CATEGORY|SAMPLE_COMPLETE|SAMPLE_PROCESSOR|CLASSIFICATION)\|/g;
function decodeCounterMessage(log) {
    if (!log.includes('##teamcity[')) return log;
    const output = /\bout='((?:\|.|[^'])*)'/.exec(log);
    if (!output) return '';
    const escapes = { '|': '|', n: '\n', r: '\r', '[': '[', ']': ']', "'": "'", x: '\u0085', l: '\u2028', p: '\u2029' };
    return output[1].replace(/\|([|nr\[\]'xlp])/g, (_, key) => escapes[key]);
}
function T18CNativeCounterReporter(baseReporterDecorator) {
    baseReporterDecorator(this);
    this.onBrowserLog = function(browser, log, type) {
        if (typeof log !== 'string' || !log.includes('T18C_WEB_')) return;
        const message = decodeCounterMessage(log);
        const matches = Array.from(message.matchAll(counterMarker));
        const fragments = matches.map((match, index) => message.slice(match.index,
            index + 1 < matches.length ? matches[index + 1].index : message.length).trim());
        if (fragments.length) counterFs.appendFileSync(counterFile,
            JSON.stringify({ receivedAtUnixMs: Date.now(), fragments: fragments }) + '\n');
    };
}
T18CNativeCounterReporter.$inject = ['baseReporterDecorator'];
if (!Array.isArray(config.plugins) || !Array.isArray(config.reporters)) throw new Error('Expected official KGP reporter/plugin arrays');
config.plugins.push({ 'reporter:t18cNativeCounters': ['type', T18CNativeCounterReporter] });
config.reporters.push('t18cNativeCounters');
}
