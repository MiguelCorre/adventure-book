import { Component, computed, input, output, signal } from '@angular/core';

import { BookSummary } from '../../core/models';

/**
 * One book in the library.
 *
 * <p>An invalid book is shown like any other, with its play button disabled and the
 * reasons available underneath. Hiding it would leave the reader wondering where it went.
 */
@Component({
  selector: 'app-book-card',
  templateUrl: './book-card.html',
  styleUrl: './book-card.scss',
})
export class BookCard {
  readonly book = input.required<BookSummary>();

  readonly begin = output<BookSummary>();
  readonly continueQuest = output<BookSummary>();

  protected readonly issuesExpanded = signal(false);

  protected readonly difficultyClass = computed(() => {
    const difficulty = this.book().difficulty;
    return difficulty ? `badge--${difficulty.toLowerCase()}` : 'badge--neutral';
  });

  protected toggleIssues(): void {
    this.issuesExpanded.update((expanded) => !expanded);
  }
}
