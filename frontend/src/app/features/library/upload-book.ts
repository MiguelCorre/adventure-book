import { Component, inject, output, signal } from '@angular/core';

import { BooksApi } from '../../core/books-api';
import { BookSummary, ValidationIssue } from '../../core/models';
import { issuesOf, messageOf, statusOf } from '../../core/problem';

/**
 * Objective 5: add a book by uploading its JSON.
 *
 * <p>A rejected file gets its whole validation report rendered, so a curator can fix
 * everything in one pass instead of discovering one problem per attempt.
 */
@Component({
  selector: 'app-upload-book',
  templateUrl: './upload-book.html',
  styleUrl: './upload-book.scss',
})
export class UploadBook {
  private readonly api = inject(BooksApi);

  readonly added = output<BookSummary>();

  protected readonly open = signal(false);
  protected readonly uploading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly issues = signal<readonly ValidationIssue[]>([]);

  protected toggle(): void {
    this.open.update((open) => !open);
    this.clearFeedback();
  }

  protected onFileSelected(input: HTMLInputElement): void {
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    this.clearFeedback();
    this.uploading.set(true);

    this.api.upload(file).subscribe({
      next: (book) => {
        this.uploading.set(false);
        this.open.set(false);
        input.value = '';
        this.added.emit(book);
      },
      error: (failure) => {
        this.uploading.set(false);
        input.value = '';
        this.issues.set(issuesOf(failure));
        this.error.set(
          statusOf(failure) === 409
            ? messageOf(failure, 'That book is already in the library.')
            : messageOf(failure, 'That file could not be added.'),
        );
      },
    });
  }

  private clearFeedback(): void {
    this.error.set(null);
    this.issues.set([]);
  }
}
