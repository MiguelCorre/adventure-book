import { Component, input, output } from '@angular/core';

import { ConsequenceView, GameStatus } from '../../core/models';

/** Shown once the adventure is over, either way. */
@Component({
  selector: 'app-game-over',
  templateUrl: './game-over.html',
  styleUrl: './game-over.scss',
})
export class GameOver {
  readonly status = input.required<Exclude<GameStatus, 'IN_PROGRESS'>>();
  readonly endingText = input.required<string>();
  /** What killed the player; only meaningful on a death. */
  readonly fatalBlow = input<ConsequenceView | null>(null);
  readonly busy = input(false);

  readonly restart = output<void>();
  readonly leave = output<void>();
}
