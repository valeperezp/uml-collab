import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse } from '../models/models';

const STORAGE_KEY = 'umlcollab.auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  /** Sesion actual, en un signal para que cualquier componente reaccione sin suscribirse a mano. */
  readonly current = signal<AuthResponse | null>(this.readFromStorage());

  constructor(private http: HttpClient) {}

  register(username: string, displayName: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/register`, { username, displayName, password })
      .pipe(tap((res) => this.setSession(res)));
  }

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, { username, password })
      .pipe(tap((res) => this.setSession(res)));
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.current.set(null);
  }

  get token(): string | null {
    return this.current()?.token ?? null;
  }

  isAuthenticated(): boolean {
    return this.current() !== null;
  }

  private setSession(res: AuthResponse): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(res));
    this.current.set(res);
  }

  private readFromStorage(): AuthResponse | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as AuthResponse) : null;
    } catch {
      return null;
    }
  }
}
