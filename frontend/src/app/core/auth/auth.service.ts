import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { tap, catchError, filter, take } from 'rxjs/operators';
import { toObservable } from '@angular/core/rxjs-interop';
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

  private readonly _authInitialized = signal(false);
  readonly authInitialized$ = toObservable(this._authInitialized);

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/login`, request, {
      withCredentials: true
    }).pipe(
        tap(response => {
          this.isLoggedIn.set(true);
          this.checkAuth().subscribe({
            next: () => {
              this._authInitialized.set(true);
            },
            error: () => {
              this._authInitialized.set(true);
            }
          });
        })
      );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.baseUrl}/register`, request, {
      withCredentials: true
    }).pipe(
        tap(response => {
          this.isLoggedIn.set(true);
          this.checkAuth().subscribe({
            next: () => {
              this._authInitialized.set(true);
            },
            error: () => {
              this._authInitialized.set(true);
            }
          });
        })
      );
  }

  checkAuth(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${environment.apiUrl}/users/me`, {
      withCredentials: true
    })
      .pipe(
        tap(user => {
          this.isLoggedIn.set(true);
          this.currentUser.set(user);
          this._authInitialized.set(true);
        }),
        catchError(err => {
          this.logout();
          this._authInitialized.set(true);
          return throwError(() => err);
        })
      );
  }

  // Call this method when app initializes
  initializeAuth(): void {
    this.checkAuth().subscribe({
      next: () => {
      },
      error: () => {
        this.logout();
      }
    });
  }

  waitForAuthInitialization(): Observable<boolean> {
    return this.authInitialized$.pipe(
      filter(initialized => initialized),
      take(1)
    );
  }

  logout() {
    this.http.post(`${this.baseUrl}/logout`, {}, {
      withCredentials: true
    }).subscribe({
      next: () => {
        this.isLoggedIn.set(false);
        this.currentUser.set(null);
        this._authInitialized.set(true);
      },
      error: () => {
        this.isLoggedIn.set(false);
        this.currentUser.set(null);
        this._authInitialized.set(true);
      }
    });
  }

}