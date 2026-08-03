import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { GamesApi } from './games-api';
import { GameState } from './models';

const A_GAME: GameState = {
  gameId: 'game-1',
  bookSlug: 'clockwork-lighthouse',
  bookTitle: 'The Clockwork Lighthouse',
  health: 10,
  maxHealth: 10,
  status: 'IN_PROGRESS',
  section: {
    id: '1',
    title: null,
    text: 'You arrive.',
    type: 'BEGIN',
    options: [{ index: 0, description: 'Go' }],
  },
  lastConsequence: null,
};

describe('GamesApi', () => {
  let api: GamesApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(GamesApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts a new game from the beginning by default', () => {
    api.start('clockwork-lighthouse').subscribe();

    const request = http.expectOne('/api/games');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ bookSlug: 'clockwork-lighthouse', fromSave: false });
    request.flush(A_GAME);
  });

  it('asks to continue from saved progress when told to', () => {
    api.start('clockwork-lighthouse', true).subscribe();

    const request = http.expectOne('/api/games');
    expect(request.request.body).toEqual({ bookSlug: 'clockwork-lighthouse', fromSave: true });
    request.flush(A_GAME);
  });

  it('sends a choice as its index', () => {
    api.choose('game-1', 2).subscribe();

    const request = http.expectOne('/api/games/game-1/choices');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ optionIndex: 2 });
    request.flush(A_GAME);
  });

  it('returns the state the server sent back', () => {
    let received: GameState | undefined;
    api.choose('game-1', 0).subscribe((state) => (received = state));

    http.expectOne('/api/games/game-1/choices').flush(A_GAME);

    expect(received).toEqual(A_GAME);
  });

  it('reads the current state of a game', () => {
    api.get('game-1').subscribe();

    const request = http.expectOne('/api/games/game-1');
    expect(request.request.method).toBe('GET');
    request.flush(A_GAME);
  });

  it('saves progress without a body of its own', () => {
    api.save('game-1').subscribe();

    const request = http.expectOne('/api/games/game-1/save');
    expect(request.request.method).toBe('POST');
    request.flush(null);
  });
});
