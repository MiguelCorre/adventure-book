import { expect, test } from '@playwright/test';

import { beginQuest, choose, expectHealth } from './support';

test.beforeEach(async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('app-book-card').first()).toBeVisible();
});

test('the header carries everything the brief asks for', async ({ page }) => {
  await beginQuest(page, 'The Clockwork Lighthouse');

  const header = page.locator('app-game-header');
  await expect(header.getByRole('button', { name: /Back to Library/ })).toBeVisible();
  await expect(header.getByRole('heading', { name: 'The Clockwork Lighthouse' })).toBeVisible();
  await expect(header.getByText('10/10')).toBeVisible();
  await expect(header.getByRole('button', { name: /Save Progress/ })).toBeEnabled();
});

test('leaving an unfinished adventure requires confirmation across navigation methods', async ({ page }) => {
  await beginQuest(page, 'The Clockwork Lighthouse');
  const gameUrl = page.url();

  page.once('dialog', (dialog) => dialog.dismiss());
  await page.getByRole('button', { name: /Back to Library/ }).click();
  await expect(page).toHaveURL(gameUrl);
  await expect(page.locator('.choice')).toHaveCount(2);

  page.once('dialog', (dialog) => dialog.accept());
  await page.goBack();
  await expect(page).toHaveURL('/');
});

test('a careful route reaches an ending without a scratch', async ({ page }) => {
  await beginQuest(page, 'The Clockwork Lighthouse');

  await choose(page, 'Take the outer stair');
  await choose(page, 'Keep climbing');
  await choose(page, 'Duck through the open window');
  await choose(page, 'Fetch the crank handle');

  await expect(page.getByText('Your adventure is complete')).toBeVisible();
  await expect(page.locator('.section__text').filter({ hasText: /the lens begins to turn/ })).toHaveCount(1);
  await expectHealth(page, '10/10');
  await expect(page.locator('.choice')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Play Again' })).toBeVisible();
});

test('a reckless route spends the health bar and kills the player', async ({ page }) => {
  await beginQuest(page, 'The Clockwork Lighthouse');

  await choose(page, 'Force the seaward door');
  await expectHealth(page, '6/10');
  await expect(page.locator('.consequence--harm')).toContainText('−4');
  await expect(page.locator('.consequence--harm')).toContainText('flagstones');

  await choose(page, 'Wade straight through');
  await expectHealth(page, '2/10');

  await choose(page, 'Pry the jammed gear');

  await expectHealth(page, '0/10');
  await expect(page.getByText('Your adventure ends here')).toBeVisible();
  await expect(page.getByText('That last choice cost you 5 health')).toBeVisible();
  // The fatal prose belongs in the consequence banner, and appears exactly once.
  await expect(page.getByText(/fingers still inside it/)).toHaveCount(1);
  await expect(page.locator('app-game-over')).not.toContainText(/fingers still inside it/);
  // Death leaves the player on the section that killed them, whose choices must not be
  // offered: the store would refuse them, and a button that cannot act is its own bug.
  await expect(page.locator('.choice')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Save Progress/ })).toBeDisabled();
});

test('healing cannot carry the player above the bar they started with', async ({ page }) => {
  await beginQuest(page, 'The Clockwork Lighthouse');

  await choose(page, 'Force the seaward door');
  await expectHealth(page, '6/10');

  await choose(page, 'Edge along the wall');
  await choose(page, 'Open the tin');

  await expect(page.locator('.consequence--heal')).toContainText('+6');
  await expectHealth(page, '10/10');
});

test('saved progress survives and resumes on the same section', async ({ page }) => {
  await beginQuest(page, 'The Sunken Orchard');
  await choose(page, 'Row out to the nearest treetop');
  // Waited for rather than captured: reading the text straight after the click raced the
  // render and silently compared the opening section against itself.
  const savedSection = page.locator('.section__text').filter({ hasText: /trees are not dead so much as sleeping/ });
  await expect(savedSection).toHaveCount(1);

  await page.getByRole('button', { name: /Save Progress/ }).click();
  await expect(page.getByText('Progress saved.')).toBeVisible();

  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: /Back to Library/ }).click();
  const orchard = page.locator('app-book-card').filter({ hasText: 'The Sunken Orchard' });
  await expect(orchard.getByText('Saved', { exact: true })).toBeVisible();

  await orchard.getByRole('button', { name: 'Continue' }).click();

  await expect(page.locator('.section__text').filter({ hasText: /trees are not dead so much as sleeping/ }))
    .toHaveCount(1);
  await expectHealth(page, '10/10');

  // Discarding asks first — a real browser dialog — then the offer to continue is gone.
  page.once('dialog', (dialog) => dialog.accept());
  await page.getByRole('button', { name: /Back to Library/ }).click();
  page.once('dialog', (dialog) => dialog.accept());
  await orchard.getByRole('button', { name: 'Discard saved progress' }).click();

  await expect(orchard.getByRole('button', { name: 'Continue' })).toHaveCount(0);
  await expect(orchard.getByText('Saved', { exact: true })).toHaveCount(0);
});

/**
 * The router reuses the game component when only the route parameter changes, so loading
 * has to follow the id. This caught a real defect: the address bar pointed at the finished
 * game while the screen still showed the fresh one.
 */
test('moving through history keeps the url and the screen in agreement', async ({ page }) => {
  await beginQuest(page, 'The Clockwork Lighthouse');
  await choose(page, 'Force the seaward door');
  await choose(page, 'Wade straight through');
  await choose(page, 'Pry the jammed gear');
  await expect(page.getByText('Your adventure ends here')).toBeVisible();
  const finishedGameUrl = page.url();

  await page.getByRole('button', { name: 'Try Again' }).click();
  await expect(page).not.toHaveURL(finishedGameUrl);
  await expectHealth(page, '10/10');
  await expect(page.locator('.choice')).toHaveCount(2);

  page.once('dialog', (dialog) => dialog.accept());
  await page.goBack();

  await expect(page).toHaveURL(finishedGameUrl);
  await expect(page.getByText('Your adventure ends here')).toBeVisible();
  await expectHealth(page, '0/10');
  await expect(page.locator('.choice')).toHaveCount(0);

  await page.goForward();

  await expect(page).not.toHaveURL(finishedGameUrl);
  await expect(page.getByText('Your adventure ends here')).toHaveCount(0);
  await expectHealth(page, '10/10');
});
