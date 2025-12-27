import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from './auth.service';

export const AdminGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.waitForAuthInitialization().pipe(
    map(() => {
      const user = authService.currentUser();
      if (user && user.role === 'ADMIN') {
        return true;
      }
      router.navigate(['/']);
      return false;
    })
  );
};
