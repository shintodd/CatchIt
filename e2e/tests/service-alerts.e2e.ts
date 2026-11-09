import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

/**
 * CatchIt — service updates.
 *
 * The alerts come from Prasarana's own "Service Alerts on PULSE" page, the
 * same source the MyRapid PULSE app reads its alerts from. These tests cover
 * the list itself and the freshness the screen promises.
 */

/** The notifications screen lists the operator's live service alerts. */
test('the notifications screen lists service alerts', async ({ app, screen }) => {
  await app.open();

  await screen.getByTestId('nav-alerts').tap();
  await expect(screen.getByText('Service updates')).toBeVisible();

  // Open the full list behind "See all".
  await screen.getByText('See all').tap();

  // The button carries the unread count: "Mark all 9 as read", or "Mark 1 as read"
  // when a single alert is left. The app data was just cleared, so the list has to
  // be fetched from the operator first - give it room to arrive.
  await expect(screen.getByText(/Mark (all )?\d+ as read/)).toBeVisible({ timeout: 30000 });
});

/**
 * The screen says how fresh the list is.
 *
 * A real-time list should never have to be taken on trust, so the screen
 * reports when it last checked the operator's page.
 */
test('the notifications screen says when it last checked', async ({ app, screen }) => {
  await app.open();

  await screen.getByTestId('nav-alerts').tap();
  await screen.getByText('See all').tap();

  // Either the list is fresh, or it says so. Both are honest states.
  const fresh = screen.getByText(/^Updated /);
  const checking = screen.getByText('Checking for updates…');
  const staleNote = screen.getByText(/out of date/);

  const freshCount = await fresh.count();
  const checkingCount = await checking.count();

  if (freshCount === 0 && checkingCount === 0) {
    // Neither form is showing, so the list must be flagged as possibly stale.
    await expect(staleNote).toBeVisible();
  } else {
    await expect(fresh.or(checking)).toBeVisible();
  }
});
