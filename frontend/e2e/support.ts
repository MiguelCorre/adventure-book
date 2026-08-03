import { Locator, Page, expect } from '@playwright/test';

/** Captures browser-level failures so every scenario can assert a clean console. */
export function captureBrowserErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push(`console: ${message.text()}`);
    }
  });
  page.on('pageerror', (error) => errors.push(`page: ${error.message}`));
  return errors;
}

/** The card for a given book title. */
export function bookCard(page: Page, title: string): Locator {
  return page.locator('app-book-card').filter({ hasText: title });
}

/** Opens a book from the library and waits for the first section to be on screen. */
export async function beginQuest(page: Page, title: string): Promise<void> {
  await bookCard(page, title).getByRole('button', { name: 'Begin Quest' }).click();
  await expect(page).toHaveURL(/\/play\//);
  await expect(page.locator('.section__text').first()).toBeVisible();
}

/**
 * Takes the choice whose text starts with the given words.
 *
 * <p>Matching on the prose rather than an index keeps these specs readable and means they
 * fail loudly if a book is edited, instead of quietly walking a different path.
 */
export async function choose(page: Page, startsWith: string): Promise<void> {
  const choice = page.locator('.choice').filter({ hasText: startsWith });
  await expect(choice).toHaveCount(1);
  await choice.click();
}

export function health(page: Page): Locator {
  return page.locator('.game-header__health-count');
}

export async function expectHealth(page: Page, value: string): Promise<void> {
  await expect(health(page)).toHaveText(value);
}

/** Proves the rendered document fits the viewport rather than hiding horizontal overflow. */
export async function expectNoHorizontalOverflow(page: Page): Promise<void> {
  const dimensions = await page.evaluate(() => ({
    content: document.documentElement.scrollWidth,
    viewport: document.documentElement.clientWidth,
  }));
  expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport);
}
