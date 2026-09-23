import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AiTestResponse, UserAiConfig } from '../../core/models/models';
import { DiagramApiService } from '../../core/services/diagram-api.service';

export interface ProviderOption {
  id: string;
  name: string;
  badge: string;
  icon: string;
  color: string;
  description: string;
  apiKeyHelpUrl: string;
  apiKeyPlaceholder: string;
  defaultModel: string;
  models: { id: string; label: string; tag?: string }[];
  defaultBaseUrl?: string;
  supportsVision: boolean;
}

export const AI_PROVIDERS: ProviderOption[] = [
  {
    id: 'gemini',
    name: 'Google Gemini',
    badge: 'Recomendado',
    icon: '✨',
    color: '#3b82f6',
    description: 'Excelente precisión, soporte nativo de imágenes/pizarras y capa gratuita generosa.',
    apiKeyHelpUrl: 'https://aistudio.google.com/app/apikey',
    apiKeyPlaceholder: 'AIzaSy...',
    defaultModel: 'gemini-2.5-flash',
    supportsVision: true,
    models: [
      { id: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash (Más rápido y recomendado)', tag: 'Default' },
      { id: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro (Máximo razonamiento)', tag: 'Pro' },
      { id: 'gemini-2.0-flash', label: 'Gemini 2.0 Flash (Alta velocidad)' },
      { id: 'gemini-1.5-flash', label: 'Gemini 1.5 Flash (Clásico)' },
      { id: 'gemini-1.5-pro', label: 'Gemini 1.5 Pro' },
    ],
  },
  {
    id: 'openai',
    name: 'OpenAI',
    badge: 'Popular',
    icon: '🟢',
    color: '#10b981',
    description: 'Modelos GPT de última generación con alto soporte para function calling.',
    apiKeyHelpUrl: 'https://platform.openai.com/api-keys',
    apiKeyPlaceholder: 'sk-proj-...',
    defaultModel: 'gpt-4o-mini',
    supportsVision: true,
    models: [
      { id: 'gpt-4o-mini', label: 'GPT-4o mini (Económico y veloz)', tag: 'Default' },
      { id: 'gpt-4o', label: 'GPT-4o (Omni multimodal avanzado)', tag: 'Top' },
      { id: 'o3-mini', label: 'o3-mini (Razonamiento profundo)' },
      { id: 'gpt-4-turbo', label: 'GPT-4 Turbo' },
    ],
  },
  {
    id: 'anthropic',
    name: 'Anthropic Claude',
    badge: 'Calidad',
    icon: '🟣',
    color: '#8b5cf6',
    description: 'Modelos Claude 3.5 con extraordinario entendimiento arquitectónico y visión.',
    apiKeyHelpUrl: 'https://console.anthropic.com/settings/keys',
    apiKeyPlaceholder: 'sk-ant-api03-...',
    defaultModel: 'claude-3-5-sonnet-20241022',
    supportsVision: true,
    models: [
      { id: 'claude-3-5-sonnet-20241022', label: 'Claude 3.5 Sonnet (Recomendado)', tag: 'Top' },
      { id: 'claude-3-5-haiku-20241022', label: 'Claude 3.5 Haiku (Rápido)', tag: 'Fast' },
      { id: 'claude-3-opus-20240229', label: 'Claude 3 Opus' },
    ],
  },
  {
    id: 'deepseek',
    name: 'DeepSeek',
    badge: 'Económico',
    icon: '🐳',
    color: '#06b6d4',
    description: 'Modelos DeepSeek V3 de alto rendimiento con costo ultra bajo.',
    apiKeyHelpUrl: 'https://platform.deepseek.com/api_keys',
    apiKeyPlaceholder: 'sk-...',
    defaultModel: 'deepseek-chat',
    supportsVision: false,
    models: [
      { id: 'deepseek-chat', label: 'DeepSeek-V3 (deepseek-chat)', tag: 'Default' },
      { id: 'deepseek-reasoner', label: 'DeepSeek-R1 (deepseek-reasoner)' },
    ],
  },
  {
    id: 'groq',
    name: 'Groq Cloud',
    badge: 'Ultra Rápido',
    icon: '⚡',
    color: '#f59e0b',
    description: 'Inferencia a velocidad ultra rápida en chips LPU con modelos Llama 3.',
    apiKeyHelpUrl: 'https://console.groq.com/keys',
    apiKeyPlaceholder: 'gsk_...',
    defaultModel: 'llama-3.3-70b-versatile',
    supportsVision: false,
    models: [
      { id: 'llama-3.3-70b-versatile', label: 'Llama 3.3 70B Versatile', tag: 'Default' },
      { id: 'llama-3.1-8b-instant', label: 'Llama 3.1 8B Instant (Ultra rápido)' },
      { id: 'mixtral-8x7b-32768', label: 'Mixtral 8x7B' },
    ],
  },
  {
    id: 'openrouter',
    name: 'OpenRouter',
    badge: 'Multi-modelo',
    icon: '🌐',
    color: '#ec4899',
    description: 'Accede a cientos de modelos con una única clave de API unificada.',
    apiKeyHelpUrl: 'https://openrouter.ai/keys',
    apiKeyPlaceholder: 'sk-or-v1-...',
    defaultModel: 'openai/gpt-4o-mini',
    supportsVision: true,
    models: [
      { id: 'openai/gpt-4o-mini', label: 'OpenAI: GPT-4o mini' },
      { id: 'anthropic/claude-3.5-sonnet', label: 'Anthropic: Claude 3.5 Sonnet' },
      { id: 'google/gemini-2.5-flash', label: 'Google: Gemini 2.5 Flash' },
      { id: 'meta-llama/llama-3.3-70b-instruct', label: 'Meta: Llama 3.3 70B' },
    ],
  },
  {
    id: 'custom',
    name: 'Servidor Local / Custom',
    badge: 'Local / Proxy',
    icon: '💻',
    color: '#64748b',
    description: 'Ollama, LMStudio, vLLM, LocalAI o cualquier endpoint compatible con OpenAI.',
    apiKeyHelpUrl: '',
    apiKeyPlaceholder: 'Opcional si es local (ej. ollama)',
    defaultModel: 'llama3.2',
    defaultBaseUrl: 'http://localhost:11434/v1',
    supportsVision: false,
    models: [
      { id: 'llama3.2', label: 'Llama 3.2 (Local)' },
      { id: 'mistral', label: 'Mistral 7B' },
      { id: 'qwen2.5-coder', label: 'Qwen 2.5 Coder' },
      { id: 'custom', label: 'Otro modelo personalizado...' },
    ],
  },
];

@Component({
  selector: 'app-ai-settings-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ai-settings-modal.component.html',
  styleUrl: './ai-settings-modal.component.scss',
})
export class AiSettingsModalComponent implements OnInit {
  @Input() open = false;
  @Output() close = new EventEmitter<void>();
  @Output() saved = new EventEmitter<UserAiConfig>();

  providers = AI_PROVIDERS;

  // Estado del formulario
  selectedProviderId = 'gemini';
  apiKey = '';
  showApiKey = false;
  selectedModel = 'gemini-2.5-flash';
  baseUrl = '';
  customEnabled = true;

  // Estado del servidor y feedback
  loading = signal(false);
  saving = signal(false);
  testing = signal(false);
  resetting = signal(false);

  currentConfig: UserAiConfig | null = null;
  testResult: AiTestResponse | null = null;
  statusMessage = signal<{ text: string; type: 'success' | 'error' | 'info' } | null>(null);

  constructor(private api: DiagramApiService) {}

  ngOnInit(): void {
    if (this.open) {
      this.loadConfig();
    }
  }

  get currentProvider(): ProviderOption {
    return this.providers.find((p) => p.id === this.selectedProviderId) ?? this.providers[0];
  }

  loadConfig(): void {
    this.loading.set(true);
    this.statusMessage.set(null);
    this.testResult = null;

    this.api.getAiConfig().subscribe({
      next: (cfg) => {
        this.currentConfig = cfg;
        this.customEnabled = cfg.customEnabled;

        if (cfg.provider && this.providers.some((p) => p.id === cfg.provider)) {
          this.selectedProviderId = cfg.provider;
        } else {
          this.selectedProviderId = 'gemini';
        }

        this.baseUrl = cfg.baseUrl || '';
        this.selectedModel = cfg.model || this.currentProvider.defaultModel;
        this.apiKey = '';
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

  onProviderSelect(providerId: string): void {
    this.selectedProviderId = providerId;
    const p = this.currentProvider;
    this.selectedModel = p.defaultModel;
    this.testResult = null;
    this.statusMessage.set(null);

    if (p.defaultBaseUrl && !this.baseUrl) {
      this.baseUrl = p.defaultBaseUrl;
    }
  }

  setModel(modelId: string): void {
    this.selectedModel = modelId;
  }

  get effectiveModelToSave(): string {
    return this.selectedModel.trim() || this.currentProvider.defaultModel;
  }

  testConnection(): void {
    this.testing.set(true);
    this.testResult = null;
    this.statusMessage.set(null);

    const modelToTest = this.effectiveModelToSave;
    const req = {
      provider: this.selectedProviderId,
      apiKey: this.apiKey.trim() || undefined,
      model: modelToTest,
      baseUrl: this.baseUrl.trim() || undefined,
      useSavedKey: !this.apiKey.trim() && (this.currentConfig?.hasCustomApiKey ?? false),
    };

    this.api.testAiConfig(req).subscribe({
      next: (res) => {
        this.testResult = res;
        this.testing.set(false);
      },
      error: (err) => {
        this.testResult = {
          success: false,
          message: err?.error?.message || 'No se pudo contactar el servidor de prueba.',
          provider: this.selectedProviderId,
          model: modelToTest,
        };
        this.testing.set(false);
      },
    });
  }

  save(): void {
    this.saving.set(true);
    this.statusMessage.set(null);

    const modelToSave = this.effectiveModelToSave;
    const payload = {
      provider: this.selectedProviderId,
      apiKey: this.apiKey.trim() || undefined,
      model: modelToSave,
      baseUrl: this.baseUrl.trim() || '',
      customEnabled: this.customEnabled,
    };

    this.api.updateAiConfig(payload).subscribe({
      next: (updated) => {
        this.currentConfig = updated;
        this.saving.set(false);
        this.apiKey = '';
        this.statusMessage.set({
          text: '✓ Credenciales de IA guardadas correctamente.',
          type: 'success',
        });
        this.saved.emit(updated);
        setTimeout(() => {
          this.closeModal();
        }, 800);
      },
      error: (err) => {
        this.saving.set(false);
        this.statusMessage.set({
          text: 'Error al guardar: ' + (err?.error?.message || 'Error desconocido'),
          type: 'error',
        });
      },
    });
  }

  resetToDefault(): void {
    if (!confirm('¿Deseas restablecer tus credenciales y usar la configuración por defecto del sistema?')) {
      return;
    }
    this.resetting.set(true);
    this.statusMessage.set(null);
    this.testResult = null;

    this.api.resetAiConfig().subscribe({
      next: (res) => {
        this.currentConfig = res;
        this.apiKey = '';
        this.baseUrl = '';
        this.customEnabled = false;
        this.selectedProviderId = res.provider || 'gemini';
        this.selectedModel = res.model || this.currentProvider.defaultModel;
        this.resetting.set(false);
        this.statusMessage.set({
          text: '✓ Credenciales restablecidas a valores del sistema.',
          type: 'info',
        });
        this.saved.emit(res);
      },
      error: (err) => {
        this.resetting.set(false);
        this.statusMessage.set({
          text: 'Error al restablecer: ' + (err?.error?.message || 'Error desconocido'),
          type: 'error',
        });
      },
    });
  }

  clearSavedApiKey(): void {
    if (!confirm('¿Eliminar la clave de API guardada para tu usuario?')) return;
    this.saving.set(true);
    this.api.updateAiConfig({ clearApiKey: true }).subscribe({
      next: (res) => {
        this.currentConfig = res;
        this.apiKey = '';
        this.saving.set(false);
        this.statusMessage.set({
          text: '✓ Clave de API eliminada.',
          type: 'info',
        });
        this.saved.emit(res);
      },
      error: () => this.saving.set(false),
    });
  }

  closeModal(): void {
    this.close.emit();
  }
}
