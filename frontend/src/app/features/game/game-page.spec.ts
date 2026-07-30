import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { GameState } from '../../core/models';
import { GamePage } from './game-page';

function game(overrides: Partial<GameState> = {}): GameState {
  return {
    gameId: 'game-1',
    bookSlug: 'clockwork-lighthouse',
    bookTitle: 'The Clockwork Lighthouse',
    health: 10,
    maxHealth: 10,
    status: 'IN_PROGRESS',
    section: {
      id: '1',
      text: 'The keeper is gone.\n\nThe lamp is dark.',
      type: 'BEGIN',
      options: [
        { index: 0, description: 'Take the outer stair' },
        { index: 1, description: 'Force the seaward door' },
      ],
    },
    lastConsequence: null,
    ...overrides,
  };
}

describe('GamePage', () => {
  let fixture: ComponentFixture<GamePage>;
  let http: HttpTestingController;

  const html = () => fixture.nativeElement as HTMLElement;

  function open(state: GameState): void {
    http.expectOne('/api/games/game-1').flush(state);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GamePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'play/:gameId', children: [] }]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(GamePage);
    fixture.componentRef.setInput('gameId', 'game-1');
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('shows the book name and health in the header', () => {
    open(game({ health: 7 }));

    const header = html().querySelector('app-game-header')!;
    expect(header.textContent).toContain('The Clockwork Lighthouse');
    expect(header.textContent).toContain('7/10');
    expect(header.textContent).toContain('Back to Library');
  });

  it('renders the section text as paragraphs', () => {
    open(game());

    const paragraphs = html().querySelectorAll('.section__text');
    expect(paragraphs.length).toBe(2);
    expect(paragraphs[0].textContent).toContain('The keeper is gone.');
    expect(paragraphs[1].textContent).toContain('The lamp is dark.');
  });

  it('lists the choices with their numbers', () => {
    open(game());

    const choices = html().querySelectorAll('.choice');
    expect(choices.length).toBe(2);
    expect(choices[0].textContent).toContain('1');
    expect(choices[0].textContent).toContain('Take the outer stair');
  });

  it('sends the chosen option index to the server', () => {
    open(game());

    html().querySelectorAll<HTMLButtonElement>('.choice')[1].click();

    const request = http.expectOne('/api/games/game-1/choices');
    expect(request.request.body).toEqual({ optionIndex: 1 });
    request.flush(game({ health: 6 }));
  });

  it('shows what a choice cost the player', () => {
    open(game({
      health: 6,
      lastConsequence: { type: 'LOSE_HEALTH', value: 4, text: 'You go down hard on the flagstones.' },
    }));

    const banner = html().querySelector('.consequence')!;
    expect(banner.textContent).toContain('−4');
    expect(banner.textContent).toContain('You go down hard on the flagstones.');
    expect(banner.classList.contains('consequence--harm')).toBe(true);
  });

  it('shows healing differently from harm', () => {
    open(game({
      lastConsequence: { type: 'GAIN_HEALTH', value: 6, text: 'The keeper kept a good tin.' },
    }));

    const banner = html().querySelector('.consequence')!;
    expect(banner.textContent).toContain('+6');
    expect(banner.classList.contains('consequence--heal')).toBe(true);
  });

  it('saves progress on request', () => {
    open(game());

    html().querySelector<HTMLButtonElement>('.game-header__save')!.click();

    const request = http.expectOne('/api/games/game-1/save');
    expect(request.request.method).toBe('POST');
    request.flush(null);
    fixture.detectChanges();

    expect(html().querySelector('[role="status"]')?.textContent).toContain('Progress saved.');
  });

  it('celebrates a win and offers to play again', () => {
    open(game({
      status: 'WON',
      section: { id: '80', text: 'The lens begins to turn.', type: 'END', options: [] },
    }));

    const ending = html().querySelector('app-game-over')!;
    expect(ending.textContent).toContain('Your adventure is complete');
    expect(ending.textContent).toContain('The lens begins to turn.');
    expect(ending.textContent).toContain('Play Again');
    expect(html().querySelectorAll('.choice').length).toBe(0);
  });

  it('explains a death with the blow that caused it', () => {
    open(game({
      status: 'DEAD',
      health: 0,
      lastConsequence: { type: 'LOSE_HEALTH', value: 5, text: 'The gear takes your fingers.' },
    }));

    const ending = html().querySelector('app-game-over')!;
    expect(ending.textContent).toContain('Your adventure ends here');
    expect(ending.textContent).toContain('The gear takes your fingers.');
    expect(ending.textContent).toContain('cost you 5 health');
    expect(ending.textContent).toContain('Try Again');
  });

  it('hides the save button once the adventure is over', () => {
    open(game({ status: 'WON', section: { id: '80', text: 'Done.', type: 'END', options: [] } }));

    expect(html().querySelector<HTMLButtonElement>('.game-header__save')!.disabled).toBe(true);
  });

  it('restarts the same book from the beginning', () => {
    open(game({ status: 'DEAD', health: 0 }));

    html().querySelector<HTMLButtonElement>('app-game-over .button--primary')!.click();

    const request = http.expectOne('/api/games');
    expect(request.request.body).toEqual({ bookSlug: 'clockwork-lighthouse', fromSave: false });
    request.flush(game({ gameId: 'game-2' }));
  });

  it('ignores further choices once the adventure is over', () => {
    open(game({ status: 'WON', section: { id: '80', text: 'Done.', type: 'END', options: [] } }));

    // No choices are rendered, and the store refuses one even if it were requested.
    expect(html().querySelectorAll('.choice').length).toBe(0);
    http.expectNone('/api/games/game-1/choices');
  });

  it('hides the choices of the section a dying player is still standing on', () => {
    // Death keeps the player where they died, so that section's options are still in the
    // state; offering them would invite a click that can no longer do anything.
    open(game({
      status: 'DEAD',
      health: 0,
      lastConsequence: { type: 'LOSE_HEALTH', value: 5, text: 'The gear takes your fingers.' },
    }));

    expect(game().section.options.length).toBe(2);
    expect(html().querySelectorAll('.choice').length).toBe(0);
  });

  /**
   * The router reuses this component when only the route parameter changes, so loading has
   * to follow the id. Without that, pressing Try Again or going back in history left the
   * address bar and the screen describing two different play-throughs.
   */
  it('loads the new game when the route id changes without the component being rebuilt', () => {
    open(game({ status: 'DEAD', health: 0 }));
    expect(html().querySelector('app-game-over')).not.toBeNull();

    fixture.componentRef.setInput('gameId', 'game-2');
    fixture.detectChanges();

    const request = http.expectOne('/api/games/game-2');
    expect(request.request.method).toBe('GET');
    request.flush(game({ gameId: 'game-2', health: 10, status: 'IN_PROGRESS' }));
    fixture.detectChanges();

    expect(html().querySelector('app-game-over')).toBeNull();
    expect(html().querySelector('app-game-header')?.textContent).toContain('10/10');
    expect(html().querySelectorAll('.choice').length).toBe(2);
  });

  it('reports a game that could not be opened', () => {
    http.expectOne('/api/games/game-1').flush(
      { title: 'Not found', detail: "No game with id 'game-1'" },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    expect(html().querySelector('[role="alert"]')?.textContent).toContain("No game with id 'game-1'");
  });
});
