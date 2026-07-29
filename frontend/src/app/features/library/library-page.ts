import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
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

  protected readonly difficulties = DIFFICULTIES;

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
    this.reload();
  }

  protected toggleDifficulty(difficulty: Difficulty): void {
    this.selectedDifficulties.update((selected) =>
      selected.includes(difficulty)
        ? selected.filter((entry) => entry !== difficulty)
        : [...selected, difficulty],
    );
    this.reload();
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
