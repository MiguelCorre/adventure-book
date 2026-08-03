import { Component, OnDestroy, effect, inject, input, untracked } from '@angular/core';

import { GameOver } from './game-over';
import { GameExitAware } from './game-exit.guard';
import { GameHeader } from './game-header';
import { GameStore } from './game-store';
import { SectionPanel } from './section-view';

/** Objectives 2 and 3: the screen the adventure is played on. */
@Component({
  selector: 'app-game-page',
  imports: [GameHeader, SectionPanel, GameOver],
  templateUrl: './game-page.html',
  styleUrl: './game-page.scss',
})
export class GamePage implements OnDestroy, GameExitAware {
  /** Bound from the route by withComponentInputBinding(). */
  readonly gameId = input.required<string>();

  protected readonly store = inject(GameStore);

  constructor() {
    // Loading follows the route id because the router can reuse this component. Restarting
    // already supplies the new state before navigation, so keep it when the URL catches up.
    effect(() => {
      const gameId = this.gameId();
      const loadedGameId = untracked(() => this.store.state()?.gameId);
      if (loadedGameId !== gameId) {
        this.store.load(gameId);
      }
    });
  }

  canDeactivate(nextUrl: string): boolean {
    // Pause owns a save-then-navigate transaction; asking whether to discard after that
    // successful save would contradict the action the player just chose.
    if (this.store.pausing()) {
      return true;
    }
    // Restart already holds the fresh state before navigating to its new UUID. That route
    // is the state catching up with its own URL, not the reader abandoning progress.
    if (nextUrl === `/play/${this.store.state()?.gameId}`) {
      return true;
    }
    return (
      !this.store.inProgress() ||
      window.confirm('Leave this adventure? Any progress since your last save will be lost.')
    );
  }

  ngOnDestroy(): void {
    this.store.reset();
  }
}
