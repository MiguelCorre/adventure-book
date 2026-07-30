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
    vi.unstubAllGlobals();
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

  it('applies a filter chip immediately, without the typing debounce', () => {
    settle([book()]);

    html().querySelectorAll<HTMLButtonElement>('.chip')[0].click();

    // No timers advanced: a click is a discrete intention, so the request is already out.
    const request = http.expectOne((r) => r.url === '/api/books');
    expect(request.request.params.get('difficulty')).toBe('EASY');
    request.flush([]);
  });

  describe('while narrowing the library', () => {
    /** Presses a filter chip without flushing the request it triggers. */
    function startRefining(): void {
      html().querySelectorAll<HTMLButtonElement>('.chip')[0].click();
      vi.advanceTimersByTime(250);
      fixture.detectChanges();
    }

    it('shows placeholders on the very first paint only', () => {
      expect(html().querySelectorAll('.skeleton').length).toBeGreaterThan(0);

      settle([book()]);
      expect(html().querySelectorAll('.skeleton').length).toBe(0);

      startRefining();
      expect(html().querySelectorAll('.skeleton').length).toBe(0);
    });

    it('keeps the current results on screen while the next set is fetched', () => {
      settle([book(), TRAPPED]);

      startRefining();

      // The old cards are still there; nothing was torn down waiting for the response.
      expect(html().querySelectorAll('app-book-card').length).toBe(2);
      expect(html().textContent).toContain('The Clockwork Lighthouse');
      expect(html().querySelector('.library__status')).toBeNull();
      http.expectOne((r) => r.url === '/api/books').flush([]);
    });

    it('marks the results as busy and stops them being clicked', () => {
      settle([book()]);
      expect(html().querySelector('.library__results--refreshing')).toBeNull();

      startRefining();

      expect(html().querySelector('.library__results--refreshing')).not.toBeNull();
      expect(html().querySelector('.library__grid')?.getAttribute('aria-busy')).toBe('true');
      http.expectOne((r) => r.url === '/api/books').flush([]);
    });

    /**
     * A shorter result set shortens the document, and the browser clamps the scroll
     * position upwards instantly when that happens below the current offset. The page
     * moves first, while the taller list is still rendered, so the jump never shows.
     */
    describe('scroll position', () => {
      let scrollTo: ReturnType<typeof vi.fn>;

      /** jsdom never scrolls, so the offset and the scroll call are both stood in for. */
      function pageScrolledTo(offset: number): void {
        vi.stubGlobal('scrollY', offset);
        scrollTo = vi.fn();
        vi.stubGlobal('scrollTo', scrollTo);
      }

      function resultsHeightOf(pixels: number): HTMLElement {
        const results = html().querySelector<HTMLElement>('.library__results')!;
        Object.defineProperty(results, 'offsetHeight', { value: pixels, configurable: true });
        return results;
      }

      afterEach(() => vi.unstubAllGlobals());

      it('glides to the top before the results shrink', () => {
        settle([book(), TRAPPED]);
        pageScrolledTo(400);

        html().querySelectorAll<HTMLButtonElement>('.chip')[0].click();

        // Offset zero is the one destination valid at every document height, so the
        // release can never be clamped.
        expect(scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
        vi.advanceTimersByTime(250);
        http.expectOne((r) => r.url === '/api/books').flush([]);
      });

      it('holds the height until the scroll settles, so the document cannot shrink under it', () => {
        settle([book(), TRAPPED]);
        pageScrolledTo(400);
        const results = resultsHeightOf(840);

        html().querySelectorAll<HTMLButtonElement>('.chip')[0].click();
        expect(results.style.minHeight).toBe('840px');

        // Results arriving does not release it: the scroll may still be in flight.
        vi.advanceTimersByTime(250);
        http.expectOne((r) => r.url === '/api/books').flush([]);
        fixture.detectChanges();
        expect(results.style.minHeight).toBe('840px');

        window.dispatchEvent(new Event('scrollend'));
        expect(results.style.minHeight).toBe('');
      });

      it('releases the height even if the browser never reports the scroll ending', () => {
        settle([book(), TRAPPED]);
        pageScrolledTo(400);
        const results = resultsHeightOf(840);

        html().querySelectorAll<HTMLButtonElement>('.chip')[0].click();
        expect(results.style.minHeight).toBe('840px');

        vi.advanceTimersByTime(250);
        http.expectOne((r) => r.url === '/api/books').flush([]);
        vi.advanceTimersByTime(1500);

        expect(results.style.minHeight).toBe('');
      });

      /**
       * The condition is whether the page is scrolled, not whether the controls are off
       * screen. A reader cannot press a filter they cannot see, so testing for hidden
       * controls excluded the very case that jumps: controls in view near the foot of a
       * page that is about to get shorter.
       */
      it('moves even when the controls are on screen, as long as the page is scrolled', () => {
        settle([book(), TRAPPED]);
        pageScrolledTo(120);

        html().querySelectorAll<HTMLButtonElement>('.chip')[0].click();

        expect(scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
        vi.advanceTimersByTime(250);
        http.expectOne((r) => r.url === '/api/books').flush([]);
      });

      it('leaves a reader who is already at the top exactly where they are', () => {
        settle([book(), TRAPPED]);
        pageScrolledTo(0);
        const results = resultsHeightOf(840);

        html().querySelectorAll<HTMLButtonElement>('.chip')[0].click();

        expect(scrollTo).not.toHaveBeenCalled();
        expect(results.style.minHeight).toBe('');
        vi.advanceTimersByTime(250);
        http.expectOne((r) => r.url === '/api/books').flush([]);
      });

      it('also moves for a search, not just a filter chip', () => {
        settle([book(), TRAPPED]);
        pageScrolledTo(300);

        const search = html().querySelector<HTMLInputElement>('.library__search input')!;
        search.value = 'pirates';
        search.dispatchEvent(new Event('input'));

        expect(scrollTo).toHaveBeenCalledOnce();
        vi.advanceTimersByTime(250);
        http.expectOne((r) => r.url === '/api/books').flush([TRAPPED]);
      });
    });

    it('clears the busy state once the new results arrive', () => {
      settle([book()]);
      startRefining();

      http.expectOne((r) => r.url === '/api/books').flush([TRAPPED]);
      fixture.detectChanges();

      expect(html().querySelector('.library__results--refreshing')).toBeNull();
      expect(html().querySelector('.library__grid')?.getAttribute('aria-busy')).toBe('false');
      expect(html().textContent).toContain('Pirates of the Jade Sea');
      expect(html().textContent).not.toContain('The Clockwork Lighthouse');
    });
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
