import { expect, test } from '@playwright/test';

import { captureBrowserErrors } from './support';

const BROKEN_BOOK = JSON.stringify({
  title: 'A Book That Cannot Be Finished',
  author: 'E. Tester',
  difficulty: 'EASY',
  sections: [
    {
      id: 1,
      text: 'You set out.',
      type: 'BEGIN',
      options: [{ description: 'Head nowhere', gotoId: 999 }],
    },
    { id: 666, text: 'A room with no doors.', type: 'NODE' },
  ],
});
const EXPECTED_REJECTION_DIAGNOSTIC = [
  'console: Failed to load resource: the server responded with a status of 422 (Unprocessable Entity)',
];

let browserErrors: string[];

test.beforeEach(async ({ page }) => {
  browserErrors = captureBrowserErrors(page);
  await page.goto('/');
  await expect(page.locator('app-book-card').first()).toBeVisible();
  await page.getByRole('button', { name: '+ Add a book' }).click();
});

async function upload(page: import('@playwright/test').Page, name: string, contents: string) {
  await page.locator('input[type="file"]').setInputFiles({
    name,
    mimeType: 'application/json',
    buffer: Buffer.from(contents),
  });
}

test('a rejected book lists every problem at once and is not added', async ({ page }) => {
  const booksBefore = await page.locator('app-book-card').count();

  await upload(page, 'broken.json', BROKEN_BOOK);

  const report = page.getByRole('alert');
  await expect(report).toContainText('not valid and was not added to the library');
  // No END section, a reference that goes nowhere, and a section with no way out: all
  // three come back in one response, so a curator fixes them in one pass.
  await expect(page.locator('.upload__issues li')).toHaveCount(3);
  await expect(report).toContainText(/no END section/);
  await expect(report).toContainText(/points at section 999, which does not exist/);
  await expect(report).toContainText(/Section 666 is not an ending/);

  // The panel stays open, holding the reasons, and the library is untouched.
  await expect(page.locator('.upload__panel')).toBeVisible();
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(page.locator('app-book-card')).toHaveCount(booksBefore);
  expect(browserErrors).toEqual(EXPECTED_REJECTION_DIAGNOSTIC);
});

test('a file that is not a book at all is refused politely', async ({ page }) => {
  await upload(page, 'notes.json', 'this is not a book');

  await expect(page.getByRole('alert')).toContainText('not valid and was not added');
  await expect(page.locator('.upload__issues li')).toHaveCount(1);
  // The parser's own wording is not asserted, only that the reader is told the file could
  // not be read at all rather than which rule it broke.
  await expect(page.getByRole('alert')).toContainText('could not be read as an adventure book');
  expect(browserErrors).toEqual(EXPECTED_REJECTION_DIAGNOSTIC);
});

test('an accepted book is published immediately and can be played', async ({ page }, testInfo) => {
  // A retry gets a fresh slug in case the first attempt reached the server before failing.
  const title =
    testInfo.retry === 0 ? 'The Brass Meridian' : `The Brass Meridian ${testInfo.retry}`;
  const validBook = JSON.stringify({
    title,
    author: 'Mara Vale',
    difficulty: 'EASY',
    sections: [
      {
        id: 1,
        text: 'A brass compass draws a line across the empty dawn.',
        type: 'BEGIN',
        options: [{ description: 'Follow the shining meridian', gotoId: 2 }],
      },
      {
        id: 2,
        text: 'At noon, the line folds into a road home.',
        type: 'END',
        options: [],
      },
    ],
  });
  const booksBefore = await page.locator('app-book-card').count();

  await upload(page, 'brass-meridian.json', validBook);

  const card = page.locator('app-book-card').filter({ hasText: title });
  await expect(page.locator('app-book-card')).toHaveCount(booksBefore + 1);
  await expect(card).toHaveCount(1);
  await expect(card.getByText('EASY', { exact: true })).toBeVisible();
  await expect(card.getByRole('button', { name: 'Begin Quest' })).toBeEnabled();

  await card.getByRole('button', { name: 'Begin Quest' }).click();
  await expect(page).toHaveURL(/\/play\//);
  await expect(page.getByText('A brass compass draws a line across the empty dawn.')).toBeVisible();
  expect(browserErrors).toEqual([]);
});
