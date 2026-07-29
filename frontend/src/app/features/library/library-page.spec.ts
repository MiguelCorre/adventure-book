import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { BookSummary } from '../../core/models';
import { LibraryPage } from './library-page';

function book(overrides: Partial<BookSummary> = {}): BookSummary {
  return {
    slug: 'clockwork-lighthouse',
    title: 'The Clockwork Lighthouse',
    author: 'Ines Vaz-Corvo',
    difficulty: 'MEDIUM',
    sectionCount: 11,
    valid: true,
    issues: [],
    hasSave: false,
    ...overrides,
  };
}

const TRAPPED = book({
  slug: 'pirates-jade-sea',
  title: 'Pirates of the Jade Sea',
  valid: false,
  issues: [
    { rule: 'VALID_REFERENCES', message: 'Choice points at section 999, which does not exist', sectionId: '1' },
    { rule: 'NO_DEAD_ENDS', message: 'Section 666 traps the player', sectionId: '666' },
  ],
});

/** The app is zoneless, so the debounce is driven with fake timers rather than fakeAsync. */
describe('LibraryPage', () => {
  let fixture: ComponentFixture<LibraryPage>;
  let http: HttpTestingController;

  const html = () => fixture.nativeElement as HTMLElement;

  function settle(response: BookSummary[]): void {
    vi.advanceTimersByTime(250);
    http.expectOne((request) => request.url === '/api/books').flush(response);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    vi.useFakeTimers();

    await TestBed.configureTestingModule({
      imports: [LibraryPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // The play route only has to exist for navigation assertions to resolve.
        provideRouter([{ path: 'play/:gameId', children: [] }]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LibraryPage);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('lists every book the API returns', () => {
    settle([book(), TRAPPED]);

    expect(html().querySelectorAll('app-book-card').length).toBe(2);
    expect(html().textContent).toContain('The Clockwork Lighthouse');
    expect(html().textContent).toContain('Pirates of the Jade Sea');
  });

  it('announces how many adventures are available', () => {
    settle([book(), TRAPPED]);

    expect(html().querySelector('.hero__count')?.textContent).toContain('2 adventures available');
  });

  it('disables the play button for an unplayable book', () => {
    settle([TRAPPED]);

    expect(html().querySelector<HTMLButtonElement>('.card__begin')?.disabled).toBe(true);
  });

  it('keeps the play button enabled for a valid book', () => {
    settle([book()]);

    expect(html().querySelector<HTMLButtonElement>('.card__begin')?.disabled).toBe(false);
  });

  it('reveals why a book cannot be played, on request', () => {
    settle([TRAPPED]);

    expect(html().textContent).toContain('Show 2 problems');
    expect(html().querySelector('.card__issue-list')).toBeNull();

    html().querySelector<HTMLButtonElement>('.card__issues-toggle')!.click();
    fixture.detectChanges();

    expect(html().textContent).toContain('Section 666 traps the player');
    expect(html().textContent).toContain('section 999, which does not exist');
  });

  it('offers Continue for a book with saved progress', () => {
    settle([book({ hasSave: true })]);

    expect(html().querySelector('.card__continue')).not.toBeNull();
  });

  it('hides Continue for a book that has never been saved', () => {
    settle([book({ hasSave: false })]);

    expect(html().querySelector('.card__continue')).toBeNull();
  });

  it('debounces typing into a single search request', () => {
    settle([book()]);

    const search = html().querySelector<HTMLInputElement>('.library__search input')!;
    search.value = 'light';
    search.dispatchEvent(new Event('input'));
    search.value = 'lighthouse';
    search.dispatchEvent(new Event('input'));

    vi.advanceTimersByTime(250);
    const request = http.expectOne((r) => r.url === '/api/books');
    expect(request.request.params.get('query')).toBe('lighthouse');
    request.flush([book()]);
  });

  it('sends the selected difficulty when a filter chip is pressed', () => {
    settle([book()]);

    html().querySelectorAll<HTMLButtonElement>('.chip')[0].click();

    vi.advanceTimersByTime(250);
    const request = http.expectOne((r) => r.url === '/api/books');
    expect(request.request.params.get('difficulty')).toBe('EASY');
    request.flush([]);
  });

  it('shows an empty state when nothing matches', () => {
    settle([]);

    expect(html().textContent).toContain('No adventures match your search');
  });

  it('reports a failure to reach the library', () => {
    vi.advanceTimersByTime(250);
    http.expectOne((r) => r.url === '/api/books').error(new ProgressEvent('network'), { status: 0 });
    fixture.detectChanges();

    expect(html().querySelector('[role="alert"]')?.textContent).toContain('Could not reach the server');
  });

  it('starts a game from the beginning when a quest is begun', () => {
    settle([book()]);

    html().querySelector<HTMLButtonElement>('.card__begin')!.click();

    const request = http.expectOne('/api/games');
    expect(request.request.body).toEqual({ bookSlug: 'clockwork-lighthouse', fromSave: false });
    request.flush({ gameId: 'game-1' });
  });

  it('asks to continue from the save when Continue is pressed', () => {
    settle([book({ hasSave: true })]);

    html().querySelector<HTMLButtonElement>('.card__continue')!.click();

    const request = http.expectOne('/api/games');
    expect(request.request.body).toEqual({ bookSlug: 'clockwork-lighthouse', fromSave: true });
    request.flush({ gameId: 'game-2' });
  });
});
