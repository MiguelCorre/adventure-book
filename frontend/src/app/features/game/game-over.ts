import { Component, input, output } from '@angular/core';

import { ConsequenceView, GameStatus } from '../../core/models';

/**
 * Shown once the adventure is over, either way.
 *
 * <p>Says how it ended and what to do next, and deliberately repeats none of the prose:
 * the closing passage is already in the reading area above, and the blow that killed the
 * player is already in the consequence banner. Printing either twice made a winning reader
 * read the same paragraph two lines apart.
 */
@Component({
  selector: 'app-game-over',
  templateUrl: './game-over.html',
  styleUrl: './game-over.scss',
})
export class GameOver {
  readonly status = input.required<Exclude<GameStatus, 'IN_PROGRESS'>>();
  /** What killed the player; only meaningful on a death. */
  readonly fatalBlow = input<ConsequenceView | null>(null);
  readonly health = input.required<number>();
  readonly maxHealth = input.required<number>();
  readonly busy = input(false);

  readonly restart = output<void>();
  readonly leave = output<void>();
}
