import { cpSync, rmSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryDirectory = resolve(frontendDirectory, '..');
const source = resolve(repositoryDirectory, 'books');
const target = resolve(repositoryDirectory, 'backend', 'target', 'e2e-books');

// Keep cleanup tied to one literal-purpose directory even if this script is edited later.
if (target !== resolve(repositoryDirectory, 'backend', 'target', 'e2e-books')) {
  throw new Error(`Refusing to manage unexpected E2E books directory: ${target}`);
}

switch (process.argv[2]) {
  case 'prepare':
    rmSync(target, { recursive: true, force: true });
    cpSync(source, target, { recursive: true });
    break;
  case 'clean':
    rmSync(target, { recursive: true, force: true });
    break;
  default:
    throw new Error('Expected catalogue-fixture.mjs prepare|clean');
}
