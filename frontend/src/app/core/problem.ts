import { HttpErrorResponse } from '@angular/common/http';

import { ProblemDetail, ValidationIssue } from './models';

/** Reads the API's problem document out of a failed response, if there is one. */
export function problemOf(error: unknown): ProblemDetail | null {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
    return error.error as ProblemDetail;
  }
  return null;
}

/** A sentence worth showing the user, falling back to something honest when offline. */
export function messageOf(error: unknown, fallback = 'Something went wrong.'): string {
  const problem = problemOf(error);
  if (problem?.detail) {
    return problem.detail;
  }
  if (error instanceof HttpErrorResponse && error.status === 0) {
    return 'Could not reach the server. Is the backend running?';
  }
  return problem?.title ?? fallback;
}

/** Validation issues attached to a rejected upload. */
export function issuesOf(error: unknown): readonly ValidationIssue[] {
  return problemOf(error)?.issues ?? [];
}

export function statusOf(error: unknown): number | null {
  return error instanceof HttpErrorResponse ? error.status : null;
}
