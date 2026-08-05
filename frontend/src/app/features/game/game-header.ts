import { Component, computed, input, output } from '@angular/core';

/**
 * The header the brief asks for: leave the adventure, the book's name, the player's
 * health, and a way to save.
 */
@Component({
  selector: 'app-game-header',
  templateUrl: './game-header.html',
  styleUrl: './game-header.scss',
})
export class GameHeader {
  readonly bookTitle = input.required<string>();
  readonly health = input.required<number>();
  readonly maxHealth = input.required<number>();
  readonly canSave = input(true);
  readonly saving = input(false);
  readonly pausing = input(false);

  readonly leave = output<void>();
  readonly pauseGame = output<void>();
  readonly save = output<void>();

  /** One pip per health point, so the bar reads at a glance rather than as a number. */
  protected readonly pips = computed(() =>
    Array.from({ length: this.maxHealth() }, (_, index) => index < this.health()),
  );

  protected readonly critical = computed(() => this.health() <= Math.ceil(this.maxHealth() * 0.3));
}
