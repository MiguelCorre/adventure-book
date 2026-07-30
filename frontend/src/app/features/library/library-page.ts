import {
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject, debounceTime, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { BooksApi } from '../../core/books-api';
import { GamesApi } from '../../core/games-api';
import { BookSummary, DIFFICULTIES, Difficulty } from '../../core/models';
import { messageOf } from '../../core/problem';
import { BookCard } from './book-card';
import { UploadBook } from './upload-book';

/** Objective 1: the home page. Lists every book, searchable and filterable. */
@Component({
  selector: 'app-library-page',
  imports: [BookCard, UploadBook],
  templateUrl: './library-page.html',
  styleUrl: './library-page.scss',
})
export class LibraryPage implements OnInit {
  private readonly booksApi = inject(BooksApi);
  private readonly gamesApi = inject(GamesApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly difficulties = DIFFICULTIES;

  private readonly controls = viewChild<ElementRef<HTMLElement>>('controls');
  private readonly results = viewChild<ElementRef<HTMLElement>>('results');

  /** Undoes the height freeze applied while scrolling back to the controls. */
  private releaseHeight: (() => void) | null = null;

  protected readonly books = signal<BookSummary[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly selectedDifficulties = signal<readonly Difficulty[]>([]);

  /** A request is in flight. */
  private readonly loading = signal(true);
  /** At least one response has come back, successfully or not. */
  private readonly settled = signal(false);

  /**
   * Only the very first load gets a placeholder. Narrowing an already-visible library
   * keeps the current results on screen and dims them, so refining a search never throws
   * the page away and rebuilds it.
   */
  protected readonly initialLoading = computed(() => this.loading() && !this.settled());
  protected readonly refreshing = computed(() => this.loading() && this.settled());

  /** Placeholder cards for the first paint; the count is arbitrary, it just fills the grid. */
  protected readonly skeletons = [0, 1, 2, 3, 4, 5];

  /** Typing should not fire a request per keystroke, nor repeat an unchanged search. */
  private readonly searches = new Subject<void>();

  constructor() {
    this.destroyRef.onDestroy(() => this.releaseHeight?.());

    this.searches
      .pipe(
        debounceTime(250),
        switchMap(() => this.booksApi.list(this.query(), this.selectedDifficulties())),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (books) => {
          this.books.set(books);
          this.settle();
          this.error.set(null);
        },
        error: (failure) => {
          this.error.set(messageOf(failure, 'The library could not be loaded.'));
          this.settle();
        },
      });
  }

  ngOnInit(): void {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.searches.next();
  }

  private settle(): void {
    this.loading.set(false);
    this.settled.set(true);
  }

  protected onSearch(value: string): void {
    this.query.set(value);
    this.narrow();
  }

  protected toggleDifficulty(difficulty: Difficulty): void {
    this.selectedDifficulties.update((selected) =>
      selected.includes(difficulty)
        ? selected.filter((entry) => entry !== difficulty)
        : [...selected, difficulty],
    );
    this.narrow();
  }

  private narrow(): void {
    this.revealControls();
    this.reload();
  }

  /**
   * Brings the search box and filter chips back into view before the results change.
   *
   * <p>A shorter result set makes the document shorter, and when that happens below the
   * current scroll offset the browser clamps the page upwards instantly. Nothing can soften
   * that jump once it has happened, so the page moves first, while the old and taller list
   * is still rendered and the scroll is an ordinary smooth one.
   *
   * <p>Moving first is not enough on its own: the scroll takes about as long as the request
   * it races, and losing that race puts the clamp back. So the results region keeps its
   * current height until the scroll settles. The document cannot shrink while it is held,
   * which means there is no clamp to see; releasing it afterwards only changes the layout
   * below the fold, where the reader is no longer looking.
   *
   * <p>Does nothing when the controls are already on screen, so a reader who can see them
   * is never moved.
   */
  private revealControls(): void {
    const controls = this.controls()?.nativeElement;
    const results = this.results()?.nativeElement;

    // Any previous freeze is undone first, so the height measured below is the natural one.
    this.releaseHeight?.();

    if (!controls || !results || controls.getBoundingClientRect().top >= 0) {
      return;
    }

    results.style.minHeight = `${results.offsetHeight}px`;

    const release = () => {
      clearTimeout(fallback);
      window.removeEventListener('scrollend', release);
      results.style.minHeight = '';
      this.releaseHeight = null;
    };
    // scrollend is the accurate signal; the timer covers browsers that never send it.
    const fallback = setTimeout(release, 700);
    window.addEventListener('scrollend', release, { once: true });
    this.releaseHeight = release;

    const prefersReducedMotion =
      typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

    controls.scrollIntoView({
      behavior: prefersReducedMotion ? 'auto' : 'smooth',
      block: 'start',
    });
  }

  protected isSelected(difficulty: Difficulty): boolean {
    return this.selectedDifficulties().includes(difficulty);
  }

  protected clearFilters(): void {
    this.query.set('');
    this.selectedDifficulties.set([]);
    this.reload();
  }

  /** Clears any narrowing so the newly added book is visible without hunting for it. */
  protected onBookAdded(): void {
    this.error.set(null);
    this.clearFilters();
  }

  protected beginQuest(book: BookSummary): void {
    this.openGame(book, false);
  }

  protected continueQuest(book: BookSummary): void {
    this.openGame(book, true);
  }

  private openGame(book: BookSummary, fromSave: boolean): void {
    this.error.set(null);
    this.gamesApi.start(book.slug, fromSave).subscribe({
      next: (game) => void this.router.navigate(['/play', game.gameId]),
      error: (failure) => this.error.set(messageOf(failure, 'This adventure could not be started.')),
    });
  }
}
