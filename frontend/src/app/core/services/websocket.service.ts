import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { DiagramEvent, PresenceInfo } from '../models/models';

/**
 * Canal de colaboracion en tiempo real: un cliente STOMP (con fallback
 * SockJS) que se suscribe a los cambios de UN diagrama a la vez. Cada
 * cambio que llega (de otro usuario, del agente de IA, de una importacion)
 * se reemite por estos Subjects para que el editor los aplique en el acto.
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client: Client | null = null;
  private currentDiagramId: string | null = null;

  readonly events$ = new Subject<DiagramEvent>();
  readonly presence$ = new Subject<PresenceInfo[]>();
  readonly connected$ = new Subject<boolean>();

  constructor(private auth: AuthService) {}

  connectToDiagram(diagramId: string): void {
    if (this.currentDiagramId === diagramId && this.client?.connected) {
      return;
    }
    this.disconnect();
    this.currentDiagramId = diagramId;

    const token = this.auth.token ?? '';
    const client = new Client({
      webSocketFactory: () => new SockJS(`${environment.wsUrl}?access_token=${encodeURIComponent(token)}`),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    client.onConnect = () => {
      this.connected$.next(true);
      client.subscribe(`/topic/diagrams/${diagramId}/events`, (message: IMessage) => {
        this.events$.next(JSON.parse(message.body) as DiagramEvent);
      });
      client.subscribe(`/topic/diagrams/${diagramId}/presence`, (message: IMessage) => {
        this.presence$.next(JSON.parse(message.body) as PresenceInfo[]);
      });
    };
    client.onWebSocketClose = () => this.connected$.next(false);

    client.activate();
    this.client = client;
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
    this.currentDiagramId = null;
  }
}
