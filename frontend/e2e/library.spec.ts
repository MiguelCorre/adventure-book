import { expect, test } from '@playwright/test';

import { bookCard } from './support';

const SUPPLIED_BOOKS = ['The Crystal Caverns', 'dragon-quest', 'Pirates of the Jade Sea', 'The Prisoner'];
const OUR_BOOKS = ['The Clockwork Lighthouse', 'The Sunken Orchard'];

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('app-book-card').first()).toBeVisible();
});

test('lists every book, and only the two we wrote can be played', async ({ page }) => {
  await expect(page.locator('app-book-card')).toHaveCount(6);

  for (const title of OUR_BOOKS) {
    await expect(bookCard(page, title).getByRole('button', { name: 'Begin Quest' })).toBeEnabled();
  }

  // Every book that came with the exercise is structurally broken.
  for (const title of SUPPLIED_BOOKS) {
    const card = bookCard(page, title);
    await expect(card.getByText('Unplayable')).toBeVisible();
    await expect(card.getByRole('button', { name: 'Begin Quest' })).toBeDisabled();
  }
});

test('explains why each supplied book cannot be finished', async ({ page }) => {
  const prisoner = bookCard(page, 'The Prisoner');
  await prisoner.getByRole('button', { name: /Show 1 problem/ }).click();
  await expect(prisoner.getByText(/Section 666 is not an ending/)).toBeVisible();

  // The empty file never became a book at all, and says so in a full sentence.
  const dragon = bookCard(page, 'dragon-quest');
  await dragon.getByRole('button', { name: /Show 1 problem/ }).click();
  await expect(dragon.getByText(/could not be read as an adventure book: file is empty/)).toBeVisible();

  // Both of the pirates' opening choices are broken, for two different reasons.
  const pirates = bookCard(page, 'Pirates of the Jade Sea');
  await pirates.getByRole('button', { name: /Show 2 problems/ }).click();
  await expect(pirates.getByText(/points at section 999, which does not exist/)).toBeVisible();
  await expect(pirates.getByText(/Section 666 is not an ending/)).toBeVisible();
});

test('narrows the library by difficulty and by author', async ({ page }) => {
  await page.getByRole('button', { name: 'EASY', exact: true }).click();
  await expect(page.locator('app-book-card')).toHaveCount(2);

  // Difficulties are additive, not exclusive.
  await page.getByRole('button', { name: 'HARD', exact: true }).click();
  await expect(page.locator('app-book-card')).toHaveCount(3);
  await expect(bookCard(page, 'The Prisoner')).toBeVisible();

  await page.getByRole('button', { name: 'EASY', exact: true }).click();
  await page.getByRole('button', { name: 'HARD', exact: true }).click();
  await expect(page.locator('app-book-card')).toHaveCount(6);

  await page.getByPlaceholder('Search adventures...').fill('stormrider');
  await expect(page.locator('app-book-card')).toHaveCount(1);
  await expect(bookCard(page, 'The Crystal Caverns')).toBeVisible();

  await page.getByPlaceholder('Search adventures...').fill('submarine');
  await expect(page.getByText('No adventures match your search')).toBeVisible();
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await expect(page.locator('app-book-card')).toHaveCount(6);
});

test('keeps the previous results on screen while the next set is fetched', async ({ page }) => {
  // Slow the API down enough to observe the in-between state.
  await page.route('**/api/books*', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 600));
    await route.continue();
  });

  await page.getByRole('button', { name: 'MEDIUM', exact: true }).click();

  // Mid-flight: the old cards are still there, marked busy, rather than replaced by a
  // loading message.
  await expect(page.locator('.library__results--refreshing')).toBeVisible();
  await expect(page.locator('app-book-card')).toHaveCount(6);
  await expect(page.locator('.library__grid')).toHaveAttribute('aria-busy', 'true');

  await expect(page.locator('app-book-card')).toHaveCount(2, { timeout: 5000 });
  await expect(page.locator('.library__results--refreshing')).toHaveCount(0);
});

/**
 * The one behaviour jsdom cannot express: a shorter result set shortens the document, and
 * the browser clamps the scroll position upwards the instant that happens below the
 * current offset. The unit test for this can only assert that a stubbed geometry API was
 * called; here the real jump either happens or it does not.
 */
test('does not let the page jump when filtering from the bottom', async ({ page }) => {
  // Tall enough that the filters are still on screen at the foot of the page, which is the
  // only way a reader can press one from down there — and exactly the case that jumped.
  await page.setViewportSize({ width: 900, height: 1000 });
  await expect(page.locator('app-book-card')).toHaveCount(6);

  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  const before = await page.evaluate(() => ({
    scrollY: Math.round(window.scrollY),
    documentHeight: document.documentElement.scrollHeight,
  }));
  expect(before.scrollY, 'the page must actually be scrolled').toBeGreaterThan(100);
  // If Playwright had to scroll the chip into view, the scenario would be a different one.
  await expect(page.getByRole('button', { name: 'MEDIUM', exact: true })).toBeInViewport();

  await page.getByRole('button', { name: 'MEDIUM', exact: true }).click();
  await expect(page.locator('app-book-card')).toHaveCount(2);
  await expect(page.locator('.library__results--refreshing')).toHaveCount(0);
  // Wait for the scroll to come to rest and the frozen height to be released.
  await page.waitForFunction(() => window.scrollY === 0);
  await page.waitForFunction(() => !document.querySelector<HTMLElement>('.library__results')!.style.minHeight);

  const after = await page.evaluate(() => ({
    scrollY: Math.round(window.scrollY),
    documentHeight: document.documentElement.scrollHeight,
    controlsVisible: document.querySelector('.library__controls')!.getBoundingClientRect().top >= 0,
  }));

  // The document did get shorter, which is the condition that used to cause the jump.
  expect(after.documentHeight).toBeLessThan(before.documentHeight);
  // Offset zero is reachable at any document height, so the release cannot be clamped.
  expect(after.scrollY, 'must end at the top, deliberately').toBe(0);
  expect(after.controlsVisible, 'the filters the reader just used must be on screen').toBe(true);
});

test('leaves a reader who is already at the top exactly where they are', async ({ page }) => {
  await page.setViewportSize({ width: 900, height: 1000 });
  await expect(page.locator('app-book-card')).toHaveCount(6);
  expect(await page.evaluate(() => Math.round(window.scrollY))).toBe(0);

  await page.getByRole('button', { name: 'MEDIUM', exact: true }).click();
  await expect(page.locator('app-book-card')).toHaveCount(2);

  expect(await page.evaluate(() => Math.round(window.scrollY))).toBe(0);
  // Nothing was frozen, because there was no jump to prevent.
  expect(await page.evaluate(
    () => document.querySelector<HTMLElement>('.library__results')!.style.minHeight,
  )).toBe('');
});
