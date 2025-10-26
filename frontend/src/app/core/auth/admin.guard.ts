import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Guard to protect admin-only routes
 * Checks if the current user has ADMIN role
 */
export const AdminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const user = authService.currentUser();

  // Check if user is logged in and has ADMIN role
  if (user && user.role === 'ADMIN') {
    return true;
  }

  // Redirect to home page if not admin
  router.navigate(['/']);
  return false;
};
