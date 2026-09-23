import { CommonModule } from '@angular/common';
import { Component, computed, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DiagramApiService } from '../../core/services/diagram-api.service';
import { AuthService } from '../../core/services/auth.service';
import { DiagramSummary } from '../../core/models/models';
import { AiSettingsModalComponent } from '../../components/ai-settings-modal/ai-settings-modal.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, AiSettingsModalComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  diagrams = signal<DiagramSummary[]>([]);
  activeFilter = signal<'all' | 'mine' | 'shared'>('all');
  newName = '';
  newDescription = '';
  joinCode = '';
  error = signal<string | null>(null);
  aiSettingsOpen = signal(false);

  filteredDiagrams = computed(() => {
    const list = this.diagrams();
    const filter = this.activeFilter();
    if (filter === 'mine') {
      return list.filter((d) => d.isOwner);
    }
    if (filter === 'shared') {
      return list.filter((d) => !d.isOwner);
    }
    return list;
  });

  ownedCount = computed(() => this.diagrams().filter((d) => d.isOwner).length);
  sharedCount = computed(() => this.diagrams().filter((d) => !d.isOwner).length);

  constructor(private api: DiagramApiService, public auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.listMine().subscribe({
      next: (list) => this.diagrams.set(list),
      error: (err) => this.error.set(err?.error?.message ?? 'No se pudieron cargar los diagramas'),
    });
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
      error: (err) => this.error.set(err?.error?.message ?? 'Código de invitación inválido o diagrama no encontrado'),
    });
  }

  open(diagram: DiagramSummary): void {
    this.router.navigate(['/diagrams', diagram.id]);
  }

  deleteDiagram(event: Event, diagram: DiagramSummary): void {
    event.stopPropagation();
    if (!confirm(`¿Estás seguro de eliminar el diagrama "${diagram.name}"? Esta acción no se puede deshacer.`)) {
      return;
    }
    this.api.deleteDiagram(diagram.id).subscribe({
      next: () => this.reload(),
      error: (err) => this.error.set(err?.error?.message ?? 'No se pudo eliminar el diagrama'),
    });
  }

  leaveDiagram(event: Event, diagram: DiagramSummary): void {
    event.stopPropagation();
    if (!confirm(`¿Deseas quitar el diagrama "${diagram.name}" de tu lista? Podrás volver a unirte con el código.`)) {
      return;
    }
    this.api.leaveDiagram(diagram.id).subscribe({
      next: () => this.reload(),
      error: (err) => this.error.set(err?.error?.message ?? 'No se pudo salir del diagrama'),
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
