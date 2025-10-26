import { HttpInterceptorFn } from '@angular/common/http';

// ✅ SECURITY FIX: Updated interceptor for cookie-based authentication
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Always send cookies with requests (withCredentials: true)
  // This ensures the HttpOnly JWT cookie is included
  const authReq = req.clone({
    withCredentials: true
  });

  return next(authReq);
};