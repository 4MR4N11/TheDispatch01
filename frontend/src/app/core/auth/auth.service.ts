import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { tap, catchError, filter, take } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { UserResponse, AuthResponse, LoginRequest, RegisterRequest } from '../../shared/models/models';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/auth`;
  readonly isLoggedIn = signal(false);
  readonly currentUser = signal<UserResponse | null>(null);
  
  // Add a subject to track initialization state
  private readonly _authInitialized = new BehaviorSubject<boolean>(false);
  public readonly authInitialized$ = this._authInitialized.asObservable();

  // ✅ SECURITY FIX: Removed localStorage token storage
  // Token is now stored ONLY in HttpOnly cookies (set by backend)
  // This prevents XSS attacks from stealing the token
  // The backend sets: httpOnly=true, secure=true, sameSite=Strict

  // Token retrieval removed - cookies are sent automatically
  getToken(): string | null {
    // Token is in HttpOnly cookie, not accessible to JavaScript (by design)
    // This method kept for backward compatibility but returns null
    return null;
  }

  // No need to store/remove tokens - handled by backend cookies
  // Cookies are automatically sent with every request via withCredentials

  login(request: LoginRequest): Observable<AuthResponse> {
    // ✅ SECURITY FIX: withCredentials enables cookie-based auth
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request, {
      withCredentials: true  // Send cookies with request
    }).pipe(
        tap(response => {
          // Cookie is set by backend automatically
          this.isLoggedIn.set(true);

          // Fetch user details after login
          this.checkAuth().subscribe({
            next: () => {
              this._authInitialized.next(true);
            },
            error: () => {
              this._authInitialized.next(true);
            }
          });
        })
      );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    // ✅ SECURITY FIX: withCredentials enables cookie-based auth
    return this.http.post<AuthResponse>(`${this.baseUrl}/register`, request, {
      withCredentials: true  // Send cookies with request
    }).pipe(
        tap(response => {
          // Cookie is set by backend automatically
          this.isLoggedIn.set(true);

          // Fetch user details after registration
          this.checkAuth().subscribe({
            next: () => {
              this._authInitialized.next(true);
            },
            error: () => {
              this._authInitialized.next(true);
            }
          });
        })
      );
  }

  checkAuth(): Observable<UserResponse> {
    // ✅ SECURITY FIX: No token check needed, cookies sent automatically
    return this.http.get<UserResponse>(`${environment.apiUrl}/users/me`, {
      withCredentials: true  // Send cookies with request
    })
      .pipe(
        tap(user => {
          this.isLoggedIn.set(true);
          this.currentUser.set(user);
          this._authInitialized.next(true);
        }),
        catchError(err => {
          this.logout();
          this._authInitialized.next(true);
          return throwError(() => err);
        })
      );
  }

  // Call this method when app initializes
  initializeAuth(): void {
    // ✅ SECURITY FIX: Check if user is authenticated via cookie
    this.checkAuth().subscribe({
      next: () => {
        // Auth initialized successfully
      },
      error: () => {
        // Cookie might be expired or invalid
        this.logout();
      }
    });
  }

  // Method to wait for auth initialization
  waitForAuthInitialization(): Observable<boolean> {
    return this.authInitialized$.pipe(
      filter(initialized => initialized),
      take(1)
    );
  }

  logout() {
    // ✅ SECURITY FIX: Logout clears cookie on backend
    // Call logout endpoint to clear HttpOnly cookie
    this.http.post(`${this.baseUrl}/logout`, {}, {
      withCredentials: true
    }).subscribe({
      next: () => {
        this.isLoggedIn.set(false);
        this.currentUser.set(null);
        this._authInitialized.next(true);
      },
      error: () => {
        // Even if logout fails, clear local state
        this.isLoggedIn.set(false);
        this.currentUser.set(null);
        this._authInitialized.next(true);
      }
    });
  }

  // Helper methods for reactive access
  getCurrentUser(): UserResponse | null {
    return this.currentUser();
  }

  getIsLoggedIn(): boolean {
    return this.isLoggedIn();
  }

  // Check if auth is initialized
  isAuthInitialized(): boolean {
    return this._authInitialized.value;
  }
}