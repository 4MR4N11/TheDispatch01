import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { map } from 'rxjs/operators';

// ✅ SECURITY FIX: Updated guard to work with cookie-based auth
export const AuthGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // No token check needed - rely on isLoggedIn signal from checkAuth
  // Wait for auth initialization to complete
  return authService.waitForAuthInitialization().pipe(
    map(() => {
      if (authService.isLoggedIn()) {
        return true;
      } else {
        router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
        return false;
      }
    })
  );
};
