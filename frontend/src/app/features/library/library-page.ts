import { Component, OnInit, inject, signal } from '@angular/core';
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
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly selectedDifficulties = signal<readonly Difficulty[]>([]);

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
          this.loading.set(false);
          this.error.set(null);
        },
        error: (failure) => {
          this.error.set(messageOf(failure, 'The library could not be loaded.'));
          this.loading.set(false);
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
