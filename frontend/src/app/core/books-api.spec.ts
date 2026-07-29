import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BooksApi } from './books-api';
import { BookSummary } from './models';

const A_BOOK: BookSummary = {
  slug: 'clockwork-lighthouse',
  title: 'The Clockwork Lighthouse',
  author: 'Ines Vaz-Corvo',
  difficulty: 'MEDIUM',
  sectionCount: 11,
  valid: true,
  issues: [],
  hasSave: false,
};

describe('BooksApi', () => {
  let api: BooksApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(BooksApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests the whole library when nothing is filtered', () => {
    api.list().subscribe();

    const request = http.expectOne('/api/books');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush([]);
  });

  it('sends the search text as a query parameter', () => {
    api.list('lighthouse').subscribe();

    const request = http.expectOne((r) => r.url === '/api/books');
    expect(request.request.params.get('query')).toBe('lighthouse');
    request.flush([]);
  });

  it('trims the search text and omits it when blank', () => {
    api.list('   ').subscribe();

    const request = http.expectOne((r) => r.url === '/api/books');
    expect(request.request.params.has('query')).toBe(false);
    request.flush([]);
  });

  it('sends selected difficulties as one comma-separated parameter', () => {
    api.list('', ['EASY', 'HARD']).subscribe();

    const request = http.expectOne((r) => r.url === '/api/books');
    expect(request.request.params.get('difficulty')).toBe('EASY,HARD');
    request.flush([]);
  });

  it('returns the books the server sent', () => {
    let received: BookSummary[] | undefined;
    api.list().subscribe((books) => (received = books));

    http.expectOne('/api/books').flush([A_BOOK]);

    expect(received).toEqual([A_BOOK]);
  });

  it('escapes the slug when fetching one book', () => {
    api.get('a book/with slash').subscribe();

    const request = http.expectOne((r) => r.method === 'GET' && r.url.startsWith('/api/books/'));
    expect(request.request.url).toBe('/api/books/a%20book%2Fwith%20slash');
    request.flush(A_BOOK);
  });

  it('posts an uploaded file as multipart form data', () => {
    const file = new File(['{}'], 'book.json', { type: 'application/json' });

    api.upload(file).subscribe();

    const request = http.expectOne('/api/books');
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBe(true);
    expect((request.request.body as FormData).get('file')).toBe(file);
    request.flush(A_BOOK);
  });
});
