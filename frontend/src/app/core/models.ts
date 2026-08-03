/**
 * Mirrors the backend API contract.
 *
 * The game state deliberately carries no destination or consequence for an option: the
 * server resolves every move, and the client is told only what the player can see.
 */

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type GameStatus = 'IN_PROGRESS' | 'WON' | 'DEAD';
export type SectionType = 'BEGIN' | 'NODE' | 'END';
export type ConsequenceType = 'LOSE_HEALTH' | 'GAIN_HEALTH';

export const DIFFICULTIES: readonly Difficulty[] = ['EASY', 'MEDIUM', 'HARD'];

export interface ValidationIssue {
  readonly rule: string;
  readonly message: string;
  readonly sectionId: string | null;
}

export interface BookSummary {
  readonly slug: string;
  readonly title: string;
  readonly author: string | null;
  readonly description: string | null;
  readonly tags: readonly string[];
  readonly difficulty: Difficulty | null;
  readonly sectionCount: number;
  readonly readingMinutes: number;
  readonly valid: boolean;
  readonly issues: readonly ValidationIssue[];
  readonly hasSave: boolean;
}

export interface OptionView {
  readonly index: number;
  readonly description: string;
}

export interface SectionView {
  readonly id: string;
  readonly text: string;
  readonly type: SectionType;
  readonly options: readonly OptionView[];
}

export interface ConsequenceView {
  readonly type: ConsequenceType;
  readonly value: number;
  readonly text: string;
}

export interface GameState {
  readonly gameId: string;
  readonly bookSlug: string;
  readonly bookTitle: string;
  readonly health: number;
  readonly maxHealth: number;
  readonly status: GameStatus;
  readonly section: SectionView;
  readonly lastConsequence: ConsequenceView | null;
}

/** RFC 7807 body returned by the API on failure. */
export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  /** Present when an upload was rejected by validation. */
  readonly issues?: readonly ValidationIssue[];
}
