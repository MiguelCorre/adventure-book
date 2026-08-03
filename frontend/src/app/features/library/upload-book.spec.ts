import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { BookSummary } from '../../core/models';
import { UploadBook } from './upload-book';

const ADDED: BookSummary = {
  slug: 'the-glass-orchard',
  title: 'The Glass Orchard',
  author: 'A. Curator',
  description: null,
  tags: [],
  difficulty: 'EASY',
  sectionCount: 2,
  readingMinutes: 1,
  valid: true,
  issues: [],
  hasSave: false,
};

describe('UploadBook', () => {
  let fixture: ComponentFixture<UploadBook>;
  let http: HttpTestingController;

  const html = () => fixture.nativeElement as HTMLElement;

  /** Selecting a file cannot be scripted directly, so the input is given one. */
  function selectFile(name = 'book.json'): void {
    const input = html().querySelector<HTMLInputElement>('input[type="file"]')!;
    const file = new File(['{}'], name, { type: 'application/json' });
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function openPanel(): void {
    html().querySelector<HTMLButtonElement>('.upload__toggle')!.click();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UploadBook],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(UploadBook);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('keeps the panel closed until asked', () => {
    expect(html().querySelector('.upload__panel')).toBeNull();

    openPanel();

    expect(html().querySelector('.upload__panel')).not.toBeNull();
  });

  it('announces the book it added and closes', () => {
    const added = vi.fn();
    fixture.componentInstance.added.subscribe(added);
    openPanel();

    selectFile();
    http.expectOne('/api/books').flush(ADDED);
    fixture.detectChanges();

    expect(added).toHaveBeenCalledWith(ADDED);
    expect(html().querySelector('.upload__panel')).toBeNull();
  });

  it('renders every reason a book was rejected', () => {
    openPanel();
    selectFile();

    http.expectOne('/api/books').flush(
      {
        title: 'Book rejected',
        detail: 'The uploaded book is not valid and was not added to the library',
        issues: [
          { rule: 'VALID_REFERENCES', message: 'Choice points at section 999, which does not exist', sectionId: '1' },
          { rule: 'NO_DEAD_ENDS', message: 'Section 666 traps the player', sectionId: '666' },
        ],
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    fixture.detectChanges();

    const error = html().querySelector('[role="alert"]')!;
    expect(error.textContent).toContain('not valid and was not added');
    expect(html().querySelectorAll('.upload__issues li').length).toBe(2);
    expect(error.textContent).toContain('section 999, which does not exist');
    expect(error.textContent).toContain('Section 666 traps the player');
    expect(error.textContent).toContain('section 666');
  });

  it('explains a name clash', () => {
    openPanel();
    selectFile();

    http.expectOne('/api/books').flush(
      { title: 'Book already exists', detail: "The library already contains a book called 'the-glass-orchard'" },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(html().querySelector('[role="alert"]')?.textContent).toContain('already contains a book');
    expect(html().querySelectorAll('.upload__issues li').length).toBe(0);
  });

  it('stays open after a rejection so the panel still shows the reasons', () => {
    openPanel();
    selectFile();

    http.expectOne('/api/books').flush({ title: 'Book rejected', issues: [] },
      { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    expect(html().querySelector('.upload__panel')).not.toBeNull();
  });
});
