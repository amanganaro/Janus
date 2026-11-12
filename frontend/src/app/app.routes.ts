import { Routes } from '@angular/router';

/**
 * The job id is part of the URL on purpose: every page rebuilds itself from the backend, so a
 * reload or a bookmarked link keeps working as long as the backend still holds the job.
 */
export const routes: Routes = [
  {
    path: '',
    title: 'Janus - new assessment',
    loadComponent: () => import('./pages/input-page/input-page').then((m) => m.InputPage),
  },
  {
    path: 'jobs/:jobId',
    title: 'Janus - assessment in progress',
    loadComponent: () => import('./pages/running-page/running-page').then((m) => m.RunningPage),
  },
  {
    path: 'jobs/:jobId/results',
    title: 'Janus - results',
    loadComponent: () => import('./pages/results-page/results-page').then((m) => m.ResultsPage),
  },
  {
    path: 'jobs/:jobId/results/:num',
    title: 'Janus - molecule detail',
    loadComponent: () =>
      import('./pages/molecule-detail-page/molecule-detail-page').then(
        (m) => m.MoleculeDetailPage,
      ),
  },
  { path: '**', redirectTo: '' },
];
