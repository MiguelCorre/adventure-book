import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { GameState } from './models';

@Injectable({ providedIn: 'root' })
export class GamesApi {
  private readonly http = inject(HttpClient);

  start(bookSlug: string, fromSave = false): Observable<GameState> {
    return this.http.post<GameState>('/api/games', { bookSlug, fromSave });
  }

  choose(gameId: string, optionIndex: number): Observable<GameState> {
    return this.http.post<GameState>(`/api/games/${gameId}/choices`, { optionIndex });
  }

  get(gameId: string): Observable<GameState> {
    return this.http.get<GameState>(`/api/games/${gameId}`);
  }

  save(gameId: string): Observable<void> {
    return this.http.post<void>(`/api/games/${gameId}/save`, {});
  }
}
