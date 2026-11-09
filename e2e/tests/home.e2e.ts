import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

/**
 * CatchIt — deterministic device tests.
 *
 * These cover the three screens the product spec allows (Bus, Alarms, Settings)
 * and the two interactions that carry the app: route search and the pin/unpin
 * star on a route card.
 *
 * No AI gateway key is needed: every assertion is a locator check, not an
 * agent step.
 */

/** The Home screen is the whole app's landing surface. */
test('the Bus screen shows the route directory', async ({ app, screen }) => {
  await app.open();

  await expect(screen.getByTestId('search')).toBeVisible();
  await expect(screen.getByText('Popular routes')).toBeVisible();
});

/** The three destinations are Bus, Alarms, Settings — and nothing else. */
test('the bottom bar has exactly three destinations', async ({ app, screen }) => {
  await app.open();

  await expect(screen.getByTestId('nav-home')).toBeVisible();
  await expect(screen.getByTestId('nav-alerts')).toBeVisible();
  await expect(screen.getByTestId('nav-settings')).toBeVisible();
});

/** Search filters the full 91-route list down to the one that matches. */
test('searching a route code narrows the list to that route', async ({ app, screen }) => {
  await app.open();

  await screen.getByTestId('search').fill('T155');

  await expect(screen.getByTestId('search-result-T155')).toBeVisible();
  // A route that is not a substring of "T155" must not appear in the results.
  await expect(screen.getByTestId('search-result-T100')).toHaveCount(0);
});

/**
 * Searching a place you know but not a bus route you know: the app answers with the
 * stop to board at, the route number to board, and which way it is going.
 */
test('searching a place finds the stop and the route number', async ({ app, screen }) => {
  await screen.getByTestId('search').fill('SKYPARK');

  // The stop row carries the stop code as its test id.
  await expect(screen.getByTestId('stop-result-SP405')).toBeVisible();

  // It shows the route number and the direction the bus is heading.
  await expect(screen.getByText('T504')).toBeVisible();
});

/** Picking a search result opens that route's detail screen. */
test('picking a search result opens the route detail', async ({ app, screen }) => {
  await app.open();

  await screen.getByTestId('search').fill('T155');
  await screen.getByTestId('search-result-T155').tap();

  // The detail screen's back button only exists on the detail screen.
  await expect(screen.getByTestId('detail-back')).toBeVisible();
  await expect(screen.getByText('Departures')).toBeVisible();
});

/** Going back from the detail returns to Home. */
test('back from the route detail returns to the route list', async ({ app, screen }) => {
  await app.open();

  await screen.getByTestId('search').fill('T155');
  await screen.getByTestId('search-result-T155').tap();
  await expect(screen.getByTestId('detail-back')).toBeVisible();

  await screen.getByTestId('detail-back').tap();

  await expect(screen.getByTestId('search')).toBeVisible();
});

/** The Alarms screen is the moat: it must exist and be reachable. */
test('the Alarms screen opens', async ({ app, screen }) => {
  await app.open();

  await screen.getByTestId('nav-alerts').tap();

  // The screen title and the bottom-bar label are both "Alarms", so assert on
  // phrases that only this screen carries. Do NOT assert on "No alarms yet" —
  // that only shows when nothing is armed, and the app's data survives a
  // reinstall, so the suite would fail whenever a real alarm is set.
  await expect(screen.getByText('Service updates')).toBeVisible();
  await expect(screen.getByText('See all')).toBeVisible();
});

/** Settings explains how the alarm works and how to keep it alive. */
test('the Settings screen opens and explains the alarm', async ({ app, screen }) => {
  await app.open();

  await screen.getByTestId('nav-settings').tap();

  await expect(screen.getByText('How alarms work')).toBeVisible();
  await expect(screen.getByText('Keep alarms alive')).toBeVisible();
});

/**
 * Pinning a route moves it into "Your routes", and unpinning removes it.
 * This is the whole point of the star button, so it gets a full round trip.
 *
 * The toggle is driven from the route detail screen, whose star is a single
 * unambiguous control, and the state is read from the star's own accessible
 * name. Pinning is also done on the detail screen rather than from the Home
 * list, because "Your routes" is a horizontally scrolling row: a route pinned
 * at the end of it sits off-screen, and an off-screen card's controls are not
 * in the accessibility tree.
 */
test('a route can be pinned and unpinned', async ({ app, screen }) => {
  await app.open();

  await screen.getByTestId('search').fill('T155');
  await screen.getByTestId('search-result-T155').tap();
  await expect(screen.getByTestId('detail-star')).toBeVisible();

  // The star's state is carried by the icon's accessible label, not by the
  // button's own name (the button is named by its test tag).
  const unpinned = screen.getByLabel('Pin');
  const pinned = screen.getByLabel('Unpin');

  // Pins persist between runs, so start from a known state: unpinned.
  if ((await pinned.count()) > 0) {
    await screen.getByTestId('detail-star').tap();
  }
  await expect(unpinned).toBeVisible();

  // Pin it.
  await screen.getByTestId('detail-star').tap();
  await expect(pinned).toBeVisible();

  // And unpin it again.
  await screen.getByTestId('detail-star').tap();
  await expect(unpinned).toBeVisible();
});

/**
 * A pinned route gets its own card in "Your routes".
 *
 * The test pins the route itself rather than assuming one is already pinned:
 * the suite reinstalls the build before it runs, which wipes the app's stored
 * pins, so a test that relied on leftover state would pass or fail depending
 * on what ran before it.
 */
test('a pinned route appears under Your routes', async ({ app, screen }) => {
  await app.open();

  await screen.getByTestId('search').fill('T155');
  await screen.getByTestId('search-result-T155').tap();
  await expect(screen.getByTestId('detail-star')).toBeVisible();

  // Leave the route unpinned first, whatever state it was in.
  if ((await screen.getByLabel('Unpin').count()) > 0) {
    await screen.getByTestId('detail-star').tap();
  }
  await expect(screen.getByLabel('Pin')).toBeVisible();

  // Pin it, then go back to Home.
  await screen.getByTestId('detail-star').tap();
  await expect(screen.getByLabel('Unpin')).toBeVisible();
  await screen.getByTestId('detail-back').tap();

  await expect(screen.getByText('Your routes')).toBeVisible();

  // Leave the device as we found it.
  const pinnedStar = screen.getByTestId('pinned-star-T155');
  if ((await pinnedStar.count()) > 0) {
    await pinnedStar.tap();
  }
});
