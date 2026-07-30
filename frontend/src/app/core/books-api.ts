import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { BookSummary, Difficulty } from './models';

@Injectable({ providedIn: 'root' })
export class BooksApi {
  private readonly http = inject(HttpClient);

  /**
   * Searching and filtering are server-side, so every client applies the same rules and
   * the browser never has to hold the whole library to narrow it down.
   */
  list(query = '', difficulties: readonly Difficulty[] = []): Observable<BookSummary[]> {
    let params = new HttpParams();
    if (query.trim()) {
      params = params.set('query', query.trim());
    }
    if (difficulties.length > 0) {
      params = params.set('difficulty', difficulties.join(','));
    }
    return this.http.get<BookSummary[]>('/api/books', { params });
  }

  get(slug: string): Observable<BookSummary> {
    return this.http.get<BookSummary>(`/api/books/${encodeURIComponent(slug)}`);
  }

  upload(file: File): Observable<BookSummary> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<BookSummary>('/api/books', body);
  }

  /** Forgets the saved progress for a book. Idempotent on the server. */
  discardSave(slug: string): Observable<void> {
    return this.http.delete<void>(`/api/books/${encodeURIComponent(slug)}/save`);
  }
}
