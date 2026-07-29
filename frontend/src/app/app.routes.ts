import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/library/library-page').then((m) => m.LibraryPage),
    title: 'The Adventure Library',
  },
  {
    path: 'play/:gameId',
    loadComponent: () => import('./features/game/game-page').then((m) => m.GamePage),
    title: 'Adventure',
  },
  { path: '**', redirectTo: '' },
];
