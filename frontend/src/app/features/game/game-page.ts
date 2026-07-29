import { Component, OnDestroy, OnInit, inject, input } from '@angular/core';

import { GameOver } from './game-over';
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
export class GamePage implements OnInit, OnDestroy {
  /** Bound from the route by withComponentInputBinding(). */
  readonly gameId = input.required<string>();

  protected readonly store = inject(GameStore);

  ngOnInit(): void {
    this.store.load(this.gameId());
  }

  ngOnDestroy(): void {
    this.store.reset();
  }
}
