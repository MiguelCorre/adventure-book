import { cpSync, rmSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryDirectory = resolve(frontendDirectory, '..');
const source = resolve(repositoryDirectory, 'books');
const backendTargetDirectory = resolve(repositoryDirectory, 'backend', 'target');
const target = resolve(backendTargetDirectory, 'e2e-books');

// Recursive cleanup may manage this one direct child of backend/target and nothing else.
if (relative(backendTargetDirectory, target) !== 'e2e-books') {
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
