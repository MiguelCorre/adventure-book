import { cpSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryDirectory = resolve(frontendDirectory, '..');
const sources = [
  resolve(repositoryDirectory, 'books'),
  resolve(repositoryDirectory, 'upload-samples'),
];
const backendTargetDirectory = resolve(repositoryDirectory, 'backend', 'target');
const target = resolve(backendTargetDirectory, 'e2e-books');

// Recursive cleanup may manage this one direct child of backend/target and nothing else.
if (relative(backendTargetDirectory, target) !== 'e2e-books') {
  throw new Error(`Refusing to manage unexpected E2E books directory: ${target}`);
}

switch (process.argv[2]) {
  case 'prepare':
    rmSync(target, { recursive: true, force: true });
    mkdirSync(target, { recursive: true });
    // Browser scenarios need playable content without changing the production boundary:
    // combine the four supplied books and the two upload samples only in this scratch copy.
    for (const source of sources) {
      for (const filename of readdirSync(source)) {
        cpSync(resolve(source, filename), resolve(target, filename));
      }
    }
    break;
  case 'clean':
    rmSync(target, { recursive: true, force: true });
    break;
  default:
    throw new Error('Expected catalogue-fixture.mjs prepare|clean');
}
