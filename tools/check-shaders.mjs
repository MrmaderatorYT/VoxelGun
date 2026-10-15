// Desktop check for the same include/define variants used by ShaderLibrary on Android.
// Usage: node tools/check-shaders.mjs /path/to/glslangValidator
import {readFileSync, readdirSync, mkdtempSync, writeFileSync, rmSync} from 'node:fs';
import {join, dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {tmpdir} from 'node:os';
import {spawnSync} from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../app/src/main/assets/shaders');
const validator = process.argv[2];
if (!validator) throw new Error('Pass the glslangValidator executable path.');
const output = mkdtempSync(join(tmpdir(), 'voxelgun-shaders-'));
function expand(file, stack = []) {
    if (stack.includes(file)) throw new Error(`Cyclic shader include: ${file}`);
    return readFileSync(join(root, file), 'utf8').replace(/^\s*#include "([^"]+)"\s*$/gm,
        (_, included) => expand(included, [...stack, file]));
}
try {
    let checked = 0;
    for (const file of readdirSync(root).filter(name => /\.(vert|frag)$/.test(name))) {
        for (const define of file === 'rt.frag' ? ['', '#define REFLECTIONS\n'] : ['']) {
            const target = join(output, file);
            writeFileSync(target, '#version 300 es\n' + define + expand(file));
            const result = spawnSync(validator, [target], {encoding: 'utf8'});
            if (result.status !== 0) throw new Error(`${file} ${define}\n${result.error?.message ?? ''}\n${result.stdout ?? ''}\n${result.stderr ?? ''}`);
            checked++;
        }
    }
    console.log(`Validated ${checked} GLES 3.0 shader variants.`);
} finally {
    // Only this invocation's freshly-created temporary directory.
    rmSync(output, {recursive: true});
}
