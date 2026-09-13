import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DiagramApiService } from '../../core/services/diagram-api.service';
import { AuthService } from '../../core/services/auth.service';
import { DiagramSummary } from '../../core/models/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  diagrams = signal<DiagramSummary[]>([]);
  newName = '';
  newDescription = '';
  joinCode = '';
  error = signal<string | null>(null);

  constructor(private api: DiagramApiService, public auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.listMine().subscribe({ next: (list) => this.diagrams.set(list) });
  }

  create(): void {
    if (!this.newName.trim()) return;
    this.api.create(this.newName.trim(), this.newDescription.trim()).subscribe({
      next: (d) => this.router.navigate(['/diagrams', d.id]),
      error: (err) => this.error.set(err?.error?.message ?? 'No se pudo crear el diagrama'),
    });
  }

  join(): void {
    if (!this.joinCode.trim()) return;
    this.api.joinByCode(this.joinCode.trim().toUpperCase()).subscribe({
      next: (d) => this.router.navigate(['/diagrams', d.id]),
      error: (err) => this.error.set(err?.error?.message ?? 'Codigo invalido'),
    });
  }

  open(diagram: DiagramSummary): void {
    this.router.navigate(['/diagrams', diagram.id]);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
