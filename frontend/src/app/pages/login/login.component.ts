import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  mode = signal<'login' | 'register'>('login');
  username = '';
  displayName = '';
  password = '';
  error = signal<string | null>(null);
  loading = signal(false);

  constructor(private auth: AuthService, private router: Router) {}

  toggleMode(): void {
    this.mode.set(this.mode() === 'login' ? 'register' : 'login');
    this.error.set(null);
  }

  submit(): void {
    this.error.set(null);
    this.loading.set(true);
    const done = () => this.router.navigateByUrl('/diagrams');
    const fail = (err: any) => {
      this.loading.set(false);
      this.error.set(err?.error?.message ?? 'No se pudo iniciar sesion');
    };

    if (this.mode() === 'login') {
      this.auth.login(this.username, this.password).subscribe({ next: done, error: fail });
    } else {
      this.auth.register(this.username, this.displayName, this.password).subscribe({ next: done, error: fail });
    }
  }
}
