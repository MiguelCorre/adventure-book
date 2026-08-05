import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { BookSummary } from '../../core/models';
import { BookCard } from './book-card';

function book(overrides: Partial<BookSummary> = {}): BookSummary {
  return {
    slug: 'clockwork-lighthouse',
    title: 'The Clockwork Lighthouse',
    author: 'Ines Vaz-Corvo',
    description: 'Relight the beacon before a ship reaches the rocks.',
    tags: ['Steampunk', 'Coastal'],
    difficulty: 'MEDIUM',
    sectionCount: 11,
    readingMinutes: 4,
    valid: true,
    issues: [],
    hasSave: false,
    ...overrides,
  };
}

describe('BookCard presentation metadata', () => {
  let fixture: ComponentFixture<BookCard>;

  const html = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [BookCard] }).compileComponents();
    fixture = TestBed.createComponent(BookCard);
  });

  it('renders a synopsis, reading time and one pill per tag', () => {
    fixture.componentRef.setInput('book', book());
    fixture.detectChanges();

    expect(html().querySelector('.card__description')?.textContent).toContain('Relight the beacon');
    expect(html().querySelector('.card__meta')?.textContent).toContain('~4 min');
    expect(html().querySelector('.card__meta')?.textContent).toContain('11 chapters');
    expect([...html().querySelectorAll('.card__tag')].map((tag) => tag.textContent?.trim()))
      .toEqual(['Steampunk', 'Coastal']);
  });

  it('does not render empty presentation containers when metadata is absent', () => {
    fixture.componentRef.setInput('book', book({ description: null, tags: [], readingMinutes: 0, sectionCount: 0 }));
    fixture.detectChanges();

    expect(html().querySelector('.card__description')).toBeNull();
    expect(html().querySelector('.card__tags')).toBeNull();
    expect(html().querySelector('.card__meta')?.textContent).toContain('0 chapters');
  });

  it('pluralises a single chapter', () => {
    fixture.componentRef.setInput('book', book({ sectionCount: 1 }));
    fixture.detectChanges();

    expect(html().querySelector('.card__meta')?.textContent).toContain('1 chapter');
    expect(html().querySelector('.card__meta')?.textContent).not.toContain('1 chapters');
  });
});
