import { CommonModule } from '@angular/common';
import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface AiLogEntry {
  role: 'user' | 'assistant' | 'error';
  text: string;
}

// Tipos minimos de la Web Speech API (no viene en lib.dom.d.ts de TypeScript por defecto).
interface SpeechRecognitionResultLike {
  isFinal: boolean;
  0: { transcript: string };
}
interface SpeechRecognitionEventLike {
  results: ArrayLike<SpeechRecognitionResultLike>;
}
interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start(): void;
  stop(): void;
  onresult: ((ev: SpeechRecognitionEventLike) => void) | null;
  onend: (() => void) | null;
  onerror: ((ev: unknown) => void) | null;
}

/**
 * Segundo tipo de interaccion que pide el enunciado: un asistente que edita
 * el diagrama por texto o por voz (Web Speech API transcribe en el
 * navegador y mandamos el texto ya transcrito al backend), y ademas permite
 * subir una foto para reconocer un diagrama completo (tercera forma).
 */
@Component({
  selector: 'app-ai-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ai-panel.component.html',
  styleUrl: './ai-panel.component.scss',
})
export class AiPanelComponent implements OnChanges {
  @Input() open = true;
  @Input() log: AiLogEntry[] = [];
  @Input() busy = false;
  @Output() close = new EventEmitter<void>();
  @Output() openSettings = new EventEmitter<void>();
  @Output() command = new EventEmitter<string>();
  @Output() imageSelected = new EventEmitter<File>();

  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;
  @ViewChild('logContainer') private logContainer?: ElementRef<HTMLDivElement>;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['log'] || changes['busy']) {
      setTimeout(() => this.scrollToBottom(), 50);
    }
  }

  private scrollToBottom(): void {
    if (this.logContainer) {
      const el = this.logContainer.nativeElement;
      el.scrollTop = el.scrollHeight;
    }
  }

  text = '';
  listening = signal(false);
  voiceSupported = signal(false);

  private recognition: SpeechRecognitionLike | null = null;

  constructor() {
    const w = window as unknown as { webkitSpeechRecognition?: new () => SpeechRecognitionLike; SpeechRecognition?: new () => SpeechRecognitionLike };
    const Ctor = w.SpeechRecognition ?? w.webkitSpeechRecognition;
    if (Ctor) {
      this.voiceSupported.set(true);
      this.recognition = new Ctor();
      this.recognition.lang = 'es-BO';
      this.recognition.continuous = false;
      this.recognition.interimResults = false;
      this.recognition.onresult = (ev) => {
        const transcript = ev.results[0]?.[0]?.transcript ?? '';
        if (transcript.trim()) {
          this.text = transcript;
          this.send();
        }
      };
      this.recognition.onend = () => this.listening.set(false);
      this.recognition.onerror = () => this.listening.set(false);
    }
  }

  toggleVoice(): void {
    if (!this.recognition) return;
    if (this.listening()) {
      this.recognition.stop();
      this.listening.set(false);
    } else {
      this.recognition.start();
      this.listening.set(true);
    }
  }

  send(): void {
    const value = this.text.trim();
    if (!value || this.busy) return;
    this.command.emit(value);
    this.text = '';
  }

  triggerImagePicker(): void {
    this.fileInput?.nativeElement.click();
  }

  onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.imageSelected.emit(file);
    }
    input.value = '';
  }
}
