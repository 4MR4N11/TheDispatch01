import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from './auth.service';

/**
 * Guard to protect admin-only routes
 * Checks if the current user has ADMIN role
 * Waits for authentication to initialize before checking
 */
export const AdminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Wait for auth initialization before checking role
  return authService.waitForAuthInitialization().pipe(
    map(() => {
      const user = authService.currentUser();

      // Check if user is logged in and has ADMIN role
      if (user && user.role === 'ADMIN') {
        return true;
      }

      // Redirect to home page if not admin
      router.navigate(['/']);
      return false;
    })
  );
};
