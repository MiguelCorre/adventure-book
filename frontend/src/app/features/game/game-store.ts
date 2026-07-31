import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { GamesApi } from '../../core/games-api';
import { GameState } from '../../core/models';
import { messageOf } from '../../core/problem';

/**
 * Holds the current play-through for the game screen.
 *
 * <p>Every transition is a round trip: the store never guesses what a choice will do, it
 * asks the server and renders the answer. That keeps one copy of the rules in the system.
 */
@Injectable({ providedIn: 'root' })
export class GameStore {
  private readonly api = inject(GamesApi);
  private readonly router = inject(Router);
  private loadSubscription?: Subscription;

  private readonly _state = signal<GameState | null>(null);
  private readonly _loading = signal(false);
  private readonly _saving = signal(false);
  private readonly _error = signal<string | null>(null);
  private readonly _notice = signal<string | null>(null);

  readonly state = this._state.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly saving = this._saving.asReadonly();
  readonly error = this._error.asReadonly();
  readonly notice = this._notice.asReadonly();

  readonly finished = computed(() => {
    const status = this._state()?.status;
    return status === 'WON' || status === 'DEAD';
  });

  /** Loads a game by its handle, used when the play screen is opened or reloaded. */
  load(gameId: string): void {
    this.loadSubscription?.unsubscribe();
    // A route id owns the whole screen. Keeping the previous game visible while the new
    // request is in flight is misleading, and keeping it after a failed request would let
    // the player make choices in a game that no longer matches the address bar.
    this._state.set(null);
    this._loading.set(true);
    this._error.set(null);
    this._notice.set(null);
    this.loadSubscription = this.api.get(gameId).subscribe({
      next: (state) => this.settle(state),
      error: (failure) => this.fail(failure, 'This adventure could not be opened.'),
    });
  }

  choose(optionIndex: number): void {
    const current = this._state();
    if (!current || this.finished() || this._loading()) {
      return;
    }

    this._loading.set(true);
    this._notice.set(null);
    this.api.choose(current.gameId, optionIndex).subscribe({
      next: (state) => this.settle(state),
      error: (failure) => this.fail(failure, 'That choice could not be made.'),
    });
  }

  save(): void {
    const current = this._state();
    if (!current) {
      return;
    }

    this._saving.set(true);
    this._error.set(null);
    this.api.save(current.gameId).subscribe({
      next: () => {
        this._saving.set(false);
        this._notice.set('Progress saved.');
      },
      error: (failure) => {
        this._saving.set(false);
        this._error.set(messageOf(failure, 'Progress could not be saved.'));
      },
    });
  }

  /** Starts the same book again from the beginning, after a death or a win. */
  restart(): void {
    const current = this._state();
    if (!current) {
      return;
    }

    this._loading.set(true);
    this._error.set(null);
    this.api.start(current.bookSlug).subscribe({
      next: (state) => {
        this.settle(state);
        void this.router.navigate(['/play', state.gameId]);
      },
      error: (failure) => this.fail(failure, 'The adventure could not be restarted.'),
    });
  }

  leave(): void {
    this.reset();
    void this.router.navigate(['/']);
  }

  reset(): void {
    this.loadSubscription?.unsubscribe();
    this.loadSubscription = undefined;
    this._state.set(null);
    this._loading.set(false);
    this._saving.set(false);
    this._error.set(null);
    this._notice.set(null);
  }

  private settle(state: GameState): void {
    this._state.set(state);
    this._loading.set(false);
    this._error.set(null);
  }

  private fail(failure: unknown, fallback: string): void {
    this._loading.set(false);
    this._error.set(messageOf(failure, fallback));
  }
}
