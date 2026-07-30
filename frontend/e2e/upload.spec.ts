import { expect, test } from '@playwright/test';

const BROKEN_BOOK = JSON.stringify({
  title: 'A Book That Cannot Be Finished',
  author: 'E. Tester',
  difficulty: 'EASY',
  sections: [
    { id: 1, text: 'You set out.', type: 'BEGIN', options: [{ description: 'Head nowhere', gotoId: 999 }] },
    { id: 666, text: 'A room with no doors.', type: 'NODE' },
  ],
});

/**
 * Only rejections are exercised here, on purpose. A successful upload writes a file into
 * the books directory and there is no delete endpoint to undo it, so the happy path would
 * leave a book behind in the working tree on every run. It is covered instead by
 * BookUploadTest, which points the application at a scratch directory it can clean up.
 */
test.beforeEach(async ({ page }) => {
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
});

test('a file that is not a book at all is refused politely', async ({ page }) => {
  await upload(page, 'notes.json', 'this is not a book');

  await expect(page.getByRole('alert')).toContainText('not valid and was not added');
  await expect(page.locator('.upload__issues li')).toHaveCount(1);
  // The parser's own wording is not asserted, only that the reader is told the file could
  // not be read at all rather than which rule it broke.
  await expect(page.getByRole('alert')).toContainText('could not be read as an adventure book');
});
