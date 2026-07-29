import { Component, input, output } from '@angular/core';

import { ConsequenceView, SectionView } from '../../core/models';

/** The page the player is reading, and the choices at the foot of it. */
@Component({
  selector: 'app-section-view',
  templateUrl: './section-view.html',
  styleUrl: './section-view.scss',
})
export class SectionPanel {
  readonly section = input.required<SectionView>();
  readonly consequence = input<ConsequenceView | null>(null);
  readonly busy = input(false);
  /**
   * Hidden once the adventure is over. A dying player stays on the section that killed
   * them, so its choices are still in the state and would otherwise invite a click that
   * can no longer do anything.
   */
  readonly showChoices = input(true);

  readonly choose = output<number>();

  /** Blank lines in the source text separate paragraphs. */
  protected paragraphs(text: string): string[] {
    return text.split(/\n\s*\n/).filter((paragraph) => paragraph.trim().length > 0);
  }
}
