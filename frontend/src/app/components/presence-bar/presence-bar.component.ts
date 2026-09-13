import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { PresenceInfo } from '../../core/models/models';

@Component({
  selector: 'app-presence-bar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './presence-bar.component.html',
  styleUrl: './presence-bar.component.scss',
})
export class PresenceBarComponent {
  @Input() people: PresenceInfo[] = [];
  @Input() connected = false;

  initials(name: string): string {
    return name
      .split(/\s+/)
      .map((p) => p[0]?.toUpperCase() ?? '')
      .slice(0, 2)
      .join('');
  }
}
