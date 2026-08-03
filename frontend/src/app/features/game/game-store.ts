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
  /** The request allowed to replace the current game state: load, choice or restart. */
  private stateSubscription?: Subscription;
  private saveSubscription?: Subscription;

  private readonly _state = signal<GameState | null>(null);
  private readonly _loading = signal(false);
  private readonly _saving = signal(false);
  private readonly _pausing = signal(false);
  private readonly _error = signal<string | null>(null);
  private readonly _notice = signal<string | null>(null);

  readonly state = this._state.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly saving = this._saving.asReadonly();
  readonly pausing = this._pausing.asReadonly();
  readonly error = this._error.asReadonly();
  readonly notice = this._notice.asReadonly();

  readonly finished = computed(() => {
    const status = this._state()?.status;
    return status === 'WON' || status === 'DEAD';
  });
  readonly inProgress = computed(() => this._state()?.status === 'IN_PROGRESS');

  /** Loads a game by its handle, used when the play screen is opened or reloaded. */
  load(gameId: string): void {
    this.cancelPendingRequests();
    // A route id owns the whole screen. Keeping the previous game visible while the new
    // request is in flight is misleading, and keeping it after a failed request would let
    // the player make choices in a game that no longer matches the address bar.
    this._state.set(null);
    this._loading.set(true);
    this._saving.set(false);
    this._pausing.set(false);
    this._error.set(null);
    this._notice.set(null);
    this.stateSubscription = this.api.get(gameId).subscribe({
      next: (state) => this.settle(state),
      error: (failure) => this.fail(failure, 'This adventure could not be opened.'),
    });
  }

  choose(optionIndex: number): void {
    const current = this._state();
    if (!current || this.finished() || this._loading() || this._saving() || this._pausing()) {
      return;
    }

    this._loading.set(true);
    this._notice.set(null);
    this.stateSubscription = this.api.choose(current.gameId, optionIndex).subscribe({
      next: (state) => this.settle(state),
      error: (failure) => this.fail(failure, 'That choice could not be made.'),
    });
  }

  save(): void {
    const current = this.currentSavableGame();
    if (!current) {
      return;
    }

    this._saving.set(true);
    this.persist(
      current.gameId,
      () => {
        this._saving.set(false);
        this._notice.set('Progress saved.');
      },
      () => this._saving.set(false),
    );
  }

  /** Persists the current position before returning to the library. */
  pause(): void {
    const current = this.currentSavableGame();
    if (!current) {
      return;
    }

    this._pausing.set(true);
    this._notice.set(null);
    this.persist(
      current.gameId,
      () => {
        void this.router.navigate(['/']).then(
          (navigated) => {
            if (!navigated) {
              this.pauseNavigationFailed();
            }
          },
          () => this.pauseNavigationFailed(),
        );
      },
      () => this._pausing.set(false),
    );
  }

  /** Starts the same book again from the beginning, after a death or a win. */
  restart(): void {
    const current = this._state();
    if (!current) {
      return;
    }

    this._loading.set(true);
    this._error.set(null);
    this.stateSubscription = this.api.start(current.bookSlug).subscribe({
      next: (state) => {
        this.settle(state);
        void this.router.navigate(['/play', state.gameId]);
      },
      error: (failure) => this.fail(failure, 'The adventure could not be restarted.'),
    });
  }

  leave(): void {
    // GamePage resets only after a successful navigation. Resetting here would erase the
    // session before CanDeactivate has a chance to ask, and a declined exit could not be
    // restored.
    if (!this._pausing()) {
      void this.router.navigate(['/']);
    }
  }

  reset(): void {
    this.cancelPendingRequests();
    this._state.set(null);
    this._loading.set(false);
    this._saving.set(false);
    this._pausing.set(false);
    this._error.set(null);
    this._notice.set(null);
  }

  /** No response owned by a game that has left the screen may mutate this root store. */
  private cancelPendingRequests(): void {
    this.stateSubscription?.unsubscribe();
    this.stateSubscription = undefined;
    this.saveSubscription?.unsubscribe();
    this.saveSubscription = undefined;
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

  private pauseNavigationFailed(): void {
    this._pausing.set(false);
    this._error.set('The library could not be opened. Your progress was saved.');
  }

  private currentSavableGame(): GameState | null {
    const current = this._state();
    return !current || this.finished() || this._loading() || this._saving() || this._pausing()
      ? null
      : current;
  }

  private persist(gameId: string, onSuccess: () => void, onError: () => void): void {
    this._error.set(null);
    this.saveSubscription = this.api.save(gameId).subscribe({
      next: onSuccess,
      error: (failure) => {
        onError();
        this._error.set(messageOf(failure, 'Progress could not be saved.'));
      },
    });
  }
}
