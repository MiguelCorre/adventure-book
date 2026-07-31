import { CanDeactivateFn } from '@angular/router';

/** Minimal contract keeps the guard independent from the lazy-loaded game component. */
export interface GameExitAware {
  canDeactivate(nextUrl: string): boolean;
}

/** Applies the component's own state-aware exit policy to every router navigation. */
export const confirmGameExit: CanDeactivateFn<GameExitAware> = (
  component,
  _currentRoute,
  _currentState,
  nextState,
) => component.canDeactivate(nextState.url);
