import { Component, OnDestroy, effect, inject, input } from '@angular/core';

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
    // Loading follows the id rather than happening once on init. The router reuses this
    // component when only the route parameter changes — pressing Try Again, or going back
    // in history — so ngOnInit would fire for the first game and never again, leaving the
    // address bar and the screen describing two different play-throughs.
    effect(() => this.store.load(this.gameId()));
  }

  canDeactivate(nextUrl: string): boolean {
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
